package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.util.SfxInteractionRules;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class SfxManualMachineListener implements Listener {
    private final ManualMachineService service;
    private final SfxItems items;

    public SfxManualMachineListener(ManualMachineService service, SfxItems items) {
        this.service = Objects.requireNonNull(service, "service");
        this.items = Objects.requireNonNull(items, "items");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        if (SfxInteractionRules.prefersBlockPlacement(items, event)
                && !service.isHandInputMachine(event.getClickedBlock())) {
            return;
        }
        if (service.tryInteract(event.getPlayer(), event.getClickedBlock())) {
            event.setCancelled(true);
        }
    }
}
