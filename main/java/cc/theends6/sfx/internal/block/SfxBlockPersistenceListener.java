package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.persistence.SfxDirtyPersistenceService;
import cc.theends6.sfx.internal.persistence.SfxFlushCoordinator;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.Chunk;
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
    private final List<SfxDirtyPersistenceService> dirtyServices;
    private final SfxFlushCoordinator flushCoordinator;
    private final long autosaveIntervalTicks;
    private volatile boolean running;

    public SfxBlockPersistenceListener(JavaPlugin plugin, SfxRuntime runtime, SfxBlockDataService blockData, SfxDirtyPersistenceService... dirtyServices) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.dirtyServices = normalizeServices(blockData, dirtyServices);
        this.flushCoordinator = new SfxFlushCoordinator(plugin.getLogger());
        for (SfxDirtyPersistenceService service : this.dirtyServices) {
            this.flushCoordinator.register(service);
        }
        this.autosaveIntervalTicks = resolveAutosaveInterval(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldSave(WorldSaveEvent event) {
        flushAllDirtyBlocking();
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
        for (SfxDirtyPersistenceService service : dirtyServices) {
            service.requestChunkFlushAsync(chunk.getWorld(), chunk.getX(), chunk.getZ());
        }
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        scheduleAutosaveFlush();
    }

    public void shutdown() {
        running = false;
    }

    private void scheduleAutosaveFlush() {
        if (!running || autosaveIntervalTicks <= 0L) {
            return;
        }
        runtime.executeGlobalLater(autosaveIntervalTicks, () -> {
            if (!running) {
                return;
            }
            requestAllDirtyFlushes();
            scheduleAutosaveFlush();
        });
    }

    private void requestAllDirtyFlushes() {
        flushCoordinator.requestAsyncFlushAll();
    }

    private void flushAllDirtyBlocking() {
        flushCoordinator.flushAllBlocking();
    }

    private List<SfxDirtyPersistenceService> normalizeServices(SfxBlockDataService blockData, SfxDirtyPersistenceService[] services) {
        List<SfxDirtyPersistenceService> normalized = new ArrayList<>();
        normalized.add(blockData);
        if (services != null) {
            for (SfxDirtyPersistenceService service : services) {
                if (service != null && !normalized.contains(service)) {
                    normalized.add(service);
                }
            }
        }
        return List.copyOf(normalized);
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
