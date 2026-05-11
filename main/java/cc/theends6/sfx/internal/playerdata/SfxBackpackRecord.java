package cc.theends6.sfx.internal.playerdata;

import org.bukkit.inventory.ItemStack;

public final class SfxBackpackRecord {
    private final int id;
    private int size;
    private long updatedAt;
    private ItemStack[] contents;

    public SfxBackpackRecord(int id, int size, ItemStack[] contents) {
        this(id, size, contents, System.currentTimeMillis());
    }

    public SfxBackpackRecord(int id, int size, ItemStack[] contents, long updatedAt) {
        this.id = id;
        this.size = size;
        this.updatedAt = updatedAt;
        this.contents = copy(contents, size);
    }

    public int id() {
        return id;
    }

    public int size() {
        return size;
    }

    public synchronized long updatedAt() {
        return updatedAt;
    }

    public synchronized ItemStack[] contentsCopy() {
        return copy(contents, size);
    }

    public synchronized void setContents(ItemStack[] updated) {
        this.contents = copy(updated, size);
        this.updatedAt = System.currentTimeMillis();
    }

    public synchronized void setSize(int size) {
        this.size = Math.max(9, size);
        this.contents = copy(contents, this.size);
        this.updatedAt = System.currentTimeMillis();
    }

    public synchronized void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    private static ItemStack[] copy(ItemStack[] source, int size) {
        ItemStack[] copy = new ItemStack[size];
        if (source == null) {
            return copy;
        }
        for (int i = 0; i < Math.min(size, source.length); i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }
}
