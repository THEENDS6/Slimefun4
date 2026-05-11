package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ManualMachineDeployPacks {
    private static final int SCHEMA = 1;

    private ManualMachineDeployPacks() {
    }

    public static ItemStack create(JavaPlugin plugin, ManualMachineDefinition definition, SfxLocalization localization) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(definition, "definition");
        if (!definition.deployable()) {
            throw new IllegalArgumentException("Manual machine deploy pack is not supported for " + definition.id());
        }
        ItemStack item = new ItemStack(definition.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String name = localization.text("machines.deploy-pack.name", "<gold>基础机器部署包</gold> <gray>- {machine}</gray>")
                .replace("{machine}", plain(localization.itemName(definition.id(), definition.name())));
        meta.displayName(Text.noItalic(Text.mm(name)));
        meta.lore(List.of(
                Text.noItalic(Text.mm(localization.text("machines.deploy-pack.lore.0", "<gray>放置后会自动部署完整基础机器。</gray>"))),
                Text.noItalic(Text.mm(localization.text("machines.deploy-pack.lore.1", "<gray>部署前会检查空间是否足够。</gray>"))),
                Text.noItalic(Text.mm(localization.text("machines.deploy-pack.lore.2", "<dark_gray>这是 SFX 的作弊辅助物品。</dark_gray>")))
        ));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "manual_machine_pack"), PersistentDataType.STRING, definition.id());
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "manual_machine_pack_schema"), PersistentDataType.INTEGER, SCHEMA);
        item.setItemMeta(meta);
        return item;
    }

    public static Optional<String> readMachineId(JavaPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        String raw = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "manual_machine_pack"), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(SfxItemDefinition.normalizeId(raw));
    }

    private static String plain(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }
}
