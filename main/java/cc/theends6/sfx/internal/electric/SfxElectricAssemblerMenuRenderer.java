package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.ui.SfxInventoryPainter;
import cc.theends6.sfx.internal.util.SfxLocalization;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class SfxElectricAssemblerMenuRenderer {
    static final int ENABLE_SLOT = 13;
    static final int OFFSET_SLOT = 31;
    static final int[] HEAD_SLOTS = {19, 28};
    static final int[] BODY_SLOTS = {25, 34};

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
        inventory.setItem(ENABLE_SLOT, enabledItem(definition, state));
        inventory.setItem(definition.ui().statusSlot(), statusIcons.render(viewerId, definition, state, null, status));
        inventory.setItem(OFFSET_SLOT, offsetItem(definition, state));
        int[] inputSlots = definition.inputSlots();
        for (int index = 0; index < inputSlots.length; index++) {
            inventory.setItem(inputSlots[index], state.input(index) == null ? null : state.input(index).toItemStack(items));
        }
    }

    private void fillFrame(Inventory inventory, SfxElectricMachineDefinition definition) {
        inventory.clear();
        SfxElectricAssemblerSpec spec = definition.assemblerSpec();
        for (SfxElectricMachineUiFrame frame : definition.ui().frame()) {
            SfxInventoryPainter.setSlots(inventory, frame.item().toItemStack(localization, Map.of()), frame.slots());
        }
        if (spec != null) {
            inventory.setItem(1, displayMaterial(definition, "assembler.head.display", spec.headMaterial(), spec.headAmount(), List.of(spec.headMaterial())));
            inventory.setItem(7, displayMaterial(definition, "assembler.body.display", spec.primaryBodyMaterial(), spec.bodyAmount(), new ArrayList<>(spec.bodyMaterials())));
        }
    }

    private ItemStack enabledItem(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        boolean enabled = state.enabled();
        return definition.ui().item(enabled ? "assembler.enabled" : "assembler.disabled",
                new SfxElectricMachineUiItem(enabled ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                        enabled ? "<green>Enabled</green>" : "<red>Disabled</red>",
                        List.of("<gray>Click to toggle this assembler.</gray>")))
                .toItemStack(localization, Map.of());
    }

    private ItemStack offsetItem(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        double offset = SfxAreaElectricMachineProviders.assemblerOffsetTenths(state) / 10.0D;
        return definition.ui().item("assembler.offset",
                new SfxElectricMachineUiItem(Material.COMPASS, "<aqua>Spawn Offset</aqua>", List.of(
                        "<gray>Offset: {offset} Block(s)</gray>",
                        "<gray>Left-click: +0.1</gray>",
                        "<gray>Right-click: -0.1</gray>",
                        "<gray>Range: -10.0 to 10.0</gray>")))
                .toItemStack(localization, Map.of("offset", offset));
    }

    private ItemStack displayMaterial(SfxElectricMachineDefinition definition, String key, Material material, int amount, List<Material> acceptedMaterials) {
        ItemStack stack = definition.ui().item(key,
                new SfxElectricMachineUiItem(material, "<yellow>Required Material</yellow>", List.of("<gray>Required: {amount}</gray>")))
                .toItemStack(material, localization, Map.of("amount", amount));
        if (acceptedMaterials.size() > 1) {
            List<Component> lore = stack.lore() == null ? new ArrayList<>() : new ArrayList<>(stack.lore());
            lore.add(localization.component("configurable-ui.assembler.accepted.lore", "<gray>Accepted materials:</gray>"));
            for (Material accepted : acceptedMaterials) {
                lore.add(Component.text(" - ").append(Component.translatable(accepted.translationKey())));
            }
            stack.lore(lore);
        }
        return stack;
    }
}
