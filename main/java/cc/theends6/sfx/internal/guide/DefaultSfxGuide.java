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
import cc.theends6.sfx.internal.machine.ManualMachineDeployPacks;
import cc.theends6.sfx.internal.util.ItemBuilder;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class DefaultSfxGuide implements SfxGuide {
    private static final int[] CONTENT_SLOTS = {
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final int[] RECIPE_SLOTS = {3, 4, 5, 12, 13, 14, 21, 22, 23};

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final DefaultSfxItemRegistry registry;
    private final SfxItems items;
    private final SfxMenus menus;
    private final SfxGuideAccessPolicy accessPolicy;
    private final DefaultManualMachineRegistry manualMachines;
    private final SfxLocalization localization;

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
            player.sendMessage(Text.prefixed(plugin, tr("guide.errors.no-open", "<red>你没有打开这本科技书的权限。</red>")));
            return;
        }
        openMain(player, mode, 0);
    }

    @Override
    public void openSettings(Player player, GuideMode mode) {
        if (!accessPolicy.canOpen(player, mode)) {
            player.sendMessage(Text.prefixed(plugin, tr("guide.errors.no-settings", "<red>你没有打开科技书设置的权限。</red>")));
            return;
        }
        SfxMenu.Builder builder = SfxMenu.builder(title(mode, tr("guide.settings.title", "设置"))).rows(3);
        ItemStack pane = ItemBuilder.of(mode == GuideMode.CHEAT ? Material.RED_STAINED_GLASS_PANE : Material.GREEN_STAINED_GLASS_PANE)
                .name("<dark_gray> </dark_gray>")
                .build();
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18) {
                builder.button(i, new SfxMenuButton(pane, click -> {
                }));
            }
        }
        builder.button(10, new SfxMenuButton(ItemBuilder.of(Material.ENCHANTED_BOOK)
                .name(tr("guide.settings.open-survival.name", "<green>打开普通科技书</green>"))
                .lore(tr("guide.settings.open-survival.lore", "<gray>经典科技书入口。</gray>"))
                .build(), click -> openMain(click.player(), GuideMode.SURVIVAL, 0)));
        builder.button(12, new SfxMenuButton(ItemBuilder.of(Material.COMMAND_BLOCK)
                .name(tr("guide.settings.open-cheat.name", "<red>打开作弊科技书</red>"))
                .lore(tr("guide.settings.open-cheat.lore", "<gray>需要作弊科技书权限。</gray>"))
                .build(), click -> open(click.player(), GuideMode.CHEAT)));
        builder.button(14, new SfxMenuButton(ItemBuilder.of(Material.COMPARATOR)
                .name(tr("guide.settings.preferences.name", "<yellow>显示偏好</yellow>"))
                .lore(tr("guide.settings.preferences.lore", "<gray>当前保留设置入口。</gray>"))
                .build(), click -> click.player().sendMessage(Text.prefixed(plugin, tr("guide.settings.preferences.todo", "<gray>科技书设置入口已保留，后续再补详细选项。</gray>")))));
        builder.button(16, new SfxMenuButton(backIcon(tr("guide.actions.back-guide", "返回科技书")), click -> openMain(click.player(), mode, 0)));
        menus.open(player, builder.build());
    }

    private void openMain(Player player, GuideMode mode, int page) {
        List<SfxItemCategory> visibleCategories = LegacySfGuideResolver.visibleCategories(registry, mode);
        int pageCount = pageCount(visibleCategories.size());
        int safePage = clampPage(page, pageCount);

        SfxMenu.Builder builder = SfxMenu.builder(title(mode, tr("guide.main.title", "主菜单"))).rows(6);
        paintFrame(builder, mode);

        int from = safePage * CONTENT_SLOTS.length;
        int to = Math.min(visibleCategories.size(), from + CONTENT_SLOTS.length);
        for (int i = from; i < to; i++) {
            SfxItemCategory category = visibleCategories.get(i);
            int slot = CONTENT_SLOTS[i - from];
            ItemStack icon = categoryButtonIcon(category);
            builder.button(slot, new SfxMenuButton(icon, click -> openCategory(click.player(), mode, category.id(), 0)));
        }

        addPagination(builder, safePage, pageCount,
                previous -> openMain(previous, mode, safePage - 1),
                next -> openMain(next, mode, safePage + 1));
        builder.button(49, new SfxMenuButton(infoIcon(mode, safePage, pageCount), click -> click.menus().close(click.player())));
        menus.open(player, builder.build());
    }

    private void openCategory(Player player, GuideMode mode, String categoryId, int page) {
        Optional<SfxItemCategory> optionalCategory = LegacySfGuideResolver.resolveCategory(registry, categoryId);
        if (optionalCategory.isEmpty()) {
            player.sendMessage(Text.prefixed(plugin, tr("guide.errors.missing-category", "<red>这个 SFX 分类不存在。</red>")));
            return;
        }
        SfxItemCategory category = optionalCategory.get();
        List<SfxItemDefinition> entries = LegacySfGuideResolver.visibleItemsInCategory(registry, category.id()).stream()
                .filter(item -> accessPolicy.canViewItem(player, mode, item))
                .toList();

        int pageCount = pageCount(entries.size());
        int safePage = clampPage(page, pageCount);

        SfxMenu.Builder builder = SfxMenu.builder(title(mode, plainCategoryName(category))).rows(6);
        paintFrame(builder, mode);

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
                            Text.mm(tr("guide.cheat.machine-pack", "<red>作弊模式：点击获取部署包</red>")),
                            Text.mm(tr("guide.cheat.machine-kit", "<red>Shift 点击获取整套机器材料</red>"))
                    ));
                } else {
                    icon = withLore(icon, List.of(
                            Component.empty(),
                            Text.mm(tr("guide.cheat.take-one", "<red>作弊模式：点击获取 1 个</red>")),
                            Text.mm(tr("guide.cheat.take-stack", "<red>Shift 点击获取 64 个</red>"))
                    ));
                }
                builder.button(slot, new SfxMenuButton(icon, click -> giveFromCheatGuide(click.player(), definition, click.clickType())));
            } else {
                icon = withLore(icon, List.of(
                        Component.empty(),
                        Text.mm(tr("guide.actions.open-recipe", "<gray>点击查看配方</gray>"))
                ));
                builder.button(slot, new SfxMenuButton(icon, click -> openRecipe(click.player(), mode, definition.id(), category.id(), 0)));
            }
        }

        builder.button(1, new SfxMenuButton(backIcon(tr("guide.actions.back-main", "返回主菜单")), click -> openMain(click.player(), mode, 0)));
        addPagination(builder, safePage, pageCount,
                previous -> openCategory(previous, mode, category.id(), safePage - 1),
                next -> openCategory(next, mode, category.id(), safePage + 1));
        builder.button(49, new SfxMenuButton(infoIcon(mode, safePage, pageCount), click -> click.menus().close(click.player())));
        menus.open(player, builder.build());
    }

    private void openRecipe(Player player, GuideMode mode, String itemId, String returnCategoryId, int recipeIndex) {
        Optional<SfxItemDefinition> optional = registry.item(itemId);
        if (optional.isEmpty()) {
            player.sendMessage(Text.prefixed(plugin, tr("guide.errors.missing-item", "<red>这个 SFX 物品不存在。</red>")));
            return;
        }
        SfxItemDefinition definition = optional.get();
        List<SfxRecipe> recipes = definition.recipes();
        int pageCount = Math.max(1, recipes.size());
        int safeRecipe = clampPage(recipeIndex, pageCount);

        SfxMenu.Builder builder = SfxMenu.builder(title(mode, tr("guide.recipe.title", "配方详情"))).rows(6);
        paintRecipeFrame(builder, mode);

        builder.button(16, new SfxMenuButton(withLore(items.create(definition, 1), List.of(
                Component.empty(),
                Text.mm(tr("guide.recipe.output", "<green>目标产物</green>"))
        )), click -> {
            if (mode == GuideMode.CHEAT) {
                giveFromCheatGuide(click.player(), definition, click.clickType());
            }
        }));

        if (recipes.isEmpty()) {
            builder.button(20, new SfxMenuButton(ItemBuilder.of(Material.BARRIER)
                    .name(tr("guide.recipe.no-recipe.name", "<red>暂无配方</red>"))
                    .lore(tr("guide.recipe.no-recipe.lore", "<gray>这个物品目前只是注册表样品或系统物品。</gray>"))
                    .build(), click -> {
            }));
        } else {
            SfxRecipe recipe = recipes.get(safeRecipe);
            int outputAmount = Math.max(1, recipe.outputAmount());
            Optional<ManualMachineDefinition> structureMachine = "multiblock-structure".equals(recipe.recipeType())
                    ? manualMachines.machine(definition.id())
                    : Optional.empty();
            List<SfxRecipeSlot> matrix = recipe.matrix();
            for (int i = 0; i < matrix.size(); i++) {
                SfxRecipeSlot slot = matrix.get(i);
                if (slot == null || slot.isEmpty()) {
                    continue;
                }
                int matrixIndex = i;
                int inventorySlot = RECIPE_SLOTS[matrixIndex];
                ItemStack ingredientIcon = structureMachine
                        .map(machine -> structureIngredientIcon(machine, matrixIndex, slot))
                        .orElseGet(() -> ingredientIcon(slot));
                builder.button(inventorySlot, new SfxMenuButton(ingredientIcon, click -> slot.sfxId().ifPresent(target ->
                        openRecipe(click.player(), mode, target, returnCategoryId, 0))));
            }
            builder.button(10, new SfxMenuButton(recipeTypeIcon(definition, recipe, safeRecipe, pageCount), click -> {
            }));

            ItemStack outputIcon = withLore(items.create(definition, outputAmount), List.of(
                    Component.empty(),
                    Text.mm(tr("guide.recipe.output", "<green>目标产物</green>"))
            ));
            builder.button(16, new SfxMenuButton(outputIcon, click -> {
                if (mode == GuideMode.CHEAT) {
                    giveFromCheatGuide(click.player(), definition, click.clickType());
                }
            }));

            manualMachines.machine(recipe.recipeType())
                    .filter(machine -> machine.operation() == cc.theends6.sfx.internal.machine.ManualMachineOperation.SINGLE_INPUT
                            || machine.operation() == cc.theends6.sfx.internal.machine.ManualMachineOperation.SHAPELESS_INPUT
                            || machine.operation() == cc.theends6.sfx.internal.machine.ManualMachineOperation.HAND_INPUT)
                    .ifPresent(machine -> builder.button(40, new SfxMenuButton(withLore(items.create(definition, outputAmount), List.of(
                            Component.empty(),
                            Text.mm(tr("guide.recipe.output-short", "<green>产物</green>"))
                    )), click -> {
                    })));
        }

        builder.button(0, new SfxMenuButton(backIcon(tr("guide.actions.back-category", "返回分类")), click -> openCategory(click.player(), mode, returnCategoryId, 0)));
        addPagination(builder, safeRecipe, pageCount,
                previous -> openRecipe(previous, mode, definition.id(), returnCategoryId, safeRecipe - 1),
                next -> openRecipe(next, mode, definition.id(), returnCategoryId, safeRecipe + 1));
        builder.button(49, new SfxMenuButton(infoIcon(mode, safeRecipe, pageCount), click -> click.menus().close(click.player())));
        menus.open(player, builder.build());
    }

    private void giveFromCheatGuide(Player player, SfxItemDefinition definition, ClickType clickType) {
        if (!accessPolicy.canReceiveFromCheatGuide(player, definition)) {
            player.sendMessage(Text.prefixed(plugin, tr("guide.errors.no-cheat-access", "<red>你没有使用作弊科技书的权限，或该物品不可直接获取。</red>")));
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
            player.sendMessage(Text.prefixed(plugin, localization.text("machines.cheat-item", "<green>已获取：</green><gray>{item} x{amount}</gray>").replace("{item}", itemDisplayName(definition)).replace("{amount}", Integer.toString(amount))));
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
                        .reduce((left, right) -> left + "、" + right)
                        .orElse(materialName(definition.triggerMaterial()));
                String key = definition.deployable() ? "machines.cheat-machine-kit" : "machines.deploy-pack-unsupported";
                String fallback = definition.deployable()
                        ? "<green>已获取基础机器整套材料：</green><gray>{item}</gray>"
                        : "<yellow>这台基础机器当前不支持部署包，已发放整套结构材料：</yellow><gray>{item}</gray>";
                player.sendMessage(Text.prefixed(plugin, localization.text(key, fallback).replace("{item}", parts)));
            } else {
                ItemStack deployPack = ManualMachineDeployPacks.create(plugin, definition, localization);
                items.give(player, deployPack);
                player.sendMessage(Text.prefixed(plugin, localization.text("machines.cheat-machine-pack", "<green>已获取基础机器部署包：</green><gray>{item}</gray>").replace("{item}", machineDisplayName(definition))));
            }
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
        });
    }


    private ItemStack structureIngredientIcon(ManualMachineDefinition machine, int index, SfxRecipeSlot slot) {
        Material displayMaterial = machine.displayPattern()[index];
        if (displayMaterial == null) {
            return ingredientIcon(slot);
        }
        if (displayMaterial == Material.DISPENSER) {
            String name;
            if (index == 1 && machine.inventoryFace() == BlockFace.UP) {
                name = tr("guide.structure.dispenser-down", "<yellow>发射器</yellow> <gray>(朝下)</gray>");
            } else {
                name = tr("guide.structure.dispenser-up", "<yellow>发射器</yellow> <gray>(朝上)</gray>");
            }
            return ItemBuilder.of(Material.DISPENSER).name(name).build();
        }
        if (displayMaterial == Material.FLINT_AND_STEEL) {
            return ItemBuilder.of(Material.FLINT_AND_STEEL)
                    .name(tr("guide.structure.ignition.name", "<gold>打火石</gold>"))
                    .lore(tr("guide.structure.ignition.lore", "<gray>用于点燃该多方块机器。</gray>"))
                    .build();
        }
        return ingredientIcon(slot);
    }

    private ItemStack ingredientIcon(SfxRecipeSlot slot) {
        if (slot == null || slot.isEmpty()) {
            return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name("<dark_gray> </dark_gray>").build();
        }
        ItemStack icon = slot.isSfxItem()
                ? items.create(slot.sfxItemId(), slot.amount())
                : new ItemStack(slot.material(), slot.amount());

        List<Component> extraLore = new ArrayList<>();
        if (slot.isSfxItem()) {
            extraLore.add(Component.empty());
            extraLore.add(Text.mm(tr("guide.actions.open-recipe", "<gray>点击查看配方</gray>")));
        } else if (slot.amount() > 1) {
            extraLore.add(Component.empty());
        }
        if (slot.amount() > 1) {
            extraLore.add(Component.text(tr("guide.recipe.amount", "数量：") + slot.amount(), NamedTextColor.GRAY));
        }
        return extraLore.isEmpty() ? icon : withLore(icon, extraLore);
    }

    private ItemStack recipeTypeIcon(SfxItemDefinition resultDefinition, SfxRecipe recipe, int index, int pageCount) {
        ItemStack icon;
        String recipeName;
        if (recipe.recipeType().equals("multiblock-structure")) {
            recipeName = tr("guide.recipe.structure", "多方块结构");
            Optional<ManualMachineDefinition> machine = manualMachines.machine(resultDefinition.id());
            if (machine.isPresent()) {
                icon = ItemBuilder.of(machine.get().triggerMaterial())
                        .name("<green>" + recipeName + "</green>")
                        .lore(pageLore(index, pageCount))
                        .build();
            } else {
                icon = ItemBuilder.of(Material.KNOWLEDGE_BOOK)
                        .name("<green>" + recipeName + "</green>")
                        .lore(pageLore(index, pageCount))
                        .build();
            }
        } else {
            Optional<ManualMachineDefinition> manualMachine = manualMachines.machine(recipe.recipeType());
            if (manualMachine.isPresent()) {
                recipeName = plainText(localization.itemName(manualMachine.get().id(), manualMachine.get().name()));
                icon = ItemBuilder.of(manualMachine.get().triggerMaterial())
                        .name("<green>" + recipeName + "</green>")
                        .lore(pageLore(index, pageCount))
                        .build();
            } else {
                Optional<SfxItemDefinition> machineDefinition = registry.item(recipe.recipeType());
                if (machineDefinition.isPresent()) {
                    recipeName = plainText(machineDefinition.get().name());
                    icon = withLore(items.create(machineDefinition.get(), 1), List.of(
                            Component.empty(),
                            Text.mm(pageLore(index, pageCount))
                    ));
                } else {
                    recipeName = recipe.recipeType();
                    icon = ItemBuilder.of(Material.BOOK)
                            .name("<green>" + recipeName + "</green>")
                            .lore(pageLore(index, pageCount))
                            .build();
                }
            }
        }
        List<Component> localizedNotes = localization.recipeNote(resultDefinition.id(), index, recipe.note());
        if (!localizedNotes.isEmpty()) {
            List<Component> noteLore = new ArrayList<>();
            noteLore.add(Component.empty());
            noteLore.addAll(localizedNotes);
            icon = withLore(icon, noteLore);
        }
        return icon;
    }

    private void paintFrame(SfxMenu.Builder builder, GuideMode mode) {
        ItemStack pane = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE)
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

        ItemStack modeIcon = mode == GuideMode.CHEAT
                ? ItemBuilder.of(Material.ENCHANTED_BOOK).name(tr("guide.mode.cheat.name", "<dark_red>作弊科技书</dark_red>")).lore(tr("guide.mode.cheat.lore", "<gray>点击物品会直接获取。</gray>")).build()
                : ItemBuilder.of(Material.ENCHANTED_BOOK).name(tr("guide.mode.survival.name", "<dark_green>科技书</dark_green>")).lore(tr("guide.mode.survival.lore", "<gray>点击物品会查看配方。</gray>")).build();
        builder.button(4, new SfxMenuButton(modeIcon, click -> {
        }));
        if (mode == GuideMode.CHEAT) {
            builder.button(1, new SfxMenuButton(pane, click -> {
            }));
        } else {
            builder.button(1, new SfxMenuButton(settingsIcon(), click -> openSettings(click.player(), mode)));
        }
        builder.button(7, new SfxMenuButton(ItemBuilder.of(Material.NAME_TAG)
                .name(tr("guide.actions.search.name", "<yellow>搜索</yellow>"))
                .lore(tr("guide.actions.search.lore", "<gray>搜索入口已预留。</gray>"))
                .build(), click -> click.player().sendMessage(Text.prefixed(plugin, tr("guide.actions.search.todo", "<gray>搜索入口已预留，后续补聊天搜索结果页。</gray>")))));
    }


    private void paintRecipeFrame(SfxMenu.Builder builder, GuideMode mode) {
        ItemStack pane = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE)
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

        if (mode != GuideMode.CHEAT) {
            builder.button(1, new SfxMenuButton(settingsIcon(), click -> openSettings(click.player(), mode)));
        }
        builder.button(7, new SfxMenuButton(ItemBuilder.of(Material.NAME_TAG)
                .name(tr("guide.actions.search.name", "<yellow>搜索</yellow>"))
                .lore(tr("guide.actions.search.lore", "<gray>搜索入口已预留。</gray>"))
                .build(), click -> click.player().sendMessage(Text.prefixed(plugin, tr("guide.actions.search.todo", "<gray>搜索入口已预留，后续补聊天搜索结果页。</gray>")))));
    }

    private void addPagination(SfxMenu.Builder builder, int page, int pageCount, PlayerAction previous, PlayerAction next) {
        ItemStack previousIcon = ItemBuilder.of(Material.ARROW)
                .name(page > 0 ? tr("guide.pagination.prev.active", "<yellow>上一页</yellow>") : tr("guide.pagination.prev.inactive", "<dark_gray>上一页</dark_gray>"))
                .lore(pageNumberLore(page, pageCount))
                .build();
        ItemStack nextIcon = ItemBuilder.of(Material.ARROW)
                .name(page + 1 < pageCount ? tr("guide.pagination.next.active", "<yellow>下一页</yellow>") : tr("guide.pagination.next.inactive", "<dark_gray>下一页</dark_gray>"))
                .lore(pageNumberLore(page, pageCount))
                .build();
        builder.button(46, new SfxMenuButton(previousIcon, click -> {
            if (page > 0) {
                previous.accept(click.player());
            }
        }));
        builder.button(52, new SfxMenuButton(nextIcon, click -> {
            if (page + 1 < pageCount) {
                next.accept(click.player());
            }
        }));
    }

    private ItemStack infoIcon(GuideMode mode, int page, int pageCount) {
        return ItemBuilder.of(Material.NETHER_STAR)
                .name(mode == GuideMode.CHEAT ? tr("guide.mode.cheat.name", "<red>SFX 作弊科技书</red>") : tr("guide.mode.survival.name", "<green>SFX 科技书</green>"))
                .lore(pageNumberLore(page, pageCount), "", tr("guide.actions.close", "<dark_gray>点击关闭。</dark_gray>"))
                .build();
    }

    private ItemStack settingsIcon() {
        return ItemBuilder.of(Material.COMPARATOR)
                .name(tr("guide.actions.settings.name", "<yellow>科技书设置</yellow>"))
                .lore(tr("guide.actions.settings.lore", "<gray>也可以 Shift + 右键科技书打开。</gray>"))
                .build();
    }

    private ItemStack backIcon(String text) {
        return ItemBuilder.of(Material.BARRIER)
                .name("<yellow>" + text + "</yellow>")
                .build();
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
        return withLore(icon, List.of(
                Component.empty(),
                Text.mm(tr("guide.actions.open-category", "<gray>点击打开分类</gray>"))
        ));
    }

    private String pageLore(int index, int pageCount) {
        return tr("guide.recipe.page", "<gray>当前配方 {current} / {total}</gray>")
                .replace("{current}", Integer.toString(index + 1))
                .replace("{total}", Integer.toString(pageCount));
    }

    private String pageNumberLore(int page, int pageCount) {
        return tr("guide.pagination.page", "<gray>页码 {current} / {total}</gray>")
                .replace("{current}", Integer.toString(page + 1))
                .replace("{total}", Integer.toString(pageCount));
    }

    private String tr(String key, String fallback) {
        return localization.text(key, fallback);
    }

    private Component title(GuideMode mode, String suffix) {
        String configured = mode == GuideMode.CHEAT
                ? plugin.getConfig().getString("guide.cheat-title", tr("guide.mode.cheat.title", "<dark_red>SFX 作弊科技书</dark_red>"))
                : plugin.getConfig().getString("guide.survival-title", tr("guide.mode.survival.title", "<dark_green>SFX 科技书</dark_green>"));
        return Text.mm(configured + " <dark_gray>|</dark_gray> <gray>" + suffix + "</gray>");
    }

    private String plainCategoryName(SfxItemCategory category) {
        return plainText(localization.categoryName(category.id(), category.name()));
    }

    private String materialName(Material material) {
        String key = "materials." + material.name().toLowerCase();
        return localization.text(key, switch (material) {
            case CRAFTING_TABLE -> "工作台";
            case DISPENSER -> "发射器";
            case OAK_FENCE -> "木栅栏";
            case NETHER_BRICK_FENCE -> "地狱砖栅栏";
            case PISTON -> "活塞";
            case IRON_BARS -> "铁栏杆";
            case CAULDRON -> "炼药锅";
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

    @FunctionalInterface
    private interface PlayerAction {
        void accept(Player player);
    }
}
