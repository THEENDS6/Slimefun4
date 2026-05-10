package cc.theends6.sfx.internal.util;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;

public final class Text {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    public static Component mm(String input) {
        if (input == null || input.isBlank()) {
            return Component.empty();
        }
        return noItalic(MINI_MESSAGE.deserialize(input));
    }


    public static Component legacy(String input) {
        if (input == null || input.isBlank()) {
            return Component.empty();
        }
        return noItalic(LEGACY_AMPERSAND.deserialize(input));
    }

    public static List<Component> legacyLore(String... lines) {
        List<Component> result = new ArrayList<>();
        if (lines == null) {
            return result;
        }
        for (String line : lines) {
            result.add(legacy(line));
        }
        return result;
    }

    public static List<Component> lore(String... lines) {
        List<Component> result = new ArrayList<>();
        if (lines == null) {
            return result;
        }
        for (String line : lines) {
            result.add(mm(line));
        }
        return result;
    }

    public static Component prefixed(JavaPlugin plugin, String message) {
        String prefix = plugin.getConfig().getString("messages.prefix", "<dark_gray>[<green>SFX</green><dark_gray>] ");
        return mm(prefix + message);
    }

    public static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
