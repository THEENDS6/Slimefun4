package cc.theends6.sfx.internal.guide;

import cc.theends6.sfx.api.guide.GuideMode;
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
import cc.theends6.sfx.internal.item.DefaultSfxItemRegistry;
import cc.theends6.sfx.internal.machine.DefaultManualMachineRegistry;
import cc.theends6.sfx.internal.machine.ManualMachineDefinition;
import cc.theends6.sfx.internal.machine.ManualMachineOperation;
import cc.theends6.sfx.internal.machine.ManualMachineOutput;
import cc.theends6.sfx.internal.machine.ManualMachineRecipe;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.playerdata.SfxPlayerProfile;
import cc.theends6.sfx.internal.research.SfxResearchDefinition;
import cc.theends6.sfx.internal.research.SfxResearchService;
import cc.theends6.sfx.internal.util.ItemBuilder;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
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
    private static final int[] RESEARCH_PROGRESS = {23, 44, 57, 92};
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

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final DefaultSfxItemRegistry registry;
    private final SfxItems items;
    private final SfxMenus menus;
    private final SfxGuideAccessPolicy accessPolicy;
    private final DefaultManualMachineRegistry manualMachines;
    private final SfxLocalization localization;
    private final SfxPlayerDataService profiles;
    private final SfxResearchService researches;
    private final Map<UUID, GuidePreferences> preferencesByPlayer = new ConcurrentHashMap<>();
    private final Map<Material, List<GuideRecipePage>> vanillaRecipeCache = new ConcurrentHashMap<>();
    private final Set<UUID> researchingPlayers = ConcurrentHashMap.newKeySet();

    public DefaultSfxGuide(
            JavaPlugin plugin,
            SfxRuntime runtime,
            DefaultSfxItemRegistry registry,
            SfxItems items,
            SfxMenus menus,
            SfxGuideAccessPolicy accessPolicy,
            DefaultManualMachineRegistry manualMachines,
            SfxLocalization localization,
            SfxPlayerDataService profiles,
            SfxResearchService researches
    ) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.registry = registry;
        this.items = items;
        this.menus = menus;
        this.accessPolicy = accessPolicy;
        this.manualMachines = manualMachines;
        this.localization = localization;
        this.profiles = profiles;
        this.researches = researches;
    }

    @Override
    public void open(Player player, GuideMode mode) {
        if (!accessPolicy.canOpen(player, mode)) {
            player.sendMessage(Text.prefixed(plugin, tr("guide.errors.no-open", "<red>You do not have permission to open this guide.</red>")));
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
            player.sendMessage(Text.prefixed(plugin, tr("guide.errors.no-settings", "<red>You do not have permission to open the guide settings.</red>")));
            return;
        }
        openSettingsView(player, mode, Navigation.ROOT);
    }

    private void openSettingsView(Player player, GuideMode mode, Navigation navigation) {
        GuidePreferences preferences = preferences(player);
        GuideLayout layout = effectiveLayout(preferences);
        GuideMode targetMode = mode == GuideMode.CHEAT ? GuideMode.SURVIVAL : GuideMode.CHEAT;
        boolean allowLayoutSwitch = plugin.getConfig().getBoolean("guide.allow-layout-switching", true)
                && plugin.getConfig().getBoolean("guide.sfx-layout-enabled", true);

        SfxMenu.Builder builder = SfxMenu.builder(title(mode, tr("guide.settings.title", "Guide Settings"))).rows(6);
        paintSettingsBackground(builder, mode);

        builder.button(0, new SfxMenuButton(backIcon(tr("guide.actions.back-guide", "Back to Guide")), click -> goBack(click.player(), mode)));
        builder.button(2, new SfxMenuButton(modeInfoIcon(mode), click -> {
        }));
        builder.button(4, new SfxMenuButton(ItemBuilder.of(Material.WRITABLE_BOOK)
                .name(tr("guide.settings.title-item", "<green>Guide Settings</green>"))
                .lore(
                        tr("guide.settings.description.1", "<gray>Configure how this guide behaves for you.</gray>"),
                        tr("guide.settings.description.2", "<gray>The classic layout is the default restored experience.</gray>")
                )
                .build(), click -> {
        }));
        builder.button(6, new SfxMenuButton(ItemBuilder.of(Material.KNOWLEDGE_BOOK)
                .name(tr("guide.settings.help.name", "<aqua>Guide Help</aqua>"))
                .lore(
                        tr("guide.settings.help.lore.1", "<gray>Shift + Right Click the guide book to open this menu.</gray>"),
                        tr("guide.settings.help.lore.2", "<gray>Layout, history, and close behavior are per-player guide preferences.</gray>")
                )
                .build(), click -> {
        }));
        builder.button(8, new SfxMenuButton(closeIcon(), click -> closeGuide(click.player())));

        builder.button(19, toggleButton(
                targetMode == GuideMode.CHEAT ? Material.COMMAND_BLOCK : Material.ENCHANTED_BOOK,
                tr("guide.settings.mode.name", "<yellow>Guide Mode</yellow>"),
                targetMode == GuideMode.CHEAT
                        ? tr("guide.settings.mode.cheat.lore", "<gray>Click to switch this book to the cheat guide.</gray>")
                        : tr("guide.settings.mode.survival.lore", "<gray>Click to switch this book back to the survival guide.</gray>"),
                mode == GuideMode.CHEAT,
                    click -> switchGuideBookMode(click.player(), targetMode)
        ));

        if (allowLayoutSwitch) {
            GuideLayout targetLayout = layout == GuideLayout.CLASSIC ? GuideLayout.SFX : GuideLayout.CLASSIC;
            builder.button(21, toggleButton(
                    targetLayout == GuideLayout.CLASSIC ? Material.CRAFTING_TABLE : Material.SMITHING_TABLE,
                    tr("guide.settings.layout.name", "<yellow>Guide Layout</yellow>"),
                    layout == GuideLayout.CLASSIC
                            ? tr("guide.settings.layout.sfx", "<gray>Current: classic. Click to switch to the expanded SFX layout.</gray>")
                            : tr("guide.settings.layout.classic", "<gray>Current: SFX. Click to switch to the restored classic layout.</gray>"),
                    layout == GuideLayout.SFX,
                    click -> {
                        preferences.setLayout(targetLayout);
                        persistPreferences(click.player(), preferences, true);
                        openSettingsView(click.player(), mode, Navigation.REPLACE);
                    }
            ));
        }

        builder.button(23, toggleButton(
                Material.COMPARATOR,
                tr("guide.settings.history.name", "<yellow>Nested Recipe History</yellow>"),
                tr("guide.settings.history.lore", "<gray>Opening a recipe from another recipe remembers the previous page.</gray>"),
                preferences.recordHistory(),
                click -> {
                    preferences.setRecordHistory(!preferences.recordHistory());
                    persistPreferences(click.player(), preferences, true);
                    openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(25, toggleButton(
                Material.OAK_DOOR,
                tr("guide.settings.close-behavior.name", "<yellow>Esc / E Behavior</yellow>"),
                tr("guide.settings.close-behavior.lore", "<gray>Choose whether closing the guide returns to the previous page.</gray>"),
                preferences.closeReturns(),
                click -> {
                    preferences.setCloseReturns(!preferences.closeReturns());
                    persistPreferences(click.player(), preferences, true);
                    openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(39, toggleButton(
                Material.FIREWORK_ROCKET,
                tr("guide.settings.fireworks.name", "<yellow>Research Fireworks</yellow>"),
                tr("guide.settings.fireworks.lore", "<gray>Show a large firework when you finish researching an item.</gray>"),
                preferences.fireworks(),
                click -> {
                    preferences.setFireworks(!preferences.fireworks());
                    persistPreferences(click.player(), preferences, true);
                    openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(41, toggleButton(
                Material.REDSTONE_TORCH,
                tr("guide.settings.unlock-animation.name", "<yellow>Unlock Animation</yellow>"),
                tr("guide.settings.unlock-animation.lore", "<gray>Show the pondering progress in chat while researching an item.</gray>"),
                preferences.unlockAnimation(),
                click -> {
                    preferences.setUnlockAnimation(!preferences.unlockAnimation());
                    persistPreferences(click.player(), preferences, true);
                    openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(43, toggleButton(
                Material.RECOVERY_COMPASS,
                tr("guide.settings.resume-last.name", "<yellow>Resume Last Page</yellow>"),
                tr("guide.settings.resume-last.lore", "<gray>Reopen the guide at the last page you closed.</gray>"),
                preferences.reopenLastLocation(),
                click -> {
                    preferences.setReopenLastLocation(!preferences.reopenLastLocation());
                    persistPreferences(click.player(), preferences, true);
                    openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(47, toggleButton(
                Material.COMPASS,
                tr("guide.settings.machine-ui.name", "<yellow>Machine UI Details</yellow>"),
                tr("guide.settings.machine-ui.lore", "<gray>Show SFX extra machine details on top of the classic progress display.</gray>"),
                preferences.machineUiExtended(),
                click -> {
                    preferences.setMachineUiExtended(!preferences.machineUiExtended());
                    persistPreferences(click.player(), preferences, true);
                    openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(51, toggleButton(
                Material.NOTE_BLOCK,
                tr("guide.settings.machine-sound.name", "<yellow>Machine Completion Sound</yellow>"),
                tr("guide.settings.machine-sound.lore", "<gray>Play the machine completion sound only for you while viewing its UI.</gray>"),
                preferences.machineCompletionSound(),
                click -> {
                    preferences.setMachineCompletionSound(!preferences.machineCompletionSound());
                    persistPreferences(click.player(), preferences, true);
                    openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(53, toggleButton(
                Material.CLOCK,
                tr("guide.settings.machine-smooth.name", "<yellow>Smooth Machine UI</yellow>"),
                tr("guide.settings.machine-smooth.lore", "<gray>Enable per-tick machine UI refresh. Disable for a classic 10-tick display style.</gray>"),
                preferences.machineSmoothUi(),
                click -> {
                    preferences.setMachineSmoothUi(!preferences.machineSmoothUi());
                    persistPreferences(click.player(), preferences, true);
                    openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(49, new SfxMenuButton(ItemBuilder.of(Material.NETHER_STAR)
                .name(tr("guide.settings.current-layout.name", "<aqua>Current Layout</aqua>"))
                .lore(
                        tr(layout == GuideLayout.CLASSIC ? "guide.settings.current-layout.classic" : "guide.settings.current-layout.sfx",
                                layout == GuideLayout.CLASSIC
                                        ? "<gray>Classic layout is active.</gray>"
                                        : "<gray>SFX layout is active.</gray>"),
                        tr("guide.settings.current-layout.mode", "<gray>Guide mode: {mode}</gray>")
                                .replace("{mode}", mode == GuideMode.CHEAT ? "Cheat" : "Survival")
                )
                .build(), click -> {
        }));

        showMenu(player, builder, navigation);
    }

    private void openMain(Player player, GuideMode mode, int page, Navigation navigation) {
        rememberLocation(player, GuideLocation.main(mode, page));
        List<SfxItemCategory> visibleCategories = visibleCategoriesFor(player, mode);
        int pageCount = pageCount(visibleCategories.size());
        int safePage = clampPage(page, pageCount);

        SfxMenu.Builder builder = SfxMenu.builder(title(mode, tr("guide.main.title", "Main Menu"))).rows(6);
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
                previous -> openMain(previous, mode, safePage - 1, Navigation.REPLACE),
                next -> openMain(next, mode, safePage + 1, Navigation.REPLACE));
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
            player.sendMessage(Text.prefixed(plugin, tr("guide.errors.missing-category", "<red>This category does not exist.</red>")));
            return;
        }
        SfxItemCategory category = optionalCategory.get();
        List<SfxItemDefinition> entries = LegacySfGuideResolver.visibleItemsInCategory(registry, category.id()).stream()
                .filter(item -> accessPolicy.canViewItem(player, mode, item))
                .toList();

        int pageCount = pageCount(entries.size());
        int safePage = clampPage(page, pageCount);

        SfxMenu.Builder builder = SfxMenu.builder(title(mode, plainCategoryName(category))).rows(6);
        paintFrame(builder, mode, effectiveLayout(preferences(player)));

        int from = safePage * CONTENT_SLOTS.length;
        int to = Math.min(entries.size(), from + CONTENT_SLOTS.length);
        for (int i = from; i < to; i++) {
            SfxItemDefinition definition = entries.get(i);
            int slot = CONTENT_SLOTS[i - from];
            SfxResearchDefinition research = researches.researchForItem(definition.id()).orElse(null);
            boolean locked = mode == GuideMode.SURVIVAL && research != null && !isUnlocked(player, research);
            ItemStack icon = locked ? lockedItemIcon(definition, research) : items.create(definition, 1);
            if (mode == GuideMode.CHEAT) {
                Optional<ManualMachineDefinition> manualMachine = manualMachines.machine(definition.id())
                        .or(() -> cc.theends6.sfx.internal.machine.ExtraDeployStructures.machine(definition.id()));
                if (manualMachine.isPresent()) {
                    icon = withLore(icon, List.of(
                            Component.empty(),
                            Text.mm(tr("guide.cheat.machine-pack", "<red>Cheat: click to receive a deploy pack</red>")),
                            Text.mm(tr("guide.cheat.machine-kit", "<red>Shift click to receive the full structure kit</red>"))
                    ));
                } else {
                    icon = withLore(icon, List.of(
                            Component.empty(),
                            Text.mm(tr("guide.cheat.take-one", "<red>Cheat: click to receive 1 item</red>")),
                            Text.mm(tr("guide.cheat.take-stack", "<red>Shift click to receive 64 items</red>"))
                    ));
                }
                builder.button(slot, new SfxMenuButton(icon, click -> giveFromCheatGuide(click.player(), definition, click.clickType())));
            } else if (locked) {
                builder.button(slot, new SfxMenuButton(icon, click -> unlockResearchAndRefresh(click.player(), mode, category.id(), safePage, definition, research)));
            } else {
                icon = withLore(icon, List.of(Component.empty(), Text.mm(tr("guide.actions.open-recipe", "<gray>Click to view recipe</gray>"))));
                builder.button(slot, new SfxMenuButton(icon, click -> openRecipe(click.player(), mode, definition.id(), 0, Navigation.OPEN)));
            }
        }

        builder.button(1, new SfxMenuButton(backIcon(tr("guide.actions.back-main", "Back to Main Menu")), click -> goBack(click.player(), mode)));
        addContentPagination(builder, safePage, pageCount,
                previous -> openCategory(previous, mode, category.id(), safePage - 1, Navigation.REPLACE),
                next -> openCategory(next, mode, category.id(), safePage + 1, Navigation.REPLACE));
        builder.button(49, new SfxMenuButton(infoIcon(mode, safePage, pageCount), click -> closeGuide(click.player())));
        showMenu(player, builder, navigation);
    }

    private void openRecipe(Player player, GuideMode mode, String itemId, int recipeIndex, Navigation navigation) {
        openRecipe(player, mode, itemId, recipeIndex, 0, navigation);
    }

    private void openRecipe(Player player, GuideMode mode, String itemId, int recipeIndex, int extraDisplayPage, Navigation navigation) {
        rememberLocation(player, GuideLocation.recipe(mode, itemId, recipeIndex));
        Optional<SfxItemDefinition> optional = registry.item(itemId);
        if (optional.isEmpty()) {
            player.sendMessage(Text.prefixed(plugin, tr("guide.errors.missing-item", "<red>This item does not exist.</red>")));
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
        GuideLayout layout = effectiveLayout(preferences(player));
        OutputAction outputAction = (targetPlayer, clickType) -> {
            if (mode == GuideMode.CHEAT) {
                giveFromCheatGuide(targetPlayer, definition, clickType);
            }
        };
        RecipePageOpener opener = (targetPlayer, nextRecipe, nextNavigation) -> openRecipe(targetPlayer, mode, definition.id(), nextRecipe, nextNavigation);

        if (pages.isEmpty()) {
            renderRecipeWithoutEntries(player, mode, layout, itemDisplayName(definition), items.create(definition, 1), navigation, outputAction, definition, List.of(), opener);
            return;
        }

        GuideRecipePage current = pages.get(safeRecipe);
        List<DisplayEntry> displayEntries = displayEntriesFor(definition, pages, current, mode, opener);
        renderRecipe(player, mode, layout, itemDisplayName(definition), items.create(definition, current.outputAmount()), pages, current, navigation, outputAction, definition, displayEntries, opener, extraDisplayPage);
    }

    private void openVanillaRecipe(Player player, GuideMode mode, Material material, int recipeIndex, Navigation navigation) {
        rememberLocation(player, GuideLocation.vanilla(mode, material, recipeIndex));
        if (!showVanillaRecipes()) {
            return;
        }
        List<GuideRecipePage> pages = vanillaRecipePages(material);
        if (pages.isEmpty()) {
            return;
        }
        int safeRecipe = clampPage(recipeIndex, pages.size());
        GuideRecipePage current = pages.get(safeRecipe);
        GuideLayout layout = effectiveLayout(preferences(player));
        ItemStack output = new ItemStack(material, current.outputAmount());
        OutputAction outputAction = (targetPlayer, clickType) -> {
            if (mode != GuideMode.CHEAT) {
                return;
            }
            int amount = clickType != null && clickType.isShiftClick() ? 64 : Math.max(1, current.outputAmount());
            targetPlayer.getInventory().addItem(new ItemStack(material, amount));
            targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
        };
        RecipePageOpener opener = (targetPlayer, nextRecipe, nextNavigation) -> openVanillaRecipe(targetPlayer, mode, material, nextRecipe, nextNavigation);
        renderRecipe(player, mode, layout, materialName(material), output, pages, current, navigation, outputAction, null,
                alternativeSourceEntries(pages, current, opener), opener, 0);
    }

    private void openLockedResearchView(Player player, GuideMode mode, SfxItemDefinition definition, SfxResearchDefinition research, Navigation navigation) {
        SfxMenu.Builder builder = SfxMenu.builder(title(mode, itemDisplayName(definition))).rows(3);
        builder.button(0, new SfxMenuButton(backIcon(tr("guide.actions.back-category", "Back to Category")), click -> goBack(click.player(), mode)));
        builder.button(1, new SfxMenuButton(settingsIcon(), click -> openSettingsView(click.player(), mode, Navigation.OPEN)));
        builder.button(8, new SfxMenuButton(closeIcon(), click -> closeGuide(click.player())));
        builder.button(13, new SfxMenuButton(lockedItemIcon(definition, research), click -> unlockResearchAndOpen(click.player(), mode, definition, research)));
        builder.button(15, new SfxMenuButton(ItemBuilder.of(Material.EXPERIENCE_BOTTLE)
                .name(tr("guide.research.unlock.name", "<green>Unlock Research</green>"))
                .lore(
                        tr("guide.research.unlock.lore.1", "<gray>Spend experience levels to unlock this item.</gray>"),
                        tr("guide.research.cost", "<gray>Cost: </gray><aqua>{cost} levels</aqua>").replace("{cost}", Integer.toString(research.cost()))
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
            RecipePageOpener opener,
            int extraDisplayPage
    ) {
        if (layout == GuideLayout.CLASSIC) {
            renderClassicRecipe(player, mode, subjectTitle, outputItem, pages, current, navigation, outputAction, definition, displayEntries, opener);
            return;
        }
        renderSfxRecipe(player, mode, subjectTitle, outputItem, pages, current, navigation, outputAction, definition, displayEntries, opener, extraDisplayPage);
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
        renderRecipe(player, mode, layout, subjectTitle, outputItem, List.of(empty), empty, navigation, outputAction, definition, displayEntries, opener, 0);
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

        builder.button(0, new SfxMenuButton(backIcon(tr("guide.actions.back-category", "Back to Category")), click -> goBack(click.player(), mode)));
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
                builder.button(CLASSIC_RECIPE_SLOTS[i], ingredientButton(slot, mode));
            }
        } else {
            for (int slot : CLASSIC_RECIPE_SLOTS) {
                builder.button(slot, new SfxMenuButton(emptyMatrixSlotIcon(), click -> {
                }));
            }
            builder.button(CLASSIC_RECIPE_CENTER_SLOT, new SfxMenuButton(ItemBuilder.of(Material.BARRIER)
                    .name(tr("guide.recipe.no-recipe.name", "<red>No Recipe</red>"))
                    .lore(tr("guide.recipe.no-recipe.lore", "<gray>This item currently only exists as a registry or system entry.</gray>"))
                    .build(), click -> {
            }));
        }

        builder.button(CLASSIC_SOURCE_SLOT, recipeSourceButton(current, mode));
        builder.button(CLASSIC_OUTPUT_SLOT, new SfxMenuButton(withLore(outputItem, List.of(
                Component.empty(),
                Text.mm(tr("guide.recipe.output", "<green>Output</green>"))
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
            RecipePageOpener opener,
            int extraDisplayPage
    ) {
        SfxDisplayLayout displayLayout = sfxDisplayLayout(displayEntries);
        DisplayPage displayPage = displayPage(displayEntries, displayLayout, extraDisplayPage);
        int[] recipeSlots = displayLayout == SfxDisplayLayout.PAIRED_GRID ? SFX_RECIPE_SLOTS_TOP : SFX_RECIPE_SLOTS_NORMAL;
        int recipeCenterSlot = displayLayout == SfxDisplayLayout.PAIRED_GRID ? SFX_RECIPE_CENTER_SLOT_TOP : SFX_RECIPE_CENTER_SLOT_NORMAL;
        int sourceSlot = displayLayout == SfxDisplayLayout.PAIRED_GRID ? SFX_SOURCE_SLOT_TOP : SFX_SOURCE_SLOT_NORMAL;
        int outputSlot = displayLayout == SfxDisplayLayout.PAIRED_GRID ? SFX_OUTPUT_SLOT_TOP : SFX_OUTPUT_SLOT_NORMAL;
        SfxMenu.Builder builder = SfxMenu.builder(title(mode, subjectTitle)).rows(6);
        paintRecipeFrame(builder, mode);
        if (displayLayout != SfxDisplayLayout.PAIRED_GRID) {
            paintSfxLowerDivider(builder, current, pages.size());
        }

        if (current.hasRecipe()) {
            for (int i = 0; i < recipeSlots.length; i++) {
                builder.button(recipeSlots[i], ingredientButton(current.matrix().get(i), mode));
            }
        } else {
            for (int slot : recipeSlots) {
                builder.button(slot, new SfxMenuButton(emptyMatrixSlotIcon(), click -> {
                }));
            }
            builder.button(recipeCenterSlot, new SfxMenuButton(ItemBuilder.of(Material.BARRIER)
                    .name(tr("guide.recipe.no-recipe.name", "<red>No Recipe</red>"))
                    .lore(tr("guide.recipe.no-recipe.lore", "<gray>This item currently only exists as a registry or system entry.</gray>"))
                    .build(), click -> {
            }));
        }

        builder.button(sourceSlot, recipeSourceButton(current, mode));
        builder.button(outputSlot, new SfxMenuButton(withLore(outputItem, List.of(
                Component.empty(),
                Text.mm(tr("guide.recipe.output", "<green>Output</green>"))
        )), click -> outputAction.accept(click.player(), click.clickType())));

        builder.button(0, new SfxMenuButton(backIcon(tr("guide.actions.back-category", "Back to Category")), click -> goBack(click.player(), mode)));
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

        if (displayLayout == SfxDisplayLayout.PAIRED_GRID) {
            paintClassicDivider(builder, current, pages.size());
            if (definition != null) {
                addExtraDisplayPagination(builder, displayPage.page(), displayPage.pageCount(),
                        previous -> openRecipe(previous, mode, definition.id(), current.index(), displayPage.page() - 1, Navigation.REPLACE),
                        next -> openRecipe(next, mode, definition.id(), current.index(), displayPage.page() + 1, Navigation.REPLACE));
            }
            renderDisplayEntries(builder, displayPage.entries(), SFX_DISPLAY_SLOTS_PAIRED);
        } else if (displayLayout == SfxDisplayLayout.COMPACT_LIST) {
            renderDisplayEntries(builder, displayPage.entries(), SFX_DISPLAY_SLOTS_COMPACT);
        }
        showMenu(player, builder, navigation);
    }

    private List<GuideRecipePage> sfxRecipePages(SfxItemDefinition definition) {
        List<GuideRecipePage> pages = new ArrayList<>();
        List<SfxRecipe> recipes = definition.recipes();
        for (int i = 0; i < recipes.size(); i++) {
            SfxRecipe recipe = recipes.get(i);
            pages.add(createSfxRecipePage(definition, recipe, i));
        }
        return List.copyOf(pages);
    }

    private GuideRecipePage createSfxRecipePage(SfxItemDefinition resultDefinition, SfxRecipe recipe, int index) {
        if ("multiblock-structure".equals(recipe.recipeType())) {
            String sourceName = tr("guide.recipe.multiblock.name", "Multiblock Machine");
            ItemStack sourceIcon = multiblockSourceIcon();
            return new GuideRecipePage(index, GuideRecipeOrigin.SFX, resultDefinition.id(), familyKey(resultDefinition.id()), sourceName,
                    null, sourceIcon, normalizeMatrix(recipe.matrix()), recipe.note(), recipe.outputAmount(), false);
        }

        Optional<ManualMachineDefinition> machine = manualMachines.machine(recipe.recipeType());
        if (machine.isPresent()) {
            return new GuideRecipePage(index, GuideRecipeOrigin.SFX, machine.get().id(), familyKey(machine.get().id()), machineDisplayName(machine.get()),
                    machine.get().id(), machineSourceIcon(machine.get()), normalizeMatrix(recipe.matrix()), recipe.note(), recipe.outputAmount(),
                    false);
        }

        Optional<SfxItemDefinition> machineDefinition = registry.item(recipe.recipeType());
        if (machineDefinition.isPresent()) {
            ItemStack sourceIcon = withLore(items.create(machineDefinition.get(), 1), List.of(Component.empty(), Text.mm(tr("guide.actions.open-recipe", "<gray>Click to view recipe</gray>"))));
            return new GuideRecipePage(index, GuideRecipeOrigin.SFX, machineDefinition.get().id(), familyKey(machineDefinition.get().id()),
                    itemDisplayName(machineDefinition.get()), machineDefinition.get().id(), sourceIcon, normalizeMatrix(recipe.matrix()), recipe.note(), recipe.outputAmount(),
                    false);
        }

        ItemStack sourceIcon = ItemBuilder.of(Material.BOOK).name("<green>" + recipe.recipeType() + "</green>").build();
        return new GuideRecipePage(index, GuideRecipeOrigin.SFX, recipe.recipeType(), familyKey(recipe.recipeType()),
                recipe.recipeType(), null, sourceIcon, normalizeMatrix(recipe.matrix()), recipe.note(), recipe.outputAmount(), false);
    }

    private List<GuideRecipePage> vanillaRecipePages(Material material) {
        return vanillaRecipeCache.computeIfAbsent(material, this::loadVanillaRecipePages);
    }

    private List<GuideRecipePage> loadVanillaRecipePages(Material material) {
        List<GuideRecipePage> pages = new ArrayList<>();
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
        return List.copyOf(pages);
    }

    private GuideRecipePage createVanillaRecipePage(Recipe recipe, int index) {
        if (recipe instanceof ShapedRecipe shaped) {
            return new GuideRecipePage(index, GuideRecipeOrigin.VANILLA, "minecraft:crafting", "minecraft:crafting",
                    tr("guide.recipe.vanilla.crafting", "Crafting Table"), null,
                    vanillaSourceIcon(Material.CRAFTING_TABLE, tr("guide.recipe.vanilla.crafting", "Crafting Table")),
                    normalizeMatrix(fromShapedRecipe(shaped)), null, Math.max(1, shaped.getResult().getAmount()), false);
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            return new GuideRecipePage(index, GuideRecipeOrigin.VANILLA, "minecraft:crafting", "minecraft:crafting",
                    tr("guide.recipe.vanilla.crafting", "Crafting Table"), null,
                    vanillaSourceIcon(Material.CRAFTING_TABLE, tr("guide.recipe.vanilla.crafting", "Crafting Table")),
                    normalizeMatrix(fromShapelessRecipe(shapeless)), null, Math.max(1, shapeless.getResult().getAmount()), false);
        }
        if (recipe instanceof StonecuttingRecipe stonecutting) {
            return new GuideRecipePage(index, GuideRecipeOrigin.VANILLA, "minecraft:stonecutting", "minecraft:stonecutting",
                    tr("guide.recipe.vanilla.stonecutting", "Stonecutter"), null,
                    vanillaSourceIcon(Material.STONECUTTER, tr("guide.recipe.vanilla.stonecutting", "Stonecutter")),
                    normalizeMatrix(singleChoiceMatrix(stonecutting.getInputChoice())), null, Math.max(1, stonecutting.getResult().getAmount()), false);
        }
        if (recipe instanceof BlastingRecipe blasting) {
            return cookingPage(index, blasting, Material.BLAST_FURNACE, tr("guide.recipe.vanilla.blasting", "Blast Furnace"));
        }
        if (recipe instanceof SmokingRecipe smoking) {
            return cookingPage(index, smoking, Material.SMOKER, tr("guide.recipe.vanilla.smoking", "Smoker"));
        }
        if (recipe instanceof CampfireRecipe campfire) {
            return cookingPage(index, campfire, Material.CAMPFIRE, tr("guide.recipe.vanilla.campfire", "Campfire"));
        }
        if (recipe instanceof FurnaceRecipe furnace) {
            return cookingPage(index, furnace, Material.FURNACE, tr("guide.recipe.vanilla.furnace", "Furnace"));
        }
        return null;
    }

    private GuideRecipePage cookingPage(int index, CookingRecipe<?> recipe, Material icon, String name) {
        return new GuideRecipePage(index, GuideRecipeOrigin.VANILLA, "minecraft:" + icon.name().toLowerCase(), "minecraft:" + icon.name().toLowerCase(),
                name, null, vanillaSourceIcon(icon, name), normalizeMatrix(singleChoiceMatrix(recipe.getInputChoice())), null, Math.max(1, recipe.getResult().getAmount()), false);
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

    private boolean usesVerticalSingleLayout(ManualMachineDefinition machine, SfxRecipe recipe) {
        return usesVerticalSingleLayout(machine.id(), recipe)
                || machine.operation() == ManualMachineOperation.SINGLE_INPUT
                || machine.operation() == ManualMachineOperation.HAND_INPUT;
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

    private List<DisplayEntry> displayEntriesFor(SfxItemDefinition definition, List<GuideRecipePage> pages, GuideRecipePage current, GuideMode mode, RecipePageOpener opener) {
        List<DisplayEntry> entries = new ArrayList<>();
        entries.addAll(alternativeSourceEntries(pages, current, opener));
        if (definition != null) {
            entries.addAll(specialDisplayEntries(definition, mode));
            entries.addAll(machineOutputEntries(definition, mode));
        }
        return entries.stream()
                .sorted(DISPLAY_ENTRY_ORDER)
                .toList();
    }

    private List<DisplayEntry> specialDisplayEntries(SfxItemDefinition definition, GuideMode mode) {
        return switch (definition.id()) {
            case "sf:composter" -> pairedDisplayEntries(List.of(
                    SfxRecipeSlot.vanilla(Material.OAK_LEAVES, 8), SfxRecipeSlot.vanilla(Material.DIRT),
                    SfxRecipeSlot.vanilla(Material.OAK_SAPLING, 8), SfxRecipeSlot.vanilla(Material.DIRT),
                    SfxRecipeSlot.vanilla(Material.STONE, 4), SfxRecipeSlot.vanilla(Material.NETHERRACK),
                    SfxRecipeSlot.vanilla(Material.SAND, 2), SfxRecipeSlot.vanilla(Material.SOUL_SAND),
                    SfxRecipeSlot.vanilla(Material.WHEAT, 4), SfxRecipeSlot.vanilla(Material.NETHER_WART)
            ), mode);
            case "sf:crucible" -> pairedDisplayEntries(List.of(
                    SfxRecipeSlot.vanilla(Material.COBBLESTONE, 16), SfxRecipeSlot.vanilla(Material.LAVA_BUCKET),
                    SfxRecipeSlot.vanilla(Material.NETHERRACK, 16), SfxRecipeSlot.vanilla(Material.LAVA_BUCKET),
                    SfxRecipeSlot.vanilla(Material.STONE, 12), SfxRecipeSlot.vanilla(Material.LAVA_BUCKET),
                    SfxRecipeSlot.vanilla(Material.OBSIDIAN, 1), SfxRecipeSlot.vanilla(Material.LAVA_BUCKET),
                    SfxRecipeSlot.vanilla(Material.TERRACOTTA, 12), SfxRecipeSlot.vanilla(Material.LAVA_BUCKET),
                    SfxRecipeSlot.vanilla(Material.OAK_LEAVES, 16), SfxRecipeSlot.vanilla(Material.WATER_BUCKET),
                    SfxRecipeSlot.vanilla(Material.BLACKSTONE, 8), SfxRecipeSlot.vanilla(Material.LAVA_BUCKET),
                    SfxRecipeSlot.vanilla(Material.BASALT, 12), SfxRecipeSlot.vanilla(Material.LAVA_BUCKET),
                    SfxRecipeSlot.vanilla(Material.COBBLED_DEEPSLATE, 12), SfxRecipeSlot.vanilla(Material.LAVA_BUCKET)
            ), mode);
            case "sf:coal_generator" -> coalFuelDisplayEntries(16, 10, mode, 300);
            case "sf:coal_generator_2" -> coalFuelDisplayEntries(30, tierTwoBurnRateTenths(), mode, 300);
            case "sf:lava_generator" -> fixedFuelDisplayEntries(20, 10, mode, 300, List.of(
                    fuelData(SfxRecipeSlot.vanilla(Material.LAVA_BUCKET), 40 * lavaSecondsMultiplier(), "lava")));
            case "sf:lava_generator_2" -> fixedFuelDisplayEntries(40, tierTwoBurnRateTenths(), mode, 300, List.of(
                    fuelData(SfxRecipeSlot.vanilla(Material.LAVA_BUCKET), 40 * lavaSecondsMultiplier(), "lava")));
            case "sf:bio_reactor" -> bioFuelDisplayEntries(8, 10, mode, 300);
            case "sf:bio_reactor_2" -> bioFuelDisplayEntries(20, tierTwoBurnRateTenths(), mode, 300);
            case "sf:combustion_reactor" -> combustionFuelDisplayEntries(mode);
            case "sf:magnesium_generator" -> fixedFuelDisplayEntries(36, 10, mode, 300, List.of(
                    fuelData(SfxRecipeSlot.sfx("sf:magnesium_salt"), 20, "magnesium")));
            case "sf:nuclear_reactor" -> fixedFuelDisplayEntries(500, 10, mode, 300, List.of(
                    fuelData(SfxRecipeSlot.sfx("sf:uranium"), 1200, "uranium"),
                    fuelData(SfxRecipeSlot.sfx("sf:neptunium"), 600, "neptunium"),
                    fuelData(SfxRecipeSlot.sfx("sf:boosted_uranium"), 1500, "boosted_uranium")));
            case "sf:netherstar_reactor" -> fixedFuelDisplayEntries(netherStarReactorEnergyPerTick(), 10, mode, 300, List.of(
                    fuelData(SfxRecipeSlot.vanilla(Material.NETHER_STAR), 1800, "nether_star")));
            default -> List.of();
        };
    }

    private List<DisplayEntry> combustionFuelDisplayEntries(GuideMode mode) {
        boolean sfxBalance = plugin.getConfig().getBoolean("energy.generator-balance.use-sfx-balance", true);
        int energy = sfxBalance ? 64 : 24;
        int oilSeconds = sfxBalance ? 40 : 30;
        int fuelSeconds = sfxBalance ? 120 : 90;
        return fixedFuelDisplayEntries(energy, 10, mode, 300, List.of(
                fuelData(SfxRecipeSlot.sfx("sf:bucket_of_oil"), oilSeconds, "oil"),
                fuelData(SfxRecipeSlot.sfx("sf:bucket_of_fuel"), fuelSeconds, "fuel")));
    }

    private List<DisplayEntry> coalFuelDisplayEntries(int energyPerTick, int burnRateTenths, GuideMode mode, int startPriority) {
        int multiplier = plugin.getConfig().getBoolean("energy.generator-balance.use-sfx-balance", true) ? 2 : 1;
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
        int multiplier = plugin.getConfig().getBoolean("energy.generator-balance.use-sfx-balance", true) ? 4 : 1;
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
                Text.mm(tr("energy.generator.fuel-duration", "<gray>Duration: </gray><aqua>{seconds}</aqua>").replace("{seconds}", formatDuration(seconds))),
                Text.mm(tr("energy.generator.fuel-total-energy", "<gray>Total Energy: </gray><aqua>{energy} J</aqua>").replace("{energy}", formatEnergyShort(totalEnergy)))
        ));
        return DisplayEntry.single(icon, slotLabel(slot) + " " + formatDuration(seconds), priority, handlerForSlot(slot, mode));
    }

    private int tierTwoBurnRateTenths() {
        return plugin.getConfig().getBoolean("energy.generator-balance.use-sfx-balance", true) ? 15 : 10;
    }

    private int lavaSecondsMultiplier() {
        return plugin.getConfig().getBoolean("energy.generator-balance.use-sfx-balance", true) ? 2 : 1;
    }

    private int netherStarReactorEnergyPerTick() {
        return plugin.getConfig().getBoolean("energy.generator-balance.use-sfx-balance", true) ? 2048 : 1024;
    }

    private String formatDuration(double seconds) {
        if (seconds < 60.0) {
            double rounded = Math.round(seconds * 10.0) / 10.0;
            if (Math.abs(rounded - Math.rint(rounded)) < 0.0001) {
                return Integer.toString((int) Math.rint(rounded)) + "s";
            }
            return String.format(Locale.ROOT, "%.1fs", rounded);
        }
        long totalSeconds = Math.max(0L, Math.round(seconds));
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long remainingSeconds = totalSeconds % 60L;
        StringBuilder builder = new StringBuilder();
        if (hours > 0L) {
            builder.append(hours).append("h");
            if (minutes > 0L) {
                builder.append(minutes).append("m");
            }
            if (remainingSeconds > 0L) {
                builder.append(remainingSeconds).append("s");
            }
            return builder.toString();
        }
        builder.append(minutes).append("m");
        if (remainingSeconds > 0L) {
            builder.append(remainingSeconds).append("s");
        }
        return builder.toString();
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
        if (slot.isSfxItem()) {
            return click -> slot.sfxId().ifPresent(target ->
                    openRecipe(click.player(), mode, target, 0, preferences(click.player()).recordHistory() ? Navigation.OPEN : Navigation.REPLACE));
        }
        if (showVanillaRecipes() && slot.material() != null && !vanillaRecipePages(slot.material()).isEmpty()) {
            return click -> openVanillaRecipe(click.player(), mode, slot.material(), 0,
                    preferences(click.player()).recordHistory() ? Navigation.OPEN : Navigation.REPLACE);
        }
        return null;
    }

    private List<DisplayEntry> alternativeSourceEntries(List<GuideRecipePage> pages, GuideRecipePage current, RecipePageOpener opener) {
        if (pages.size() <= 1) {
            return List.of();
        }
        Map<String, List<GuideRecipePage>> grouped = new LinkedHashMap<>();
        for (GuideRecipePage page : pages) {
            grouped.computeIfAbsent(page.sourceFamily(), ignored -> new ArrayList<>()).add(page);
        }
        List<DisplayEntry> entries = new ArrayList<>();
        for (List<GuideRecipePage> group : grouped.values()) {
            boolean containsCurrent = group.stream().anyMatch(page -> page.index() == current.index());
            if (containsCurrent && group.size() == 1) {
                continue;
            }
            GuideRecipePage representative = group.getFirst();
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Text.mm(tr("guide.recipe.alternative-source", "<gray>Click to open this recipe source.</gray>")));
            if (group.size() > 1) {
                lore.add(Text.mm(tr("guide.recipe.alternative-source-group", "<gray>Also available in:</gray>")));
                for (GuideRecipePage member : group) {
                    if (member.index() == current.index()) {
                        continue;
                    }
                    lore.add(Text.mm("<dark_gray>-</dark_gray> <gray>" + member.sourceName() + "</gray>"));
                }
            }
            ItemStack icon = withLore(representative.sourceIcon(), lore);
            entries.add(DisplayEntry.single(icon, representative.sourceName(), representative.index() * 10 + 5, click -> {
                if (current.index() == representative.index()) {
                    return;
                }
                opener.open(click.player(), representative.index(), preferences(click.player()).recordHistory() ? Navigation.OPEN : Navigation.REPLACE);
            }));
        }
        return entries;
    }

    private List<DisplayEntry> machineOutputEntries(SfxItemDefinition definition, GuideMode mode) {
        Optional<ManualMachineDefinition> machine = manualMachines.machine(definition.id());
        if (machine.isEmpty() || !supportsMachineOutputDisplay(machine.get())) {
            return List.of();
        }
        Map<String, DisplayEntry> entries = new LinkedHashMap<>();
        int order = 1000;
        for (ManualMachineRecipe recipe : manualMachines.recipesFor(machine.get().id())) {
            for (ManualMachineOutput output : recipe.outputs()) {
                if (output.isSfxItem()) {
                    String target = output.sfxItemId();
                    Optional<SfxItemDefinition> targetDefinition = registry.item(target);
                    if (targetDefinition.isEmpty()) {
                        continue;
                    }
                    ItemStack icon = withLore(targetDefinition.map(def -> items.create(def, output.amount())).orElseGet(() -> items.create(target, output.amount())), List.of(
                            Component.empty(),
                            Text.mm(tr("guide.actions.open-recipe", "<gray>Click to view recipe</gray>"))
                    ));
                    entries.putIfAbsent("sfx:" + target, DisplayEntry.single(icon, itemDisplayName(targetDefinition.get()), order,
                            click -> openRecipe(click.player(), mode, target, 0, preferences(click.player()).recordHistory() ? Navigation.OPEN : Navigation.REPLACE)));
                } else {
                    Material material = output.material();
                    ItemStack icon = withLore(new ItemStack(material, output.amount()), List.of(
                            Component.empty(),
                            Text.mm(showVanillaRecipes()
                                    ? tr("guide.actions.open-vanilla-recipe", "<gray>Click to view vanilla recipes</gray>")
                                    : tr("guide.recipe.vanilla.disabled", "<dark_gray>Vanilla recipe lookup is disabled.</dark_gray>"))
                    ));
                    entries.putIfAbsent("vanilla:" + material.name(), DisplayEntry.single(icon, materialName(material), order,
                            showVanillaRecipes()
                                    ? click -> openVanillaRecipe(click.player(), mode, material, 0, preferences(click.player()).recordHistory() ? Navigation.OPEN : Navigation.REPLACE)
                                    : click -> {
                            }));
                }
                order += 10;
            }
        }
        return entries.values().stream().toList();
    }

    private boolean supportsMachineOutputDisplay(ManualMachineDefinition machine) {
        return switch (machine.operation()) {
            case SINGLE_INPUT, SHAPELESS_INPUT, HAND_INPUT -> true;
            case SHAPED_3X3 -> false;
        };
    }

    private SfxDisplayLayout sfxDisplayLayout(List<DisplayEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return SfxDisplayLayout.NONE;
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
                Cell cell = entry == null ? null : new Cell(entry.primaryIcon(), entry.primaryHandler());
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
                top[column] = new Cell(entry.primaryIcon(), entry.primaryHandler());
                bottom[column] = new Cell(entry.secondaryIcon(), entry.secondaryHandler());
                continue;
            }
            int cell = firstFreeSingleCell(top, bottom);
            if (cell < 0) {
                break;
            }
            if (cell < columns) {
                top[cell] = new Cell(entry.primaryIcon(), entry.primaryHandler());
            } else {
                bottom[cell - columns] = new Cell(entry.primaryIcon(), entry.primaryHandler());
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
        builder.button(slot, new SfxMenuButton(cell.icon(), click -> {
            if (cell.handler() != null) {
                cell.handler().accept(click);
            }
        }));
    }

    private SfxMenuButton ingredientButton(SfxRecipeSlot slot, GuideMode mode) {
        if (slot == null || slot.isEmpty()) {
            return new SfxMenuButton(emptyMatrixSlotIcon(), click -> {
            });
        }
        ItemStack icon = ingredientIcon(slot);
        if (slot.isSfxItem()) {
            return new SfxMenuButton(icon, click -> slot.sfxId().ifPresent(target ->
                    openRecipe(click.player(), mode, target, 0, preferences(click.player()).recordHistory() ? Navigation.OPEN : Navigation.REPLACE)));
        }
        if (showVanillaRecipes() && slot.material() != null && !vanillaRecipePages(slot.material()).isEmpty()) {
            return new SfxMenuButton(icon, click -> openVanillaRecipe(click.player(), mode, slot.material(), 0,
                    preferences(click.player()).recordHistory() ? Navigation.OPEN : Navigation.REPLACE));
        }
        return new SfxMenuButton(icon, click -> {
        });
    }

    private SfxMenuButton recipeSourceButton(GuideRecipePage current, GuideMode mode) {
        ItemStack icon = current.hasRecipe() ? withRecipeSourceLore(current) : ItemBuilder.of(Material.KNOWLEDGE_BOOK)
                .name(tr("guide.recipe.no-recipe.name", "<red>No Recipe</red>"))
                .build();
        if (current.machineTargetId() != null && machineLinksEnabled()) {
            return new SfxMenuButton(icon, click -> openRecipe(click.player(), mode, current.machineTargetId(), 0,
                    preferences(click.player()).recordHistory() ? Navigation.OPEN : Navigation.REPLACE));
        }
        return new SfxMenuButton(icon, click -> {
        });
    }

    private ItemStack withRecipeSourceLore(GuideRecipePage current) {
        List<Component> lore = new ArrayList<>();
        if (current.machineTargetId() != null && machineLinksEnabled()) {
            lore.add(Component.empty());
            lore.add(Text.mm(tr("guide.actions.open-machine", "<gray>Click to open this machine in the guide</gray>")));
        }
        if (current.note() != null) {
            lore.add(Component.empty());
            lore.add(Text.noItalic(current.note()));
        }
        return lore.isEmpty() ? current.sourceIcon() : withLore(current.sourceIcon(), lore);
    }

    private void giveFromCheatGuide(Player player, SfxItemDefinition definition, ClickType clickType) {
        if (!accessPolicy.canReceiveFromCheatGuide(player, definition)) {
            player.sendMessage(Text.prefixed(plugin, tr("guide.errors.no-cheat-access", "<red>You cannot use the cheat guide for this item.</red>")));
            return;
        }

        Optional<ManualMachineDefinition> manualMachine = manualMachines.machine(definition.id())
                .or(() -> cc.theends6.sfx.internal.machine.ExtraDeployStructures.machine(definition.id()));
        if (manualMachine.isPresent()) {
            giveManualMachineFromCheatGuide(player, manualMachine.get(), clickType != null && clickType.isShiftClick());
            return;
        }

        int amount = clickType != null && clickType.isShiftClick() ? 64 : 1;
        runtime.executeForPlayer(player, () -> {
            items.give(player, items.create(definition, amount));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
            player.sendMessage(Text.prefixed(plugin, localization.text("machines.cheat-item", "<green>Received:</green><gray>{item} x{amount}</gray>")
                    .replace("{item}", itemDisplayName(definition))
                    .replace("{amount}", Integer.toString(amount))));
        });
    }

    private void giveManualMachineFromCheatGuide(Player player, ManualMachineDefinition definition, boolean fullKit) {
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
                String fallback = definition.deployable()
                        ? "<green>Received full machine kit:</green><gray>{item}</gray>"
                        : "<yellow>This machine does not support deploy packs yet, a full structure kit was given instead:</yellow><gray>{item}</gray>";
                player.sendMessage(Text.prefixed(plugin, localization.text(key, fallback).replace("{item}", parts)));
            } else {
                ItemStack deployPack = cc.theends6.sfx.internal.machine.ManualMachineDeployPacks.create(plugin, definition, localization);
                items.give(player, deployPack);
                String itemName = deployPack.hasItemMeta() && deployPack.getItemMeta() != null && deployPack.getItemMeta().hasDisplayName()
                        ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(deployPack.getItemMeta().displayName())
                        : machineDisplayName(definition);
                player.sendMessage(Text.prefixed(plugin, localization.text("machines.cheat-machine-pack", "<green>Received machine deploy pack:</green><gray>{item}</gray>")
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
            extraLore.add(Text.mm(tr("guide.actions.open-recipe", "<gray>Click to view recipe</gray>")));
        } else if (showVanillaRecipes() && slot.material() != null && !vanillaRecipePages(slot.material()).isEmpty()) {
            extraLore.add(Component.empty());
            extraLore.add(Text.mm(tr("guide.actions.open-vanilla-recipe", "<gray>Click to view vanilla recipes</gray>")));
        }
        if (slot.amount() > 1) {
            if (extraLore.isEmpty()) {
                extraLore.add(Component.empty());
            }
            extraLore.add(Component.text(tr("guide.recipe.amount", "Amount: ") + slot.amount(), NamedTextColor.GRAY));
        }
        return extraLore.isEmpty() ? icon : withLore(icon, extraLore);
    }

    private ItemStack emptyRecipeSlotIcon() {
        return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name("<dark_gray> </dark_gray>").build();
    }

    private ItemStack emptyMatrixSlotIcon() {
        return new ItemStack(Material.AIR);
    }

    private ItemStack modeInfoIcon(GuideMode mode) {
        return ItemBuilder.of(mode == GuideMode.CHEAT ? Material.ENCHANTED_BOOK : Material.BOOK)
                .name(mode == GuideMode.CHEAT
                        ? tr("guide.mode.cheat.name", "<dark_red>Slimefun Cheat Guide</dark_red>")
                        : tr("guide.mode.survival.name", "<dark_green>Slimefun Guide</dark_green>"))
                .lore(mode == GuideMode.CHEAT
                        ? tr("guide.mode.cheat.lore", "<gray>Click items to obtain them directly.</gray>")
                        : tr("guide.mode.survival.lore", "<gray>Click items to view recipes.</gray>"))
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
        builder.button(7, new SfxMenuButton(searchIcon(), click -> click.player().sendMessage(Text.prefixed(plugin, tr("guide.actions.search.todo", "<gray>The search entry is reserved for a future chat search page.</gray>")))));
    }

    private void paintRecipeFrame(SfxMenu.Builder builder, GuideMode mode) {
        ItemStack pane = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name("<dark_gray> </dark_gray>").build();
        for (int i = 0; i < 9; i++) {
            builder.button(i, new SfxMenuButton(pane, click -> {
            }));
        }
        for (int i = 45; i < 54; i++) {
            builder.button(i, new SfxMenuButton(pane, click -> {
            }));
        }
        builder.button(1, new SfxMenuButton(settingsIcon(), click -> openSettingsView(click.player(), mode, Navigation.OPEN)));
        builder.button(7, new SfxMenuButton(searchIcon(), click -> click.player().sendMessage(Text.prefixed(plugin, tr("guide.actions.search.todo", "<gray>The search entry is reserved for a future chat search page.</gray>")))));
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

    private void paintSettingsBackground(SfxMenu.Builder builder, GuideMode mode) {
        ItemStack background = ItemBuilder.of(mode == GuideMode.CHEAT ? Material.RED_STAINED_GLASS_PANE : Material.GREEN_STAINED_GLASS_PANE)
                .name("<dark_gray> </dark_gray>")
                .build();
        for (int slot : SETTINGS_BACKGROUND) {
            builder.button(slot, new SfxMenuButton(background, click -> {
            }));
        }
    }

    private void addContentPagination(SfxMenu.Builder builder, int page, int pageCount, PlayerAction previous, PlayerAction next) {
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

    private void addRecipePagination(SfxMenu.Builder builder, int page, int pageCount, PlayerAction previous, PlayerAction next) {
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

    private void addExtraDisplayPagination(SfxMenu.Builder builder, int page, int pageCount, PlayerAction previous, PlayerAction next) {
        if (pageCount <= 1) {
            return;
        }
        builder.button(27, new SfxMenuButton(previousRecipeIcon(page, pageCount), click -> {
            if (page > 0) {
                playGuidePageSound(click.player());
                previous.accept(click.player());
            }
        }));
        builder.button(35, new SfxMenuButton(nextRecipeIcon(page, pageCount), click -> {
            if (page + 1 < pageCount) {
                playGuidePageSound(click.player());
                next.accept(click.player());
            }
        }));
    }

    private ItemStack previousRecipeIcon(int page, int pageCount) {
        return ItemBuilder.of(Material.ARROW)
                .name(page > 0 ? tr("guide.pagination.prev.active", "<yellow>Previous Page</yellow>") : tr("guide.pagination.prev.inactive", "<dark_gray>Previous Page</dark_gray>"))
                .lore(pageNumberLore(page, pageCount))
                .build();
    }

    private ItemStack nextRecipeIcon(int page, int pageCount) {
        return ItemBuilder.of(Material.ARROW)
                .name(page + 1 < pageCount ? tr("guide.pagination.next.active", "<yellow>Next Page</yellow>") : tr("guide.pagination.next.inactive", "<dark_gray>Next Page</dark_gray>"))
                .lore(pageNumberLore(page, pageCount))
                .build();
    }

    private ItemStack infoIcon(GuideMode mode, int page, int pageCount) {
        return ItemBuilder.of(Material.NETHER_STAR)
                .name(mode == GuideMode.CHEAT
                        ? tr("guide.mode.cheat.name", "<red>Slimefun Cheat Guide</red>")
                        : tr("guide.mode.survival.name", "<green>Slimefun Guide</green>"))
                .lore(pageNumberLore(page, pageCount), "", tr("guide.actions.close", "<dark_gray>Click to close.</dark_gray>"))
                .build();
    }

    private ItemStack settingsIcon() {
        return ItemBuilder.of(Material.COMPARATOR)
                .name(tr("guide.actions.settings.name", "<yellow>Guide Settings</yellow>"))
                .lore(tr("guide.actions.settings.lore", "<gray>You can also Shift + Right Click the guide book.</gray>"))
                .build();
    }

    private ItemStack searchIcon() {
        return ItemBuilder.of(Material.NAME_TAG)
                .name(tr("guide.actions.search.name", "<yellow>Search</yellow>"))
                .lore(tr("guide.actions.search.lore", "<gray>The search entry is reserved.</gray>"))
                .build();
    }

    private ItemStack closeIcon() {
        return ItemBuilder.of(Material.BARRIER).name(tr("guide.actions.close-menu", "<red>Close</red>")).build();
    }

    private ItemStack backIcon(String text) {
        return ItemBuilder.of(Material.BARRIER).name("<yellow>" + text + "</yellow>").build();
    }

    private ItemStack lockedItemIcon(SfxItemDefinition definition, SfxResearchDefinition research) {
        return ItemBuilder.of(Material.BARRIER)
                .name("<white>" + itemDisplayName(definition) + "</white>")
                .lore(
                        "<gray>" + displayResearchName(research, definition) + "</gray>",
                        "",
                        tr("guide.research.locked", "<red>Locked</red>"),
                        "",
                        tr("guide.research.click-unlock", "<green>Click to unlock</green>"),
                        "",
                        tr("guide.research.cost", "<gray>Cost: </gray><aqua>{cost} levels</aqua>").replace("{cost}", Integer.toString(research.cost()))
                )
                .build();
    }

    private String displayResearchName(SfxResearchDefinition research, SfxItemDefinition definition) {
        Component fallback = definition == null ? Text.mm(research.name()) : localization.itemName(definition.id(), definition.name());
        return PlainTextComponentSerializer.plainText().serialize(localization.researchName(research.id(), fallback));
    }

    private List<SfxItemCategory> visibleCategoriesFor(Player player, GuideMode mode) {
        return LegacySfGuideResolver.visibleCategories(registry, mode).stream()
                .filter(category -> isCategoryVisible(category.id()))
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

    private SfxMenuButton toggleButton(Material material, String name, String lore, boolean enabled, java.util.function.Consumer<cc.theends6.sfx.api.menu.SfxMenuClickContext> handler) {
        return new SfxMenuButton(ItemBuilder.of(material)
                .name(name)
                .lore(
                        enabled
                                ? tr("guide.settings.toggle.enabled", "<green>Enabled</green>")
                                : tr("guide.settings.toggle.disabled", "<red>Disabled</red>"),
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
            meta.displayName(Text.noItalic(localization.categoryName(category.id(), category.name())));
            if ("guide:sf:magical_gadgets".equals(category.id())
                    || "guide:sf:magical_armor".equals(category.id())
                    || "guide:sf:armor".equals(category.id())) {
                meta.setEnchantmentGlintOverride(Boolean.TRUE);
            }
            icon.setItemMeta(meta);
        }
        return withLore(icon, List.of(Component.empty(), Text.mm(tr("guide.actions.open-category", "<gray>Click to open category</gray>"))));
    }

    private ItemStack lockedCategoryIcon(Player player, SfxItemCategory category) {
        List<Component> lore = new ArrayList<>();
        lore.add(Text.mm("<white>" + tr("guide.locked-itemgroup.line1", "To unlock this category you will")));
        lore.add(Text.mm("<white>" + tr("guide.locked-itemgroup.line2", "need to unlock all items from the")));
        lore.add(Text.mm("<white>" + tr("guide.locked-itemgroup.line3", "following categories")));
        lore.add(Component.empty());
        for (String parentId : LegacySfGuideResolver.parentCategories(category.id())) {
            LegacySfGuideResolver.resolveCategory(registry, parentId)
                    .map(parent -> localization.categoryName(parent.id(), parent.name()))
                    .ifPresent(lore::add);
        }
        ItemStack item = ItemBuilder.of(Material.BARRIER)
                .name("<red>" + tr("guide.locked", "LOCKED") + " <gray>-</gray> <white>" + plainCategoryName(category) + "</white>")
                .build();
        return withLore(item, lore);
    }

    private ItemStack machineSourceIcon(ManualMachineDefinition machine) {
        return ItemBuilder.of(machine.triggerMaterial())
                .name("<green>" + machineDisplayName(machine) + "</green>")
                .build();
    }

    private ItemStack multiblockSourceIcon() {
        return ItemBuilder.of(Material.NETHER_BRICK_FENCE)
                .name(tr("guide.recipe.multiblock.name", "<gold>Multiblock Machine</gold>"))
                .lore(
                        tr("guide.recipe.multiblock.lore.1", "<gray>This recipe is crafted in a multiblock machine.</gray>"),
                        tr("guide.recipe.multiblock.lore.2", "<gray>Build the structure in the world, then interact with its trigger block.</gray>")
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
        if (candidate == null || candidate.isBlank()) {
            return null;
        }

        if (candidate.indexOf(':') >= 0) {
            return resolveSoundKey(candidate);
        }

        Sound namespaced = resolveSoundKey("minecraft:" + candidate);
        if (namespaced != null) {
            return namespaced;
        }

        String dotted = candidate.replace('_', '.');
        if (!dotted.equals(candidate)) {
            namespaced = resolveSoundKey("minecraft:" + dotted);
            if (namespaced != null) {
                return namespaced;
            }
        }

        return null;
    }

    private Sound resolveSoundKey(String rawKey) {
        NamespacedKey key = NamespacedKey.fromString(rawKey);
        return key == null ? null : Registry.SOUNDS.get(key);
    }

    private void showMenu(Player player, SfxMenu.Builder builder, Navigation navigation) {
        builder.restorePreviousOnClose(preferences(player).closeReturns());
        SfxMenu menu = builder.build();
        switch (navigation) {
            case ROOT -> menus.openRoot(player, menu);
            case OPEN -> menus.open(player, menu);
            case REPLACE -> menus.replace(player, menu);
        }
    }

    private void closeGuide(Player player) {
        menus.close(player, false);
    }

    private void goBack(Player player, GuideMode fallbackMode) {
        if (menus.hasHistory(player)) {
            menus.close(player, true);
            return;
        }
        openMain(player, fallbackMode, 0, Navigation.ROOT);
    }

    private GuidePreferences preferences(Player player) {
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

    private void persistPreferences(Player player, GuidePreferences preferences, boolean saveAsync) {
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

    private GuideLayout effectiveLayout(GuidePreferences preferences) {
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
            player.sendMessage(Text.prefixed(plugin, tr("messages.profile.loading", "<yellow>Your SFX player data is still loading. Try again in a moment.</yellow>")));
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
            player.sendMessage(Text.prefixed(plugin, tr("messages.profile.loading", "<yellow>Your SFX player data is still loading. Try again in a moment.</yellow>")));
            return;
        }
        beginResearchUnlock(player, optional.get(), definition, research,
                () -> openRecipe(player, mode, definition.id(), 0, Navigation.REPLACE),
                () -> openLockedResearchView(player, mode, definition, research, Navigation.REPLACE));
    }

    private void beginResearchUnlock(Player player, SfxPlayerProfile profile, SfxItemDefinition definition, SfxResearchDefinition research, Runnable onSuccess, Runnable onFailure) {
        if (profile.hasUnlocked(research.id())) {
            onSuccess.run();
            return;
        }
        if (!canAffordResearch(player, research)) {
            player.sendMessage(Text.prefixed(plugin, tr("messages.not-enough-xp", "<red>You do not have enough levels to unlock this research.</red>")));
            onFailure.run();
            return;
        }
        if (!researchingPlayers.add(player.getUniqueId())) {
            return;
        }

        String researchName = displayResearchName(research, definition);
        consumeResearchCost(player, research);
        GuidePreferences preferences = preferences(player);

        if (!preferences.unlockAnimation()) {
            finishResearchUnlock(player, profile, definition, research, onSuccess);
            return;
        }

        player.sendMessage(Text.prefixed(plugin,
                tr("messages.research.start", "<gray>The Ancient Spirits whisper mysterious words into your ear!</gray>")));

        runtime.executeForPlayerLater(player, 5L, () -> {
            if (!player.isOnline()) {
                finishResearchUnlock(player, profile, definition, research, onSuccess);
                return;
            }
                playResearchSound(player);
                player.sendMessage(Text.prefixed(plugin,
                    tr("messages.research.progress", "<gray>You start to wonder about </gray><aqua>{name}</aqua><gray> ({progress})</gray>")
                            .replace("{name}", researchName)
                            .replace("{progress}", "0%")));
        });

        for (int index = 0; index < RESEARCH_PROGRESS.length; index++) {
            int progress = RESEARCH_PROGRESS[index];
            long delay = (index + 1L) * 20L;
            runtime.executeForPlayerLater(player, delay, () -> {
                if (!player.isOnline()) {
                    return;
                }
                playResearchSound(player);
                player.sendMessage(Text.prefixed(plugin,
                        tr("messages.research.progress", "<gray>You start to wonder about </gray><aqua>{name}</aqua><gray> ({progress})</gray>")
                                .replace("{name}", researchName)
                                .replace("{progress}", progress + "%")));
            });
        }

        runtime.executeForPlayerLater(player, (RESEARCH_PROGRESS.length + 1L) * 20L, () ->
                finishResearchUnlock(player, profile, definition, research, onSuccess));
    }

    private void finishResearchUnlock(Player player, SfxPlayerProfile profile, SfxItemDefinition definition, SfxResearchDefinition research, Runnable onSuccess) {
        researchingPlayers.remove(player.getUniqueId());
        researches.grant(profile, research);
        if (player.isOnline()) {
            GuidePreferences preferences = preferences(player);
            if (preferences.fireworks()) {
                launchResearchFirework(player);
            }
            player.sendMessage(Text.prefixed(plugin,
                    tr("messages.research.unlocked", "<aqua>You have unlocked </aqua><gray>\"{name}\"</gray>")
                            .replace("{name}", displayResearchName(research, definition))));
            onSuccess.run();
        }
    }

    private boolean canAffordResearch(Player player, SfxResearchDefinition research) {
        return player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getLevel() >= research.cost();
    }

    private void consumeResearchCost(Player player, SfxResearchDefinition research) {
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            player.setLevel(player.getLevel() - research.cost());
        }
    }

    private void playResearchSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.7f, 1.0f);
    }

    private void switchGuideBookMode(Player player, GuideMode mode) {
        if (!accessPolicy.canOpen(player, mode)) {
            player.sendMessage(Text.prefixed(plugin, tr("guide.errors.no-open", "<red>You do not have permission to open this guide.</red>")));
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

    private String tr(String key, String fallback) {
        return localization.text(key, fallback);
    }

    private Component title(GuideMode mode, String suffix) {
        String configured = mode == GuideMode.CHEAT
                ? plugin.getConfig().getString("guide.cheat-title", tr("guide.mode.cheat.title", "<dark_red>Slimefun Cheat Guide</dark_red>"))
                : plugin.getConfig().getString("guide.survival-title", tr("guide.mode.survival.title", "<dark_green>Slimefun Guide</dark_green>"));
        return Text.mm(configured + " <dark_gray>|</dark_gray> <gray>" + suffix + "</gray>");
    }

    private String plainCategoryName(SfxItemCategory category) {
        return plainText(localization.categoryName(category.id(), category.name()));
    }

    private String materialName(Material material) {
        String key = "materials." + material.name().toLowerCase();
        return localization.text(key, switch (material) {
            case CRAFTING_TABLE -> "Crafting Table";
            case DISPENSER -> "Dispenser";
            case OAK_FENCE -> "Oak Fence";
            case NETHER_BRICK_FENCE -> "Nether Brick Fence";
            case PISTON -> "Piston";
            case IRON_BARS -> "Iron Bars";
            case CAULDRON -> "Cauldron";
            case STONECUTTER -> "Stonecutter";
            case SMITHING_TABLE -> "Smithing Table";
            case CAMPFIRE -> "Campfire";
            case SMOKER -> "Smoker";
            case BLAST_FURNACE -> "Blast Furnace";
            default -> material.name().toLowerCase();
        });
    }

    private String itemDisplayName(SfxItemDefinition definition) {
        return plainText(localization.itemName(definition.id(), definition.name()));
    }

    private String machineDisplayName(ManualMachineDefinition definition) {
        return plainText(localization.itemName(definition.id(), definition.name()));
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
        return tr("guide.pagination.page", "<gray>Page {current} / {total}</gray>")
                .replace("{current}", Integer.toString(page + 1))
                .replace("{total}", Integer.toString(pageCount));
    }


}
