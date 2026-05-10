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
import cc.theends6.sfx.internal.machine.ManualMachineOutput;
import cc.theends6.sfx.internal.machine.ManualMachineRecipe;
import cc.theends6.sfx.internal.util.ItemBuilder;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class DefaultSfxGuide implements SfxGuide {
    private static final int[] CONTENT_SLOTS = {
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final int[] CLASSIC_RECIPE_SLOTS = {3, 4, 5, 12, 13, 14, 21, 22, 23};
    private static final int[] SFX_RECIPE_SLOTS = {12, 13, 14, 21, 22, 23, 30, 31, 32};
    private static final int[] CLASSIC_DISPLAY_SLOTS = {
            36, 37, 38, 39, 40, 41, 42, 43, 44,
            45, 46, 47, 48, 49, 50, 51, 52, 53
    };
    private static final int[] SFX_DISPLAY_SLOTS = {36, 37, 38, 39, 40, 41, 42, 43, 44};
    private static final int CLASSIC_RECIPE_CENTER_SLOT = 13;
    private static final int SFX_RECIPE_CENTER_SLOT = 22;
    private static final int CLASSIC_SOURCE_SLOT = 10;
    private static final int CLASSIC_OUTPUT_SLOT = 16;
    private static final int SFX_SOURCE_SLOT = 19;
    private static final int SFX_OUTPUT_SLOT = 25;
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
    private final Map<UUID, GuidePreferences> preferencesByPlayer = new ConcurrentHashMap<>();
    private final Map<Material, List<GuideRecipePage>> vanillaRecipeCache = new ConcurrentHashMap<>();

    public DefaultSfxGuide(
            JavaPlugin plugin,
            SfxRuntime runtime,
            DefaultSfxItemRegistry registry,
            SfxItems items,
            SfxMenus menus,
            SfxGuideAccessPolicy accessPolicy,
            DefaultManualMachineRegistry manualMachines,
            SfxLocalization localization
    ) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.registry = registry;
        this.items = items;
        this.menus = menus;
        this.accessPolicy = accessPolicy;
        this.manualMachines = manualMachines;
        this.localization = localization;
    }

    @Override
    public void open(Player player, GuideMode mode) {
        if (!accessPolicy.canOpen(player, mode)) {
            player.sendMessage(Text.prefixed(plugin, tr("guide.errors.no-open", "<red>You do not have permission to open this guide.</red>")));
            return;
        }
        openMain(player, mode, 0, Navigation.ROOT);
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
                    openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(39, toggleButton(
                Material.FIREWORK_ROCKET,
                tr("guide.settings.fireworks.name", "<yellow>Research Fireworks</yellow>"),
                tr("guide.settings.fireworks.lore", "<gray>Reserved for the future research system. This matches classic guide settings.</gray>"),
                preferences.fireworks(),
                click -> {
                    preferences.setFireworks(!preferences.fireworks());
                    openSettingsView(click.player(), mode, Navigation.REPLACE);
                }
        ));

        builder.button(41, toggleButton(
                Material.REDSTONE_TORCH,
                tr("guide.settings.unlock-animation.name", "<yellow>Unlock Animation</yellow>"),
                tr("guide.settings.unlock-animation.lore", "<gray>Reserved for the future research system. This matches classic guide settings.</gray>"),
                preferences.unlockAnimation(),
                click -> {
                    preferences.setUnlockAnimation(!preferences.unlockAnimation());
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
        List<SfxItemCategory> visibleCategories = LegacySfGuideResolver.visibleCategories(registry, mode);
        int pageCount = pageCount(visibleCategories.size());
        int safePage = clampPage(page, pageCount);

        SfxMenu.Builder builder = SfxMenu.builder(title(mode, tr("guide.main.title", "Main Menu"))).rows(6);
        paintFrame(builder, mode, effectiveLayout(preferences(player)));

        int from = safePage * CONTENT_SLOTS.length;
        int to = Math.min(visibleCategories.size(), from + CONTENT_SLOTS.length);
        for (int i = from; i < to; i++) {
            SfxItemCategory category = visibleCategories.get(i);
            int slot = CONTENT_SLOTS[i - from];
            builder.button(slot, new SfxMenuButton(categoryButtonIcon(category), click -> openCategory(click.player(), mode, category.id(), 0, Navigation.OPEN)));
        }

        addContentPagination(builder, safePage, pageCount,
                previous -> openMain(previous, mode, safePage - 1, Navigation.REPLACE),
                next -> openMain(next, mode, safePage + 1, Navigation.REPLACE));
        builder.button(49, new SfxMenuButton(infoIcon(mode, safePage, pageCount), click -> closeGuide(click.player())));
        showMenu(player, builder, navigation);
    }

    private void openCategory(Player player, GuideMode mode, String categoryId, int page, Navigation navigation) {
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
            ItemStack icon = items.create(definition, 1);
            if (mode == GuideMode.CHEAT) {
                Optional<ManualMachineDefinition> manualMachine = manualMachines.machine(definition.id());
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
        Optional<SfxItemDefinition> optional = registry.item(itemId);
        if (optional.isEmpty()) {
            player.sendMessage(Text.prefixed(plugin, tr("guide.errors.missing-item", "<red>This item does not exist.</red>")));
            return;
        }
        SfxItemDefinition definition = optional.get();
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
        renderRecipe(player, mode, layout, itemDisplayName(definition), items.create(definition, current.outputAmount()), pages, current, navigation, outputAction, definition, displayEntries, opener);
    }

    private void openVanillaRecipe(Player player, GuideMode mode, Material material, int recipeIndex, Navigation navigation) {
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
                alternativeSourceEntries(pages, current, opener), opener);
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
            RecipePageOpener opener
    ) {
        if (layout == GuideLayout.CLASSIC) {
            renderClassicRecipe(player, mode, subjectTitle, outputItem, pages, current, navigation, outputAction, definition, displayEntries, opener);
            return;
        }
        renderSfxRecipe(player, mode, subjectTitle, outputItem, pages, current, navigation, outputAction, definition, displayEntries, opener);
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
        renderRecipe(player, mode, layout, subjectTitle, outputItem, List.of(empty), empty, navigation, outputAction, definition, displayEntries, opener);
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
            RecipePageOpener opener
    ) {
        SfxMenu.Builder builder = SfxMenu.builder(title(mode, subjectTitle)).rows(6);
        paintRecipeFrame(builder, mode);

        if (current.hasRecipe()) {
            for (int i = 0; i < SFX_RECIPE_SLOTS.length; i++) {
                builder.button(SFX_RECIPE_SLOTS[i], ingredientButton(current.matrix().get(i), mode));
            }
        } else {
            for (int slot : SFX_RECIPE_SLOTS) {
                builder.button(slot, new SfxMenuButton(emptyMatrixSlotIcon(), click -> {
                }));
            }
            builder.button(SFX_RECIPE_CENTER_SLOT, new SfxMenuButton(ItemBuilder.of(Material.BARRIER)
                    .name(tr("guide.recipe.no-recipe.name", "<red>No Recipe</red>"))
                    .lore(tr("guide.recipe.no-recipe.lore", "<gray>This item currently only exists as a registry or system entry.</gray>"))
                    .build(), click -> {
            }));
        }

        builder.button(SFX_SOURCE_SLOT, recipeSourceButton(current, mode));
        builder.button(SFX_OUTPUT_SLOT, new SfxMenuButton(withLore(outputItem, List.of(
                Component.empty(),
                Text.mm(tr("guide.recipe.output", "<green>Output</green>"))
        )), click -> outputAction.accept(click.player(), click.clickType())));

        builder.button(0, new SfxMenuButton(backIcon(tr("guide.actions.back-category", "Back to Category")), click -> goBack(click.player(), mode)));
        addRecipePagination(builder, current.index(), pages.size(),
                previous -> opener.open(previous, current.index() - 1, Navigation.REPLACE),
                next -> opener.open(next, current.index() + 1, Navigation.REPLACE));
        builder.button(49, new SfxMenuButton(infoIcon(mode, current.index(), pages.size()), click -> closeGuide(click.player())));

        renderDisplayEntries(builder, displayEntries, SFX_DISPLAY_SLOTS);
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
                    resultDefinition.id(), sourceIcon, normalizeMatrix(recipe.matrix()), recipe.note(), recipe.outputAmount());
        }

        Optional<ManualMachineDefinition> machine = manualMachines.machine(recipe.recipeType());
        if (machine.isPresent()) {
            return new GuideRecipePage(index, GuideRecipeOrigin.SFX, machine.get().id(), familyKey(machine.get().id()), machineDisplayName(machine.get()),
                    machine.get().id(), machineSourceIcon(machine.get()), normalizeMatrix(recipe.matrix()), recipe.note(), recipe.outputAmount());
        }

        Optional<SfxItemDefinition> machineDefinition = registry.item(recipe.recipeType());
        if (machineDefinition.isPresent()) {
            ItemStack sourceIcon = withLore(items.create(machineDefinition.get(), 1), List.of(Component.empty(), Text.mm(tr("guide.actions.open-recipe", "<gray>Click to view recipe</gray>"))));
            return new GuideRecipePage(index, GuideRecipeOrigin.SFX, machineDefinition.get().id(), familyKey(machineDefinition.get().id()),
                    itemDisplayName(machineDefinition.get()), machineDefinition.get().id(), sourceIcon, normalizeMatrix(recipe.matrix()), recipe.note(), recipe.outputAmount());
        }

        ItemStack sourceIcon = ItemBuilder.of(Material.BOOK).name("<green>" + recipe.recipeType() + "</green>").build();
        return new GuideRecipePage(index, GuideRecipeOrigin.SFX, recipe.recipeType(), familyKey(recipe.recipeType()),
                recipe.recipeType(), null, sourceIcon, normalizeMatrix(recipe.matrix()), recipe.note(), recipe.outputAmount());
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
                    normalizeMatrix(fromShapedRecipe(shaped)), null, Math.max(1, shaped.getResult().getAmount()));
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            return new GuideRecipePage(index, GuideRecipeOrigin.VANILLA, "minecraft:crafting", "minecraft:crafting",
                    tr("guide.recipe.vanilla.crafting", "Crafting Table"), null,
                    vanillaSourceIcon(Material.CRAFTING_TABLE, tr("guide.recipe.vanilla.crafting", "Crafting Table")),
                    normalizeMatrix(fromShapelessRecipe(shapeless)), null, Math.max(1, shapeless.getResult().getAmount()));
        }
        if (recipe instanceof StonecuttingRecipe stonecutting) {
            return new GuideRecipePage(index, GuideRecipeOrigin.VANILLA, "minecraft:stonecutting", "minecraft:stonecutting",
                    tr("guide.recipe.vanilla.stonecutting", "Stonecutter"), null,
                    vanillaSourceIcon(Material.STONECUTTER, tr("guide.recipe.vanilla.stonecutting", "Stonecutter")),
                    normalizeMatrix(singleChoiceMatrix(stonecutting.getInputChoice())), null, Math.max(1, stonecutting.getResult().getAmount()));
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
                name, null, vanillaSourceIcon(icon, name), normalizeMatrix(singleChoiceMatrix(recipe.getInputChoice())), null, Math.max(1, recipe.getResult().getAmount()));
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
            entries.addAll(machineOutputEntries(definition, mode));
        }
        return entries.stream()
                .sorted(DISPLAY_ENTRY_ORDER)
                .limit(CLASSIC_DISPLAY_SLOTS.length)
                .toList();
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
            entries.add(new DisplayEntry(icon, representative.sourceName(), representative.index() * 10 + 5, click -> {
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
                    entries.putIfAbsent("sfx:" + target, new DisplayEntry(icon, itemDisplayName(targetDefinition.get()), order,
                            click -> openRecipe(click.player(), mode, target, 0, preferences(click.player()).recordHistory() ? Navigation.OPEN : Navigation.REPLACE)));
                } else {
                    Material material = output.material();
                    ItemStack icon = withLore(new ItemStack(material, output.amount()), List.of(
                            Component.empty(),
                            Text.mm(showVanillaRecipes()
                                    ? tr("guide.actions.open-vanilla-recipe", "<gray>Click to view vanilla recipes</gray>")
                                    : tr("guide.recipe.vanilla.disabled", "<dark_gray>Vanilla recipe lookup is disabled.</dark_gray>"))
                    ));
                    entries.putIfAbsent("vanilla:" + material.name(), new DisplayEntry(icon, materialName(material), order,
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

    private void renderDisplayEntries(SfxMenu.Builder builder, List<DisplayEntry> entries, int[] slots) {
        for (int i = 0; i < slots.length; i++) {
            int slot = slots[i];
            if (i >= entries.size()) {
                builder.button(slot, new SfxMenuButton(emptyRecipeSlotIcon(), click -> {
                }));
                continue;
            }
            DisplayEntry entry = entries.get(i);
            builder.button(slot, new SfxMenuButton(entry.icon(), click -> {
                if (entry.handler() != null) {
                    entry.handler().accept(click);
                }
            }));
        }
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

        Optional<ManualMachineDefinition> manualMachine = manualMachines.machine(definition.id());
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
                player.sendMessage(Text.prefixed(plugin, localization.text("machines.cheat-machine-pack", "<green>Received machine deploy pack:</green><gray>{item}</gray>")
                        .replace("{item}", machineDisplayName(definition))));
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
                previous.accept(click.player());
            }
        }));
        builder.button(52, new SfxMenuButton(nextRecipeIcon(page, pageCount), click -> {
            if (page + 1 < pageCount) {
                next.accept(click.player());
            }
        }));
    }

    private void addRecipePagination(SfxMenu.Builder builder, int page, int pageCount, PlayerAction previous, PlayerAction next) {
        builder.button(46, new SfxMenuButton(previousRecipeIcon(page, pageCount), click -> {
            if (page > 0) {
                previous.accept(click.player());
            }
        }));
        builder.button(52, new SfxMenuButton(nextRecipeIcon(page, pageCount), click -> {
            if (page + 1 < pageCount) {
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
            icon.setItemMeta(meta);
        }
        return withLore(icon, List.of(Component.empty(), Text.mm(tr("guide.actions.open-category", "<gray>Click to open category</gray>"))));
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
        return preferencesByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> new GuidePreferences(defaultLayout(), true, true, true, true));
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

    private enum Navigation {
        ROOT,
        OPEN,
        REPLACE
    }

    private enum GuideLayout {
        CLASSIC,
        SFX;

        static GuideLayout from(String raw) {
            if (raw == null || raw.isBlank()) {
                return CLASSIC;
            }
            return "sfx".equalsIgnoreCase(raw) ? SFX : CLASSIC;
        }
    }

    private enum GuideRecipeOrigin {
        SFX,
        VANILLA
    }

    private record GuideRecipePage(
            int index,
            GuideRecipeOrigin origin,
            String sourceId,
            String sourceFamily,
            String sourceName,
            String machineTargetId,
            ItemStack sourceIcon,
            List<SfxRecipeSlot> matrix,
            Component note,
            int outputAmount
    ) {
        static GuideRecipePage noRecipe() {
            return new GuideRecipePage(0, GuideRecipeOrigin.SFX, "no-recipe", "no-recipe", "No Recipe", null,
                    ItemBuilder.of(Material.BARRIER).name("<red>No Recipe</red>").build(),
                    Collections.nCopies(9, SfxRecipeSlot.empty()), null, 1);
        }

        boolean hasRecipe() {
            return !"no-recipe".equals(sourceId);
        }

        GuideMode mode() {
            return GuideMode.SURVIVAL;
        }

        Material outputMaterial() {
            return null;
        }
    }

    private static final class GuidePreferences {
        private GuideLayout layout;
        private boolean recordHistory;
        private boolean closeReturns;
        private boolean fireworks;
        private boolean unlockAnimation;

        private GuidePreferences(GuideLayout layout, boolean recordHistory, boolean closeReturns, boolean fireworks, boolean unlockAnimation) {
            this.layout = layout;
            this.recordHistory = recordHistory;
            this.closeReturns = closeReturns;
            this.fireworks = fireworks;
            this.unlockAnimation = unlockAnimation;
        }

        GuideLayout layout() {
            return layout;
        }

        void setLayout(GuideLayout layout) {
            this.layout = layout;
        }

        boolean recordHistory() {
            return recordHistory;
        }

        void setRecordHistory(boolean recordHistory) {
            this.recordHistory = recordHistory;
        }

        boolean closeReturns() {
            return closeReturns;
        }

        void setCloseReturns(boolean closeReturns) {
            this.closeReturns = closeReturns;
        }

        boolean fireworks() {
            return fireworks;
        }

        void setFireworks(boolean fireworks) {
            this.fireworks = fireworks;
        }

        boolean unlockAnimation() {
            return unlockAnimation;
        }

        void setUnlockAnimation(boolean unlockAnimation) {
            this.unlockAnimation = unlockAnimation;
        }
    }

    private record DisplayEntry(ItemStack icon, String label, int priority, ClickHandler handler) {
    }

    @FunctionalInterface
    private interface ClickHandler {
        void accept(cc.theends6.sfx.api.menu.SfxMenuClickContext click);
    }

    @FunctionalInterface
    private interface OutputAction {
        void accept(Player player, ClickType clickType);
    }

    @FunctionalInterface
    private interface RecipePageOpener {
        void open(Player player, int recipeIndex, Navigation navigation);
    }

    @FunctionalInterface
    private interface PlayerAction {
        void accept(Player player);
    }
}
