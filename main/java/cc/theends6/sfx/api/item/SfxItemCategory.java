package cc.theends6.sfx.api.item;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

public record SfxItemCategory(String id, Component name, ItemStack icon, int order, boolean hidden, String permission) {
    public SfxItemCategory(String id, Component name, ItemStack icon, int order, boolean hidden) {
        this(id, name, icon, order, hidden, null);
    }

    public SfxItemCategory {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(icon, "icon");
        id = normalizeId(id);
        icon = icon.clone();
        permission = normalizePermission(permission);
    }

    private static String normalizePermission(String permission) {
        return permission == null || permission.isBlank() ? null : permission.trim().toLowerCase();
    }

    public ItemStack icon() {
        return icon.clone();
    }

    public static String normalizeId(String id) {
        String normalized = Objects.requireNonNull(id, "id").trim().toLowerCase();
        if (!normalized.matches("[a-z0-9_./:-]+")) {
            throw new IllegalArgumentException("Invalid SFX category id: " + id);
        }
        return normalized;
    }
}
