package cc.theends6.sfx.internal.playerdata;

import org.bukkit.inventory.ItemStack;

public final class SfxBackpackRecord {
    private final int id;
    private final int size;
    private ItemStack[] contents;

    public SfxBackpackRecord(int id, int size, ItemStack[] contents) {
        this.id = id;
        this.size = size;
        this.contents = copy(contents, size);
    }

    public int id() {
        return id;
    }

    public int size() {
        return size;
    }

    public synchronized ItemStack[] contentsCopy() {
        return copy(contents, size);
    }

    public synchronized void setContents(ItemStack[] updated) {
        this.contents = copy(updated, size);
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
