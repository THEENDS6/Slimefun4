package cc.theends6.sfx.internal.playerdata;

import cc.theends6.sfx.api.runtime.SfxRuntime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxPlayerDataService {
    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxPlayerDataRepository repository;
    private final Map<UUID, CompletableFuture<SfxPlayerProfile>> profiles = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<Void>> pendingWrites = ConcurrentHashMap.newKeySet();
    private volatile boolean shuttingDown;

    public SfxPlayerDataService(JavaPlugin plugin, SfxRuntime runtime, SfxPlayerDataRepository repository) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.repository = repository;
    }

    public void initialize() throws Exception {
        repository.initialize();
    }

    public void request(Player player, Consumer<SfxPlayerProfile> callback) {
        UUID uuid = player.getUniqueId();
        CompletableFuture<SfxPlayerProfile> future = profiles.computeIfAbsent(uuid, ignored -> loadAsync(uuid, player.getName()));
        future.whenComplete((profile, throwable) -> runtime.executeForPlayer(player, () -> completeProfileCallback(player.getName(), callback, profile, throwable)));
    }

    public void request(OfflinePlayer player, Consumer<SfxPlayerProfile> callback) {
        request(player.getUniqueId(), player.getName() == null ? player.getUniqueId().toString() : player.getName(), callback);
    }

    public void request(UUID uuid, String lastKnownName, Consumer<SfxPlayerProfile> callback) {
        CompletableFuture<SfxPlayerProfile> future = profiles.computeIfAbsent(uuid, ignored -> loadAsync(uuid, lastKnownName));
        future.whenComplete((profile, throwable) -> runtime.executeGlobal(() -> completeProfileCallback(lastKnownName, callback, profile, throwable)));
    }

    public Optional<SfxPlayerProfile> find(UUID uuid) {
        CompletableFuture<SfxPlayerProfile> future = profiles.get(uuid);
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) {
            return Optional.empty();
        }
        return Optional.ofNullable(future.getNow(null));
    }

    public void saveAsync(SfxPlayerProfile profile) {
        if (profile == null || !profile.isDirty()) {
            return;
        }
        enqueueWrite(() -> saveNow(profile));
    }

    public void saveAndUnload(UUID uuid) {
        CompletableFuture<SfxPlayerProfile> future = profiles.remove(uuid);
        if (future == null) {
            return;
        }
        future.whenComplete((profile, throwable) -> {
            if (throwable != null || profile == null) {
                return;
            }
            enqueueWrite(() -> saveNow(profile));
        });
    }

    public void shutdown() {
        shuttingDown = true;
        awaitPendingWrites();
        for (Map.Entry<UUID, CompletableFuture<SfxPlayerProfile>> entry : profiles.entrySet()) {
            SfxPlayerProfile profile = profileOrNull(entry.getValue());
            if (profile == null) {
                continue;
            }
            saveNow(profile);
        }
        awaitPendingWrites();
        profiles.clear();
        repository.close();
    }

    private CompletableFuture<SfxPlayerProfile> loadAsync(UUID uuid, String name) {
        CompletableFuture<SfxPlayerProfile> future = new CompletableFuture<>();
        runtime.executeAsync(() -> {
            try {
                future.complete(repository.load(uuid, name));
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    private SfxPlayerProfile profileOrNull(CompletableFuture<SfxPlayerProfile> future) {
        try {
            return future.get(5L, TimeUnit.SECONDS);
        } catch (Exception exception) {
            plugin.getLogger().warning("Timed out while loading SFX profile during shutdown: " + exception.getMessage());
            return null;
        }
    }

    private void enqueueWrite(Runnable write) {
        if (shuttingDown) {
            write.run();
            return;
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        pendingWrites.add(future);
        runtime.executeAsync(() -> {
            try {
                write.run();
                future.complete(null);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
                plugin.getLogger().warning("Failed to execute SFX player data write: " + throwable.getMessage());
            } finally {
                pendingWrites.remove(future);
            }
        });
    }

    private void awaitPendingWrites() {
        CompletableFuture<?>[] futures = pendingWrites.toArray(CompletableFuture[]::new);
        if (futures.length == 0) {
            return;
        }
        try {
            CompletableFuture.allOf(futures).get(5L, TimeUnit.SECONDS);
        } catch (Exception exception) {
            plugin.getLogger().warning("Timed out while flushing SFX player data writes: " + exception.getMessage());
        }
    }

    private void saveNow(SfxPlayerProfile profile) {
        try {
            repository.save(profile);
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to save SFX profile for " + profile.ownerId() + ": " + exception.getMessage());
        }
    }

    private void completeProfileCallback(String name, Consumer<SfxPlayerProfile> callback, SfxPlayerProfile profile, Throwable throwable) {
        if (throwable != null) {
            plugin.getLogger().warning("Failed to load SFX profile for " + name + ": " + throwable.getMessage());
            return;
        }
        callback.accept(profile);
    }
}
