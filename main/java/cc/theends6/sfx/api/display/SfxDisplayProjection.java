package cc.theends6.sfx.api.display;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.data.BlockData;


public record SfxDisplayProjection(UUID id, String typeId, Location location, SfxDisplayKind kind,
                                   ItemStack item, BlockData blockData, SfxDisplayTransform transform) {
    public SfxDisplayProjection(UUID id, String typeId, Location location, ItemStack item) {
        this(id, typeId, location, SfxDisplayKind.ITEM, item, null, SfxDisplayTransform.IDENTITY);
    }

    public static SfxDisplayProjection item(UUID id, String typeId, Location location, ItemStack item,
                                            SfxDisplayTransform transform) {
        return new SfxDisplayProjection(id, typeId, location, SfxDisplayKind.ITEM, item, null, transform);
    }

    public static SfxDisplayProjection block(UUID id, String typeId, Location location, BlockData blockData,
                                             SfxDisplayTransform transform) {
        return new SfxDisplayProjection(id, typeId, location, SfxDisplayKind.BLOCK, null, blockData, transform);
    }

    public SfxDisplayProjection {
        Objects.requireNonNull(id, "id");
        if (typeId == null || typeId.isBlank()) throw new IllegalArgumentException("typeId must not be blank");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(location.getWorld(), "location.world");
        Objects.requireNonNull(kind, "kind");
        transform = transform == null ? SfxDisplayTransform.IDENTITY : transform;
        if (kind == SfxDisplayKind.ITEM && (item == null || item.getType().isAir())) {
            throw new IllegalArgumentException("item display payload must not be empty");
        }
        if (kind == SfxDisplayKind.BLOCK && blockData == null) {
            throw new IllegalArgumentException("block display payload must not be empty");
        }
        location = location.clone();
        item = item == null ? null : item.clone();
        blockData = blockData == null ? null : blockData.clone();
    }

    @Override public Location location() { return location.clone(); }
    @Override public ItemStack item() { return item == null ? null : item.clone(); }
    @Override public BlockData blockData() { return blockData == null ? null : blockData.clone(); }
}
