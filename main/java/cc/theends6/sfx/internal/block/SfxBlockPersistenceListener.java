package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.runtime.SfxRuntime;
import java.io.File;
import java.util.Objects;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxBlockPersistenceListener implements Listener {
    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxBlockDataService blockData;
    private final long autosaveIntervalTicks;
    private volatile boolean running = true;

    public SfxBlockPersistenceListener(JavaPlugin plugin, SfxRuntime runtime, SfxBlockDataService blockData) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.autosaveIntervalTicks = resolveAutosaveInterval(plugin);
        scheduleAutosaveFlush();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldSave(WorldSaveEvent event) {
        blockData.flushNow();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        blockData.reconcileChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        blockData.reconcileChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
        blockData.flushChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    public void shutdown() {
        running = false;
        blockData.flushNow();
    }

    private void scheduleAutosaveFlush() {
        if (!running || autosaveIntervalTicks <= 0L) {
            return;
        }
        runtime.executeGlobalLater(autosaveIntervalTicks, () -> {
            if (!running) {
                return;
            }
            blockData.flushNow();
            scheduleAutosaveFlush();
        });
    }

    private long resolveAutosaveInterval(JavaPlugin plugin) {
        long configured = plugin.getConfig().getLong("storage.block-data.autosave-interval-ticks", -1L);
        if (configured > 0L) {
            return configured;
        }
        File bukkitConfigFile = new File("bukkit.yml");
        if (bukkitConfigFile.isFile()) {
            int bukkitAutosave = YamlConfiguration.loadConfiguration(bukkitConfigFile).getInt("ticks-per.autosave", 6000);
            if (bukkitAutosave > 0) {
                return bukkitAutosave;
            }
        }
        return 6000L;
    }
}
