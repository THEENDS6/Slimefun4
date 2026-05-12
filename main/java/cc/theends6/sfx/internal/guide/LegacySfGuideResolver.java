package cc.theends6.sfx.internal.guide;

import cc.theends6.sfx.api.guide.GuideMode;
import cc.theends6.sfx.api.item.SfxItemCategory;
import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.internal.item.DefaultSfxItemRegistry;
import cc.theends6.sfx.internal.util.HeadTextures;
import cc.theends6.sfx.internal.util.ItemBuilder;
import cc.theends6.sfx.internal.util.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class LegacySfGuideResolver {
    private static final Map<String, SfxItemCategory> VIRTUAL_CATEGORIES = LegacySfGuideData.createVirtualCategories();
    private static final Map<String, String> EXACT_CATEGORY_BY_ITEM = LegacySfGuideData.createExactCategoryByItem();
    private static final Map<String, String> SOURCE_CATEGORY_FALLBACKS = LegacySfGuideData.createSourceCategoryFallbacks();
    private static final Map<String, Integer> CLASSIC_ITEM_ORDER = LegacySfGuideData.createClassicItemOrder();
    private static final Map<String, Integer> CLASSIC_BASIC_MACHINE_ORDER = LegacySfGuideData.createClassicBasicMachineOrder();
    private static final Map<String, Integer> CLASSIC_CARGO_ORDER = LegacySfGuideData.createClassicCargoOrder();
    private static final Map<String, Integer> CLASSIC_ARMOR_ORDER = LegacySfGuideData.createClassicArmorOrder();
    private static final Map<String, Integer> CLASSIC_ELECTRICITY_ORDER = LegacySfGuideData.createClassicElectricityOrder();
    private static final Map<String, Integer> CLASSIC_ITEM_ORDER_OVERRIDES = LegacySfGuideData.createClassicItemOrderOverrides();
    private static final Map<String, List<String>> LOCKED_CATEGORY_PARENTS = LegacySfGuideData.createLockedCategoryParents();
    private static final Set<String> SEASONAL_CATEGORIES = Set.of(
            "guide:sf:christmas",
            "guide:sf:valentines_day",
            "guide:sf:easter",
            "guide:sf:birthday",
            "guide:sf:halloween"
    );

    private LegacySfGuideResolver() {
    }

    static List<SfxItemCategory> visibleCategories(DefaultSfxItemRegistry registry, GuideMode mode) {
        List<SfxItemCategory> categories = new ArrayList<>();
        for (SfxItemCategory category : registry.categories()) {
            if (category.id().startsWith("sf:")) {
                continue;
            }
            if ("sfx:internal".equals(category.id())) {
                continue;
            }
            if (!category.hidden() || mode == GuideMode.CHEAT) {
                categories.add(category);
            }
        }
        for (SfxItemCategory category : VIRTUAL_CATEGORIES.values()) {
            if (!visibleItemsInCategory(registry, category.id()).isEmpty()) {
                categories.add(category);
            }
        }
        categories.sort(Comparator
                .comparingInt(LegacySfGuideResolver::categoryOrder)
                .thenComparing(SfxItemCategory::id));
        return categories;
    }

    static Optional<SfxItemCategory> resolveCategory(DefaultSfxItemRegistry registry, String categoryId) {
        SfxItemCategory virtual = VIRTUAL_CATEGORIES.get(categoryId);
        if (virtual != null) {
            return Optional.of(virtual);
        }
        return registry.category(categoryId).filter(category -> !category.id().startsWith("sf:"));
    }

    static List<String> parentCategories(String categoryId) {
        return LOCKED_CATEGORY_PARENTS.getOrDefault(categoryId, List.of());
    }

    static boolean isSeasonalCategory(String categoryId) {
        return SEASONAL_CATEGORIES.contains(categoryId);
    }

    static List<SfxItemDefinition> visibleItemsInCategory(DefaultSfxItemRegistry registry, String categoryId) {
        if (!VIRTUAL_CATEGORIES.containsKey(categoryId)) {
            return registry.visibleItemsInCategory(categoryId).stream().toList();
        }
        return registry.items().stream()
                .filter(item -> !item.hidden())
                .filter(LegacySfGuideResolver::isLegacySlimefunItem)
                .filter(item -> categoryId.equals(resolveLegacyGuideCategory(item)))
                .sorted(Comparator
                        .comparingInt(LegacySfGuideResolver::legacySuggestedOrder)
                        .thenComparing(SfxItemDefinition::id))
                .toList();
    }

    public static int legacySuggestedOrder(SfxItemDefinition item) {
        String guideCategory = resolveLegacyGuideCategory(item);
        if ("guide:sf:basic_machines".equals(guideCategory)) {
            Integer order = CLASSIC_BASIC_MACHINE_ORDER.get(item.id());
            if (order != null) {
                return order;
            }
        }
        if ("guide:sf:cargo".equals(guideCategory)) {
            Integer cargoOrder = CLASSIC_CARGO_ORDER.get(item.id());
            if (cargoOrder != null) {
                return cargoOrder;
            }
        }
        if ("guide:sf:armor".equals(guideCategory)) {
            Integer armorOrder = CLASSIC_ARMOR_ORDER.get(item.id());
            if (armorOrder != null) {
                return armorOrder;
            }
        }
        if ("guide:sf:electricity".equals(guideCategory)) {
            Integer electricityOrder = CLASSIC_ELECTRICITY_ORDER.get(item.id());
            if (electricityOrder != null) {
                return electricityOrder;
            }
        }
        Integer overridden = CLASSIC_ITEM_ORDER_OVERRIDES.get(item.id());
        if (overridden != null) {
            return overridden;
        }
        if (item.order() != SfxItemDefinition.DEFAULT_ORDER) {
            return item.order();
        }
        return CLASSIC_ITEM_ORDER.getOrDefault(item.id(), SfxItemDefinition.DEFAULT_ORDER);
    }

    private static int categoryOrder(SfxItemCategory category) {
        if (VIRTUAL_CATEGORIES.containsKey(category.id())) {
            return category.order();
        }
        return 10_000 + category.order();
    }

    private static boolean isLegacySlimefunItem(SfxItemDefinition item) {
        return item.flags().contains("legacy-sf") || item.id().startsWith("sf:");
    }

    private static String resolveLegacyGuideCategory(SfxItemDefinition item) {
        String id = item.id();
        if (isEnderTalismanId(id)) {
            return "guide:sf:ender_talismans";
        }
        if (isTalismanId(id)) {
            return "guide:sf:talismans";
        }
        String exact = EXACT_CATEGORY_BY_ITEM.get(item.id());
        if (exact != null) {
            return exact;
        }
        if (id.endsWith("_xmas")) {
            return "guide:sf:christmas";
        }
        if (id.endsWith("_valentine")) {
            return "guide:sf:valentines_day";
        }
        if (id.endsWith("_halloween")) {
            return "guide:sf:halloween";
        }
        if ("sf:birthday_cake".equals(id)) {
            return "guide:sf:birthday";
        }
        return SOURCE_CATEGORY_FALLBACKS.getOrDefault(item.categoryId(), "guide:sf:misc");
    }


    private static boolean isEnderTalismanId(String id) {
        return "sf:ender_talisman".equals(id) || (id.startsWith("sf:ender_") && id.endsWith("_talisman"));
    }

    private static boolean isTalismanId(String id) {
        return "sf:common_talisman".equals(id) || (id.endsWith("_talisman") && !isEnderTalismanId(id));
    }


}
