package cc.theends6.sfx.internal.util;

import cc.theends6.sfx.api.item.SfxItems;
import java.util.Objects;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class SfxInteractionRules {
    private static final String PLACEABLE_BLOCK_FLAG = "placeable-block";
    private static final Set<String> KNOWN_PLACEABLE_SFX_IDS = Set.of(
            "sf:energy_regulator",
            "sf:energy_connector",
            "sf:charging_bench",
            "sf:small_capacitor",
            "sf:medium_capacitor",
            "sf:big_capacitor",
            "sf:large_capacitor",
            "sf:carbonado_edged_capacitor",
            "sf:energized_capacitor",
            "sf:solar_generator",
            "sf:solar_generator_2",
            "sf:solar_generator_3",
            "sf:solar_generator_4",
            "sf:coal_generator",
            "sf:coal_generator_2",
            "sf:lava_generator",
            "sf:lava_generator_2",
            "sf:bio_reactor",
            "sf:combustion_reactor",
            "sf:magnesium_generator",
            "sf:ancient_altar",
            "sf:ancient_pedestal"
    );

    private SfxInteractionRules() {
    }

    public static boolean prefersBlockPlacement(SfxItems items, PlayerInteractEvent event) {
        Objects.requireNonNull(items, "items");
        if (event == null
                || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null) {
            return false;
        }
        if (!event.getPlayer().isSneaking()) {
            return false;
        }
        return isPlaceableHeldItem(items, event.getItem());
    }

    public static boolean isPlaceableHeldItem(SfxItems items, ItemStack item) {
        Objects.requireNonNull(items, "items");
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        if (items.isSfxItem(item)) {
            return items.readMarker(item)
                    .map(marker -> marker.flags().contains(PLACEABLE_BLOCK_FLAG) || KNOWN_PLACEABLE_SFX_IDS.contains(marker.itemId()))
                    .orElse(false);
        }
        return item.getType().isBlock();
    }
}
