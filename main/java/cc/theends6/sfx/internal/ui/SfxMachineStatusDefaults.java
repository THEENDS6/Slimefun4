package cc.theends6.sfx.internal.ui;

import cc.theends6.sfx.internal.util.SfxLocalization;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

public final class SfxMachineStatusDefaults {
    private SfxMachineStatusDefaults() {
    }

    public static Material material(SfxMachineStatusKey status) {
        if (status == null) {
            return Material.BLACK_STAINED_GLASS_PANE;
        }
        return switch (status) {
            case WORKING -> Material.LIME_STAINED_GLASS_PANE;
            case PAUSED -> Material.YELLOW_STAINED_GLASS_PANE;
            case FULL -> Material.GREEN_STAINED_GLASS_PANE;
            case OUTPUT_FULL -> Material.ORANGE_STAINED_GLASS_PANE;
            case NO_POWER, BLOCKED_OUTPUT, INVALID_INPUT, NO_NETWORK, NETWORK_CONFLICT, AREA_CONFLICT, MISSING_RESOURCE -> Material.RED_STAINED_GLASS_PANE;
            case IDLE, DISABLED, NO_INPUT, NO_RECIPE, NO_TARGET, CUSTOM -> Material.BLACK_STAINED_GLASS_PANE;
        };
    }

    public static Component name(SfxLocalization localization, SfxMachineStatusKey status) {
        if (localization == null || status == null) {
            return Component.text(" ");
        }
        return switch (status) {
            case IDLE -> localization.component("machine-status.idle.name", "<gray>Idle</gray>");
            case WORKING -> localization.component("machine-status.working.name", "<yellow>Working</yellow>");
            case NO_POWER -> localization.component("machine-status.no-power.name", "<red>No Power</red>");
            case PAUSED -> localization.component("machine-status.paused.name", "<yellow>Paused</yellow>");
            case DISABLED -> localization.component("machine-status.disabled.name", "<gray>Disabled</gray>");
            case NO_INPUT -> localization.component("machine-status.no-input.name", "<gray>No Input</gray>");
            case NO_RECIPE -> localization.component("machine-status.no-recipe.name", "<gray>No Recipe</gray>");
            case NO_TARGET -> localization.component("machine-status.no-target.name", "<red>No Target</red>");
            case OUTPUT_FULL -> localization.component("machine-status.output-full.name", "<red>Output Full</red>");
            case BLOCKED_OUTPUT -> localization.component("machine-status.blocked-output.name", "<red>Blocked</red>");
            case INVALID_INPUT -> localization.component("machine-status.invalid-input.name", "<red>Invalid Input</red>");
            case FULL -> localization.component("machine-status.full.name", "<green>Full</green>");
            case NO_NETWORK -> localization.component("machine-status.no-network.name", "<red>Not Connected</red>");
            case NETWORK_CONFLICT -> localization.component("machine-status.network-conflict.name", "<red>Network Conflict</red>");
            case AREA_CONFLICT -> localization.component("machine-status.area-conflict.name", "<red>Work Area Conflict</red>");
            case MISSING_RESOURCE -> localization.component("machine-status.missing-resource.name", "<red>Missing Resource</red>");
            case CUSTOM -> Component.text(" ");
        };
    }

    public static Component lore(SfxLocalization localization, SfxMachineStatusKey status) {
        if (localization == null || status == null || status == SfxMachineStatusKey.CUSTOM) {
            return Component.empty();
        }
        return switch (status) {
            case IDLE -> localization.component("machine-status.idle.lore", "<gray>Waiting for input.</gray>");
            case WORKING -> localization.component("machine-status.working.lore", "<gray>The machine is working.</gray>");
            case NO_POWER -> localization.component("machine-status.no-power.lore", "<gray>Charge this machine to continue.</gray>");
            case PAUSED -> localization.component("machine-status.paused.lore", "<gray>This machine is paused.</gray>");
            case DISABLED -> localization.component("machine-status.disabled.lore", "<gray>This machine is disabled.</gray>");
            case NO_INPUT -> localization.component("machine-status.no-input.lore", "<gray>Waiting for input.</gray>");
            case NO_RECIPE -> localization.component("machine-status.no-recipe.lore", "<gray>The current input has no matching recipe.</gray>");
            case NO_TARGET -> localization.component("machine-status.no-target.lore", "<gray>No valid target was found.</gray>");
            case OUTPUT_FULL -> localization.component("machine-status.output-full.lore", "<gray>Free an output slot to continue.</gray>");
            case BLOCKED_OUTPUT -> localization.component("machine-status.blocked-output.lore", "<gray>The output is full. Free a slot to commit the finished item.</gray>");
            case INVALID_INPUT -> localization.component("machine-status.invalid-input.lore", "<gray>The inserted item cannot be processed here.</gray>");
            case FULL -> localization.component("machine-status.full.lore", "<gray>This machine is full.</gray>");
            case NO_NETWORK -> localization.component("machine-status.no-network.lore", "<gray>This machine is not connected to an energy network.</gray>");
            case NETWORK_CONFLICT -> localization.component("machine-status.network-conflict.lore", "<gray>Resolve regulator or shared-node conflicts first.</gray>");
            case AREA_CONFLICT -> localization.component("machine-status.area-conflict.lore", "<gray>Machines of the same type cannot have overlapping work areas.</gray>");
            case MISSING_RESOURCE -> localization.component("machine-status.missing-resource.lore", "<gray>Add the missing resource to continue.</gray>");
            case CUSTOM -> Component.empty();
        };
    }
}
