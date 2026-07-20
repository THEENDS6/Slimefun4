package cc.theends6.sfx.api.display;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;


public record SfxDisplayProjection(UUID id, String typeId, Location location, ItemStack item) {
    public SfxDisplayProjection {
        Objects.requireNonNull(id, "id");
        if (typeId == null || typeId.isBlank()) throw new IllegalArgumentException("typeId must not be blank");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(location.getWorld(), "location.world");
        Objects.requireNonNull(item, "item");
        if (item.getType().isAir()) throw new IllegalArgumentException("display item must not be air");
        location = location.clone();
        item = item.clone();
    }

    @Override public Location location() { return location.clone(); }
    @Override public ItemStack item() { return item.clone(); }
}
