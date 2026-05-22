package cc.theends6.sfx.internal.virtualcontainer;

import java.util.Arrays;
import org.bukkit.inventory.ItemStack;

public final class SfxVirtualContainer {
    private final SfxVirtualContainerKey key;
    private ItemStack[] mirror;
    private boolean cargoAttached;
    private boolean viewerActive;
    private boolean externalActive;
    private boolean externalDirty;
    private boolean mirrorDirty;
    private long revision;
    private long lastWorldSyncTick;
    private boolean externalFinalizationPending;

    SfxVirtualContainer(SfxVirtualContainerKey key, int size) {
        this.key = key;
        this.mirror = new ItemStack[Math.max(1, size)];
    }

    public SfxVirtualContainerKey key() {
        return key;
    }

    public ItemStack[] snapshot() {
        return cloneContents(mirror);
    }

    public ItemStack get(int slot) {
        if (slot < 0 || slot >= mirror.length) {
            return null;
        }
        ItemStack stack = mirror[slot];
        return stack == null ? null : stack.clone();
    }

    public void setContents(ItemStack[] contents) {
        int size = contents == null ? 0 : contents.length;
        if (size <= 0) {
            return;
        }
        this.mirror = cloneContents(contents);
        this.revision++;
        this.mirrorDirty = true;
    }

    public ItemStack[] rawMirror() {
        return mirror;
    }

    public int size() {
        return mirror.length;
    }

    public boolean cargoAttached() {
        return cargoAttached;
    }

    public void cargoAttached(boolean cargoAttached) {
        this.cargoAttached = cargoAttached;
    }

    public boolean viewerActive() {
        return viewerActive;
    }

    public void viewerActive(boolean viewerActive) {
        this.viewerActive = viewerActive;
    }

    public boolean externalActive() {
        return externalActive;
    }

    public void externalActive(boolean externalActive) {
        this.externalActive = externalActive;
    }

    public boolean externalDirty() {
        return externalDirty;
    }

    public void externalDirty(boolean externalDirty) {
        this.externalDirty = externalDirty;
    }

    public boolean mirrorDirty() {
        return mirrorDirty;
    }

    public void mirrorDirty(boolean mirrorDirty) {
        this.mirrorDirty = mirrorDirty;
        if (mirrorDirty) {
            revision++;
        }
    }

    public long revision() {
        return revision;
    }

    public long lastWorldSyncTick() {
        return lastWorldSyncTick;
    }

    public void lastWorldSyncTick(long lastWorldSyncTick) {
        this.lastWorldSyncTick = lastWorldSyncTick;
    }

    public boolean externalFinalizationPending() {
        return externalFinalizationPending;
    }

    public void externalFinalizationPending(boolean externalFinalizationPending) {
        this.externalFinalizationPending = externalFinalizationPending;
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = Arrays.copyOf(contents, contents.length);
        for (int i = 0; i < copy.length; i++) {
            copy[i] = copy[i] == null ? null : copy[i].clone();
        }
        return copy;
    }
}
