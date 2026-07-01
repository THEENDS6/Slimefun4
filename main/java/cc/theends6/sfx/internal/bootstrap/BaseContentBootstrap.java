package cc.theends6.sfx.internal.bootstrap;

import cc.theends6.sfx.api.item.SfxItemCategory;
import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItemKind;
import cc.theends6.sfx.api.item.SfxItemRegistry;
import cc.theends6.sfx.api.item.SfxRecipe;
import cc.theends6.sfx.api.item.SfxRecipeSlot;
import cc.theends6.sfx.internal.item.DefaultSfxItemRegistry;
import cc.theends6.sfx.internal.machine.DefaultManualMachineRegistry;
import cc.theends6.sfx.internal.machine.ManualMachineDefinition;
import cc.theends6.sfx.internal.util.ItemBuilder;
import cc.theends6.sfx.internal.util.Text;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

/**
 * Core bootstrap now only registers internal guide/system entries and the manual machine framework.
 * Player-visible classic content is loaded from YAML resources.
 */
public final class BaseContentBootstrap {
    public static final String ENHANCED_CRAFTING_TABLE = "sf:enhanced_crafting_table";
    public static final String GRIND_STONE = "sf:grind_stone";
    public static final String MANUAL_COMPRESSOR = "sf:compressor";
    public static final String ORE_CRUSHER = "sf:ore_crusher";
    public static final String ORE_WASHER = "sf:ore_washer";
    public static final String ARMOR_FORGE = "sf:armor_forge";
    public static final String MAKESHIFT_SMELTERY = "sf:makeshift_smeltery";
    public static final String SMELTERY = "sf:smeltery";
    public static final String PRESSURE_CHAMBER = "sf:pressure_chamber";
    public static final String MAGIC_WORKBENCH = "sf:magic_workbench";
    public static final String TABLE_SAW = "sf:table_saw";
    public static final String JUICER = "sf:juicer";
    public static final String AUTOMATED_PANNING_MACHINE = "sf:automated_panning_machine";

    private BaseContentBootstrap() {
    }

    public static void register(SfxItemRegistry registry, DefaultManualMachineRegistry machines) {
        registerInternalCategories(registry);
        registerGuideItems(registry);
        // Manual multiblock machine definitions are loaded from content/machines/manual-machines.yml.
    }

    public static void syncManualMachineGuideContent(DefaultSfxItemRegistry registry, DefaultManualMachineRegistry machines) {
        for (ManualMachineDefinition machine : machines.machines()) {
            registry.item(machine.id()).ifPresent(machineItem ->
                    registry.replaceItem(withAddedRecipe(machineItem, structureRecipe(machine))));
        }
    }

    private static void registerInternalCategories(SfxItemRegistry registry) {
        registry.registerCategory(new SfxItemCategory(
                "sfx:internal",
                Text.mm("<dark_gray>Internal</dark_gray>"),
                ItemBuilder.of(Material.BARRIER).name("<dark_gray>Internal</dark_gray>").build(),
                999999,
                true
        ));
    }

    private static void registerGuideItems(SfxItemRegistry registry) {
        registry.registerItem(SfxItemDefinition.builder("sfx:guide", Material.ENCHANTED_BOOK, Text.mm("<green>SFX Guide</green>"))
                .category("sfx:internal")
                .kind(SfxItemKind.GUIDE)
                .hidden(true)
                .giveable(false)
                .build());

        registry.registerItem(SfxItemDefinition.builder("sfx:cheat_guide", Material.ENCHANTED_BOOK, Text.mm("<red>SFX Cheat Guide</red>"))
                .category("sfx:internal")
                .kind(SfxItemKind.GUIDE)
                .hidden(true)
                .giveable(false)
                .build());
    }

    private static SfxRecipe structureRecipe(ManualMachineDefinition machine) {
        return SfxRecipe.shaped("multiblock-structure", toRecipeMatrix(machine.displayPattern()), null);
    }

    private static List<SfxRecipeSlot> toRecipeMatrix(Material[] pattern) {
        List<SfxRecipeSlot> matrix = new ArrayList<>(9);
        for (Material material : pattern) {
            matrix.add(material == null ? SfxRecipeSlot.empty() : SfxRecipeSlot.vanilla(material));
        }
        return matrix;
    }

    private static SfxItemDefinition withAddedRecipe(SfxItemDefinition existing, SfxRecipe extra) {
        SfxItemDefinition.Builder builder = SfxItemDefinition.builder(existing.id(), existing.material(), existing.name())
                .lore(existing.lore())
                .category(existing.categoryId())
                .order(existing.order())
                .version(existing.version())
                .hidden(existing.hidden())
                .giveable(existing.giveable())
                .kind(existing.kind())
                .variant(existing.variant());
        if (existing.headTextureHash() != null) {
            builder.headTexture(existing.headTextureHash());
        }
        if (existing.colorRgb() != null) {
            builder.colorRgb(existing.colorRgb());
        }
        for (String flag : existing.flags()) {
            builder.flag(flag);
        }
        for (String itemFlag : existing.itemFlags()) {
            builder.itemFlag(itemFlag);
        }
        for (var enchantment : existing.enchantments().entrySet()) {
            builder.enchantment(enchantment.getKey(), enchantment.getValue());
        }
        builder.unbreakable(existing.unbreakable());
        for (SfxRecipe recipe : existing.recipes()) {
            builder.addRecipe(recipe);
        }
        builder.addRecipe(extra);
        return builder.build();
    }
}
