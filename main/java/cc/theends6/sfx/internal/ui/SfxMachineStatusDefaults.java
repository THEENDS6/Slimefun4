package cc.theends6.sfx.internal.ui;

import cc.theends6.sfx.api.machine.runtime.*;

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
            case OUTPUT_FULL, NO_POWER, BLOCKED_OUTPUT, INVALID_INPUT, NO_NETWORK, NETWORK_CONFLICT, AREA_CONFLICT, MISSING_RESOURCE, CHUNK_NOT_SCANNED -> Material.RED_STAINED_GLASS_PANE;
            case IDLE, DISABLED, NO_INPUT, NO_RECIPE, NO_TARGET, NO_GEO_RESOURCE, CUSTOM -> Material.BLACK_STAINED_GLASS_PANE;
        };
    }

    public static Component name(SfxLocalization localization, SfxMachineStatusKey status) {
        if (localization == null || status == null) {
            return Component.text(" ");
        }
        return switch (status) {
            case IDLE -> localization.component("machine-status.idle.name");
            case WORKING -> localization.component("machine-status.working.name");
            case NO_POWER -> localization.component("machine-status.no-power.name");
            case PAUSED -> localization.component("machine-status.paused.name");
            case DISABLED -> localization.component("machine-status.disabled.name");
            case NO_INPUT -> localization.component("machine-status.no-input.name");
            case NO_RECIPE -> localization.component("machine-status.no-recipe.name");
            case NO_TARGET -> localization.component("machine-status.no-target.name");
            case CHUNK_NOT_SCANNED -> localization.component("machine-status.chunk-not-scanned.name");
            case NO_GEO_RESOURCE -> localization.component("machine-status.no-geo-resource.name");
            case OUTPUT_FULL -> localization.component("machine-status.output-full.name");
            case BLOCKED_OUTPUT -> localization.component("machine-status.blocked-output.name");
            case INVALID_INPUT -> localization.component("machine-status.invalid-input.name");
            case FULL -> localization.component("machine-status.full.name");
            case NO_NETWORK -> localization.component("machine-status.no-network.name");
            case NETWORK_CONFLICT -> localization.component("machine-status.network-conflict.name");
            case AREA_CONFLICT -> localization.component("machine-status.area-conflict.name");
            case MISSING_RESOURCE -> localization.component("machine-status.missing-resource.name");
            case CUSTOM -> Component.text(" ");
        };
    }

    public static Component lore(SfxLocalization localization, SfxMachineStatusKey status) {
        if (localization == null || status == null || status == SfxMachineStatusKey.CUSTOM) {
            return Component.empty();
        }
        return switch (status) {
            case IDLE -> localization.component("machine-status.idle.lore");
            case WORKING -> localization.component("machine-status.working.lore");
            case NO_POWER -> localization.component("machine-status.no-power.lore");
            case PAUSED -> localization.component("machine-status.paused.lore");
            case DISABLED -> localization.component("machine-status.disabled.lore");
            case NO_INPUT -> localization.component("machine-status.no-input.lore");
            case NO_RECIPE -> localization.component("machine-status.no-recipe.lore");
            case NO_TARGET -> localization.component("machine-status.no-target.lore");
            case CHUNK_NOT_SCANNED -> localization.component("machine-status.chunk-not-scanned.lore");
            case NO_GEO_RESOURCE -> localization.component("machine-status.no-geo-resource.lore");
            case OUTPUT_FULL -> localization.component("machine-status.output-full.lore");
            case BLOCKED_OUTPUT -> localization.component("machine-status.blocked-output.lore");
            case INVALID_INPUT -> localization.component("machine-status.invalid-input.lore");
            case FULL -> localization.component("machine-status.full.lore");
            case NO_NETWORK -> localization.component("machine-status.no-network.lore");
            case NETWORK_CONFLICT -> localization.component("machine-status.network-conflict.lore");
            case AREA_CONFLICT -> localization.component("machine-status.area-conflict.lore");
            case MISSING_RESOURCE -> localization.component("machine-status.missing-resource.lore");
            case CUSTOM -> Component.empty();
        };
    }
}
