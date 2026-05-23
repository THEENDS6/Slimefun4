package cc.theends6.sfx.internal.android;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class SfxAndroidMenuHolder implements InventoryHolder {
    enum MenuType {
        MAIN,
        EDITOR,
        SCRIPT,
        INSTRUCTIONS,
        DOWNLOADER,
        UPLOAD_VISIBILITY
    }

    private final UUID viewerId;
    private final UUID instanceId;
    private final MenuType menuType;
    private final int page;
    private final int editIndex;
    private final boolean adding;
    private Inventory inventory;

    SfxAndroidMenuHolder(UUID viewerId, UUID instanceId, MenuType menuType, int page, int editIndex, boolean adding) {
        this.viewerId = viewerId;
        this.instanceId = instanceId;
        this.menuType = menuType;
        this.page = page;
        this.editIndex = editIndex;
        this.adding = adding;
    }

    UUID viewerId() {
        return viewerId;
    }

    UUID instanceId() {
        return instanceId;
    }

    MenuType menuType() {
        return menuType;
    }

    int page() {
        return page;
    }

    int editIndex() {
        return editIndex;
    }

    boolean adding() {
        return adding;
    }

    void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
