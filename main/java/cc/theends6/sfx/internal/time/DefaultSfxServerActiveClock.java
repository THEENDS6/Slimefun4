package cc.theends6.sfx.internal.time;

import cc.theends6.sfx.api.time.SfxServerActiveClock;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.plugin.java.JavaPlugin;


public final class DefaultSfxServerActiveClock implements SfxServerActiveClock, AutoCloseable {
    private static final long FLUSH_INTERVAL_TICKS = 1200L;
    private final JavaPlugin plugin;
    private final Path file;
    private final AtomicLong activeTicks = new AtomicLong();
    private volatile ScheduledTask task;

    public DefaultSfxServerActiveClock(JavaPlugin plugin, Path file) {
        this.plugin = plugin;
        this.file = file;
        load();
    }

    public synchronized void start() {
        if (task != null) return;
        task = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, ignored -> {
            long tick = activeTicks.incrementAndGet();
            if (tick % FLUSH_INTERVAL_TICKS == 0L) flush();
        }, 1L, 1L);
    }

    @Override public long activeTicks() { return activeTicks.get(); }

    public synchronized void flush() {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, Long.toString(activeTicks.get()), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to persist server active ticks: " + exception.getMessage());
        }
    }

    private void load() {
        try {
            if (!Files.isRegularFile(file)) return;
            activeTicks.set(Math.max(0L, Long.parseLong(Files.readString(file, StandardCharsets.UTF_8).trim())));
        } catch (IOException | NumberFormatException exception) {
            plugin.getLogger().warning("Failed to load server active ticks; starting from zero: " + exception.getMessage());
        }
    }

    @Override public synchronized void close() {
        ScheduledTask current = task;
        task = null;
        if (current != null) current.cancel();
        flush();
    }
}
