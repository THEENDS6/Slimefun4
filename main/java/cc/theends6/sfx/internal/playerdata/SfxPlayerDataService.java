package cc.theends6.sfx.internal.playerdata;

import cc.theends6.sfx.api.runtime.SfxRuntime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxPlayerDataService {
    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxPlayerDataRepository repository;
    private final Map<UUID, CompletableFuture<SfxPlayerProfile>> profiles = new ConcurrentHashMap<>();

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
        future.whenComplete((profile, throwable) -> runtime.executeForPlayer(player, () -> {
            if (throwable != null) {
                plugin.getLogger().warning("Failed to load SFX profile for " + player.getName() + ": " + throwable.getMessage());
                return;
            }
            callback.accept(profile);
        }));
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
        runtime.executeAsync(() -> {
            try {
                repository.save(profile);
            } catch (Exception exception) {
                plugin.getLogger().warning("Failed to save SFX profile for " + profile.ownerId() + ": " + exception.getMessage());
            }
        });
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
            try {
                repository.save(profile);
            } catch (Exception exception) {
                plugin.getLogger().warning("Failed to save SFX profile for " + uuid + ": " + exception.getMessage());
            }
        });
    }

    public void shutdown() {
        for (Map.Entry<UUID, CompletableFuture<SfxPlayerProfile>> entry : profiles.entrySet()) {
            CompletableFuture<SfxPlayerProfile> future = entry.getValue();
            if (!future.isDone() || future.isCompletedExceptionally()) {
                continue;
            }
            SfxPlayerProfile profile = future.getNow(null);
            if (profile == null) {
                continue;
            }
            try {
                repository.save(profile);
            } catch (Exception exception) {
                plugin.getLogger().warning("Failed to save SFX profile for " + profile.ownerId() + ": " + exception.getMessage());
            }
        }
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
}
