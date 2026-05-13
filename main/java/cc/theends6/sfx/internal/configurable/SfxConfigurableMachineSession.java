package cc.theends6.sfx.internal.configurable;

import java.util.UUID;
import org.bukkit.inventory.Inventory;

record SfxConfigurableMachineSession(
        UUID viewerId,
        UUID panelInstanceId,
        UUID hostInstanceId,
        SfxConfigurableMachineHolder.PanelType panelType,
        Inventory inventory
) {
}
