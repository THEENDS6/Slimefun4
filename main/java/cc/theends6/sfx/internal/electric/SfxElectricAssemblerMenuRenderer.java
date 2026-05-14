package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.util.SfxLocalization;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class SfxElectricAssemblerMenuRenderer {
    static final int ENABLE_SLOT = 13;
    static final int STATUS_SLOT = 22;
    static final int OFFSET_SLOT = 31;
    static final int[] HEAD_SLOTS = {19, 28};
    static final int[] BODY_SLOTS = {25, 34};

    private static final int[] BACKGROUND_SLOTS = {
            0, 2, 3, 4, 5, 6, 8,
            12, 14,
            21, 23,
            30, 32,
            39, 40, 41,
            45, 46, 47, 48, 49, 50, 51, 52, 53
    };
    private static final int[] HEAD_FRAME = {9, 10, 11, 18, 20, 27, 29, 36, 37, 38};
    private static final int[] BODY_FRAME = {15, 16, 17, 24, 26, 33, 35, 42, 43, 44};

    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxElectricMachineStatusIconRenderer statusIcons;

    SfxElectricAssemblerMenuRenderer(SfxItems items, SfxLocalization localization, SfxPlayerDataService profiles) {
        this.items = items;
        this.localization = localization;
        this.statusIcons = new SfxElectricMachineStatusIconRenderer(items, localization, profiles);
    }

    void render(UUID viewerId, SfxElectricMachineDefinition definition, Inventory inventory, SfxElectricMachineState state, SfxElectricMachineRenderStatus status) {
        fillFrame(inventory, definition);
        inventory.setItem(ENABLE_SLOT, enabledItem(state));
        inventory.setItem(STATUS_SLOT, statusIcons.render(viewerId, definition, state, null, status));
        inventory.setItem(OFFSET_SLOT, offsetItem(state));
        int[] inputSlots = definition.inputSlots();
        for (int index = 0; index < inputSlots.length; index++) {
            inventory.setItem(inputSlots[index], state.input(index) == null ? null : state.input(index).toItemStack(items));
        }
    }

    private void fillFrame(Inventory inventory, SfxElectricMachineDefinition definition) {
        inventory.clear();
        SfxElectricAssemblerSpec spec = definition.assemblerSpec();
        Material headPane = definition.id().equals("sf:iron_golem_assembler") ? Material.ORANGE_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
        Material bodyPane = definition.id().equals("sf:iron_golem_assembler") ? Material.WHITE_STAINED_GLASS_PANE : Material.BROWN_STAINED_GLASS_PANE;
        setSlots(inventory, namedItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of()), BACKGROUND_SLOTS);
        setSlots(inventory, namedItem(headPane, localization.component("configurable-ui.assembler.head-slot.name", "<yellow>Head Material</yellow>"), List.of()), HEAD_FRAME);
        setSlots(inventory, namedItem(bodyPane, localization.component("configurable-ui.assembler.body-slot.name", "<gold>Body Material</gold>"), List.of()), BODY_FRAME);
        if (spec != null) {
            inventory.setItem(1, displayMaterial(spec.headMaterial(), localization.component("configurable-ui.assembler.head-slot.name", "<yellow>Head Material</yellow>"), spec.headAmount(), List.of(spec.headMaterial())));
            inventory.setItem(7, displayMaterial(spec.primaryBodyMaterial(), localization.component("configurable-ui.assembler.body-slot.name", "<gold>Body Material</gold>"), spec.bodyAmount(), new ArrayList<>(spec.bodyMaterials())));
        }
    }

    private ItemStack enabledItem(SfxElectricMachineState state) {
        boolean enabled = state.enabled();
        List<Component> lore = new ArrayList<>();
        if (!enabled) {
            lore.add(localization.component("configurable-ui.assembler.disabled.lore", "<gray>This assembler is disabled.</gray>"));
        }
        lore.add(localization.component("configurable-ui.assembler.toggle.lore", "<gray>Click to toggle this assembler.</gray>"));
        return namedItem(
                enabled ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                enabled
                        ? localization.component("configurable-ui.assembler.enabled.name", "<green>Enabled</green>")
                        : localization.component("configurable-ui.assembler.disabled.name", "<red>Disabled</red>"),
                lore);
    }

    private ItemStack offsetItem(SfxElectricMachineState state) {
        double offset = SfxAreaElectricMachineProviders.assemblerOffsetTenths(state) / 10.0D;
        return namedItem(
                Material.COMPASS,
                localization.component("configurable-ui.assembler.offset.name", "<aqua>Spawn Offset</aqua>", Map.of("offset", offset)),
                List.of(localization.component("configurable-ui.assembler.offset.status", "<gray>Offset: {offset} Block(s)</gray>", Map.of("offset", offset)),
                        localization.component("configurable-ui.assembler.offset.left", "<gray>Left-click: +0.1</gray>"),
                        localization.component("configurable-ui.assembler.offset.right", "<gray>Right-click: -0.1</gray>"),
                        localization.component("configurable-ui.assembler.offset.range", "<gray>Range: -10.0 to 10.0</gray>")));
    }

    private ItemStack displayMaterial(Material material, Component name, int amount, List<Material> acceptedMaterials) {
        List<Component> lore = new ArrayList<>();
        lore.add(localization.component("configurable-ui.assembler.required.lore", "<gray>Required: {amount}</gray>", Map.of("amount", amount)));
        if (acceptedMaterials.size() > 1) {
            lore.add(localization.component("configurable-ui.assembler.accepted.lore", "<gray>Accepted materials are shown by Minecraft localization.</gray>"));
            for (Material accepted : acceptedMaterials) {
                lore.add(Component.text(" - ").append(Component.translatable(accepted.translationKey())));
            }
        }
        return namedItem(material, name, lore);
    }

    private void setSlots(Inventory inventory, ItemStack item, int... slots) {
        for (int slot : slots) {
            inventory.setItem(slot, item);
        }
    }

    private ItemStack namedItem(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
