package cc.theends6.sfx.internal.guide;

import cc.theends6.sfx.api.energy.runtime.*;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.api.guide.GuideMode;
import cc.theends6.sfx.api.chat.SfxChatInputService;
import cc.theends6.sfx.api.guide.SfxGuide;
import cc.theends6.sfx.api.guide.SfxGuideAccessPolicy;
import cc.theends6.sfx.api.item.SfxItemCategory;
import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.item.SfxRecipe;
import cc.theends6.sfx.api.item.SfxRecipeSlot;
import cc.theends6.sfx.api.menu.SfxMenu;
import cc.theends6.sfx.api.menu.SfxMenuButton;
import cc.theends6.sfx.api.menu.SfxMenus;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.energy.SfxEnergyBalance;
import cc.theends6.sfx.api.energy.runtime.SfxEnergyComponentDefinition;
import cc.theends6.sfx.internal.energy.SfxEnergyDefinitions;
import cc.theends6.sfx.api.machine.runtime.SfxElectricStack;
import cc.theends6.sfx.api.machine.runtime.SfxElectricRecipe;
import cc.theends6.sfx.api.machine.runtime.SfxElectricMachineDefinition;
import cc.theends6.sfx.internal.item.DefaultSfxItemRegistry;
import cc.theends6.sfx.internal.machine.DefaultManualMachineRegistry;
import cc.theends6.sfx.api.machine.manual.SfxManualMachineDefinition;
import cc.theends6.sfx.api.machine.manual.SfxManualMachineOperation;
import cc.theends6.sfx.api.machine.manual.SfxManualMachineOutput;
import cc.theends6.sfx.api.machine.manual.SfxManualMachineRecipe;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.playerdata.SfxPlayerProfile;
import cc.theends6.sfx.internal.research.SfxResearchDefinition;
import cc.theends6.sfx.api.research.SfxResearchPaymentResult;
import cc.theends6.sfx.internal.research.SfxResearchPaymentRouter;
import cc.theends6.sfx.internal.research.SfxResearchService;
import cc.theends6.sfx.internal.recipe.DefaultSfxRecipeRegistry;
import cc.theends6.sfx.internal.recipe.SfxRecipeDefinition;
import cc.theends6.sfx.internal.recipe.SfxRecipeOutputDefinition;
import cc.theends6.sfx.internal.util.ItemBuilder;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.api.text.Text;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class DefaultSfxGuide implements SfxGuide {
    private static final String DEFAULT_GUIDE_SOUND_KEY = "minecraft:item.book.page_turn";
    private static final Map<String, String> SOUND_ALIASES = Map.of(
            "item_book_page_turn", DEFAULT_GUIDE_SOUND_KEY,
            "item.book.page_turn", DEFAULT_GUIDE_SOUND_KEY,
            "minecraft:item_book_page_turn", DEFAULT_GUIDE_SOUND_KEY
    );
    private static final Set<String> VERTICAL_SINGLE_RECIPE_TYPES = Set.of(
            "sf:composter",
            "sf:crucible",
            "sf:ore_washer",
            "sf:grind_stone",
            "sf:ore_crusher",
            "sf:juicer",
            "sf:automated_panning_machine"
    );
    private static final List<Tag<Material>> GUIDE_MATERIAL_VARIANT_TAGS = List.of(
            Tag.LOGS,
            Tag.WOODEN_FENCES,
            Tag.WOODEN_SLABS,
            Tag.WOODEN_TRAPDOORS
    );
    static final int[] RESEARCH_PROGRESS = {23, 44, 57, 92};
    private static final org.bukkit.Color[] RESEARCH_FIREWORK_COLORS = {
            org.bukkit.Color.AQUA,
            org.bukkit.Color.BLUE,
            org.bukkit.Color.FUCHSIA,
            org.bukkit.Color.GREEN,
            org.bukkit.Color.LIME,
            org.bukkit.Color.ORANGE,
            org.bukkit.Color.PURPLE,
            org.bukkit.Color.RED,
            org.bukkit.Color.YELLOW
    };
    private static final int[] CONTENT_SLOTS = {
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final int[] CLASSIC_RECIPE_SLOTS = {3, 4, 5, 12, 13, 14, 21, 22, 23};
    private static final int[] SFX_RECIPE_SLOTS_TOP = {3, 4, 5, 12, 13, 14, 21, 22, 23};
    private static final int[] SFX_RECIPE_SLOTS_NORMAL = {12, 13, 14, 21, 22, 23, 30, 31, 32};
    private static final int[] SFX_RECIPE_BORDER_TOP = {2, 6, 11, 15, 20, 24};
    private static final int[] SFX_RECIPE_BORDER_NORMAL = {11, 15, 20, 24, 29, 33};
    private static final int CLASSIC_VERTICAL_INPUT_SLOT = 13;
    private static final int CLASSIC_VERTICAL_OUTPUT_SLOT = 22;
    private static final int SFX_VERTICAL_INPUT_SLOT = 13;
    private static final int SFX_VERTICAL_OUTPUT_SLOT = 22;
    private static final int[] CLASSIC_DISPLAY_SLOTS = {
            36, 37, 38, 39, 40, 41, 42, 43, 44,
            45, 46, 47, 48, 49, 50, 51, 52, 53
    };
    private static final int[] SFX_DISPLAY_SLOTS_PAIRED = {
            36, 37, 38, 39, 40, 41, 42, 43, 44,
            45, 46, 47, 48, 49, 50, 51, 52, 53
    };
    private static final int[] SFX_DISPLAY_SLOTS_COMPACT = {45, 46, 47, 48, 49, 50, 51, 52, 53};
    private static final int CLASSIC_RECIPE_CENTER_SLOT = 13;
    private static final int SFX_RECIPE_CENTER_SLOT_TOP = 13;
    private static final int SFX_RECIPE_CENTER_SLOT_NORMAL = 22;
    private static final int CLASSIC_SOURCE_SLOT = 10;
    private static final int CLASSIC_OUTPUT_SLOT = 16;
    private static final int SFX_SOURCE_SLOT_TOP = 10;
    private static final int SFX_OUTPUT_SLOT_TOP = 16;
    private static final int SFX_SOURCE_SLOT_NORMAL = 19;
    private static final int SFX_OUTPUT_SLOT_NORMAL = 25;
    private static final int[] SETTINGS_BACKGROUND = {
            1, 3, 5, 7,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 26, 27, 35, 36, 37, 38, 39, 40,
            41, 42, 43, 44, 45, 46, 48, 50, 52, 53
    };
    private static final Comparator<DisplayEntry> DISPLAY_ENTRY_ORDER = Comparator
            .comparingInt(DisplayEntry::priority)
            .thenComparing(DisplayEntry::label);

    final JavaPlugin plugin;
    final SfxRuntime runtime;
    private final DefaultSfxItemRegistry registry;
    private final SfxItems items;
    private final SfxMenus menus;
    private final SfxChatInputService chatInput;
    private final SfxGuideAccessPolicy accessPolicy;
    private final SfxGuideSearchIndex searchIndex;
    private final DefaultManualMachineRegistry manualMachines;
    private final SfxLocalization localization;
    final SfxPlayerDataService profiles;
    final SfxResearchService researches;
    private final SfxResearchPaymentRouter researchPayments;
    private final Map<UUID, GuidePreferences> preferencesByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, AggregatedRecipeView>> aggregatedRecipeViews = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, DisplaySection>> displaySectionsByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, List<DisplaySection>>> displaySectionPreferencesByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> activeRecipeTrails = new ConcurrentHashMap<>();
    private final Map<Material, List<GuideRecipePage>> vanillaRecipeCache = new ConcurrentHashMap<>();
    private volatile Map<String, SfxEnergyComponentDefinition> guideEnergyDefinitions;
    private volatile DefaultSfxRecipeRegistry recipeRegistry;
    private volatile List<SfxElectricMachineDefinition> electricMachines = List.of();
    final Set<UUID> researchingPlayers = ConcurrentHashMap.newKeySet();

    public DefaultSfxGuide(
            JavaPlugin plugin,
            SfxRuntime runtime,
            DefaultSfxItemRegistry registry,
            SfxItems items,
            SfxMenus menus,
            SfxChatInputService chatInput,
            SfxGuideAccessPolicy accessPolicy,
            DefaultManualMachineRegistry manualMachines,
            SfxLocalization localization,
            SfxPlayerDataService profiles,
            SfxResearchService researches,
            SfxResearchPaymentRouter researchPayments
    ) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.registry = registry;
        this.items = items;
        this.menus = menus;
        this.chatInput = chatInput;
        this.accessPolicy = accessPolicy;
        this.searchIndex = new SfxGuideSearchIndex(registry);
        this.manualMachines = manualMachines;
        this.localization = localization;
        this.profiles = profiles;
        this.researches = researches;
        this.researchPayments = Objects.requireNonNull(researchPayments, "researchPayments");
    }

    public void bindRecipeRegistry(DefaultSfxRecipeRegistry recipeRegistry) {
        this.recipeRegistry = Objects.requireNonNull(recipeRegistry, "recipeRegistry");
        vanillaRecipeCache.clear();
    }

    public void bindElectricMachines(Collection<SfxElectricMachineDefinition> definitions) {
        this.electricMachines = definitions == null ? List.of() : List.copyOf(definitions);
    }

    @Override
    public void open(Player player, GuideMode mode) {
        if (!accessPolicy.canOpen(player, mode)) {
            player.sendMessage(Text.prefixed(plugin, tr("guide.errors.no-open")));
            return;
        }
        GuidePreferences preferences = preferences(player);
        if (preferences.reopenLastLocation() && reopenLastLocation(player, mode, preferences.lastLocation())) {
            playGuideOpenSound(player);
            return;
        }
        openMain(player, mode, 0, Navigation.ROOT);
        playGuideOpenSound(player);
    }

    @Override
    public void openSettings(Player player, GuideMode mode) {
        if (!accessPolicy.canOpen(player, mode)) {
            player.sendMessage(Text.prefixed(plugin, tr("guide.errors.no-settings")));
            return;
        }
        openSettingsView(player, mode, Navigation.ROOT);
    }

    void openSettingsView(Player player, GuideMode mode, Navigation navigation) {
        SfxGuideSettingsView.open(this, player, mode, navigation);
    }

    private void openMain(Player player, GuideMode mode, int page, Navigation navigation) {
        rememberLocation(player, GuideLocation.main(mode, page));
        List<SfxItemCategory> visibleCategories = visibleCategoriesFor(player, mode);
        int pageCount = pageCount(visibleCategories.size());
        int safePage = clampPage(page, pageCount);

        SfxMenu.Builder builder = SfxMenu.builder(title(mode, tr("guide.main.title"))).rows(6)
                .historyKey("guide:main:" + mode + ":" + safePage);
        paintFrame(builder, mode, effectiveLayout(preferences(player)));

        int from = safePage * CONTENT_SLOTS.length;
        int to = Math.min(visibleCategories.size(), from + CONTENT_SLOTS.length);
        for (int i = from; i < to; i++) {
            SfxItemCategory category = visibleCategories.get(i);
            int slot = CONTENT_SLOTS[i - from];
            if (mode == GuideMode.SURVIVAL && !isCategoryUnlocked(player, category.id())) {
                builder.button(slot, new SfxMenuButton(lockedCategoryIcon(player, category), click -> {
                }));
            } else {
                builder.button(slot, new SfxMenuButton(categoryButtonIcon(category), click -> {
                    playGuideCategorySound(click.player());
                    openCategory(click.player(), mode, category.id(), 0, Navigation.OPEN);
                }));
            }
        }

        addContentPagination(builder, safePage, pageCount,
                previous -> openMain(previous, mode, safePage - 1, Navigation.OPEN),
                next -> openMain(next, mode, safePage + 1, Navigation.OPEN));
        builder.button(49, new SfxMenuButton(infoIcon(mode, safePage, pageCount), click -> closeGuide(click.player())));
        showMenu(player, builder, navigation);
    }

    private void openCategory(Player player, GuideMode mode, String categoryId, int page, Navigation navigation) {
        if (!isCategoryVisible(categoryId)) {
            openMain(player, mode, 0, navigation == Navigation.ROOT ? Navigation.ROOT : Navigation.REPLACE);
            return;
        }
        if (mode == GuideMode.SURVIVAL && !isCategoryUnlocked(player, categoryId)) {
            openMain(player, mode, 0, navigation == Navigation.ROOT ? Navigation.ROOT : Navigation.REPLACE);
            return;
        }
        rememberLocation(player, GuideLocation.category(mode, categoryId, page));
        Optional<SfxItemCategory> optionalCategory = LegacySfGuideResolver.resolveCategory(registry, categoryId);
        if (optionalCategory.isEmpty()) {
            player.sendMessage(Text.prefixed(plugin, tr("guide.errors.missing-category")));
            return;
        }
        SfxItemCategory category = optionalCategory.get();
        if (!accessPolicy.canViewCategory(player, mode, category)) {
            openMain(player, mode, 0, navigation == Navigation.ROOT ? Navigation.ROOT : Navigation.REPLACE);
            return;
        }
        List<SfxItemDefinition> entries = LegacySfGuideResolver.visibleItemsInCategory(registry, category.id()).stream()
                .filter(item -> accessPolicy.canViewItem(player, mode, item))
                .toList();

        int pageCount = pageCount(entries.size());
        int safePage = clampPage(page, pageCount);

        SfxMenu.Builder builder = SfxMenu.builder(title(mode, plainCategoryName(category))).rows(6)
                .historyKey("guide:category:" + mode + ":" + category.id() + ":" + safePage);
        paintFrame(builder, mode, effectiveLayout(preferences(player)));

        int from = safePage * CONTENT_SLOTS.length;
        int to = Math.min(entries.size(), from + CONTENT_SLOTS.length);
        for (int i = from; i < to; i++) {
            SfxItemDefinition definition = entries.get(i);
            int slot = CONTENT_SLOTS[i - from];
            SfxResearchDefinition research = researches.researchForItem(definition.id()).orElse(null);
            boolean locked = mode == GuideMode.SURVIVAL && research != null && !isUnlocked(player, research);
            ItemStack icon = locked ? lockedItemIcon(player, definition, research) : items.create(definition, 1);
            if (mode == GuideMode.CHEAT) {
                Optional<SfxManualMachineDefinition> manualMachine = manualMachines.machine(definition.id())
                        .or(() -> cc.theends6.sfx.internal.machine.ExtraDeployStructures.machine(definition.id()));
                if (manualMachine.isPresent()) {
                    icon = withLore(icon, List.of(
                            Component.empty(),
                            Text.mm(tr("guide.cheat.machine-pack")),
                            Text.mm(tr("guide.cheat.machine-kit")),
                            Text.mm(tr("guide.actions.view-uses"))
                    ));
                } else {
                    icon = withLore(icon, List.of(
                            Component.empty(),
                            Text.mm(tr("guide.cheat.take-one")),
                            Text.mm(tr("guide.cheat.take-stack")),
                            Text.mm(tr("guide.actions.view-uses"))
                    ));
                }
                builder.button(slot, new SfxMenuButton(icon, click -> {
                    if (click.clickType().isRightClick()) {
                        openUses(click.player(), mode, definition, 0, Navigation.OPEN);
                    } else {
                        giveFromCheatGuide(click.player(), definition, click.clickType());
                    }
                }));
            } else if (locked) {
                builder.button(slot, new SfxMenuButton(icon, click -> unlockResearchAndRefresh(click.player(), mode, category.id(), safePage, definition, research)));
            } else {
                icon = withLore(icon, List.of(Component.empty(),
                        Text.mm(tr("guide.actions.open-recipe")), Text.mm(tr("guide.actions.view-uses"))));
                builder.button(slot, new SfxMenuButton(icon, click -> {
                    if (click.clickType().isRightClick()) openUses(click.player(), mode, definition, 0, Navigation.OPEN);
                    else openRecipe(click.player(), mode, definition.id(), 0, Navigation.OPEN);
                }));
            }
        }

        builder.button(1, new SfxMenuButton(backIcon(tr("guide.actions.back-main")), click -> goBack(click.player(), mode)));
        addContentPagination(builder, safePage, pageCount,
                previous -> openCategory(previous, mode, category.id(), safePage - 1, Navigation.OPEN),
                next -> openCategory(next, mode, category.id(), safePage + 1, Navigation.OPEN));
        builder.button(49, new SfxMenuButton(infoIcon(mode, safePage, pageCount), click -> closeGuide(click.player())));
        showMenu(player, builder, navigation);
    }

    private void beginSearch(Player player, GuideMode mode) {
        if (!plugin.getConfig().getBoolean("guide.search.enabled", true)) {
            player.sendMessage(Text.prefixed(plugin, tr("guide.search.disabled")));
            return;
        }
        menus.suspend(player);
        player.sendMessage(Text.prefixed(plugin, tr("guide.search.prompt")));
        int timeoutSeconds = Math.max(5, plugin.getConfig().getInt("guide.search.timeout-seconds", 30));
        chatInput.await(player, "guide-search", Duration.ofSeconds(timeoutSeconds), input -> {
            if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("取消")) {
                player.sendMessage(Text.prefixed(plugin, tr("guide.search.cancelled")));
                menus.resume(player);
                return;
            }
            String query = limitSearchInput(input);
            if (SfxGuideSearchIndex.normalize(query).isEmpty()) {
                player.sendMessage(Text.prefixed(plugin, tr("guide.search.empty")));
                menus.resume(player);
                return;
            }
            openSearchResults(player, mode, query, 0, Navigation.OPEN);
        }, () -> {
            player.sendMessage(Text.prefixed(plugin, tr("guide.search.timeout")));
            menus.resume(player);
        });
    }

    private void openSearchResults(Player player, GuideMode mode, String query, int page, Navigation navigation) {
        List<SfxItemDefinition> results = searchIndex.search(
                        query,
                        plugin.getConfig().getBoolean("guide.search.pinyin-enabled", true),
                        plugin.getConfig().getBoolean("guide.search.pinyin-initials-enabled", true))
                .stream()
                .filter(item -> accessPolicy.canViewItem(player, mode, item))
                .filter(item -> searchCategoryId(item) == null || isCategoryVisible(searchCategoryId(item)))
                .filter(item -> mode != GuideMode.SURVIVAL || searchCategoryId(item) == null || isCategoryUnlocked(player, searchCategoryId(item)))
                .toList();
        int pageCount = pageCount(results.size());
        int safePage = clampPage(page, pageCount);
        SfxMenu.Builder builder = SfxMenu.builder(title(mode, tr("guide.search.title").replace("{query}", query))).rows(6)
                .historyKey("guide:search:" + mode + ":" + query + ":" + safePage);
        paintFrame(builder, mode, effectiveLayout(preferences(player)));

        int from = safePage * CONTENT_SLOTS.length;
        int to = Math.min(results.size(), from + CONTENT_SLOTS.length);
        for (int i = from; i < to; i++) {
            SfxItemDefinition definition = results.get(i);
            SfxResearchDefinition research = researches.researchForItem(definition.id()).orElse(null);
            boolean locked = mode == GuideMode.SURVIVAL && research != null && !isUnlocked(player, research);
            ItemStack icon = locked ? lockedItemIcon(player, definition, research) : searchResultIcon(definition);
            int slot = CONTENT_SLOTS[i - from];
            if (mode == GuideMode.CHEAT) {
                icon = withLore(icon, List.of(
                        Text.mm(tr("guide.cheat.take-one")),
                        Text.mm(tr("guide.cheat.take-stack"))));
                builder.button(slot, new SfxMenuButton(icon, click -> {
                    if (click.clickType().isRightClick()) {
                        openUses(click.player(), mode, definition, 0, Navigation.OPEN);
                    } else {
                        giveFromCheatGuide(click.player(), definition, click.clickType());
                    }
                }));
            } else if (locked) {
                builder.button(slot, new SfxMenuButton(icon, click -> unlockResearchAndOpen(click.player(), mode, definition, research)));
            } else {
                builder.button(slot, new SfxMenuButton(icon, click -> {
                    if (click.clickType().isRightClick()) openUses(click.player(), mode, definition, 0, Navigation.OPEN);
                    else openRecipe(click.player(), mode, definition.id(), 0, Navigation.OPEN);
                }));
            }
        }
        if (results.isEmpty()) {
            builder.button(22, new SfxMenuButton(ItemBuilder.of(Material.BARRIER)
                    .name(tr("guide.search.no-results"))
                    .build(), click -> { }));
        }
        builder.button(1, new SfxMenuButton(backIcon(tr("guide.actions.back-guide")), click -> goBack(click.player(), mode)));
        addContentPagination(builder, safePage, pageCount,
                previous -> openSearchResults(previous, mode, query, safePage - 1, Navigation.OPEN),
                next -> openSearchResults(next, mode, query, safePage + 1, Navigation.OPEN));
        builder.button(49, new SfxMenuButton(infoIcon(mode, safePage, pageCount), click -> closeGuide(click.player())));
        showMenu(player, builder, navigation);
    }

    private ItemStack searchResultIcon(SfxItemDefinition definition) {
        List<Component> lore = new ArrayList<>();
        String categoryId = searchCategoryId(definition);
        if (categoryId != null) {
            LegacySfGuideResolver.resolveCategory(registry, categoryId)
                    .map(this::plainCategoryName)
                    .ifPresent(name -> lore.add(Text.mm(tr("guide.search.category").replace("{category}", name))));
        }
        lore.add(Component.empty());
        lore.add(Text.mm(tr("guide.actions.open-recipe")));
        lore.add(Text.mm(tr("guide.actions.view-uses")));
        return withLore(items.create(definition, 1), lore);
    }

    private String searchCategoryId(SfxItemDefinition definition) {
        return definition.guideCategoryId() == null ? definition.categoryId() : definition.guideCategoryId();
    }

    private String limitSearchInput(String input) {
        String value = input == null ? "" : input.trim();
        int maximum = Math.max(1, plugin.getConfig().getInt("guide.search.max-query-length", 64));
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private void openRecipe(Player player, GuideMode mode, String itemId, int recipeIndex, Navigation navigation) {
        openRecipe(player, mode, itemId, recipeIndex, 0, navigation, List.of());
    }

    private void openRecipe(Player player, GuideMode mode, String itemId, int recipeIndex, int extraDisplayPage, Navigation navigation) {
        openRecipe(player, mode, itemId, recipeIndex, extraDisplayPage, navigation, List.of());
    }

    private void openRecipe(Player player, GuideMode mode, String itemId, int recipeIndex, int extraDisplayPage,
                            Navigation navigation, List<String> trail) {
        rememberLocation(player, GuideLocation.recipe(mode, itemId, recipeIndex));
        Optional<SfxItemDefinition> optional = registry.item(itemId);
        if (optional.isEmpty()) {
            player.sendMessage(Text.prefixed(plugin, tr("guide.errors.missing-item")));
            return;
        }
        SfxItemDefinition definition = optional.get();
        if (mode == GuideMode.SURVIVAL) {
            Optional<SfxResearchDefinition> research = researches.researchForItem(definition.id());
            if (research.isPresent() && !isUnlocked(player, research.get())) {
                openLockedResearchView(player, mode, definition, research.get(), navigation);
                return;
            }
        }
        List<GuideRecipePage> pages = sfxRecipePages(definition);
        int pageCount = Math.max(1, pages.size());
        int safeRecipe = clampPage(recipeIndex, pageCount);
        String node = "sfx:" + definition.id() + "#" + safeRecipe;
        boolean cycleTerminal = trail.contains(node) || trail.size() >= 100;
        List<String> nextTrail = appendTrail(trail, node);
        activeRecipeTrails.put(player.getUniqueId(), nextTrail);
        GuideLayout layout = effectiveLayout(preferences(player));
        OutputAction outputAction = (targetPlayer, clickType) -> {
            if (clickType == ClickType.MIDDLE) {
                openWithDisplayPreference(targetPlayer, mode, definition, safeRecipe, Navigation.REPLACE, trail,
                        DisplaySection.FUNCTIONS, DisplaySection.USAGES, DisplaySection.SOURCES);
                return;
            }
            if (clickType != null && clickType.isRightClick()) {
                openWithDisplayPreference(targetPlayer, mode, definition, safeRecipe, Navigation.REPLACE, trail,
                        DisplaySection.USAGES, DisplaySection.FUNCTIONS, DisplaySection.SOURCES);
                return;
            }
            if (mode == GuideMode.CHEAT) {
                giveFromCheatGuide(targetPlayer, definition, clickType);
            }
        };
        RecipePageOpener opener = (targetPlayer, nextRecipe, nextNavigation) -> openRecipe(
                targetPlayer, mode, definition.id(), nextRecipe, 0, nextNavigation, trail);

        if (pages.isEmpty()) {
            renderRecipeWithoutEntries(player, mode, layout, itemDisplayName(definition), items.create(definition, 1), navigation, outputAction, definition, List.of(), opener);
            return;
        }

        GuideRecipePage current = pages.get(safeRecipe);
        if (cycleTerminal) {
            current = cycleTerminalPage(current, trail.size() >= 100);
        }
        DisplayContent display = cycleTerminal
                ? new DisplayContent(List.of(), DisplaySection.SOURCES, List.of(), false)
                : displayContentFor(player, definition, pages, current, mode, opener, nextTrail);
        renderRecipe(player, mode, layout, itemDisplayName(definition), items.create(definition, current.outputAmount()), pages, current, navigation, outputAction, definition,
                display.entries(), display.section(), display.availableSections(), display.pairedLayout(), opener, extraDisplayPage, nextTrail);
    }

    private static List<String> appendTrail(List<String> trail, String node) {
        List<String> copy = new ArrayList<>(Math.min(100, trail.size() + 1));
        copy.addAll(trail.stream().limit(99).toList());
        copy.add(node);
        return List.copyOf(copy);
    }

    private static List<String> parentTrail(List<String> trail) {
        if (trail == null || trail.isEmpty()) {
            return List.of();
        }
        return List.copyOf(trail.subList(0, trail.size() - 1));
    }

    private GuideRecipePage cycleTerminalPage(GuideRecipePage original, boolean depthLimit) {
        String reason = depthLimit ? tr("guide.recipe.depth-limit") : tr("guide.recipe.cycle-detected");
        return new GuideRecipePage(original.index(), original.origin(), original.sourceId(), "cycle-terminal",
                original.sourceName(), null,
                new ItemStack(Material.AIR),
                emptyMatrix(), Text.mm(reason),
                original.outputAmount(), false, original.recipeIds(), List.of());
    }

    private void openVanillaRecipe(Player player, GuideMode mode, Material material, int recipeIndex, Navigation navigation) {
        openVanillaRecipe(player, mode, material, recipeIndex, navigation, List.of());
    }

    private void openVanillaRecipe(Player player, GuideMode mode, Material material, int recipeIndex, Navigation navigation,
                                   List<String> trail) {
        openVanillaRecipe(player, mode, material, recipeIndex, navigation, trail, 0);
    }

    private void openVanillaRecipe(Player player, GuideMode mode, Material material, int recipeIndex, Navigation navigation,
                                   List<String> trail, int extraDisplayPage) {
        rememberLocation(player, GuideLocation.vanilla(mode, material, recipeIndex));
        if (!showVanillaRecipes()) {
            return;
        }
        List<GuideRecipePage> pages = vanillaRecipePages(material);
        if (pages.isEmpty()) {
            return;
        }
        int safeRecipe = clampPage(recipeIndex, pages.size());
        String node = "vanilla:" + material.name() + "#" + safeRecipe;
        boolean cycleTerminal = trail.contains(node) || trail.size() >= 100;
        List<String> nextTrail = appendTrail(trail, node);
        activeRecipeTrails.put(player.getUniqueId(), nextTrail);
        GuideRecipePage current = pages.get(safeRecipe);
        int recipeOutputAmount = current.outputAmount();
        if (cycleTerminal) {
            current = cycleTerminalPage(current, trail.size() >= 100);
        }
        GuideLayout layout = effectiveLayout(preferences(player));
        ItemStack output = new ItemStack(material, current.outputAmount());
        OutputAction outputAction = (targetPlayer, clickType) -> {
            if (clickType == ClickType.MIDDLE) {
                openWithDisplayPreference(targetPlayer, mode, material, safeRecipe, Navigation.REPLACE, trail,
                        DisplaySection.FUNCTIONS, DisplaySection.USAGES, DisplaySection.SOURCES);
                return;
            }
            if (clickType != null && clickType.isRightClick()) {
                openWithDisplayPreference(targetPlayer, mode, material, safeRecipe, Navigation.REPLACE, trail,
                        DisplaySection.USAGES, DisplaySection.FUNCTIONS, DisplaySection.SOURCES);
                return;
            }
            if (mode != GuideMode.CHEAT) {
                return;
            }
            int amount = clickType != null && clickType.isShiftClick() ? 64 : Math.max(1, recipeOutputAmount);
            targetPlayer.getInventory().addItem(new ItemStack(material, amount));
            targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
        };
        RecipePageOpener opener = (targetPlayer, nextRecipe, nextNavigation) -> openVanillaRecipe(targetPlayer, mode, material, nextRecipe, nextNavigation, trail);
        List<DisplayEntry> displayEntries = new ArrayList<>();
        DisplayContent display = new DisplayContent(List.of(), DisplaySection.SOURCES, List.of(), false);
        if (!cycleTerminal) {
            display = displayContentFor(player, material, pages, current, mode, opener, nextTrail);
            displayEntries.addAll(display.entries());
        }
        renderRecipe(player, mode, layout, materialName(material), output, pages, current, navigation, outputAction, null,
                displayEntries, display.section(), display.availableSections(), display.pairedLayout(), opener, extraDisplayPage, nextTrail);
    }

    private void openUses(Player player, GuideMode mode, SfxItemDefinition definition, int page, Navigation navigation) {
        openWithDisplayPreference(player, mode, definition, page, navigation, List.of(),
                DisplaySection.USAGES, DisplaySection.FUNCTIONS, DisplaySection.SOURCES);
    }

    private void openUses(Player player, GuideMode mode, Material material, int page, Navigation navigation) {
        openWithDisplayPreference(player, mode, material, page, navigation, List.of(),
                DisplaySection.USAGES, DisplaySection.FUNCTIONS, DisplaySection.SOURCES);
    }

    private void openFunctions(Player player, GuideMode mode, SfxItemDefinition definition, int page, Navigation navigation) {
        openWithDisplayPreference(player, mode, definition, page, navigation, List.of(),
                DisplaySection.FUNCTIONS, DisplaySection.USAGES, DisplaySection.SOURCES);
    }

    private void openFunctions(Player player, GuideMode mode, Material material, int page, Navigation navigation) {
        openWithDisplayPreference(player, mode, material, page, navigation, List.of(),
                DisplaySection.FUNCTIONS, DisplaySection.USAGES, DisplaySection.SOURCES);
    }

    private void openWithDisplayPreference(Player player, GuideMode mode, SfxItemDefinition definition, int recipeIndex,
                                           Navigation navigation, List<String> trail, DisplaySection... sections) {
        setPreferredDisplaySections(player, displaySectionKey(definition), sections);
        openRecipe(player, mode, definition.id(), recipeIndex, 0, navigation, trail == null ? List.of() : trail);
    }

    private void openWithDisplayPreference(Player player, GuideMode mode, Material material, int recipeIndex,
                                           Navigation navigation, List<String> trail, DisplaySection... sections) {
        setPreferredDisplaySections(player, displaySectionKey(material), sections);
        openVanillaRecipe(player, mode, material, recipeIndex, navigation, trail == null ? List.of() : trail);
    }

    private List<UsageTarget> usageTargets(SfxRecipeSlot ingredient, GuideMode mode) {
        Map<String, UsageTarget> targets = new LinkedHashMap<>();
        for (SfxItemDefinition result : registry.items()) {
            List<GuideRecipePage> pages = sfxRecipePages(result);
            for (int index = 0; index < pages.size(); index++) {
                GuideRecipePage page = pages.get(index);
                if (!pageUses(page, ingredient)) {
                    continue;
                }
                int recipeIndex = index;
                ItemStack icon = withLore(items.create(result, page.outputAmount()), List.of(
                        Component.empty(), Text.mm(tr("guide.actions.open-recipe"))));
                ClickHandler handler = click -> openRecipe(click.player(), mode, result.id(), recipeIndex, 0,
                        preferences(click.player()).recordHistory() ? Navigation.OPEN : Navigation.REPLACE, List.of());
                targets.putIfAbsent("sfx:" + result.id() + "#" + recipeIndex,
                        new UsageTarget(icon, handler));
            }
        }

        DefaultSfxRecipeRegistry compiledRecipes = recipeRegistry;
        if (compiledRecipes != null) {
            for (SfxRecipeDefinition recipe : compiledRecipes.definitions()) {
                if (!recipe.inputs().stream().anyMatch(slot -> sameIngredient(slot, ingredient))) {
                    continue;
                }
                for (SfxRecipeOutputDefinition output : recipe.allOutputs()) {
                    if (!output.isVanilla()) {
                        continue;
                    }
                    Material outputMaterial = output.material();
                    List<GuideRecipePage> pages = vanillaRecipePages(outputMaterial);
                    int recipeIndex = -1;
                    for (int index = 0; index < pages.size(); index++) {
                        if (pages.get(index).recipeIds().contains(recipe.id())) {
                            recipeIndex = index;
                            break;
                        }
                    }
                    if (recipeIndex < 0) {
                        continue;
                    }
                    int targetIndex = recipeIndex;
                    ItemStack icon = withLore(new ItemStack(outputMaterial, output.amount()), List.of(
                            Component.empty(), Text.mm(tr("guide.actions.open-recipe"))));
                    ClickHandler handler = click -> openVanillaRecipe(click.player(), mode, outputMaterial, targetIndex,
                            preferences(click.player()).recordHistory() ? Navigation.OPEN : Navigation.REPLACE, List.of());
                    targets.putIfAbsent("sfx-vanilla:" + outputMaterial.name() + "#" + recipe.id(),
                            new UsageTarget(icon, handler));
                }
            }
        }

        if (showVanillaRecipes()) {
            var iterator = Bukkit.recipeIterator();
            while (iterator.hasNext()) {
                Recipe recipe = iterator.next();
                GuideRecipePage raw = createVanillaRecipePage(recipe, 0);
                ItemStack result = recipe.getResult();
                if (raw == null || result == null || result.getType().isAir() || !pageUses(raw, ingredient)) {
                    continue;
                }
                List<GuideRecipePage> pages = vanillaRecipePages(result.getType());
                String recipeId = vanillaRecipeId(recipe);
                int recipeIndex = -1;
                for (int index = 0; index < pages.size(); index++) {
                    if (pages.get(index).recipeIds().contains(recipeId)) {
                        recipeIndex = index;
                        break;
                    }
                }
                if (recipeIndex < 0) {
                    continue;
                }
                int targetIndex = recipeIndex;
                Material targetMaterial = result.getType();
                ItemStack icon = withLore(result.clone(), List.of(
                        Component.empty(), Text.mm(tr("guide.actions.open-vanilla-recipe"))));
                ClickHandler handler = click -> openVanillaRecipe(click.player(), mode, targetMaterial, targetIndex,
                        preferences(click.player()).recordHistory() ? Navigation.OPEN : Navigation.REPLACE, List.of());
                targets.putIfAbsent("vanilla:" + targetMaterial.name() + "#" + targetIndex,
                        new UsageTarget(icon, handler));
            }
        }
        return List.copyOf(targets.values());
    }

    private List<DisplayEntry> itemUsageEntries(SfxItemDefinition definition, GuideMode mode, List<String> trail) {
        SfxRecipeSlot ingredient = SfxRecipeSlot.sfx(definition.id());
        List<DisplayEntry> entries = new ArrayList<>();
        int priority = 2000;
        for (UsageTarget target : usageTargets(ingredient, mode)) {
            entries.add(DisplayEntry.single(target.icon(), definition.id(), priority, target.handler(), DisplayEntryKind.RELATED));
            priority += 10;
        }
        return entries;
    }

    private List<DisplayEntry> itemUsageEntries(Material material, GuideMode mode, List<String> trail) {
        SfxRecipeSlot ingredient = SfxRecipeSlot.vanilla(material);
        List<DisplayEntry> entries = new ArrayList<>();
        int priority = 2000;
        for (UsageTarget target : usageTargets(ingredient, mode)) {
            entries.add(DisplayEntry.single(target.icon(), material.name(), priority, target.handler(), DisplayEntryKind.RELATED));
            priority += 10;
        }
        return entries;
    }

    private static boolean pageUses(GuideRecipePage page, SfxRecipeSlot ingredient) {
        return page.matrix().stream().anyMatch(slot -> sameIngredient(slot, ingredient))
                || page.inputAlternatives().stream().anyMatch(slot -> sameIngredient(slot, ingredient));
    }

    private static boolean sameIngredient(SfxRecipeSlot left, SfxRecipeSlot right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return false;
        }
        return left.isSfxItem() == right.isSfxItem()
                && (left.isSfxItem()
                ? Objects.equals(left.sfxItemId(), right.sfxItemId())
                : left.material() == right.material());
    }

    private void openLockedResearchView(Player player, GuideMode mode, SfxItemDefinition definition, SfxResearchDefinition research, Navigation navigation) {
        SfxMenu.Builder builder = SfxMenu.builder(title(mode, itemDisplayName(definition))).rows(3);
        builder.button(0, new SfxMenuButton(backIcon(tr("guide.actions.back-category")), click -> goBack(click.player(), mode)));
        builder.button(1, new SfxMenuButton(settingsIcon(), click -> openSettingsView(click.player(), mode, Navigation.OPEN)));
        builder.button(8, new SfxMenuButton(closeIcon(), click -> closeGuide(click.player())));
        builder.button(13, new SfxMenuButton(lockedItemIcon(player, definition, research), click -> unlockResearchAndOpen(click.player(), mode, definition, research)));
        builder.button(15, new SfxMenuButton(ItemBuilder.of(Material.EXPERIENCE_BOTTLE)
                .name(tr("guide.research.unlock.name"))
                .lore(
                        tr("guide.research.unlock.lore.1"),
                        researchCostDisplay(player, research)
                )
                .build(), click -> unlockResearchAndOpen(click.player(), mode, definition, research)));
        showMenu(player, builder, navigation);
    }

    private void renderRecipe(
            Player player,
            GuideMode mode,
            GuideLayout layout,
            String subjectTitle,
            ItemStack outputItem,
            List<GuideRecipePage> pages,
            GuideRecipePage current,
            Navigation navigation,
            OutputAction outputAction,
            SfxItemDefinition definition,
            List<DisplayEntry> displayEntries,
            DisplaySection displaySection,
            List<DisplaySection> availableDisplaySections,
            boolean pairedDisplayLayout,
            RecipePageOpener opener,
            int extraDisplayPage,
            List<String> trail
    ) {
        renderSfxRecipe(player, mode, subjectTitle, outputItem, pages, current, navigation, outputAction, definition, displayEntries,
                displaySection, availableDisplaySections, pairedDisplayLayout, opener, extraDisplayPage, trail);
    }

    private void renderRecipeWithoutEntries(
            Player player,
            GuideMode mode,
            GuideLayout layout,
            String subjectTitle,
            ItemStack outputItem,
            Navigation navigation,
            OutputAction outputAction,
            SfxItemDefinition definition,
            List<DisplayEntry> displayEntries,
            RecipePageOpener opener
    ) {
        GuideRecipePage empty = GuideRecipePage.noRecipe();
        renderRecipe(player, mode, layout, subjectTitle, outputItem, List.of(empty), empty, navigation, outputAction, definition, displayEntries,
                DisplaySection.SOURCES, List.of(), false, opener, 0, List.of());
    }

    private void renderClassicRecipe(
            Player player,
            GuideMode mode,
            String subjectTitle,
            ItemStack outputItem,
            List<GuideRecipePage> pages,
            GuideRecipePage current,
            Navigation navigation,
            OutputAction outputAction,
            SfxItemDefinition definition,
            List<DisplayEntry> displayEntries,
            RecipePageOpener opener
    ) {
        boolean special = !displayEntries.isEmpty();
        SfxMenu.Builder builder = SfxMenu.builder(title(mode, subjectTitle)).rows(special ? 6 : 3);

        builder.button(0, new SfxMenuButton(backIcon(tr("guide.actions.back-category")), click -> goBack(click.player(), mode)));
        builder.button(1, new SfxMenuButton(settingsIcon(), click -> openSettingsView(click.player(), mode, Navigation.OPEN)));
        builder.button(7, new SfxMenuButton(previousRecipeIcon(current.index(), pages.size()), click -> {
            if (current.index() > 0) {
                opener.open(click.player(), current.index() - 1, Navigation.REPLACE);
            }
        }));
        builder.button(8, new SfxMenuButton(nextRecipeIcon(current.index(), pages.size()), click -> {
            if (current.index() + 1 < pages.size()) {
                opener.open(click.player(), current.index() + 1, Navigation.REPLACE);
            }
        }));

        if (current.hasRecipe()) {
            for (int i = 0; i < CLASSIC_RECIPE_SLOTS.length; i++) {
                SfxRecipeSlot slot = current.matrix().get(i);
                addIngredientButton(builder, CLASSIC_RECIPE_SLOTS[i], slot, mode,
                        current.cycleMaterialVariants(), current.inputAlternatives(), List.of());
            }
        } else {
            for (int slot : CLASSIC_RECIPE_SLOTS) {
                builder.button(slot, new SfxMenuButton(emptyMatrixSlotIcon(), click -> {
                }));
            }
            builder.button(CLASSIC_RECIPE_CENTER_SLOT, new SfxMenuButton(ItemBuilder.of(Material.BARRIER)
                    .name(tr("guide.recipe.no-recipe.name"))
                    .lore(tr("guide.recipe.no-recipe.lore"))
                    .build(), click -> {
            }));
        }

        builder.button(CLASSIC_SOURCE_SLOT, recipeSourceButton(current, mode));
        builder.button(CLASSIC_OUTPUT_SLOT, new SfxMenuButton(withLore(outputItem, List.of(
                Component.empty(),
                Text.mm(tr("guide.recipe.output")),
                Text.mm(tr("guide.actions.view-uses")),
                Text.mm(tr("guide.actions.view-functions"))
        )), click -> outputAction.accept(click.player(), click.clickType())));

        if (special) {
            paintClassicDivider(builder, current, pages.size());
            renderDisplayEntries(builder, displayEntries, CLASSIC_DISPLAY_SLOTS);
        }

        showMenu(player, builder, navigation);
    }

    private void renderSfxRecipe(
            Player player,
            GuideMode mode,
            String subjectTitle,
            ItemStack outputItem,
            List<GuideRecipePage> pages,
            GuideRecipePage current,
            Navigation navigation,
            OutputAction outputAction,
            SfxItemDefinition definition,
            List<DisplayEntry> displayEntries,
            DisplaySection displaySection,
            List<DisplaySection> availableDisplaySections,
            boolean pairedDisplayLayout,
            RecipePageOpener opener,
            int extraDisplayPage,
            List<String> trail
    ) {
        SfxDisplayLayout displayLayout = sfxDisplayLayout(displayEntries, pairedDisplayLayout);
        DisplayPage displayPage = displayPage(displayEntries, displayLayout, extraDisplayPage);
        int[] recipeSlots = displayLayout == SfxDisplayLayout.PAIRED_GRID ? SFX_RECIPE_SLOTS_TOP : SFX_RECIPE_SLOTS_NORMAL;
        int recipeCenterSlot = displayLayout == SfxDisplayLayout.PAIRED_GRID ? SFX_RECIPE_CENTER_SLOT_TOP : SFX_RECIPE_CENTER_SLOT_NORMAL;
        int sourceSlot = displayLayout == SfxDisplayLayout.PAIRED_GRID ? SFX_SOURCE_SLOT_TOP : SFX_SOURCE_SLOT_NORMAL;
        int outputSlot = displayLayout == SfxDisplayLayout.PAIRED_GRID ? SFX_OUTPUT_SLOT_TOP : SFX_OUTPUT_SLOT_NORMAL;
        SfxMenu.Builder builder = SfxMenu.builder(title(mode, subjectTitle)).rows(6);
        paintRecipeFrame(builder, mode, displayLayout);
        AggregatedRecipeView aggregateView = aggregateView(player, current);

        if ("cycle-terminal".equals(current.sourceFamily())) {
            for (int slot : recipeSlots) {
                builder.button(slot, new SfxMenuButton(emptyMatrixSlotIcon(), click -> { }));
            }
            builder.button(recipeCenterSlot, new SfxMenuButton(ItemBuilder.of(Material.BARRIER)
                    .name("<red>" + tr("guide.recipe.cycle-blocked") + "</red>")
                    .build(), click -> { }));
        } else if (aggregateView != null && aggregateView.listMode()) {
            int first = aggregateView.page() * 9;
            for (int i = 0; i < recipeSlots.length; i++) {
                int alternative = first + i;
                if (alternative < current.inputAlternatives().size()) {
                    builder.button(recipeSlots[i], ingredientButton(current.inputAlternatives().get(alternative), mode, trail));
                } else {
                    builder.button(recipeSlots[i], new SfxMenuButton(emptyMatrixSlotIcon(), click -> { }));
                }
            }
        } else if (current.hasRecipe()) {
            for (int i = 0; i < recipeSlots.length; i++) {
                addIngredientButton(builder, recipeSlots[i], current.matrix().get(i), mode,
                        current.cycleMaterialVariants(), current.inputAlternatives(), trail);
            }
        } else {
            for (int slot : recipeSlots) {
                builder.button(slot, new SfxMenuButton(emptyMatrixSlotIcon(), click -> {
                }));
            }
            builder.button(recipeCenterSlot, new SfxMenuButton(ItemBuilder.of(Material.BARRIER)
                    .name(tr("guide.recipe.no-recipe.name"))
                    .lore(tr("guide.recipe.no-recipe.lore"))
                    .build(), click -> {
            }));
        }

        if ("cycle-terminal".equals(current.sourceFamily())) {
            builder.button(sourceSlot, new SfxMenuButton(new ItemStack(Material.AIR), click -> { }));
        } else {
            builder.button(sourceSlot, recipeSourceButton(current, mode, trail));
        }
        builder.button(outputSlot, new SfxMenuButton(withLore(outputItem, List.of(
                Component.empty(),
                Text.mm(tr("guide.recipe.output")),
                Text.mm(tr("guide.actions.view-uses")),
                Text.mm(tr("guide.actions.view-functions"))
        )), click -> outputAction.accept(click.player(), click.clickType())));

        if (aggregateView != null) {
            addAggregatedControls(builder, player, current, aggregateView, opener);
        }

        builder.button(0, new SfxMenuButton(backIcon(tr("guide.actions.back-category")), click -> goBack(click.player(), mode)));
        builder.button(1, new SfxMenuButton(settingsIcon(), click -> openSettingsView(click.player(), mode, Navigation.OPEN)));
        builder.button(8, new SfxMenuButton(closeIcon(), click -> closeGuide(click.player())));
        PageAction openDisplayPage = (target, targetPage) -> {
            if (definition != null) {
                openRecipe(target, mode, definition.id(), current.index(), targetPage, Navigation.REPLACE, parentTrail(trail));
            } else if (outputItem != null && !outputItem.getType().isAir()) {
                openVanillaRecipe(target, mode, outputItem.getType(), current.index(), Navigation.REPLACE, parentTrail(trail), targetPage);
            }
        };

        if (displayLayout == SfxDisplayLayout.PAIRED_GRID) {
            paintDetailDivider(builder, 27, current, pages.size());
            addDisplaySectionButtons(builder, 27, displaySection, availableDisplaySections, displaySectionKey(definition, outputItem), opener, current);
            addDisplaySectionPagination(builder, 33, 34, 35, displayPage, displaySection, availableDisplaySections,
                    openDisplayPage);
            addRecipePagination(builder, 30, 31, 32, current.index(), pages.size(),
                    (target, targetPage) -> opener.open(target, targetPage, Navigation.REPLACE), current.sourceName());
            renderDisplayEntries(builder, displayPage.entries(), SFX_DISPLAY_SLOTS_PAIRED);
        } else {
            paintDetailDivider(builder, 36, current, pages.size());
            addDisplaySectionButtons(builder, 36, displaySection, availableDisplaySections, displaySectionKey(definition, outputItem), opener, current);
            addDisplaySectionPagination(builder, 42, 43, 44, displayPage, displaySection, availableDisplaySections,
                    openDisplayPage);
            addRecipePagination(builder, 39, 40, 41, current.index(), pages.size(),
                    (target, targetPage) -> opener.open(target, targetPage, Navigation.REPLACE), current.sourceName());
            if (displayLayout == SfxDisplayLayout.COMPACT_LIST) {
                renderDisplayEntries(builder, displayPage.entries(), SFX_DISPLAY_SLOTS_COMPACT);
            }
        }
        showMenu(player, builder, navigation);
    }

    private void addDisplaySectionPagination(SfxMenu.Builder builder, int previousSlot, int pageSlot, int nextSlot,
                                             DisplayPage page, DisplaySection section, List<DisplaySection> availableSections,
                                             PageAction openDisplayPage) {
        int pageCount = page.pageCount();
        builder.button(previousSlot, new SfxMenuButton(previousRecipeIcon(page.page(), pageCount), click -> {
            if (page.page() > 0) {
                openDisplayPage.accept(click.player(), page.page() - 1);
            }
        }));
        String sectionName = displaySectionName(section);
        ItemStack pageIcon = SfxGuideIconLibrary.page(sectionName, pageNumberLore(page.page(), pageCount), page.page() + 1);
        applyPageProgress(pageIcon, page.page(), pageCount);
        builder.button(pageSlot, new SfxMenuButton(pageIcon, click -> { }));
        builder.button(nextSlot, new SfxMenuButton(nextRecipeIcon(page.page(), pageCount), click -> {
            if (page.page() + 1 < pageCount) {
                openDisplayPage.accept(click.player(), page.page() + 1);
            }
        }));
    }

    private void addDisplaySectionButtons(SfxMenu.Builder builder, int firstSlot, DisplaySection selected,
                                          List<DisplaySection> availableSections, String sectionKey,
                                          RecipePageOpener opener, GuideRecipePage recipe) {
        DisplaySection[] sections = {DisplaySection.SOURCES, DisplaySection.FUNCTIONS, DisplaySection.USAGES};
        for (int i = 0; i < sections.length; i++) {
            DisplaySection section = sections[i];
            boolean available = availableSections != null && availableSections.contains(section);
            boolean active = selected == section;
            ItemStack icon = displaySectionButtonIcon(section, active, available);
            builder.button(firstSlot + i, new SfxMenuButton(icon, click -> {
                if (available && sectionKey != null) {
                    setDisplaySection(click.player(), sectionKey, section);
                    opener.open(click.player(), recipe.index(), Navigation.REPLACE);
                }
            }));
        }
    }

    private ItemStack displaySectionButtonIcon(DisplaySection section, boolean active, boolean available) {
        String name = displaySectionName(section);
        String lore = available ? tr("guide.recipe.section.select") : tr("guide.recipe.section.empty");
        ItemStack icon = switch (section) {
            case SOURCES -> SfxGuideIconLibrary.craftingTable(name, lore);
            case FUNCTIONS -> SfxGuideIconLibrary.furnace(name, lore);
            case USAGES -> SfxGuideIconLibrary.oakLog(name, lore);
        };
        applyFixedProgress(icon, active ? 99 : 1, 100);
        return icon;
    }

    private String displaySectionName(DisplaySection section) {
        return tr(switch (section) {
            case FUNCTIONS -> "guide.recipe.section.functions";
            case USAGES -> "guide.recipe.section.usages";
            case SOURCES -> "guide.recipe.section.sources";
        });
    }

    private AggregatedRecipeView aggregateView(Player player, GuideRecipePage page) {
        if (page.inputAlternatives().size() <= 1 || "cycle-terminal".equals(page.sourceFamily())) {
            return null;
        }
        String key = aggregatedRecipeKey(page);
        return aggregatedRecipeViews.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(key, ignored -> new AggregatedRecipeView(false, 0));
    }

    private void addAggregatedControls(SfxMenu.Builder builder, Player player, GuideRecipePage page,
                                       AggregatedRecipeView view, RecipePageOpener opener) {
        int indicatorSlot = 17;
        int toggleSlot = 26;
        if (!view.listMode()) {
            SfxMenuButton fallback = new SfxMenuButton(aggregateIndexIcon(0, page.inputAlternatives().size()), click -> { });
            builder.dynamicButton(indicatorSlot, fallback, ignored -> aggregateIndexIcon(
                    rotatingIndex(page.inputAlternatives().size()), page.inputAlternatives().size()));
        } else {
            int first = Math.min(page.inputAlternatives().size() - 1, view.page() * 9);
            builder.button(indicatorSlot, new SfxMenuButton(aggregateIndexIcon(first, page.inputAlternatives().size()), click -> { }));
        }
        builder.button(toggleSlot, aggregateModeButton(player, page, view, opener));
    }

    private void addAggregatedListPagination(SfxMenu.Builder builder, Player player, GuideRecipePage page,
                                             AggregatedRecipeView view, RecipePageOpener opener, int firstSlot) {
        if (view == null || !view.listMode()) {
            return;
        }
        int pageCount = Math.max(1, (int) Math.ceil(page.inputAlternatives().size() / 9.0D));
        if (pageCount <= 1) {
            return;
        }
        builder.button(firstSlot, new SfxMenuButton(previousRecipeIcon(view.page(), pageCount), click -> {
            if (view.page() > 0) {
                setAggregateView(click.player(), page, new AggregatedRecipeView(true, view.page() - 1));
                opener.open(click.player(), page.index(), Navigation.REPLACE);
            }
        }));
        ItemStack pageIcon = SfxGuideIconLibrary.page(
                "<yellow>" + (view.page() + 1) + " / " + pageCount + "</yellow>",
                pageNumberLore(view.page(), pageCount), view.page() + 1);
        applyPageProgress(pageIcon, view.page(), pageCount);
        builder.button(firstSlot + 1, new SfxMenuButton(pageIcon, click -> { }));
        builder.button(firstSlot + 2, new SfxMenuButton(nextRecipeIcon(view.page(), pageCount), click -> {
            if (view.page() + 1 < pageCount) {
                setAggregateView(click.player(), page, new AggregatedRecipeView(true, view.page() + 1));
                opener.open(click.player(), page.index(), Navigation.REPLACE);
            }
        }));
    }

    private SfxMenuButton aggregateModeButton(Player player, GuideRecipePage page, AggregatedRecipeView view, RecipePageOpener opener) {
        String key = view.listMode() ? "guide.recipe.aggregate.mode-cycle" : "guide.recipe.aggregate.mode-list";
        Material material = view.listMode() ? Material.CLOCK : Material.CHEST;
        return new SfxMenuButton(ItemBuilder.of(material).name(tr(key)).lore(tr("guide.recipe.aggregate.toggle")).build(), click -> {
            setAggregateView(click.player(), page, new AggregatedRecipeView(!view.listMode(), 0));
            opener.open(click.player(), page.index(), Navigation.REPLACE);
        });
    }

    private void setAggregateView(Player player, GuideRecipePage page, AggregatedRecipeView view) {
        aggregatedRecipeViews.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put(aggregatedRecipeKey(page), view);
    }

    private static String aggregatedRecipeKey(GuideRecipePage page) {
        return page.origin() + "|" + page.sourceId() + "|" + String.join(",", page.recipeIds());
    }

    private ItemStack aggregateIndexIcon(int index, int size) {
        String name = tr("guide.recipe.aggregate.index")
                .replace("{current}", Integer.toString(index + 1))
                .replace("{total}", Integer.toString(size));
        ItemStack icon = SfxGuideIconLibrary.page(name, pageNumberLore(index, size), index + 1);
        applyPageProgress(icon, index, size);
        return icon;
    }

    private static int rotatingIndex(int size) {
        return size <= 1 ? 0 : (int) ((System.currentTimeMillis() / 1000L) % size);
    }

    private List<GuideRecipePage> sfxRecipePages(SfxItemDefinition definition) {
        List<GuideRecipePage> pages = new ArrayList<>();
        List<SfxRecipe> recipes = definition.recipes();
        for (int i = 0; i < recipes.size(); i++) {
            SfxRecipe recipe = recipes.get(i);
            pages.add(createSfxRecipePage(definition, recipe, i));
        }
        return List.copyOf(mergeEquivalentSingleInputPages(mergeVanillaCookingPages(pages)));
    }

    private List<GuideRecipePage> mergeVanillaCookingPages(List<GuideRecipePage> pages) {
        List<GuideRecipePage> merged = new ArrayList<>();
        boolean[] consumed = new boolean[pages.size()];
        for (int i = 0; i < pages.size(); i++) {
            if (consumed[i]) {
                continue;
            }
            GuideRecipePage page = pages.get(i);
            if (!"minecraft:furnace".equals(page.sourceId()) && !"minecraft:blast_furnace".equals(page.sourceId())) {
                merged.add(reindexPage(page, merged.size()));
                continue;
            }
            int counterpart = -1;
            String wanted = "minecraft:furnace".equals(page.sourceId()) ? "minecraft:blast_furnace" : "minecraft:furnace";
            for (int j = i + 1; j < pages.size(); j++) {
                GuideRecipePage candidate = pages.get(j);
                if (!consumed[j] && wanted.equals(candidate.sourceId())
                        && page.matrix().equals(candidate.matrix()) && page.outputAmount() == candidate.outputAmount()) {
                    counterpart = j;
                    break;
                }
            }
            if (counterpart >= 0) {
                consumed[counterpart] = true;
                String name = tr("guide.recipe.vanilla.smelting");
                merged.add(new GuideRecipePage(merged.size(), GuideRecipeOrigin.VANILLA,
                        "minecraft:smelting", "minecraft:smelting", name, null,
                        vanillaSourceIcon(Material.FURNACE, name), page.matrix(), page.note(), page.outputAmount(), false,
                        combinedRecipeIds(page, pages.get(counterpart)), combinedInputAlternatives(page, pages.get(counterpart))));
            } else {
                merged.add(reindexPage(page, merged.size()));
            }
        }
        return merged;
    }

    private List<GuideRecipePage> mergeEquivalentSingleInputPages(List<GuideRecipePage> pages) {
        List<GuideRecipePage> merged = new ArrayList<>();
        for (GuideRecipePage page : pages) {
            int inputIndex = singleInputIndex(page);
            if (inputIndex < 0) {
                merged.add(reindexPage(page, merged.size()));
                continue;
            }
            int target = -1;
            for (int i = 0; i < merged.size(); i++) {
                GuideRecipePage candidate = merged.get(i);
                if (singleInputIndex(candidate) == inputIndex
                        && candidate.outputAmount() == page.outputAmount()
                        && Objects.equals(candidate.origin(), page.origin())
                        && Objects.equals(candidate.sourceId(), page.sourceId())
                        && Objects.equals(candidate.machineTargetId(), page.machineTargetId())
                        && Objects.equals(candidate.note(), page.note())) {
                    target = i;
                    break;
                }
            }
            if (target < 0) {
                List<SfxRecipeSlot> alternatives = page.inputAlternatives().isEmpty()
                        ? List.of(page.matrix().get(inputIndex))
                        : page.inputAlternatives();
                merged.add(new GuideRecipePage(merged.size(), page.origin(), page.sourceId(), page.sourceFamily(), page.sourceName(),
                        page.machineTargetId(), page.sourceIcon(), page.matrix(), page.note(), page.outputAmount(), page.cycleMaterialVariants(),
                        page.recipeIds(), alternatives));
                continue;
            }
            GuideRecipePage existing = merged.get(target);
            LinkedHashSet<SfxRecipeSlot> alternatives = new LinkedHashSet<>(existing.inputAlternatives());
            alternatives.addAll(page.inputAlternatives().isEmpty()
                    ? List.of(page.matrix().get(inputIndex))
                    : page.inputAlternatives());
            merged.set(target, new GuideRecipePage(existing.index(), existing.origin(), existing.sourceId(), existing.sourceFamily(),
                    existing.sourceName(), existing.machineTargetId(), existing.sourceIcon(), existing.matrix(), existing.note(),
                    existing.outputAmount(), existing.cycleMaterialVariants(), combinedRecipeIds(existing, page), List.copyOf(alternatives)));
        }
        return merged;
    }

    private static int singleInputIndex(GuideRecipePage page) {
        int found = -1;
        for (int i = 0; i < page.matrix().size(); i++) {
            if (page.matrix().get(i).isEmpty()) {
                continue;
            }
            if (found >= 0) {
                return -1;
            }
            found = i;
        }
        return found;
    }

    private static List<String> combinedRecipeIds(GuideRecipePage first, GuideRecipePage second) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(first.recipeIds());
        ids.addAll(second.recipeIds());
        return List.copyOf(ids);
    }

    private static List<SfxRecipeSlot> combinedInputAlternatives(GuideRecipePage first, GuideRecipePage second) {
        LinkedHashSet<SfxRecipeSlot> alternatives = new LinkedHashSet<>(first.inputAlternatives());
        alternatives.addAll(second.inputAlternatives());
        return List.copyOf(alternatives);
    }

    private static GuideRecipePage reindexPage(GuideRecipePage page, int index) {
        return new GuideRecipePage(index, page.origin(), page.sourceId(), page.sourceFamily(), page.sourceName(),
                page.machineTargetId(), page.sourceIcon(), page.matrix(), page.note(), page.outputAmount(), page.cycleMaterialVariants(),
                page.recipeIds(), page.inputAlternatives());
    }

    private GuideRecipePage createSfxRecipePage(SfxItemDefinition resultDefinition, SfxRecipe recipe, int index) {
        if ("multiblock-structure".equals(recipe.recipeType())) {
            String sourceName = tr("guide.recipe.multiblock.name");
            ItemStack sourceIcon = multiblockSourceIcon();
            return new GuideRecipePage(index, GuideRecipeOrigin.SFX, resultDefinition.id(), familyKey(resultDefinition.id()), sourceName,
                    null, sourceIcon, normalizeMatrix(recipe.matrix()), recipe.note(), recipe.outputAmount(), true,
                    List.of(recipe.id()), List.of());
        }

        if ("sf:simple_smelting".equals(recipe.recipeType()) || "sf:alloy_smelting".equals(recipe.recipeType())) {
            boolean alloy = "sf:alloy_smelting".equals(recipe.recipeType());
            String sourceName = tr(alloy ? "guide.recipe.process.alloy-smelting" : "guide.recipe.process.simple-smelting");
            String machineId = alloy ? "sf:smeltery" : "sf:makeshift_smeltery";
            ItemStack sourceIcon = registry.item(machineId).map(item -> items.create(item, 1))
                    .orElseGet(() -> new ItemStack(alloy ? Material.FURNACE : Material.BLAST_FURNACE));
            return new GuideRecipePage(index, GuideRecipeOrigin.SFX, recipe.recipeType(), recipe.recipeType(), sourceName,
                    null, sourceIcon, normalizeMatrix(recipe.matrix()), recipe.note(), recipe.outputAmount(), false,
                    List.of(recipe.id()), List.of());
        }

        Optional<SfxManualMachineDefinition> machine = manualMachines.machine(recipe.recipeType());
        if (machine.isPresent()) {
            return new GuideRecipePage(index, GuideRecipeOrigin.SFX, machine.get().id(), familyKey(machine.get().id()), machineDisplayName(machine.get()),
                    machine.get().id(), machineSourceIcon(machine.get()), normalizeMatrix(recipe.matrix()), recipe.note(), recipe.outputAmount(),
                    false, List.of(recipe.id()), List.of());
        }

        Optional<SfxItemDefinition> machineDefinition = registry.item(recipe.recipeType());
        if (machineDefinition.isPresent()) {
            ItemStack sourceIcon = withLore(items.create(machineDefinition.get(), 1), List.of(Component.empty(), Text.mm(tr("guide.actions.open-recipe"))));
            return new GuideRecipePage(index, GuideRecipeOrigin.SFX, machineDefinition.get().id(), familyKey(machineDefinition.get().id()),
                    itemDisplayName(machineDefinition.get()), machineDefinition.get().id(), sourceIcon, normalizeMatrix(recipe.matrix()), recipe.note(), recipe.outputAmount(),
                    false, List.of(recipe.id()), List.of());
        }

        ItemStack sourceIcon = ItemBuilder.of(Material.BOOK).name("<green>" + recipe.recipeType() + "</green>").build();
        return new GuideRecipePage(index, GuideRecipeOrigin.SFX, recipe.recipeType(), familyKey(recipe.recipeType()),
                recipe.recipeType(), null, sourceIcon, normalizeMatrix(recipe.matrix()), recipe.note(), recipe.outputAmount(), false,
                List.of(recipe.id()), List.of());
    }

    private List<GuideRecipePage> vanillaRecipePages(Material material) {
        return vanillaRecipeCache.computeIfAbsent(material, this::loadVanillaRecipePages);
    }

    private List<GuideRecipePage> loadVanillaRecipePages(Material material) {
        List<GuideRecipePage> pages = new ArrayList<>();
        DefaultSfxRecipeRegistry compiledRecipes = recipeRegistry;
        if (compiledRecipes != null) {
            for (SfxRecipeDefinition recipe : compiledRecipes.definitions()) {
                for (SfxRecipeOutputDefinition output : recipe.allOutputs()) {
                    if (!output.isVanilla() || output.material() != material) {
                        continue;
                    }
                    GuideRecipePage page = createCompiledVanillaRecipePage(recipe, output, pages.size());
                    if (page != null) {
                        pages.add(page);
                    }
                }
            }
        }
        var iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            ItemStack result = recipe.getResult();
            if (result == null || result.getType() != material) {
                continue;
            }
            GuideRecipePage page = createVanillaRecipePage(recipe, pages.size());
            if (page != null) {
                pages.add(page);
            }
        }
        return List.copyOf(mergeEquivalentSingleInputPages(mergeVanillaCookingPages(pages)));
    }

    private GuideRecipePage createCompiledVanillaRecipePage(SfxRecipeDefinition recipe, SfxRecipeOutputDefinition output, int index) {
        List<SfxRecipeSlot> matrix = switch (recipe.operation()) {
            case SHAPED -> recipe.inputs();
            case SHAPELESS -> compiledShapelessMatrix(recipe.inputs());
            case SINGLE, HAND -> compiledSingleMatrix(recipe.inputs().getFirst());
        };
        Optional<SfxItemDefinition> machine = registry.item(recipe.recipeType());
        Optional<SfxManualMachineDefinition> manualMachine = manualMachines.machine(recipe.recipeType());
        ItemStack sourceIcon;
        String sourceName;
        String machineTargetId = null;
        if (machine.isPresent()) {
            sourceIcon = withLore(items.create(machine.get(), 1), List.of(Component.empty(), Text.mm(tr("guide.actions.open-recipe"))));
            sourceName = itemDisplayName(machine.get());
            machineTargetId = machine.get().id();
        } else if (manualMachine.isPresent()) {
            sourceIcon = machineSourceIcon(manualMachine.get());
            sourceName = machineDisplayName(manualMachine.get());
            machineTargetId = manualMachine.get().id();
        } else {
            sourceIcon = ItemBuilder.of(Material.BOOK).name("<green>" + recipe.recipeType() + "</green>").build();
            sourceName = recipe.recipeType();
        }
        return new GuideRecipePage(index, GuideRecipeOrigin.SFX, recipe.recipeType(), familyKey(recipe.recipeType()),
                sourceName, machineTargetId, sourceIcon, normalizeMatrix(matrix), null, output.amount(), false,
                List.of(recipe.id()), List.of());
    }

    private List<SfxRecipeSlot> compiledShapelessMatrix(List<SfxRecipeSlot> inputs) {
        List<SfxRecipeSlot> matrix = emptyMatrix();
        for (int index = 0; index < Math.min(inputs.size(), matrix.size()); index++) {
            matrix.set(index, inputs.get(index));
        }
        return matrix;
    }

    private List<SfxRecipeSlot> compiledSingleMatrix(SfxRecipeSlot input) {
        List<SfxRecipeSlot> matrix = emptyMatrix();
        matrix.set(4, input);
        return matrix;
    }

    private GuideRecipePage createVanillaRecipePage(Recipe recipe, int index) {
        if (recipe instanceof ShapedRecipe shaped) {
            return new GuideRecipePage(index, GuideRecipeOrigin.VANILLA, "minecraft:crafting", "minecraft:crafting",
                    tr("guide.recipe.vanilla.crafting"), null,
                    vanillaSourceIcon(Material.CRAFTING_TABLE, tr("guide.recipe.vanilla.crafting")),
                    normalizeMatrix(fromShapedRecipe(shaped)), null, Math.max(1, shaped.getResult().getAmount()), false,
                    List.of(vanillaRecipeId(recipe)), List.of());
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            return new GuideRecipePage(index, GuideRecipeOrigin.VANILLA, "minecraft:crafting", "minecraft:crafting",
                    tr("guide.recipe.vanilla.crafting"), null,
                    vanillaSourceIcon(Material.CRAFTING_TABLE, tr("guide.recipe.vanilla.crafting")),
                    normalizeMatrix(fromShapelessRecipe(shapeless)), null, Math.max(1, shapeless.getResult().getAmount()), false,
                    List.of(vanillaRecipeId(recipe)), List.of());
        }
        if (recipe instanceof StonecuttingRecipe stonecutting) {
            return new GuideRecipePage(index, GuideRecipeOrigin.VANILLA, "minecraft:stonecutting", "minecraft:stonecutting",
                    tr("guide.recipe.vanilla.stonecutting"), null,
                    vanillaSourceIcon(Material.STONECUTTER, tr("guide.recipe.vanilla.stonecutting")),
                    normalizeMatrix(singleChoiceMatrix(stonecutting.getInputChoice())), null, Math.max(1, stonecutting.getResult().getAmount()), false,
                    List.of(vanillaRecipeId(recipe)), slotsFromChoice(stonecutting.getInputChoice()));
        }
        if (recipe instanceof BlastingRecipe blasting) {
            return cookingPage(index, blasting, Material.BLAST_FURNACE, tr("guide.recipe.vanilla.blasting"));
        }
        if (recipe instanceof SmokingRecipe smoking) {
            return cookingPage(index, smoking, Material.SMOKER, tr("guide.recipe.vanilla.smoking"));
        }
        if (recipe instanceof CampfireRecipe campfire) {
            return cookingPage(index, campfire, Material.CAMPFIRE, tr("guide.recipe.vanilla.campfire"));
        }
        if (recipe instanceof FurnaceRecipe furnace) {
            return cookingPage(index, furnace, Material.FURNACE, tr("guide.recipe.vanilla.furnace"));
        }
        return null;
    }

    private GuideRecipePage cookingPage(int index, CookingRecipe<?> recipe, Material icon, String name) {
        return new GuideRecipePage(index, GuideRecipeOrigin.VANILLA, "minecraft:" + icon.name().toLowerCase(), "minecraft:" + icon.name().toLowerCase(),
                name, null, vanillaSourceIcon(icon, name), normalizeMatrix(singleChoiceMatrix(recipe.getInputChoice())), null, Math.max(1, recipe.getResult().getAmount()), false,
                List.of(vanillaRecipeId(recipe)), slotsFromChoice(recipe.getInputChoice()));
    }

    private static String vanillaRecipeId(Recipe recipe) {
        return recipe instanceof org.bukkit.Keyed keyed ? keyed.getKey().toString() : recipe.getClass().getName();
    }

    private List<SfxRecipeSlot> fromShapedRecipe(ShapedRecipe recipe) {
        List<SfxRecipeSlot> matrix = emptyMatrix();
        String[] shape = recipe.getShape();
        Map<Character, RecipeChoice> choices = recipe.getChoiceMap();
        for (int row = 0; row < shape.length && row < 3; row++) {
            String line = shape[row];
            for (int col = 0; col < line.length() && col < 3; col++) {
                char key = line.charAt(col);
                RecipeChoice choice = choices.get(key);
                matrix.set(row * 3 + col, slotFromChoice(choice));
            }
        }
        return matrix;
    }

    private List<SfxRecipeSlot> fromShapelessRecipe(ShapelessRecipe recipe) {
        List<SfxRecipeSlot> matrix = emptyMatrix();
        List<RecipeChoice> choices = recipe.getChoiceList();
        for (int i = 0; i < choices.size() && i < 9; i++) {
            matrix.set(i, slotFromChoice(choices.get(i)));
        }
        return matrix;
    }

    private List<SfxRecipeSlot> singleChoiceMatrix(RecipeChoice choice) {
        List<SfxRecipeSlot> matrix = emptyMatrix();
        matrix.set(4, slotFromChoice(choice));
        return matrix;
    }

    private boolean usesVerticalSingleLayout(SfxManualMachineDefinition machine, SfxRecipe recipe) {
        return usesVerticalSingleLayout(machine.id(), recipe)
                || machine.operation() == SfxManualMachineOperation.SINGLE_INPUT
                || machine.operation() == SfxManualMachineOperation.HAND_INPUT;
    }

    private boolean usesVerticalSingleLayout(String recipeType, SfxRecipe recipe) {
        return VERTICAL_SINGLE_RECIPE_TYPES.contains(recipeType) && nonEmptySlots(recipe.matrix()) == 1;
    }

    private int nonEmptySlots(List<SfxRecipeSlot> matrix) {
        int count = 0;
        for (SfxRecipeSlot slot : matrix) {
            if (slot != null && !slot.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private SfxRecipeSlot firstNonEmptySlot(List<SfxRecipeSlot> matrix) {
        for (SfxRecipeSlot slot : matrix) {
            if (slot != null && !slot.isEmpty()) {
                return slot;
            }
        }
        return SfxRecipeSlot.empty();
    }

    private SfxRecipeSlot slotFromChoice(RecipeChoice choice) {
        if (choice == null) {
            return SfxRecipeSlot.empty();
        }
        if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
            List<Material> materials = materialChoice.getChoices();
            return materials.isEmpty() ? SfxRecipeSlot.empty() : SfxRecipeSlot.vanilla(materials.getFirst());
        }
        if (choice instanceof RecipeChoice.ExactChoice exactChoice) {
            List<ItemStack> choices = exactChoice.getChoices();
            if (choices.isEmpty()) {
                return SfxRecipeSlot.empty();
            }
            ItemStack first = choices.getFirst();
            return SfxRecipeSlot.vanilla(first.getType(), Math.max(1, first.getAmount()));
        }
        return SfxRecipeSlot.empty();
    }

    private List<SfxRecipeSlot> slotsFromChoice(RecipeChoice choice) {
        if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
            return materialChoice.getChoices().stream().map(SfxRecipeSlot::vanilla).distinct().toList();
        }
        if (choice instanceof RecipeChoice.ExactChoice exactChoice) {
            return exactChoice.getChoices().stream()
                    .map(stack -> SfxRecipeSlot.vanilla(stack.getType(), Math.max(1, stack.getAmount())))
                    .distinct()
                    .toList();
        }
        return List.of();
    }

    private List<SfxRecipeSlot> normalizeMatrix(List<SfxRecipeSlot> matrix) {
        List<SfxRecipeSlot> normalized = emptyMatrix();
        for (int i = 0; i < Math.min(9, matrix.size()); i++) {
            normalized.set(i, matrix.get(i) == null ? SfxRecipeSlot.empty() : matrix.get(i));
        }
        return normalized;
    }

    private List<SfxRecipeSlot> emptyMatrix() {
        List<SfxRecipeSlot> matrix = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            matrix.add(SfxRecipeSlot.empty());
        }
        return matrix;
    }

    private DisplayContent displayContentFor(Player player, SfxItemDefinition definition, List<GuideRecipePage> pages,
                                             GuideRecipePage current, GuideMode mode, RecipePageOpener opener, List<String> trail) {
        List<DisplayEntry> sources = new ArrayList<>(alternativeSourceEntries(pages, current, opener));
        List<DisplayEntry> functions = new ArrayList<>();
        List<DisplayEntry> usages = new ArrayList<>();
        if (definition != null) {
            sources.addAll(executorEntriesFor(definition, current, mode, trail));
            functions.addAll(specialDisplayEntries(definition, mode));
            if (!usesSpecialOnlyFunctionDisplay(definition.id())) {
                functions.addAll(machineOutputEntries(definition, mode, trail));
            }
            usages.addAll(itemUsageEntries(definition, mode, trail));
        }
        sources = sortDisplayEntries(sources);
        functions = sortDisplayEntries(functions);
        usages = sortDisplayEntries(usages);
        List<DisplaySection> availableSections = availableDisplaySections(sources, functions, usages);
        boolean pairedLayout = sfxDisplayLayout(sources) == SfxDisplayLayout.PAIRED_GRID
                || sfxDisplayLayout(functions) == SfxDisplayLayout.PAIRED_GRID
                || sfxDisplayLayout(usages) == SfxDisplayLayout.PAIRED_GRID;
        DisplaySection section = selectedDisplaySection(player,
                definition == null ? null : displaySectionKey(definition), availableSections);
        return new DisplayContent(entriesForDisplaySection(section, sources, functions, usages), section, availableSections, pairedLayout);
    }

    private DisplayContent displayContentFor(Player player, Material material, List<GuideRecipePage> pages,
                                             GuideRecipePage current, GuideMode mode, RecipePageOpener opener, List<String> trail) {
        List<DisplayEntry> sources = new ArrayList<>(alternativeSourceEntries(pages, current, opener));
        sources.addAll(executorEntriesFor(null, current, mode, material, trail));
        List<DisplayEntry> functions = List.of();
        List<DisplayEntry> usages = itemUsageEntries(material, mode, trail);
        sources = sortDisplayEntries(sources);
        usages = sortDisplayEntries(usages);
        List<DisplaySection> availableSections = availableDisplaySections(sources, functions, usages);
        boolean pairedLayout = sfxDisplayLayout(sources) == SfxDisplayLayout.PAIRED_GRID
                || sfxDisplayLayout(usages) == SfxDisplayLayout.PAIRED_GRID;
        DisplaySection section = selectedDisplaySection(player, displaySectionKey(material), availableSections);
        return new DisplayContent(entriesForDisplaySection(section, sources, functions, usages), section, availableSections, pairedLayout);
    }

    private static List<DisplaySection> availableDisplaySections(List<DisplayEntry> sources,
                                                                  List<DisplayEntry> functions,
                                                                  List<DisplayEntry> usages) {
        List<DisplaySection> sections = new ArrayList<>(3);
        if (!sources.isEmpty()) {
            sections.add(DisplaySection.SOURCES);
        }
        if (!functions.isEmpty()) {
            sections.add(DisplaySection.FUNCTIONS);
        }
        if (!usages.isEmpty()) {
            sections.add(DisplaySection.USAGES);
        }
        return List.copyOf(sections);
    }

    private static List<DisplayEntry> entriesForDisplaySection(DisplaySection section,
                                                               List<DisplayEntry> sources,
                                                               List<DisplayEntry> functions,
                                                               List<DisplayEntry> usages) {
        return switch (section) {
            case FUNCTIONS -> functions;
            case USAGES -> usages;
            case SOURCES -> sources;
        };
    }

    private List<DisplayEntry> sortDisplayEntries(List<DisplayEntry> entries) {
        boolean hasPairs = entries.stream().anyMatch(DisplayEntry::paired);
        Comparator<DisplayEntry> order = hasPairs
                ? Comparator.comparingInt((DisplayEntry entry) -> entry.paired() ? 0 : 1).thenComparing(DISPLAY_ENTRY_ORDER)
                : DISPLAY_ENTRY_ORDER;
        return entries.stream().sorted(order).toList();
    }

    private DisplaySection selectedDisplaySection(Player player, String itemId) {
        return selectedDisplaySection(player, itemId, List.of());
    }

    private DisplaySection selectedDisplaySection(Player player, String itemId, List<DisplaySection> availableSections) {
        if (player == null || itemId == null) {
            return DisplaySection.SOURCES;
        }
        List<DisplaySection> preferred = displaySectionPreferencesByPlayer
                .computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .get(itemId);
        if (preferred != null && !preferred.isEmpty()) {
            for (DisplaySection section : preferred) {
                if (availableSections == null || availableSections.isEmpty() || availableSections.contains(section)) {
                    return section;
                }
            }
        }
        DisplaySection selected = displaySectionsByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .getOrDefault(itemId, DisplaySection.SOURCES);
        if (availableSections != null && !availableSections.isEmpty() && !availableSections.contains(selected)) {
            return availableSections.getFirst();
        }
        return selected;
    }

    private void setPreferredDisplaySections(Player player, String itemId, DisplaySection... sections) {
        if (player == null || itemId == null || sections == null || sections.length == 0) {
            return;
        }
        List<DisplaySection> preferred = new ArrayList<>(sections.length);
        for (DisplaySection section : sections) {
            if (section != null && !preferred.contains(section)) {
                preferred.add(section);
            }
        }
        if (preferred.isEmpty()) {
            return;
        }
        displaySectionPreferencesByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put(itemId, List.copyOf(preferred));
        displaySectionsByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put(itemId, preferred.getFirst());
    }

    private void setDisplaySection(Player player, String itemId, DisplaySection section) {
        if (player == null || itemId == null || section == null) {
            return;
        }
        displaySectionsByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>()).put(itemId, section);
        displaySectionPreferencesByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put(itemId, List.of(section));
    }

    private static String displaySectionKey(SfxItemDefinition definition) {
        return definition == null ? null : "sfx:" + definition.id();
    }

    private static String displaySectionKey(Material material) {
        return material == null ? null : "vanilla:" + material.name();
    }

    private static String displaySectionKey(SfxItemDefinition definition, ItemStack outputItem) {
        if (definition != null) {
            return displaySectionKey(definition);
        }
        if (outputItem == null || outputItem.getType().isAir()) {
            return null;
        }
        return displaySectionKey(outputItem.getType());
    }

    private List<DisplayEntry> executorEntriesFor(SfxItemDefinition definition, GuideRecipePage current, GuideMode mode,
                                                   List<String> trail) {
        return executorEntriesFor(definition, current, mode, null, trail);
    }

    private List<DisplayEntry> executorEntriesFor(SfxItemDefinition definition, GuideRecipePage current, GuideMode mode,
                                                   Material vanillaOutput, List<String> trail) {
        Set<String> executorIds = new LinkedHashSet<>();
        String recipeType = current.sourceId();
        if (registry.item(recipeType).isPresent()) {
            executorIds.add(recipeType);
        }

        DefaultSfxRecipeRegistry recipes = recipeRegistry;
        if (definition != null && recipes != null) {
            for (String recipeId : current.recipeIds()) {
                executorIds.addAll(recipes.executionMachineIds(recipeId, manualMachines));
            }
        }
        for (SfxElectricMachineDefinition machine : electricMachines) {
            if (machine.executesGuideRecipeType(recipeType)
                    || machine.recipeProvider().recipes().stream().anyMatch(recipe -> definition != null
                    ? electricRecipeMatchesPage(recipe, definition.id(), current)
                    : electricRecipeMatchesVanillaPage(recipe, vanillaOutput, current))) {
                executorIds.add(machine.id());
            }
        }
        List<DisplayEntry> entries = new ArrayList<>();
        int priority = 100;
        for (Material material : vanillaExecutorMaterials(recipeType)) {
            ItemStack icon = withLore(new ItemStack(material), List.of(
                    Component.empty(), Text.mm(tr("guide.recipe.executor")), Text.mm(tr("guide.actions.open-vanilla-recipe"))));
            entries.add(DisplayEntry.single(icon, materialName(material), priority,
                    handlerForSlot(SfxRecipeSlot.vanilla(material), mode), DisplayEntryKind.EXECUTOR));
            priority += 10;
        }
        for (String executorId : executorIds) {
            if (definition != null && definition.id().equals(executorId)) {
                continue;
            }
            Optional<SfxItemDefinition> machine = registry.item(executorId);
            Optional<SfxManualMachineDefinition> manualMachine = manualMachines.machine(executorId);
            if (machine.isEmpty() && manualMachine.isEmpty()) {
                continue;
            }
            ItemStack baseIcon = machine.map(definitionItem -> items.create(definitionItem, 1))
                    .orElseGet(() -> machineSourceIcon(manualMachine.orElseThrow()));
            ItemStack icon = withLore(associationMachineIcon(executorId, baseIcon), List.of(
                    Component.empty(),
                    Text.mm(tr("guide.recipe.executor")),
                    Text.mm(tr("guide.actions.open-recipe"))
            ));
            String label = machine.map(this::itemDisplayName).orElseGet(() -> machineDisplayName(manualMachine.orElseThrow()));
            ClickHandler handler = machine.isPresent() ? machineAssociationHandler(mode, executorId, trail) : null;
            entries.add(DisplayEntry.single(icon, label, priority, handler, DisplayEntryKind.EXECUTOR));
            priority += 10;
        }
        return entries;
    }

    private static List<Material> vanillaExecutorMaterials(String recipeType) {
        return switch (recipeType) {
            case "minecraft:crafting" -> List.of(Material.CRAFTING_TABLE);
            case "minecraft:smelting" -> List.of(Material.FURNACE, Material.BLAST_FURNACE);
            case "minecraft:furnace" -> List.of(Material.FURNACE);
            case "minecraft:blast_furnace" -> List.of(Material.BLAST_FURNACE);
            case "minecraft:smoker" -> List.of(Material.SMOKER);
            case "minecraft:campfire" -> List.of(Material.CAMPFIRE, Material.SOUL_CAMPFIRE);
            case "minecraft:stonecutting" -> List.of(Material.STONECUTTER);
            default -> List.of();
        };
    }

    private static boolean electricRecipeMatchesPage(SfxElectricRecipe recipe, String outputItemId, GuideRecipePage page) {
        List<SfxRecipeSlot> pageInputs = page.matrix().stream().filter(slot -> !slot.isEmpty()).toList();
        boolean inputsMatch = pageInputs.equals(recipe.inputs())
                || (recipe.inputs().size() == 1 && page.inputAlternatives().contains(recipe.inputs().getFirst()));
        if (!inputsMatch) {
            return false;
        }
        for (List<SfxElectricStack> group : recipe.outputGroups()) {
            for (SfxElectricStack output : group) {
                if (output.isSfxItem() && outputItemId.equals(output.itemId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean electricRecipeMatchesVanillaPage(SfxElectricRecipe recipe, Material outputMaterial, GuideRecipePage page) {
        if (outputMaterial == null) {
            return false;
        }
        List<SfxRecipeSlot> pageInputs = page.matrix().stream().filter(slot -> !slot.isEmpty()).toList();
        boolean inputsMatch = pageInputs.equals(recipe.inputs())
                || (recipe.inputs().size() == 1 && page.inputAlternatives().contains(recipe.inputs().getFirst()));
        if (!inputsMatch) {
            return false;
        }
        return recipe.outputGroups().stream().flatMap(List::stream)
                .anyMatch(output -> !output.isSfxItem() && output.material() == outputMaterial);
    }

    private List<DisplayEntry> specialDisplayEntries(SfxItemDefinition definition, GuideMode mode) {
        if ("bio-reactor".equals(definition.guideFuelProfile())) {
            return compiledBioFuelDisplayEntries(definition.id(), mode, 300);
        }
        return switch (definition.id()) {
            case "sf:composter" -> compiledMachineProcessEntries("sf:composter", mode, List.of());
            case "sf:crucible", "sf:electrified_crucible", "sf:electrified_crucible_2", "sf:electrified_crucible_3" ->
                    crucibleDisplayEntries(mode);
            case "sf:auto_anvil", "sf:auto_anvil_2" -> autoAnvilDisplayEntries(mode);
            case "sf:duct_tape" -> ductTapeDisplayEntries(mode);
            case "sf:produce_collector" -> produceCollectorDisplayEntries(mode);
            case "sf:auto_breeder" -> List.of(functionPair(Material.WHEAT, Material.COW_SPAWN_EGG, "auto-breeder", 300, mode));
            case "sf:animal_growth_accelerator" -> List.of(functionPair(SfxRecipeSlot.sfx("sf:organic_food"), SfxRecipeSlot.vanilla(Material.COW_SPAWN_EGG), "animal-growth", 300, mode));
            case "sf:crop_growth_accelerator", "sf:crop_growth_accelerator_2" -> List.of(functionPair(SfxRecipeSlot.sfx("sf:fertilizer"), SfxRecipeSlot.vanilla(Material.WHEAT), "crop-growth", 300, mode));
            case "sf:tree_growth_accelerator" -> List.of(functionPair(SfxRecipeSlot.sfx("sf:fertilizer"), SfxRecipeSlot.vanilla(Material.OAK_SAPLING), "tree-growth", 300, mode));
            case "sf:xp_collector" -> List.of(functionPair(Material.EXPERIENCE_BOTTLE, Material.EXPERIENCE_BOTTLE, "xp-collector", 300, mode));
            case "sf:fluid_pump" -> fluidPumpDisplayEntries(mode);
            case "sf:geo_miner" -> List.of(functionSingle(Material.DIAMOND_PICKAXE, "geo-miner", 300));
            case "sf:oil_pump" -> List.of(functionPair(Material.BUCKET, Material.BUCKET, "oil-pump", 300, mode));
            case "sf:gps_transmitter", "sf:gps_transmitter_2", "sf:gps_transmitter_3", "sf:gps_transmitter_4" ->
                    List.of(functionSingle(Material.COMPASS, "gps-transmitter", 300));
            case "sf:iron_golem_assembler" -> List.of(functionPair(Material.CARVED_PUMPKIN, Material.IRON_BLOCK, "iron-golem-assembler", 300, mode));
            case "sf:wither_assembler" -> List.of(functionPair(Material.WITHER_SKELETON_SKULL, Material.NETHER_STAR, "wither-assembler", 300, mode));
            case "sf:coal_generator" -> coalFuelDisplayEntries(16, 10, mode, 300);
            case "sf:coal_generator_2" -> coalFuelDisplayEntries(30, tierTwoBurnRateTenths(), mode, 300);
            case "sf:lava_generator" -> fixedFuelDisplayEntries(20, 10, mode, 300, List.of(
                    fuelData(SfxRecipeSlot.vanilla(Material.LAVA_BUCKET), 40 * lavaSecondsMultiplier(), "lava")));
            case "sf:lava_generator_2" -> fixedFuelDisplayEntries(40, tierTwoBurnRateTenths(), mode, 300, List.of(
                    fuelData(SfxRecipeSlot.vanilla(Material.LAVA_BUCKET), 40 * lavaSecondsMultiplier(), "lava")));
            case "sf:bio_reactor" -> bioFuelDisplayEntries(8, 10, mode, 300);
            case "sf:combustion_reactor" -> combustionFuelDisplayEntries(mode);
            case "sf:magnesium_generator" -> fixedFuelDisplayEntries(36, 10, mode, 300, List.of(
                    fuelData(SfxRecipeSlot.sfx("sf:magnesium_salt"), 20, "magnesium")));
            case "sf:nuclear_reactor" -> fixedFuelDisplayEntries(500, 10, mode, 300, List.of(
                    fuelData(SfxRecipeSlot.sfx("sf:uranium"), 1200, "uranium"),
                    fuelData(SfxRecipeSlot.sfx("sf:neptunium"), 600, "neptunium"),
                    fuelData(SfxRecipeSlot.sfx("sf:boosted_uranium"), 1500, "boosted_uranium")));
            case "sf:netherstar_reactor" -> fixedFuelDisplayEntries(netherStarReactorEnergyPerTick(), 10, mode, 300, List.of(
                    fuelData(SfxRecipeSlot.vanilla(Material.NETHER_STAR), 1800, "nether_star")));
            case "sfx:oxidizing_generator" -> oxidizingGeneratorDisplayEntries(mode);
            default -> List.of();
        };
    }

    private boolean usesSpecialOnlyFunctionDisplay(String itemId) {
        return "sf:electrified_crucible".equals(itemId)
                || "sf:electrified_crucible_2".equals(itemId)
                || "sf:electrified_crucible_3".equals(itemId);
    }

    private List<DisplayEntry> autoAnvilDisplayEntries(GuideMode mode) {
        return List.of(DisplayEntry.paired(
                functionIcon(SfxRecipeSlot.sfx("sf:duct_tape"), "auto-anvil-input"),
                functionIcon(Material.IRON_PICKAXE, "auto-anvil-output"),
                tr("guide.recipe.special.auto-anvil.label"),
                300,
                handlerForSlot(SfxRecipeSlot.sfx("sf:duct_tape"), mode),
                null,
                DisplayEntryKind.MACHINE_RECIPE));
    }

    private List<DisplayEntry> ductTapeDisplayEntries(GuideMode mode) {
        List<DisplayEntry> entries = new ArrayList<>();
        addMachineLink(entries, "sf:auto_anvil", 300, mode);
        addMachineLink(entries, "sf:auto_anvil_2", 310, mode);
        return entries;
    }

    private List<DisplayEntry> produceCollectorDisplayEntries(GuideMode mode) {
        return List.of(
                functionPair(Material.BUCKET, Material.MILK_BUCKET, "produce-milk", 300, mode),
                functionPair(Material.BOWL, Material.MUSHROOM_STEW, "produce-stew", 310, mode),
                functionPair(Material.SHEARS, Material.WHITE_WOOL, "produce-wool", 320, mode),
                functionPair(Material.GLASS_BOTTLE, Material.HONEY_BOTTLE, "produce-honey-bottle", 330, mode),
                functionPair(Material.SHEARS, Material.HONEYCOMB, "produce-honeycomb", 340, mode),
                functionPair(Material.BRUSH, Material.ARMADILLO_SCUTE, "produce-armadillo-scute", 350, mode));
    }

    private List<DisplayEntry> fluidPumpDisplayEntries(GuideMode mode) {
        return List.of(
                functionPair(Material.BUCKET, Material.WATER_BUCKET, "fluid-pump-water", 300, mode),
                functionPair(Material.BUCKET, Material.LAVA_BUCKET, "fluid-pump-lava", 310, mode));
    }

    private void addMachineLink(List<DisplayEntry> entries, String machineId, int priority, GuideMode mode) {
        registry.item(machineId).ifPresent(machine -> {
            ItemStack icon = withLore(items.create(machine, 1), List.of(
                    Component.empty(),
                    Text.mm(tr("guide.actions.open-machine")),
                    Text.mm(tr("guide.recipe.special.duct-tape-machine.lore"))
            ));
            entries.add(DisplayEntry.single(icon, itemDisplayName(machine), priority,
                    click -> openFunctions(click.player(), mode, machine, 0,
                            preferences(click.player()).recordHistory() ? Navigation.OPEN : Navigation.REPLACE),
                    DisplayEntryKind.RELATED));
        });
    }

    private DisplayEntry functionPair(Material input, Material output, String key, int priority, GuideMode mode) {
        return functionPair(SfxRecipeSlot.vanilla(input), SfxRecipeSlot.vanilla(output), key, priority, mode);
    }

    private DisplayEntry functionPair(SfxRecipeSlot input, SfxRecipeSlot output, String key, int priority, GuideMode mode) {
        return DisplayEntry.paired(
                functionIcon(input, key + ".input"),
                functionIcon(output, key + ".output"),
                tr("guide.recipe.special." + key + ".label"),
                priority,
                handlerForSlot(input, mode),
                handlerForSlot(output, mode),
                DisplayEntryKind.MACHINE_RECIPE);
    }

    private DisplayEntry functionSingle(Material icon, String key, int priority) {
        return DisplayEntry.single(
                functionIcon(icon, key),
                tr("guide.recipe.special." + key + ".label"),
                priority,
                null,
                DisplayEntryKind.RELATED);
    }

    private ItemStack functionIcon(Material material, String key) {
        return withLore(new ItemStack(material), specialFunctionLore(key));
    }

    private ItemStack functionIcon(SfxRecipeSlot slot, String key) {
        return withLore(ingredientIcon(slot), specialFunctionLore(key));
    }

    private List<Component> specialFunctionLore(String key) {
        return List.of(Component.empty(), Text.mm(tr("guide.recipe.special." + key + ".lore")));
    }

    private List<DisplayEntry> oxidizingGeneratorDisplayEntries(GuideMode mode) {
        return List.of(
                oxidizingResourceEntry(SfxRecipeSlot.vanilla(Material.COPPER_INGOT), "copper", 1200, mode),
                oxidizingResourceEntry(SfxRecipeSlot.sfx("sf:zinc_ingot"), "zinc", 1210, mode),
                oxidizingResourceEntry(SfxRecipeSlot.sfx("sf:magnesium_ingot"), "magnesium", 1220, mode),
                oxidizingResourceEntry(SfxRecipeSlot.sfx("sf:salt"), "salt", 1230, mode),
                oxidizingResourceEntry(SfxRecipeSlot.vanilla(Material.WATER_BUCKET), "water", 1240, mode));
    }

    private DisplayEntry oxidizingResourceEntry(SfxRecipeSlot slot, String loreKey, int priority, GuideMode mode) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.addAll(localization.requiredList("content-expansion.guide.oxidizing." + loreKey).stream()
                .map(Text::mm)
                .toList());
        ItemStack icon = withLore(ingredientIcon(slot), lore);
        return DisplayEntry.single(icon, slotLabel(slot), priority, handlerForSlot(slot, mode), DisplayEntryKind.FUEL);
    }

    private List<DisplayEntry> compiledBioFuelDisplayEntries(String componentId, GuideMode mode, int startPriority) {
        SfxEnergyComponentDefinition definition = guideEnergyDefinitions().get(componentId);
        if (definition == null || definition.fuelRules().isEmpty()) {
            return List.of();
        }
        List<FuelDisplayData> fuels = new ArrayList<>();
        for (SfxEnergyComponentDefinition.FuelRule rule : definition.fuelRules()) {
            SfxRecipeSlot slot = recipeSlot(rule.input());
            if (slot != null) {
                fuels.add(fuelData(slot, rule.seconds(), rule.key()));
            }
        }
        return fixedFuelDisplayEntries(definition.energyPerTick(), definition.fuelBurnRateTenths(), mode, startPriority, fuels);
    }

    private Map<String, SfxEnergyComponentDefinition> guideEnergyDefinitions() {
        Map<String, SfxEnergyComponentDefinition> cached = guideEnergyDefinitions;
        if (cached == null) {
            cached = SfxEnergyDefinitions.create(plugin);
            guideEnergyDefinitions = cached;
        }
        return cached;
    }

    private SfxRecipeSlot recipeSlot(SfxElectricStack stack) {
        if (stack == null || stack.hasSnapshot()) {
            return null;
        }
        return stack.isSfxItem()
                ? SfxRecipeSlot.sfx(stack.itemId(), stack.amount())
                : SfxRecipeSlot.vanilla(stack.material(), stack.amount());
    }

    private List<DisplayEntry> combustionFuelDisplayEntries(GuideMode mode) {
        boolean sfxBalance = generatorBalanceEnabled();
        int energy = sfxBalance ? 64 : 24;
        int oilSeconds = sfxBalance ? 40 : 30;
        int fuelSeconds = sfxBalance ? 120 : 90;
        return fixedFuelDisplayEntries(energy, 10, mode, 300, List.of(
                fuelData(SfxRecipeSlot.sfx("sf:bucket_of_oil"), oilSeconds, "oil"),
                fuelData(SfxRecipeSlot.sfx("sf:bucket_of_fuel"), fuelSeconds, "fuel")));
    }

    private List<DisplayEntry> coalFuelDisplayEntries(int energyPerTick, int burnRateTenths, GuideMode mode, int startPriority) {
        int multiplier = SfxEnergyBalance.rules(plugin).coalFuelTicksMultiplier();
        List<FuelDisplayData> fuels = List.of(
                fuelData(SfxRecipeSlot.vanilla(Material.CHARCOAL), vanillaFuelSeconds(1600, multiplier), "charcoal"),
                fuelData(SfxRecipeSlot.vanilla(Material.COAL), vanillaFuelSeconds(1600, multiplier), "coal"),
                fuelData(SfxRecipeSlot.vanilla(Material.COAL_BLOCK), vanillaFuelSeconds(16000, multiplier), "coal_block"),
                fuelData(SfxRecipeSlot.vanilla(Material.OAK_PLANKS), vanillaFuelSeconds(300, multiplier), "oak_planks")
        );
        return fixedFuelDisplayEntries(energyPerTick, burnRateTenths, mode, startPriority, fuels);
    }

    private double vanillaFuelSeconds(int burnTicks, int multiplier) {
        return (burnTicks * multiplier) / 200.0;
    }

    private List<DisplayEntry> bioFuelDisplayEntries(int energyPerTick, int burnRateTenths, GuideMode mode, int startPriority) {
        int multiplier = SfxEnergyBalance.rules(plugin).bioFuelSecondsMultiplier();
        List<FuelDisplayData> fuels = new ArrayList<>();
        addBioFuel(fuels, Material.ROTTEN_FLESH, 2, multiplier);
        addBioFuel(fuels, Material.SPIDER_EYE, 2, multiplier);
        addBioFuel(fuels, Material.BONE, 2, multiplier);
        addBioFuel(fuels, Material.STRING, 2, multiplier);
        addBioFuel(fuels, Material.APPLE, 3, multiplier);
        addBioFuel(fuels, Material.MELON_SLICE, 3, multiplier);
        addBioFuel(fuels, Material.MELON, 27, multiplier);
        addBioFuel(fuels, Material.PUMPKIN, 3, multiplier);
        addBioFuel(fuels, Material.PUMPKIN_SEEDS, 3, multiplier);
        addBioFuel(fuels, Material.MELON_SEEDS, 3, multiplier);
        addBioFuel(fuels, Material.WHEAT, 3, multiplier);
        addBioFuel(fuels, Material.WHEAT_SEEDS, 3, multiplier);
        addBioFuel(fuels, Material.CARROT, 3, multiplier);
        addBioFuel(fuels, Material.POTATO, 3, multiplier);
        addBioFuel(fuels, Material.SUGAR_CANE, 3, multiplier);
        addBioFuel(fuels, Material.NETHER_WART, 3, multiplier);
        addBioFuel(fuels, Material.RED_MUSHROOM, 2, multiplier);
        addBioFuel(fuels, Material.BROWN_MUSHROOM, 2, multiplier);
        addBioFuel(fuels, Material.VINE, 2, multiplier);
        addBioFuel(fuels, Material.CACTUS, 2, multiplier);
        addBioFuel(fuels, Material.LILY_PAD, 2, multiplier);
        addBioFuel(fuels, Material.CHORUS_FRUIT, 8, multiplier);
        addBioFuel(fuels, Material.KELP, 1, multiplier);
        addBioFuel(fuels, Material.DRIED_KELP, 2, multiplier);
        addBioFuel(fuels, Material.DRIED_KELP_BLOCK, 20, multiplier);
        addBioFuel(fuels, Material.SEAGRASS, 1, multiplier);
        addBioFuel(fuels, Material.SEA_PICKLE, 2, multiplier);
        addBioFuel(fuels, Material.BAMBOO, 1, multiplier);
        addBioFuel(fuels, Material.SWEET_BERRIES, 2, multiplier);
        addBioFuel(fuels, Material.COCOA_BEANS, 2, multiplier);
        addBioFuel(fuels, Material.BEETROOT, 3, multiplier);
        addBioFuel(fuels, Material.BEETROOT_SEEDS, 3, multiplier);
        addBioFuel(fuels, Material.HONEYCOMB, 4, multiplier);
        addBioFuel(fuels, Material.HONEYCOMB_BLOCK, 40, multiplier);
        addBioFuel(fuels, Material.SHROOMLIGHT, 4, multiplier);
        addBioFuel(fuels, Material.CRIMSON_FUNGUS, 2, multiplier);
        addBioFuel(fuels, Material.WARPED_FUNGUS, 2, multiplier);
        fuels.add(fuelData(SfxRecipeSlot.sfx("sf:strange_nether_goo"), 16 * multiplier, "strange_nether_goo"));
        addOptionalBioFuel(fuels, Material.GLOW_BERRIES, 2, multiplier);
        addOptionalBioFuel(fuels, Material.SMALL_DRIPLEAF, 3, multiplier);
        addOptionalBioFuel(fuels, Material.BIG_DRIPLEAF, 3, multiplier);
        addOptionalBioFuel(fuels, Material.GLOW_LICHEN, 2, multiplier);
        addOptionalBioFuel(fuels, Material.SPORE_BLOSSOM, 20, multiplier);
        addOptionalBioFuel(fuels, Material.POPPY, 1, multiplier);
        addOptionalBioFuel(fuels, Material.OAK_LEAVES, 1, multiplier);
        addOptionalBioFuel(fuels, Material.OAK_SAPLING, 1, multiplier);
        addOptionalBioFuel(fuels, Material.BRAIN_CORAL, 2, multiplier);
        addOptionalBioFuel(fuels, Material.BRAIN_CORAL_BLOCK, 2, multiplier);
        return fixedFuelDisplayEntries(energyPerTick, burnRateTenths, mode, startPriority, fuels);
    }

    private void addBioFuel(List<FuelDisplayData> fuels, Material material, int seconds, int multiplier) {
        fuels.add(fuelData(SfxRecipeSlot.vanilla(material), seconds * multiplier, material.key().toString()));
    }

    private void addOptionalBioFuel(List<FuelDisplayData> fuels, Material material, int seconds, int multiplier) {
        if (material != null) {
            addBioFuel(fuels, material, seconds, multiplier);
        }
    }

    private List<DisplayEntry> fixedFuelDisplayEntries(int energyPerTick, int burnRateTenths, GuideMode mode, int startPriority, List<FuelDisplayData> fuels) {
        List<DisplayEntry> entries = new ArrayList<>();
        int priority = startPriority;
        for (FuelDisplayData fuel : fuels) {
            double effectiveSeconds = fuel.baseSeconds() * 10.0 / Math.max(1, burnRateTenths);
            entries.add(fuelDisplayEntry(fuel.slot(), effectiveSeconds, energyPerTick, priority, mode));
            priority += 5;
        }
        return entries;
    }

    private FuelDisplayData fuelData(SfxRecipeSlot slot, double seconds, String key) {
        return new FuelDisplayData(slot, seconds, key);
    }

    private DisplayEntry fuelDisplayEntry(SfxRecipeSlot slot, double seconds, int energyPerTick, int priority, GuideMode mode) {
        long totalEnergy = Math.round(seconds * 20.0D * energyPerTick);
        ItemStack icon = withLore(ingredientIcon(slot), List.of(
                Component.empty(),
                Text.mm(tr("guide.recipe.consumable-fuel")),
                Text.mm(tr("energy.generator.fuel-duration").replace("{seconds}", formatDuration(seconds))),
                Text.mm(tr("energy.generator.fuel-total-energy").replace("{energy}", formatEnergyShort(totalEnergy)))
        ));
        return DisplayEntry.single(icon, slotLabel(slot) + " " + formatDuration(seconds), priority,
                handlerForSlot(slot, mode), DisplayEntryKind.FUEL);
    }

    private int tierTwoBurnRateTenths() {
        return SfxEnergyBalance.rules(plugin).tierTwoBurnRateTenths();
    }

    private int lavaSecondsMultiplier() {
        return SfxEnergyBalance.rules(plugin).lavaFuelSecondsMultiplier();
    }

    private int netherStarReactorEnergyPerTick() {
        return SfxEnergyBalance.rules(plugin).netherStarReactorEnergyPerTick();
    }

    private boolean generatorBalanceEnabled() {
        return SfxEnergyBalance.rules(plugin).generatorBalanceEnabled();
    }

    private String formatDuration(double seconds) {
        return SfxGuideFormatting.formatDuration(seconds);
    }

    private String formatEnergyShort(long value) {
        long abs = Math.abs(value);
        if (abs < 1000) {
            return Long.toString(value);
        }
        String[] units = {"k", "m", "b", "t", "p", "e"};
        double scaled = abs;
        int unitIndex = -1;
        while (scaled >= 1000.0 && unitIndex + 1 < units.length) {
            scaled /= 1000.0;
            unitIndex++;
        }
        String number = scaled < 10.0 ? String.format(Locale.ROOT, "%.1f", scaled) : String.format(Locale.ROOT, "%.0f", scaled);
        if (number.endsWith(".0") && scaled >= 10.0) {
            number = number.substring(0, number.length() - 2);
        }
        return (value < 0 ? "-" : "") + number + units[unitIndex];
    }

    private List<DisplayEntry> pairedDisplayEntries(List<SfxRecipeSlot> slots, GuideMode mode) {
        List<DisplayEntry> entries = new ArrayList<>();
        int priority = 200;
        for (int i = 0; i + 1 < slots.size(); i += 2) {
            SfxRecipeSlot input = slots.get(i);
            SfxRecipeSlot output = slots.get(i + 1);
            if (input == null || input.isEmpty() || output == null || output.isEmpty()) {
                continue;
            }
            entries.add(DisplayEntry.paired(
                    ingredientIcon(input),
                    ingredientIcon(output),
                    slotLabel(output),
                    priority,
                    handlerForSlot(input, mode),
                    handlerForSlot(output, mode)));
            priority += 5;
        }
        return entries;
    }

    private DisplayEntry displayEntryForSlot(SfxRecipeSlot slot, GuideMode mode, int priority) {
        ItemStack icon = ingredientIcon(slot);
        return DisplayEntry.single(icon, slotLabel(slot), priority, handlerForSlot(slot, mode));
    }

    private String slotLabel(SfxRecipeSlot slot) {
        return slot.isSfxItem()
                ? slot.sfxId().flatMap(registry::item).map(this::itemDisplayName).orElse(slot.sfxItemId())
                : materialName(slot.material());
    }

    private ClickHandler handlerForSlot(SfxRecipeSlot slot, GuideMode mode) {
        return handlerForSlot(slot, mode, null);
    }

    private List<String> clickTrail(Player player, List<String> trail) {
        if (trail != null) {
            return trail;
        }
        return activeRecipeTrails.getOrDefault(player.getUniqueId(), List.of());
    }

    private ClickHandler handlerForSlot(SfxRecipeSlot slot, GuideMode mode, List<String> trail) {
        if (slot.isSfxItem()) {
            return click -> slot.sfxId().ifPresent(target -> registry.item(target).ifPresent(definition -> {
                if (click.clickType() == ClickType.MIDDLE) {
                    openWithDisplayPreference(click.player(), mode, definition, 0,
                            preferences(click.player()).recordHistory() ? Navigation.OPEN : Navigation.REPLACE,
                            clickTrail(click.player(), trail),
                            DisplaySection.FUNCTIONS, DisplaySection.USAGES, DisplaySection.SOURCES);
                } else if (click.clickType().isRightClick()) {
                    openWithDisplayPreference(click.player(), mode, definition, 0,
                            preferences(click.player()).recordHistory() ? Navigation.OPEN : Navigation.REPLACE,
                            clickTrail(click.player(), trail),
                            DisplaySection.USAGES, DisplaySection.FUNCTIONS, DisplaySection.SOURCES);
                } else {
                    openRecipe(click.player(), mode, target, 0, 0,
                            preferences(click.player()).recordHistory() ? Navigation.OPEN : Navigation.REPLACE,
                            clickTrail(click.player(), trail));
                }
            }));
        }
        if (showVanillaRecipes() && slot.material() != null && !vanillaRecipePages(slot.material()).isEmpty()) {
            return click -> {
                if (click.clickType() == ClickType.MIDDLE) {
                    openWithDisplayPreference(click.player(), mode, slot.material(), 0,
                            preferences(click.player()).recordHistory() ? Navigation.OPEN : Navigation.REPLACE,
                            clickTrail(click.player(), trail),
                            DisplaySection.FUNCTIONS, DisplaySection.USAGES, DisplaySection.SOURCES);
                } else if (click.clickType().isRightClick()) {
                    openWithDisplayPreference(click.player(), mode, slot.material(), 0,
                            preferences(click.player()).recordHistory() ? Navigation.OPEN : Navigation.REPLACE,
                            clickTrail(click.player(), trail),
                            DisplaySection.USAGES, DisplaySection.FUNCTIONS, DisplaySection.SOURCES);
                } else {
                    openVanillaRecipe(click.player(), mode, slot.material(), 0,
                            preferences(click.player()).recordHistory() ? Navigation.OPEN : Navigation.REPLACE,
                            clickTrail(click.player(), trail));
                }
            };
        }
        return null;
    }

    private ClickHandler machineAssociationHandler(GuideMode mode, String machineId, List<String> trail) {
        return click -> registry.item(machineId).ifPresent(machine -> {
            Navigation navigation = preferences(click.player()).recordHistory() ? Navigation.OPEN : Navigation.REPLACE;
            List<String> clickTrail = clickTrail(click.player(), trail);
            if (click.clickType() == ClickType.MIDDLE) {
                openWithDisplayPreference(click.player(), mode, machine, 0, navigation, clickTrail,
                        DisplaySection.FUNCTIONS, DisplaySection.USAGES, DisplaySection.SOURCES);
            } else if (click.clickType().isRightClick()) {
                openWithDisplayPreference(click.player(), mode, machine, 0, navigation, clickTrail,
                        DisplaySection.USAGES, DisplaySection.FUNCTIONS, DisplaySection.SOURCES);
            } else {
                openRecipe(click.player(), mode, machineId, 0, 0, navigation, clickTrail);
            }
        });
    }

    private List<DisplayEntry> alternativeSourceEntries(List<GuideRecipePage> pages, GuideRecipePage current, RecipePageOpener opener) {
        
        
        
        return List.of();
    }

    private List<DisplayEntry> machineOutputEntries(SfxItemDefinition definition, GuideMode mode, List<String> trail) {
        Optional<SfxManualMachineDefinition> machine = manualMachines.machine(definition.id());
        if (machine.isEmpty()) {
            List<DisplayEntry> electric = electricMachineProcessEntries(definition.id(), mode, trail);
            return electric.isEmpty() ? compiledMachineProcessEntries(definition.id(), mode, trail) : electric;
        }
        if (!supportsMachineOutputDisplay(machine.get())) {
            return List.of();
        }
        Map<String, DisplayEntry> entries = new LinkedHashMap<>();
        int order = 1000;
        for (SfxManualMachineRecipe recipe : manualMachines.recipesFor(machine.get().id())) {
            boolean shaped = recipe.operation() == SfxManualMachineOperation.SHAPED_3X3;
            SfxRecipeSlot input = recipe.input().stream().filter(slot -> slot != null && !slot.isEmpty()).findFirst().orElse(null);
            if (input == null) {
                continue;
            }
            for (SfxManualMachineOutput output : recipe.outputs()) {
                if (output.isSfxItem()) {
                    String target = output.sfxItemId();
                    Optional<SfxItemDefinition> targetDefinition = registry.item(target);
                    if (targetDefinition.isEmpty()) {
                        continue;
                    }
                    ItemStack icon = withLore(targetDefinition.map(def -> items.create(def, output.amount())).orElseGet(() -> items.create(target, output.amount())), List.of(
                            Component.empty(),
                            Text.mm(tr("guide.actions.open-recipe"))
                    ));
                    ClickHandler outputHandler = click -> openRecipe(click.player(), mode, target, 0, 0,
                            preferences(click.player()).recordHistory() ? Navigation.OPEN : Navigation.REPLACE, trail);
                    DisplayEntry entry = shaped
                            ? DisplayEntry.single(icon, itemDisplayName(targetDefinition.get()), order, outputHandler, DisplayEntryKind.MACHINE_RECIPE)
                            : DisplayEntry.paired(ingredientIcon(input), icon, itemDisplayName(targetDefinition.get()), order,
                            handlerForSlot(input, mode, trail), outputHandler, DisplayEntryKind.MACHINE_RECIPE);
                    entries.putIfAbsent((shaped ? "" : input + "->") + "sfx:" + target, entry);
                } else {
                    Material material = output.material();
                    ItemStack icon = withLore(new ItemStack(material, output.amount()), List.of(
                            Component.empty(),
                            Text.mm(showVanillaRecipes()
                                    ? tr("guide.actions.open-vanilla-recipe")
                                    : tr("guide.recipe.vanilla.disabled"))
                    ));
                    ClickHandler outputHandler = showVanillaRecipes()
                            ? click -> openVanillaRecipe(click.player(), mode, material, 0,
                            preferences(click.player()).recordHistory() ? Navigation.OPEN : Navigation.REPLACE, trail)
                            : click -> { };
                    DisplayEntry entry = shaped
                            ? DisplayEntry.single(icon, materialName(material), order, outputHandler, DisplayEntryKind.MACHINE_RECIPE)
                            : DisplayEntry.paired(ingredientIcon(input), icon, materialName(material), order,
                            handlerForSlot(input, mode, trail), outputHandler, DisplayEntryKind.MACHINE_RECIPE);
                    entries.putIfAbsent((shaped ? "" : input + "->") + "vanilla:" + material.name(), entry);
                }
                order += 10;
            }
        }
        return entries.values().stream().toList();
    }

    private List<DisplayEntry> electricMachineProcessEntries(String machineId, GuideMode mode, List<String> trail) {
        SfxElectricMachineDefinition machine = electricMachines.stream()
                .filter(candidate -> machineId.equals(candidate.id())).findFirst().orElse(null);
        if (machine == null || machine.recipeProvider().recipes().isEmpty()) {
            return List.of();
        }
        List<DisplayEntry> entries = new ArrayList<>();
        int priority = 1000;
        for (SfxElectricRecipe recipe : machine.recipeProvider().recipes()) {
            SfxRecipeSlot input = recipe.inputs().getFirst();
            for (List<SfxElectricStack> group : recipe.outputGroups()) {
                SfxElectricStack output = group.getFirst();
                SfxRecipeSlot outputSlot = output.isSfxItem()
                        ? SfxRecipeSlot.sfx(output.itemId(), output.amount())
                        : SfxRecipeSlot.vanilla(output.material(), output.amount());
                entries.add(DisplayEntry.paired(ingredientIcon(input), ingredientIcon(outputSlot), recipe.key(), priority,
                        handlerForSlot(input, mode, trail), handlerForSlot(outputSlot, mode, trail), DisplayEntryKind.MACHINE_RECIPE));
                priority += 10;
            }
        }
        return entries;
    }

    private List<DisplayEntry> crucibleDisplayEntries(GuideMode mode) {
        List<SfxRecipeSlot> inputs = List.of(
                SfxRecipeSlot.vanilla(Material.COBBLESTONE, 16),
                SfxRecipeSlot.vanilla(Material.NETHERRACK, 16),
                SfxRecipeSlot.vanilla(Material.STONE, 12),
                SfxRecipeSlot.vanilla(Material.OBSIDIAN),
                SfxRecipeSlot.vanilla(Material.TERRACOTTA, 12),
                SfxRecipeSlot.vanilla(Material.OAK_LEAVES, 16),
                SfxRecipeSlot.vanilla(Material.BLACKSTONE, 8),
                SfxRecipeSlot.vanilla(Material.BASALT, 12),
                SfxRecipeSlot.vanilla(Material.COBBLED_DEEPSLATE, 12),
                SfxRecipeSlot.vanilla(Material.DEEPSLATE, 12),
                SfxRecipeSlot.vanilla(Material.TUFF, 12));
        List<DisplayEntry> entries = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            boolean water = i == 5;
            ItemStack fluid = ItemBuilder.of(water ? Material.WATER_BUCKET : Material.LAVA_BUCKET)
                    .name(water ? tr("guide.recipe.fluid.water") : tr("guide.recipe.fluid.lava"))
                    .lore(tr("guide.recipe.fluid.container-ignored"))
                    .build();
            SfxRecipeSlot input = inputs.get(i);
            ItemStack inputIcon = withLore(ingredientIcon(input), List.of(
                    Component.empty(),
                    Text.mm(tr("guide.recipe.fluid.requires-empty-bucket"))
            ));
            entries.add(DisplayEntry.paired(inputIcon, fluid,
                    water ? tr("guide.recipe.fluid.water") : tr("guide.recipe.fluid.lava"),
                    200 + i * 5, handlerForSlot(input, mode), null));
        }
        return entries;
    }

    private List<DisplayEntry> compiledMachineProcessEntries(String machineId, GuideMode mode, List<String> trail) {
        DefaultSfxRecipeRegistry recipes = recipeRegistry;
        if (recipes == null) {
            return List.of();
        }
        List<DisplayEntry> entries = new ArrayList<>();
        int priority = 1000;
        for (SfxRecipeDefinition recipe : recipes.definitions()) {
            if (!machineId.equals(recipe.recipeType()) || recipe.inputs().isEmpty() || recipe.allOutputs().isEmpty()) {
                continue;
            }
            SfxRecipeSlot input = recipe.inputs().stream().filter(slot -> slot != null && !slot.isEmpty()).findFirst().orElse(null);
            SfxRecipeOutputDefinition output = recipe.allOutputs().getFirst();
            if (input == null) {
                continue;
            }
            SfxRecipeSlot outputSlot = output.isSfxItem()
                    ? SfxRecipeSlot.sfx(output.sfxItemId(), output.amount())
                    : SfxRecipeSlot.vanilla(output.material(), output.amount());
            entries.add(DisplayEntry.paired(
                    ingredientIcon(input), ingredientIcon(outputSlot), recipe.id(), priority,
                    handlerForSlot(input, mode, trail), handlerForSlot(outputSlot, mode, trail), DisplayEntryKind.MACHINE_RECIPE));
            priority += 10;
        }
        return entries;
    }

    private boolean supportsMachineOutputDisplay(SfxManualMachineDefinition machine) {
        return true;
    }

    private SfxDisplayLayout sfxDisplayLayout(List<DisplayEntry> entries) {
        return sfxDisplayLayout(entries, false);
    }

    private SfxDisplayLayout sfxDisplayLayout(List<DisplayEntry> entries, boolean forcePaired) {
        if (entries == null || entries.isEmpty()) {
            return SfxDisplayLayout.NONE;
        }
        if (forcePaired) {
            return SfxDisplayLayout.PAIRED_GRID;
        }
        boolean compact = entries.size() <= SFX_DISPLAY_SLOTS_COMPACT.length && entries.stream().noneMatch(DisplayEntry::paired);
        return compact ? SfxDisplayLayout.COMPACT_LIST : SfxDisplayLayout.PAIRED_GRID;
    }

    private DisplayPage displayPage(List<DisplayEntry> entries, SfxDisplayLayout layout, int requestedPage) {
        if (entries == null || entries.isEmpty() || layout == SfxDisplayLayout.NONE) {
            return new DisplayPage(List.of(), 0, 1);
        }
        int capacity = layout == SfxDisplayLayout.COMPACT_LIST ? SFX_DISPLAY_SLOTS_COMPACT.length : SFX_DISPLAY_SLOTS_PAIRED.length;
        List<List<DisplayEntry>> pages = new ArrayList<>();
        List<DisplayEntry> current = new ArrayList<>();
        int used = 0;
        for (DisplayEntry entry : entries) {
            int cost = entry.paired() ? 2 : 1;
            if (!current.isEmpty() && used + cost > capacity) {
                pages.add(List.copyOf(current));
                current.clear();
                used = 0;
            }
            current.add(entry);
            used += cost;
        }
        if (!current.isEmpty()) {
            pages.add(List.copyOf(current));
        }
        if (pages.isEmpty()) {
            return new DisplayPage(List.of(), 0, 1);
        }
        int safePage = clampPage(requestedPage, pages.size());
        return new DisplayPage(pages.get(safePage), safePage, pages.size());
    }

    private void renderDisplayEntries(SfxMenu.Builder builder, List<DisplayEntry> entries, int[] slots) {
        if (slots.length <= 9) {
            for (int i = 0; i < slots.length; i++) {
                DisplayEntry entry = i < entries.size() ? entries.get(i) : null;
                Cell cell = entry == null ? null : (entry.rotating() ? Cell.rotating(entry) : Cell.fixed(entry.primaryIcon(), entry.primaryHandler()));
                paintDisplayCell(builder, slots[i], cell);
            }
            return;
        }

        int columns = slots.length / 2;
        Cell[] top = new Cell[columns];
        Cell[] bottom = new Cell[columns];

        for (DisplayEntry entry : entries) {
            if (entry.paired()) {
                int column = firstFreePairColumn(top, bottom);
                if (column < 0) {
                    break;
                }
                top[column] = Cell.fixed(entry.primaryIcon(), entry.primaryHandler());
                bottom[column] = Cell.fixed(entry.secondaryIcon(), entry.secondaryHandler());
                continue;
            }
            int cell = firstFreeSingleCell(top, bottom);
            if (cell < 0) {
                break;
            }
            if (cell < columns) {
                top[cell] = entry.rotating() ? Cell.rotating(entry) : Cell.fixed(entry.primaryIcon(), entry.primaryHandler());
            } else {
                bottom[cell - columns] = entry.rotating() ? Cell.rotating(entry) : Cell.fixed(entry.primaryIcon(), entry.primaryHandler());
            }
        }

        for (int i = 0; i < columns; i++) {
            paintDisplayCell(builder, slots[i], top[i]);
            paintDisplayCell(builder, slots[i + columns], bottom[i]);
        }
    }

    private int firstFreePairColumn(Cell[] top, Cell[] bottom) {
        for (int i = 0; i < top.length; i++) {
            if (top[i] == null && bottom[i] == null) {
                return i;
            }
        }
        return -1;
    }

    private int firstFreeSingleCell(Cell[] top, Cell[] bottom) {
        for (int i = 0; i < top.length; i++) {
            if (top[i] == null) {
                return i;
            }
        }
        for (int i = 0; i < bottom.length; i++) {
            if (bottom[i] == null) {
                return i + top.length;
            }
        }
        return -1;
    }

    private void paintDisplayCell(SfxMenu.Builder builder, int slot, Cell cell) {
        if (cell == null) {
            builder.button(slot, new SfxMenuButton(emptyRecipeSlotIcon(), click -> {
            }));
            return;
        }
        if (cell.variants() != null && cell.variants().size() > 1) {
            SfxMenuButton fallback = new SfxMenuButton(cell.icon(), click -> {
                ClickHandler handler = rotatingDisplayVariant(cell.variants()).handler();
                if (handler != null) handler.accept(click);
            });
            builder.dynamicButton(slot, fallback, ignored -> rotatingDisplayVariant(cell.variants()).icon());
        } else {
            builder.button(slot, new SfxMenuButton(cell.icon(), click -> {
                if (cell.handler() != null) {
                    cell.handler().accept(click);
                }
            }));
        }
    }

    private static DisplayVariant rotatingDisplayVariant(List<DisplayVariant> variants) {
        return variants.get(rotatingIndex(variants.size()));
    }

    private SfxMenuButton ingredientButton(SfxRecipeSlot slot, GuideMode mode) {
        return ingredientButton(slot, mode, List.of());
    }

    private SfxMenuButton ingredientButton(SfxRecipeSlot slot, GuideMode mode, List<String> trail) {
        if (slot == null || slot.isEmpty()) {
            return new SfxMenuButton(emptyMatrixSlotIcon(), click -> {
            });
        }
        ItemStack icon = ingredientIcon(slot);
        return new SfxMenuButton(icon, click -> {
            if (click.clickType() == ClickType.MIDDLE) {
                openPreferredSectionForSlot(click.player(), mode, slot, trail,
                        DisplaySection.FUNCTIONS, DisplaySection.USAGES, DisplaySection.SOURCES);
            } else if (click.clickType().isRightClick()) {
                openPreferredSectionForSlot(click.player(), mode, slot, trail,
                        DisplaySection.USAGES, DisplaySection.FUNCTIONS, DisplaySection.SOURCES);
            } else {
                openIngredientRecipe(click.player(), slot, mode, trail);
            }
        });
    }

    private void openUsesForSlot(Player player, GuideMode mode, SfxRecipeSlot slot) {
        openPreferredSectionForSlot(player, mode, slot, List.of(),
                DisplaySection.USAGES, DisplaySection.FUNCTIONS, DisplaySection.SOURCES);
    }

    private void openPreferredSectionForSlot(Player player, GuideMode mode, SfxRecipeSlot slot, List<String> trail, DisplaySection... sections) {
        if (slot.isSfxItem()) {
            slot.sfxId().flatMap(registry::item)
                    .ifPresent(definition -> openWithDisplayPreference(player, mode, definition, 0,
                            preferences(player).recordHistory() ? Navigation.OPEN : Navigation.REPLACE, trail, sections));
        } else if (slot.material() != null) {
            openWithDisplayPreference(player, mode, slot.material(), 0,
                    preferences(player).recordHistory() ? Navigation.OPEN : Navigation.REPLACE, trail, sections);
        }
    }

    private void openIngredientRecipe(Player player, SfxRecipeSlot slot, GuideMode mode, List<String> trail) {
        if (slot.isSfxItem()) {
            slot.sfxId().ifPresent(target -> openRecipe(player, mode, target, 0, 0,
                    preferences(player).recordHistory() ? Navigation.OPEN : Navigation.REPLACE, trail));
        } else if (showVanillaRecipes() && slot.material() != null && !vanillaRecipePages(slot.material()).isEmpty()) {
            openVanillaRecipe(player, mode, slot.material(), 0,
                    preferences(player).recordHistory() ? Navigation.OPEN : Navigation.REPLACE, trail);
        }
    }

    private SfxMenuButton recipeSourceButton(GuideRecipePage current, GuideMode mode) {
        return recipeSourceButton(current, mode, List.of());
    }

    private SfxMenuButton recipeSourceButton(GuideRecipePage current, GuideMode mode, List<String> trail) {
        ItemStack icon = current.hasRecipe() ? withRecipeSourceLore(current) : ItemBuilder.of(Material.KNOWLEDGE_BOOK)
                .name(tr("guide.recipe.no-recipe.name"))
                .build();
        if (current.machineTargetId() != null && machineLinksEnabled()) {
            ClickHandler handler = machineAssociationHandler(mode, current.machineTargetId(), trail);
            return new SfxMenuButton(icon, handler::accept);
        }
        return new SfxMenuButton(icon, click -> {
        });
    }

    private ItemStack withRecipeSourceLore(GuideRecipePage current) {
        List<Component> lore = new ArrayList<>();
        if (current.machineTargetId() != null && machineLinksEnabled()) {
            lore.add(Component.empty());
            lore.add(Text.mm(tr("guide.actions.open-machine")));
        }
        if (current.note() != null) {
            lore.add(Component.empty());
            lore.add(Text.noItalic(current.note()));
        }
        return lore.isEmpty() ? current.sourceIcon() : withLore(current.sourceIcon(), lore);
    }

    private void giveFromCheatGuide(Player player, SfxItemDefinition definition, ClickType clickType) {
        if (!accessPolicy.canReceiveFromCheatGuide(player, definition)) {
            player.sendMessage(Text.prefixed(plugin, tr("guide.errors.no-cheat-access")));
            return;
        }

        Optional<SfxManualMachineDefinition> manualMachine = manualMachines.machine(definition.id())
                .or(() -> cc.theends6.sfx.internal.machine.ExtraDeployStructures.machine(definition.id()));
        if (manualMachine.isPresent()) {
            giveManualMachineFromCheatGuide(player, manualMachine.get(), clickType != null && clickType.isShiftClick());
            return;
        }

        int amount = clickType != null && clickType.isShiftClick() ? 64 : 1;
        runtime.executeForPlayer(player, () -> {
            items.give(player, items.create(definition, amount));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
            player.sendMessage(Text.prefixed(plugin, localization.text("machines.cheat-item")
                    .replace("{item}", itemDisplayName(definition))
                    .replace("{amount}", Integer.toString(amount))));
        });
    }

    private void giveManualMachineFromCheatGuide(Player player, SfxManualMachineDefinition definition, boolean fullKit) {
        runtime.executeForPlayer(player, () -> {
            if (fullKit || !definition.deployable()) {
                for (ItemStack part : definition.structureKit()) {
                    items.give(player, part);
                }
                String parts = definition.structureKit().stream()
                        .map(stack -> materialName(stack.getType()) + " x" + stack.getAmount())
                        .reduce((left, right) -> left + ", " + right)
                        .orElse(materialName(definition.triggerMaterial()));
                String key = definition.deployable() ? "machines.cheat-machine-kit" : "machines.deploy-pack-unsupported";
                player.sendMessage(Text.prefixed(plugin, localization.text(key).replace("{item}", parts)));
            } else {
                ItemStack deployPack = cc.theends6.sfx.internal.machine.ManualMachineDeployPacks.create(plugin, definition, localization);
                items.give(player, deployPack);
                String itemName = deployPack.hasItemMeta() && deployPack.getItemMeta() != null && deployPack.getItemMeta().hasDisplayName()
                        ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(deployPack.getItemMeta().displayName())
                        : machineDisplayName(definition);
                player.sendMessage(Text.prefixed(plugin, localization.text("machines.cheat-machine-pack")
                        .replace("{item}", itemName)));
            }
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
        });
    }

    private ItemStack ingredientIcon(SfxRecipeSlot slot) {
        if (slot == null || slot.isEmpty()) {
            return emptyRecipeSlotIcon();
        }
        ItemStack icon = slot.isSfxItem() ? items.create(slot.sfxItemId(), slot.amount()) : new ItemStack(slot.material(), slot.amount());
        List<Component> extraLore = new ArrayList<>();
        if (slot.isSfxItem()) {
            extraLore.add(Component.empty());
            extraLore.add(Text.mm(tr("guide.actions.open-recipe")));
        } else if (showVanillaRecipes() && slot.material() != null && !vanillaRecipePages(slot.material()).isEmpty()) {
            extraLore.add(Component.empty());
            extraLore.add(Text.mm(tr("guide.actions.open-vanilla-recipe")));
        }
        if (slot.amount() > 1) {
            if (extraLore.isEmpty()) {
                extraLore.add(Component.empty());
            }
            extraLore.add(Component.text(tr("guide.recipe.amount") + slot.amount(), NamedTextColor.GRAY));
        }
        return extraLore.isEmpty() ? icon : withLore(icon, extraLore);
    }

    private ItemStack emptyRecipeSlotIcon() {
        return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name("<dark_gray> </dark_gray>").build();
    }

    private ItemStack emptyMatrixSlotIcon() {
        return new ItemStack(Material.AIR);
    }

    ItemStack modeInfoIcon(GuideMode mode) {
        return ItemBuilder.of(mode == GuideMode.CHEAT ? Material.ENCHANTED_BOOK : Material.BOOK)
                .name(mode == GuideMode.CHEAT
                        ? tr("guide.mode.cheat.name")
                        : tr("guide.mode.survival.name"))
                .lore(mode == GuideMode.CHEAT
                        ? tr("guide.mode.cheat.lore")
                        : tr("guide.mode.survival.lore"))
                .build();
    }

    private void paintFrame(SfxMenu.Builder builder, GuideMode mode, GuideLayout layout) {
        ItemStack pane = ItemBuilder.of(layout == GuideLayout.CLASSIC ? Material.GRAY_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE)
                .name("<dark_gray> </dark_gray>")
                .build();

        for (int i = 0; i < 9; i++) {
            builder.button(i, new SfxMenuButton(pane, click -> {
            }));
        }
        for (int i = 45; i < 54; i++) {
            builder.button(i, new SfxMenuButton(pane, click -> {
            }));
        }

        builder.button(4, new SfxMenuButton(modeInfoIcon(mode), click -> {
        }));
        builder.button(1, new SfxMenuButton(settingsIcon(), click -> openSettingsView(click.player(), mode, Navigation.OPEN)));
        builder.button(7, new SfxMenuButton(searchIcon(), click -> beginSearch(click.player(), mode)));
    }

    private void paintRecipeFrame(SfxMenu.Builder builder, GuideMode mode, SfxDisplayLayout displayLayout) {
        ItemStack pane = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name("<dark_gray> </dark_gray>").build();
        ItemStack recipeBorder = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name("<dark_gray> </dark_gray>").build();
        if (displayLayout != SfxDisplayLayout.PAIRED_GRID) {
            for (int i = 0; i < 9; i++) {
                builder.button(i, new SfxMenuButton(pane, click -> {
                }));
            }
        }
        int[] borderSlots = displayLayout == SfxDisplayLayout.PAIRED_GRID
                ? SFX_RECIPE_BORDER_TOP
                : SFX_RECIPE_BORDER_NORMAL;
        for (int slot : borderSlots) {
            builder.button(slot, new SfxMenuButton(recipeBorder, click -> { }));
        }
        for (int i = 45; i < 54; i++) {
            builder.button(i, new SfxMenuButton(pane, click -> {
            }));
        }
        builder.button(1, new SfxMenuButton(settingsIcon(), click -> openSettingsView(click.player(), mode, Navigation.OPEN)));
        builder.button(7, new SfxMenuButton(searchIcon(), click -> beginSearch(click.player(), mode)));
    }

    private void addIngredientButton(SfxMenu.Builder builder, int menuSlot, SfxRecipeSlot recipeSlot, GuideMode mode,
                                     boolean cycleVariants, List<SfxRecipeSlot> inputAlternatives, List<String> trail) {
        if (inputAlternatives.size() > 1 && inputAlternatives.contains(recipeSlot)) {
            SfxMenuButton fallback = new SfxMenuButton(ingredientIcon(inputAlternatives.getFirst()), click -> {
                SfxRecipeSlot selected = rotatingVariant(inputAlternatives);
                if (click.clickType() == ClickType.MIDDLE) {
                    openPreferredSectionForSlot(click.player(), mode, selected, trail,
                            DisplaySection.FUNCTIONS, DisplaySection.USAGES, DisplaySection.SOURCES);
                } else if (click.clickType().isRightClick()) {
                    openPreferredSectionForSlot(click.player(), mode, selected, trail,
                            DisplaySection.USAGES, DisplaySection.FUNCTIONS, DisplaySection.SOURCES);
                } else {
                    openIngredientRecipe(click.player(), selected, mode, trail);
                }
            });
            builder.dynamicButton(menuSlot, fallback, player -> ingredientIcon(rotatingVariant(inputAlternatives)));
            return;
        }
        SfxMenuButton fallback = ingredientButton(recipeSlot, mode, trail);
        List<Material> variants = cycleVariants ? materialVariants(recipeSlot) : List.of();
        if (variants.size() <= 1) {
            builder.button(menuSlot, fallback);
            return;
        }
        builder.dynamicButton(menuSlot, fallback, player -> {
            int index = (int) ((System.currentTimeMillis() / 1000L) % variants.size());
            return ingredientButton(SfxRecipeSlot.vanilla(variants.get(index), recipeSlot.amount()), mode, trail).icon();
        });
    }

    private static SfxRecipeSlot rotatingVariant(List<SfxRecipeSlot> variants) {
        int index = (int) ((System.currentTimeMillis() / 1000L) % variants.size());
        return variants.get(index);
    }

    private List<Material> materialVariants(SfxRecipeSlot slot) {
        if (slot == null || slot.isEmpty() || slot.isSfxItem() || slot.material() == null) {
            return List.of();
        }
        for (Tag<Material> tag : GUIDE_MATERIAL_VARIANT_TAGS) {
            if (tag.isTagged(slot.material())) {
                return tag.getValues().stream()
                        .filter(Material::isItem)
                        .sorted(Comparator.comparing(Material::name))
                        .toList();
            }
        }
        return List.of();
    }

    private void paintDetailDivider(SfxMenu.Builder builder, int firstSlot, GuideRecipePage current, int pageCount) {
        ItemStack divider = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name("<dark_gray> </dark_gray>").build();
        for (int slot = firstSlot; slot < firstSlot + 9; slot++) {
            builder.button(slot, new SfxMenuButton(divider, click -> {
            }));
        }
        if (pageCount <= 1) {
            builder.button(firstSlot + 4, new SfxMenuButton(ItemBuilder.of(Material.KNOWLEDGE_BOOK)
                    .name("<yellow>" + current.sourceName() + "</yellow>")
                    .build(), click -> {
            }));
        }
    }

    private void paintClassicDivider(SfxMenu.Builder builder, GuideRecipePage current, int pageCount) {
        ItemStack divider = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name("<dark_gray> </dark_gray>").build();
        for (int slot = 27; slot <= 35; slot++) {
            builder.button(slot, new SfxMenuButton(divider, click -> {
            }));
        }
        builder.button(31, new SfxMenuButton(ItemBuilder.of(Material.KNOWLEDGE_BOOK)
                .name("<yellow>" + current.sourceName() + "</yellow>")
                .lore(pageNumberLore(current.index(), pageCount))
                .build(), click -> {
        }));
    }

    private void paintSfxLowerDivider(SfxMenu.Builder builder, GuideRecipePage current, int pageCount) {
        ItemStack divider = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name("<dark_gray> </dark_gray>").build();
        for (int slot = 36; slot <= 44; slot++) {
            builder.button(slot, new SfxMenuButton(divider, click -> {
            }));
        }
        builder.button(40, new SfxMenuButton(ItemBuilder.of(Material.KNOWLEDGE_BOOK)
                .name("<yellow>" + current.sourceName() + "</yellow>")
                .lore(pageNumberLore(current.index(), pageCount))
                .build(), click -> {
        }));
    }

    void paintSettingsBackground(SfxMenu.Builder builder, GuideMode mode) {
        ItemStack background = ItemBuilder.of(mode == GuideMode.CHEAT ? Material.RED_STAINED_GLASS_PANE : Material.GREEN_STAINED_GLASS_PANE)
                .name("<dark_gray> </dark_gray>")
                .build();
        for (int slot : SETTINGS_BACKGROUND) {
            builder.button(slot, new SfxMenuButton(background, click -> {
            }));
        }
    }

    private void addContentPagination(SfxMenu.Builder builder, int page, int pageCount, PlayerAction previous, PlayerAction next) {
        if (pageCount <= 1) {
            return;
        }
        builder.button(46, new SfxMenuButton(previousRecipeIcon(page, pageCount), click -> {
            if (page > 0) {
                playGuidePageSound(click.player());
                previous.accept(click.player());
            }
        }));
        builder.button(52, new SfxMenuButton(nextRecipeIcon(page, pageCount), click -> {
            if (page + 1 < pageCount) {
                playGuidePageSound(click.player());
                next.accept(click.player());
            }
        }));
    }

    private void addRecipePagination(SfxMenu.Builder builder, int previousSlot, int pageSlot, int nextSlot, int page, int pageCount,
                                     PageAction openPage, String sourceName) {
        if (pageCount <= 1) {
            return;
        }
        builder.button(previousSlot, new SfxMenuButton(previousRecipeIcon(page, pageCount), click -> {
            if (page > 0) {
                playGuidePageSound(click.player());
                openPage.accept(click.player(), Math.max(0, page - (click.clickType().isRightClick() ? 5 : 1)));
            }
        }));
        ItemStack pageIcon = SfxGuideIconLibrary.page(
                "<yellow>" + sourceName + "</yellow>", pageNumberLore(page, pageCount), page + 1);
        applyPageProgress(pageIcon, page, pageCount);
        builder.button(pageSlot, new SfxMenuButton(pageIcon, click -> { }));
        builder.button(nextSlot, new SfxMenuButton(nextRecipeIcon(page, pageCount), click -> {
            if (page + 1 < pageCount) {
                playGuidePageSound(click.player());
                openPage.accept(click.player(), Math.min(pageCount - 1, page + (click.clickType().isRightClick() ? 5 : 1)));
            }
        }));
    }

    private void addExtraDisplayPagination(SfxMenu.Builder builder, int previousSlot, int pageSlot, int nextSlot,
                                           int page, int pageCount, PageAction openPage, String title) {
        if (pageCount <= 1) {
            return;
        }
        builder.button(previousSlot, new SfxMenuButton(previousRecipeIcon(page, pageCount), click -> {
            if (page > 0) {
                playGuidePageSound(click.player());
                openPage.accept(click.player(), Math.max(0, page - (click.clickType().isRightClick() ? 5 : 1)));
            }
        }));
        ItemStack pageIcon = SfxGuideIconLibrary.page(
                title, pageNumberLore(page, pageCount), page + 1);
        applyPageProgress(pageIcon, page, pageCount);
        builder.button(pageSlot, new SfxMenuButton(pageIcon, click -> { }));
        builder.button(nextSlot, new SfxMenuButton(nextRecipeIcon(page, pageCount), click -> {
            if (page + 1 < pageCount) {
                playGuidePageSound(click.player());
                openPage.accept(click.player(), Math.min(pageCount - 1, page + (click.clickType().isRightClick() ? 5 : 1)));
            }
        }));
    }

    private String displaySectionTitle(List<DisplayEntry> entries) {
        Set<DisplayEntryKind> kinds = entries.stream().map(DisplayEntry::kind).collect(java.util.stream.Collectors.toSet());
        if (kinds.size() != 1) {
            return tr("guide.recipe.related-content");
        }
        return switch (kinds.iterator().next()) {
            case EXECUTOR -> tr("guide.recipe.executor");
            case MACHINE_RECIPE -> tr("guide.recipe.machine-recipes");
            case FUEL -> tr("guide.recipe.consumable-fuel");
            case RELATED -> tr("guide.recipe.related-content");
        };
    }

    private ItemStack previousRecipeIcon(int page, int pageCount) {
        return SfxGuideIconLibrary.previous(
                page > 0 ? tr("guide.pagination.prev.active") : tr("guide.pagination.prev.inactive"),
                pageNumberLore(page, pageCount), tr("guide.pagination.click-hint"));
    }

    private ItemStack nextRecipeIcon(int page, int pageCount) {
        return SfxGuideIconLibrary.next(
                page + 1 < pageCount ? tr("guide.pagination.next.active") : tr("guide.pagination.next.inactive"),
                pageNumberLore(page, pageCount), tr("guide.pagination.click-hint"));
    }

    private ItemStack infoIcon(GuideMode mode, int page, int pageCount) {
        ItemStack icon = ItemBuilder.of(Material.NETHER_STAR)
                .name(mode == GuideMode.CHEAT
                        ? tr("guide.mode.cheat.name")
                        : tr("guide.mode.survival.name"))
                .lore(pageNumberLore(page, pageCount), "", tr("guide.actions.close"))
                .build();
        applyPageProgress(icon, page, pageCount);
        return icon;
    }

    private static void applyPageProgress(ItemStack icon, int page, int pageCount) {
        if (pageCount <= 1) {
            return;
        }
        cc.theends6.sfx.internal.ui.SfxItemProgressBar.applyToDisplayItem(icon, page + 1, pageCount);
    }

    private static void applyFixedProgress(ItemStack icon, int current, int total) {
        cc.theends6.sfx.internal.ui.SfxItemProgressBar.applyToDisplayItem(icon, current, total);
    }

    private ItemStack settingsIcon() {
        return ItemBuilder.of(Material.COMPARATOR)
                .name(tr("guide.actions.settings.name"))
                .lore(tr("guide.actions.settings.lore"))
                .build();
    }

    private ItemStack searchIcon() {
        return ItemBuilder.of(Material.NAME_TAG)
                .name(tr("guide.actions.search.name"))
                .lore(tr("guide.actions.search.lore"))
                .build();
    }

    ItemStack closeIcon() {
        return ItemBuilder.of(Material.BARRIER).name(tr("guide.actions.close-menu")).build();
    }

    ItemStack backIcon(String text) {
        return SfxGuideIconLibrary.back("<yellow>" + text + "</yellow>");
    }

    private ItemStack lockedItemIcon(Player player, SfxItemDefinition definition, SfxResearchDefinition research) {
        return ItemBuilder.of(Material.BARRIER)
                .name("<white>" + itemDisplayName(definition) + "</white>")
                .lore(
                        "<gray>" + displayResearchName(research, definition) + "</gray>",
                        "",
                        tr("guide.research.locked"),
                        "",
                        tr("guide.research.click-unlock"),
                        "",
                        researchCostDisplay(player, research)
                )
                .build();
    }

    String displayResearchName(SfxResearchDefinition research, SfxItemDefinition definition) {
        return PlainTextComponentSerializer.plainText().serialize(localization.component(research.nameKey()));
    }

    private List<SfxItemCategory> visibleCategoriesFor(Player player, GuideMode mode) {
        return LegacySfGuideResolver.visibleCategories(registry, mode).stream()
                .filter(category -> isCategoryVisible(category.id()))
                .filter(category -> accessPolicy.canViewCategory(player, mode, category))
                .toList();
    }

    private boolean isCategoryVisible(String categoryId) {
        if (!LegacySfGuideResolver.isSeasonalCategory(categoryId)) {
            return true;
        }
        if (!plugin.getConfig().getBoolean("guide.seasonal-categories.enabled", true)) {
            return false;
        }
        if (plugin.getConfig().getBoolean("guide.seasonal-categories.always-show", true)) {
            return true;
        }
        return switch (categoryId) {
            case "guide:sf:christmas" -> currentMonth() == Month.DECEMBER;
            case "guide:sf:valentines_day" -> currentMonth() == Month.FEBRUARY;
            case "guide:sf:easter" -> currentMonth() == Month.APRIL;
            case "guide:sf:birthday", "guide:sf:halloween" -> currentMonth() == Month.OCTOBER;
            default -> true;
        };
    }

    private Month currentMonth() {
        return LocalDate.now().getMonth();
    }

    private boolean isCategoryUnlocked(Player player, String categoryId) {
        List<String> parents = LegacySfGuideResolver.parentCategories(categoryId);
        if (parents.isEmpty()) {
            return true;
        }
        Optional<SfxPlayerProfile> profile = profiles.find(player.getUniqueId());
        if (profile.isEmpty()) {
            return false;
        }
        for (String parentId : parents) {
            for (SfxItemDefinition item : LegacySfGuideResolver.visibleItemsInCategory(registry, parentId)) {
                Optional<SfxResearchDefinition> research = researches.researchForItem(item.id());
                if (research.isPresent() && !profile.get().hasUnlocked(research.get().id())) {
                    return false;
                }
            }
        }
        return true;
    }

    private void launchResearchFirework(Player player) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        org.bukkit.Color color = RESEARCH_FIREWORK_COLORS[random.nextInt(RESEARCH_FIREWORK_COLORS.length)];
        Firework firework = player.getWorld().spawn(player.getLocation().clone().add(random.nextInt(3) - 1, 0.0, random.nextInt(3) - 1), Firework.class, spawned -> {
            FireworkMeta meta = spawned.getFireworkMeta();
            meta.setDisplayName(org.bukkit.ChatColor.GREEN + "Slimefun Research");
            meta.setPower(random.nextInt(2) + 1);
            meta.addEffect(org.bukkit.FireworkEffect.builder()
                    .withColor(color)
                    .with(random.nextBoolean() ? org.bukkit.FireworkEffect.Type.BALL : org.bukkit.FireworkEffect.Type.BALL_LARGE)
                    .trail(random.nextBoolean())
                    .flicker(random.nextBoolean())
                    .build());
            spawned.setFireworkMeta(meta);
        });
        firework.setShotAtAngle(false);
        player.playSound(firework.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
    }

    SfxMenuButton toggleButton(Material material, String name, String lore, boolean enabled, java.util.function.Consumer<cc.theends6.sfx.api.menu.SfxMenuClickContext> handler) {
        return new SfxMenuButton(ItemBuilder.of(material)
                .name(name)
                .lore(
                        enabled
                                ? tr("guide.settings.toggle.enabled")
                                : tr("guide.settings.toggle.disabled"),
                        lore
                )
                .build(), handler::accept);
    }

    private ItemStack categoryButtonIcon(SfxItemCategory category) {
        ItemStack icon = switch (category.id()) {
            case "guide:sf:talismans" -> items.create("sf:common_talisman", 1);
            case "guide:sf:ender_talismans" -> items.create("sf:ender_talisman", 1);
            default -> category.icon();
        };
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.noItalic(localization.categoryName(category.id())));
            if ("guide:sf:magical_gadgets".equals(category.id())
                    || "guide:sf:magical_armor".equals(category.id())
                    || "guide:sf:armor".equals(category.id())) {
                meta.setEnchantmentGlintOverride(Boolean.TRUE);
            }
            icon.setItemMeta(meta);
        }
        return withLore(icon, List.of(Component.empty(), Text.mm(tr("guide.actions.open-category"))));
    }

    private ItemStack lockedCategoryIcon(Player player, SfxItemCategory category) {
        List<Component> lore = new ArrayList<>();
        lore.add(Text.mm("<white>" + tr("guide.locked-itemgroup.line1")));
        lore.add(Text.mm("<white>" + tr("guide.locked-itemgroup.line2")));
        lore.add(Text.mm("<white>" + tr("guide.locked-itemgroup.line3")));
        lore.add(Component.empty());
        for (String parentId : LegacySfGuideResolver.parentCategories(category.id())) {
            LegacySfGuideResolver.resolveCategory(registry, parentId)
                    .map(parent -> localization.categoryName(parent.id()))
                    .ifPresent(lore::add);
        }
        ItemStack item = ItemBuilder.of(Material.BARRIER)
                .name("<red>" + tr("guide.locked") + " <gray>-</gray> <white>" + plainCategoryName(category) + "</white>")
                .build();
        return withLore(item, lore);
    }

    private ItemStack machineSourceIcon(SfxManualMachineDefinition machine) {
        return ItemBuilder.of(machine.triggerMaterial())
                .name("<green>" + machineDisplayName(machine) + "</green>")
                .build();
    }

    private ItemStack associationMachineIcon(String machineId, ItemStack source) {
        ItemStack icon = source.clone();
        if (!"sf:enhanced_crafting_table".equals(machineId)) {
            return icon;
        }
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack multiblockSourceIcon() {
        return ItemBuilder.of(Material.NETHER_BRICK_FENCE)
                .name(tr("guide.recipe.multiblock.name"))
                .lore(
                        tr("guide.recipe.multiblock.lore.1"),
                        tr("guide.recipe.multiblock.lore.2")
                )
                .build();
    }

    private ItemStack vanillaSourceIcon(Material material, String name) {
        return ItemBuilder.of(material).name("<green>" + name + "</green>").build();
    }

    private String familyKey(String sourceId) {
        if (sourceId == null) {
            return "unknown";
        }
        return sourceId.replaceFirst("_(\\d+)$", "");
    }

    private boolean showVanillaRecipes() {
        return plugin.getConfig().getBoolean("guide.show-vanilla-recipes", true);
    }

    private boolean machineLinksEnabled() {
        return plugin.getConfig().getBoolean("guide.recipe.machine-link-enabled", true);
    }

    private boolean reopenLastLocation(Player player, GuideMode mode, GuideLocation location) {
        if (location == null || location.mode() != mode) {
            return false;
        }
        switch (location.kind()) {
            case MAIN -> openMain(player, mode, location.page(), Navigation.ROOT);
            case CATEGORY -> openCategory(player, mode, location.categoryId(), location.page(), Navigation.ROOT);
            case RECIPE -> openRecipe(player, mode, location.itemId(), location.recipeIndex(), Navigation.ROOT);
            case VANILLA -> openVanillaRecipe(player, mode, location.material(), location.recipeIndex(), Navigation.ROOT);
        }
        return true;
    }

    private void playGuideOpenSound(Player player) {
        playGuideSound(player, "guide.sounds.open", DEFAULT_GUIDE_SOUND_KEY, Sound.ITEM_BOOK_PAGE_TURN);
    }

    private void playGuideCategorySound(Player player) {
        playGuideSound(player, "guide.sounds.category", DEFAULT_GUIDE_SOUND_KEY, Sound.ITEM_BOOK_PAGE_TURN);
    }

    private void playGuidePageSound(Player player) {
        playGuideSound(player, "guide.sounds.page", DEFAULT_GUIDE_SOUND_KEY, Sound.ITEM_BOOK_PAGE_TURN);
    }

    private void playGuideSound(Player player, String soundPath, String fallbackKey, Sound fallback) {
        if (player == null || !plugin.getConfig().getBoolean("guide.sounds.enabled", true)) {
            return;
        }
        Sound sound = parseSound(plugin.getConfig().getString(soundPath, fallbackKey), fallback);
        float volume = (float) plugin.getConfig().getDouble("guide.sounds.volume", 0.7D);
        float pitch = (float) plugin.getConfig().getDouble("guide.sounds.pitch", 1.0D);
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private Sound parseSound(String raw, Sound fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        String candidate = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        String aliased = SOUND_ALIASES.get(candidate);
        if (aliased != null) {
            Sound sound = resolveSoundKey(aliased);
            return sound == null ? fallback : sound;
        }

        Sound direct = resolveSoundCandidate(candidate);
        return direct == null ? fallback : direct;
    }


    private Sound resolveSoundCandidate(String candidate) {
        return SfxGuideFormatting.resolveSoundCandidate(candidate, this::resolveSoundKey);
    }

    Sound resolveSoundKey(String rawKey) {
        NamespacedKey key = NamespacedKey.fromString(rawKey);
        return key == null ? null : Registry.SOUNDS.get(key);
    }

    void showMenu(Player player, SfxMenu.Builder builder, Navigation navigation) {
        builder.restorePreviousOnClose(true);
        SfxMenu menu = builder.build();
        switch (navigation) {
            case ROOT -> menus.openRoot(player, menu);
            case OPEN -> menus.open(player, menu);
            case REPLACE -> menus.replace(player, menu);
        }
    }

    void closeGuide(Player player) {
        menus.close(player, true);
    }

    void goBack(Player player, GuideMode fallbackMode) {
        if (menus.hasHistory(player)) {
            menus.close(player, true);
            return;
        }
        openMain(player, fallbackMode, 0, Navigation.ROOT);
    }

    GuidePreferences preferences(Player player) {
        return preferencesByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> profiles.find(player.getUniqueId())
                .map(this::preferencesFromProfile)
                .orElseGet(this::defaultPreferences));
    }

    private GuidePreferences defaultPreferences() {
        return new GuidePreferences(defaultLayout(), true, true, true, true, false, true, true, true, null);
    }

    private GuidePreferences preferencesFromProfile(SfxPlayerProfile profile) {
        String rawLayout = profile.guideLayoutMode();
        return new GuidePreferences(
                GuideLayout.from(rawLayout == null || rawLayout.isBlank() ? defaultLayout().name() : rawLayout),
                profile.guideRecordHistory(),
                profile.guideCloseReturns(),
                profile.guideFireworks(),
                profile.guideUnlockAnimation(),
                profile.guideReopenLastLocation(),
                profile.machineUiExtended(),
                profile.machineCompletionSound(),
                profile.machineSmoothUi(),
                decodeGuideLocation(profile.guideLastLocation()));
    }

    void persistPreferences(Player player, GuidePreferences preferences, boolean saveAsync) {
        SfxPlayerProfile profile = profiles.find(player.getUniqueId()).orElse(null);
        if (profile == null) {
            return;
        }
        syncPreferencesToProfile(profile, preferences);
        if (saveAsync) {
            profiles.saveAsync(profile);
        }
    }

    private void syncPreferencesToProfile(SfxPlayerProfile profile, GuidePreferences preferences) {
        profile.setGuideLayoutMode(preferences.layout().name());
        profile.setGuideRecordHistory(preferences.recordHistory());
        profile.setGuideCloseReturns(preferences.closeReturns());
        profile.setGuideFireworks(preferences.fireworks());
        profile.setGuideUnlockAnimation(preferences.unlockAnimation());
        profile.setGuideReopenLastLocation(preferences.reopenLastLocation());
        profile.setGuideLastLocation(encodeGuideLocation(preferences.lastLocation()));
        profile.setMachineUiExtended(preferences.machineUiExtended());
        profile.setMachineCompletionSound(preferences.machineCompletionSound());
        profile.setMachineSmoothUi(preferences.machineSmoothUi());
    }

    private void rememberLocation(Player player, GuideLocation location) {
        GuidePreferences preferences = preferences(player);
        preferences.setLastLocation(location);
        SfxPlayerProfile profile = profiles.find(player.getUniqueId()).orElse(null);
        if (profile != null) {
            profile.setGuideLastLocation(encodeGuideLocation(location));
        }
    }

    private String encodeGuideLocation(GuideLocation location) {
        if (location == null) {
            return null;
        }
        return String.join("\t",
                location.mode().name(),
                location.kind().name(),
                Integer.toString(location.page()),
                location.categoryId() == null ? "" : location.categoryId(),
                location.itemId() == null ? "" : location.itemId(),
                location.material() == null ? "" : location.material().name(),
                Integer.toString(location.recipeIndex()));
    }

    private GuideLocation decodeGuideLocation(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split("\t", -1);
        if (parts.length != 7) {
            return null;
        }
        try {
            GuideMode mode = GuideMode.valueOf(parts[0]);
            GuideLocationKind kind = GuideLocationKind.valueOf(parts[1]);
            int page = Integer.parseInt(parts[2]);
            String categoryId = parts[3].isBlank() ? null : parts[3];
            String itemId = parts[4].isBlank() ? null : parts[4];
            Material material = parts[5].isBlank() ? null : Material.valueOf(parts[5]);
            int recipeIndex = Integer.parseInt(parts[6]);
            return new GuideLocation(mode, kind, page, categoryId, itemId, material, recipeIndex);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private GuideLayout defaultLayout() {
        return GuideLayout.from(plugin.getConfig().getString("guide.layout-mode", "classic"));
    }

    GuideLayout effectiveLayout(GuidePreferences preferences) {
        if (!plugin.getConfig().getBoolean("guide.sfx-layout-enabled", true)) {
            return GuideLayout.CLASSIC;
        }
        return preferences.layout();
    }

    private boolean isUnlocked(Player player, SfxResearchDefinition research) {
        return profiles.find(player.getUniqueId())
                .map(profile -> profile.hasUnlocked(research.id()))
                .orElse(false);
    }

    private void unlockResearchAndRefresh(Player player, GuideMode mode, String categoryId, int page, SfxItemDefinition definition, SfxResearchDefinition research) {
        Optional<SfxPlayerProfile> optional = profiles.find(player.getUniqueId());
        if (optional.isEmpty()) {
            profiles.request(player, profile -> openCategory(player, mode, categoryId, page, Navigation.REPLACE));
            player.sendMessage(Text.prefixed(plugin, tr("messages.profile.loading")));
            return;
        }
        beginResearchUnlock(player, optional.get(), definition, research,
                () -> openCategory(player, mode, categoryId, page, Navigation.REPLACE),
                () -> openCategory(player, mode, categoryId, page, Navigation.REPLACE));
    }

    private void unlockResearchAndOpen(Player player, GuideMode mode, SfxItemDefinition definition, SfxResearchDefinition research) {
        Optional<SfxPlayerProfile> optional = profiles.find(player.getUniqueId());
        if (optional.isEmpty()) {
            profiles.request(player, profile -> openLockedResearchView(player, mode, definition, research, Navigation.REPLACE));
            player.sendMessage(Text.prefixed(plugin, tr("messages.profile.loading")));
            return;
        }
        beginResearchUnlock(player, optional.get(), definition, research,
                () -> openRecipe(player, mode, definition.id(), 0, Navigation.REPLACE),
                () -> openLockedResearchView(player, mode, definition, research, Navigation.REPLACE));
    }

    private void beginResearchUnlock(Player player, SfxPlayerProfile profile, SfxItemDefinition definition, SfxResearchDefinition research, Runnable onSuccess, Runnable onFailure) {
        SfxGuideResearchUnlockController.begin(this, player, profile, definition, research, onSuccess, onFailure);
    }

    void finishResearchUnlock(Player player, SfxPlayerProfile profile, SfxItemDefinition definition, SfxResearchDefinition research, Runnable onSuccess) {
        researchingPlayers.remove(player.getUniqueId());
        researches.grant(profile, research);
        if (player.isOnline()) {
            GuidePreferences preferences = preferences(player);
            if (preferences.fireworks()) {
                launchResearchFirework(player);
            }
            player.sendMessage(Text.prefixed(plugin,
                    tr("messages.research.unlocked")
                            .replace("{name}", displayResearchName(research, definition))));
            onSuccess.run();
        }
    }

    String researchCostDisplay(Player player, SfxResearchDefinition research) {
        return researchPayments.displayCost(player, research);
    }

    SfxResearchPaymentResult chargeResearch(Player player, SfxResearchDefinition research) {
        return researchPayments.charge(player, research);
    }

    void playResearchSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.7f, 1.0f);
    }

    void switchGuideBookMode(Player player, GuideMode mode) {
        if (!accessPolicy.canOpen(player, mode)) {
            player.sendMessage(Text.prefixed(plugin, tr("guide.errors.no-open")));
            return;
        }
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (items.readGuideMode(mainHand).isPresent()) {
            player.getInventory().setItemInMainHand(items.createGuideBook(mode));
        } else {
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (items.readGuideMode(offHand).isPresent()) {
                player.getInventory().setItemInOffHand(items.createGuideBook(mode));
            }
        }
        openMain(player, mode, 0, Navigation.ROOT);
    }

    String tr(String key) {
        return localization.text(key);
    }

    Component title(GuideMode mode, String suffix) {
        String configured = mode == GuideMode.CHEAT
                ? plugin.getConfig().getString("guide.cheat-title", tr("guide.mode.cheat.title"))
                : plugin.getConfig().getString("guide.survival-title", tr("guide.mode.survival.title"));
        return Text.mm(configured + " <dark_gray>|</dark_gray> <gray>" + suffix + "</gray>");
    }

    private String plainCategoryName(SfxItemCategory category) {
        return plainText(localization.categoryName(category.id()));
    }

    private String materialName(Material material) {
        String key = "materials." + material.name().toLowerCase();
        return localization.text(key);
    }

    private String itemDisplayName(SfxItemDefinition definition) {
        return plainText(localization.itemName(definition.id()));
    }

    private String machineDisplayName(SfxManualMachineDefinition definition) {
        return plainText(localization.itemName(definition.id()));
    }

    private String plainText(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private int pageCount(int size) {
        return Math.max(1, (int) Math.ceil(size / (double) CONTENT_SLOTS.length));
    }

    private int clampPage(int page, int pageCount) {
        if (page < 0) {
            return 0;
        }
        return Math.min(page, pageCount - 1);
    }

    private ItemStack withLore(ItemStack base, Collection<Component> append) {
        ItemStack copy = base.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.addAll(append.stream().map(Text::noItalic).toList());
        meta.lore(lore);
        copy.setItemMeta(meta);
        return copy;
    }

    private String pageNumberLore(int page, int pageCount) {
        return tr("guide.pagination.page")
                .replace("{current}", Integer.toString(page + 1))
                .replace("{total}", Integer.toString(pageCount));
    }

    private record AggregatedRecipeView(boolean listMode, int page) {
        private AggregatedRecipeView {
            page = Math.max(0, page);
        }
    }

    private record UsageTarget(ItemStack icon, ClickHandler handler) {
    }


}
