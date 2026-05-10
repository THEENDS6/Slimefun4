package cc.theends6.sfx.internal.listener;

import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxDebugJoinListener implements Listener {
    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxLocalization localization;
    private final List<String> testingLines;

    public SfxDebugJoinListener(JavaPlugin plugin, SfxRuntime runtime, SfxLocalization localization) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.testingLines = loadTestingLines(plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("debug-text.enabled", true)) {
            return;
        }
        Player player = event.getPlayer();
        plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> runtime.executeForPlayer(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.sendMessage(Text.mm(localization.text("debug.header", "<dark_gray>====================</dark_gray> <green>SFX DEBUG TEXT</green> <dark_gray>====================</dark_gray>")));
            for (String line : testingLines) {
                if (line.isBlank()) {
                    player.sendMessage(Text.mm("<gray> </gray>"));
                } else {
                    player.sendMessage(Text.mm("<gray>" + escape(line) + "</gray>"));
                }
            }
            player.sendMessage(Text.mm(localization.text("debug.footer", "<dark_gray>====================</dark_gray> <green>END</green> <dark_gray>====================</dark_gray>")));
        }), 20L);
    }

    private List<String> loadTestingLines(JavaPlugin plugin) {
        try (InputStream stream = plugin.getResource("TESTING.md")) {
            if (stream == null) {
                return List.of(localization.text("debug.missing-testing", "TESTING.md 未被打包进插件。"));
            }
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            return lines;
        } catch (IOException ex) {
            return List.of(localization.text("debug.failed-testing", "读取 TESTING.md 失败：{error}").replace("{error}", ex.getMessage()));
        }
    }

    private String escape(String text) {
        return text.replace("\\", "\\\\").replace("<", "\\<");
    }
}
