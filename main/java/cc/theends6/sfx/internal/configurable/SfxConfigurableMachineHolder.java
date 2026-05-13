package cc.theends6.sfx.internal.configurable;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class SfxConfigurableMachineHolder implements InventoryHolder {
    private final UUID panelInstanceId;
    private final UUID hostInstanceId;
    private final PanelType panelType;

    SfxConfigurableMachineHolder(UUID panelInstanceId, UUID hostInstanceId, PanelType panelType) {
        this.panelInstanceId = panelInstanceId;
        this.hostInstanceId = hostInstanceId;
        this.panelType = panelType;
    }

    UUID panelInstanceId() {
        return panelInstanceId;
    }

    UUID hostInstanceId() {
        return hostInstanceId;
    }

    PanelType panelType() {
        return panelType;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    enum PanelType {
        ASSEMBLER,
        REACTOR,
        ACCESS_PORT
    }
}
