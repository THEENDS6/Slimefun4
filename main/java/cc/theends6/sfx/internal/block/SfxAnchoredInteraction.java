package cc.theends6.sfx.internal.block;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public record SfxAnchoredInteraction(
        Player player,
        Block block,
        SfxAnchorRecord anchor,
        SfxBlockInstanceRecord instance
) {
    public static SfxAnchoredInteraction resolve(PlayerInteractEvent event, SfxBlockDataService blockData) {
        if (event == null
                || blockData == null
                || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND) {
            return null;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return null;
        }
        SfxAnchorRecord anchor = blockData.findAnchor(clicked.getLocation()).orElse(null);
        if (anchor == null) {
            return null;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
        if (instance == null) {
            denyOrphanAnchorInteraction(event);
            return null;
        }
        return new SfxAnchoredInteraction(event.getPlayer(), clicked, anchor, instance);
    }

    private static void denyOrphanAnchorInteraction(PlayerInteractEvent event) {
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
    }
}
