package cc.theends6.sfx.internal.feature;

import cc.theends6.sfx.api.feature.SfxFeature;
import cc.theends6.sfx.api.feature.SfxFeatureRegistry;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class DefaultSfxFeatureRegistry implements SfxFeatureRegistry {
    private final JavaPlugin plugin;
    private final Map<String, Entry> features = new LinkedHashMap<>();

    public DefaultSfxFeatureRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void clear() {
        features.clear();
    }

    public synchronized void registerBoolean(String addonId, String featureId, String configPath, boolean defaultEnabled,
                                             FileConfiguration addonConfig) {
        if (addonId == null || addonId.isBlank()) {
            throw new IllegalArgumentException("Feature addon id must not be blank.");
        }
        if (featureId == null || featureId.isBlank()) {
            throw new IllegalArgumentException("Feature id must not be blank.");
        }
        if (configPath == null || configPath.isBlank()) {
            throw new IllegalArgumentException("Feature config path must not be blank.");
        }
        Entry previous = features.putIfAbsent(featureId, new Entry(addonId, featureId, configPath, defaultEnabled, addonConfig));
        if (previous != null) {
            throw new IllegalStateException("Duplicate SFX feature id: " + featureId);
        }
    }

    @Override
    public synchronized Optional<SfxFeature> feature(String id) {
        Entry entry = features.get(id);
        return entry == null ? Optional.empty() : Optional.of(entry.toFeature(plugin));
    }

    @Override
    public synchronized Collection<SfxFeature> features() {
        return features.values().stream().map(entry -> entry.toFeature(plugin)).toList();
    }

    @Override
    public boolean enabled(String id) {
        Entry entry;
        synchronized (this) {
            entry = features.get(id);
        }
        if (entry == null) {
            return false;
        }
        return entry.config().getBoolean(entry.configPath(), entry.defaultEnabled());
    }

    private record Entry(String addonId, String id, String configPath, boolean defaultEnabled, FileConfiguration config) {
        SfxFeature toFeature(JavaPlugin plugin) {
            return new SfxFeature(id, addonId, configPath, defaultEnabled, config.getBoolean(configPath, defaultEnabled));
        }
    }
}
