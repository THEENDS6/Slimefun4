package cc.theends6.sfx.internal.gps;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

final class SfxGpsDataStore {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, List<SfxGpsWaypoint>> waypoints = new ConcurrentHashMap<>();
    private final Map<String, EnumMap<SfxGeoResourceType, Integer>> geoResources = new ConcurrentHashMap<>();
    private final Map<String, Long> scannedChunks = new ConcurrentHashMap<>();

    SfxGpsDataStore(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.file = new File(plugin.getDataFolder(), "gps.yml");
    }

    synchronized void load() {
        waypoints.clear();
        geoResources.clear();
        scannedChunks.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection waypointRoot = yaml.getConfigurationSection("waypoints");
        if (waypointRoot != null) {
            for (String ownerKey : waypointRoot.getKeys(false)) {
                UUID owner;
                try {
                    owner = UUID.fromString(ownerKey);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                List<SfxGpsWaypoint> list = new ArrayList<>();
                ConfigurationSection ownerSection = waypointRoot.getConfigurationSection(ownerKey);
                if (ownerSection == null) {
                    continue;
                }
                for (String index : ownerSection.getKeys(false)) {
                    ConfigurationSection section = ownerSection.getConfigurationSection(index);
                    if (section == null) {
                        continue;
                    }
                    try {
                        UUID worldId = UUID.fromString(section.getString("worldId", ""));
                        list.add(new SfxGpsWaypoint(
                                owner,
                                section.getString("name", "Waypoint"),
                                worldId,
                                section.getString("worldName", "world"),
                                section.getDouble("x"),
                                section.getDouble("y"),
                                section.getDouble("z"),
                                (float) section.getDouble("yaw"),
                                (float) section.getDouble("pitch"),
                                section.getLong("createdAt", Instant.now().toEpochMilli())
                        ));
                    } catch (RuntimeException ignored) {
                        // Skip malformed persisted waypoints.
                    }
                }
                waypoints.put(owner, list);
            }
        }
        ConfigurationSection scanned = yaml.getConfigurationSection("scannedChunks");
        if (scanned != null) {
            for (String key : scanned.getKeys(false)) {
                scannedChunks.put(key, scanned.getLong(key));
            }
        }
        ConfigurationSection resources = yaml.getConfigurationSection("geoResources");
        if (resources != null) {
            for (String key : resources.getKeys(false)) {
                ConfigurationSection section = resources.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                EnumMap<SfxGeoResourceType, Integer> values = new EnumMap<>(SfxGeoResourceType.class);
                for (SfxGeoResourceType type : SfxGeoResourceType.values()) {
                    values.put(type, Math.max(0, section.getInt(type.name().toLowerCase(), 0)));
                }
                geoResources.put(key, values);
            }
        }
    }

    synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, List<SfxGpsWaypoint>> entry : waypoints.entrySet()) {
            String root = "waypoints." + entry.getKey();
            int index = 0;
            for (SfxGpsWaypoint waypoint : entry.getValue()) {
                String path = root + "." + index++;
                yaml.set(path + ".name", waypoint.name());
                yaml.set(path + ".worldId", waypoint.worldId().toString());
                yaml.set(path + ".worldName", waypoint.worldName());
                yaml.set(path + ".x", waypoint.x());
                yaml.set(path + ".y", waypoint.y());
                yaml.set(path + ".z", waypoint.z());
                yaml.set(path + ".yaw", waypoint.yaw());
                yaml.set(path + ".pitch", waypoint.pitch());
                yaml.set(path + ".createdAt", waypoint.createdAt());
            }
        }
        for (Map.Entry<String, Long> entry : scannedChunks.entrySet()) {
            yaml.set("scannedChunks." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, EnumMap<SfxGeoResourceType, Integer>> entry : geoResources.entrySet()) {
            for (Map.Entry<SfxGeoResourceType, Integer> value : entry.getValue().entrySet()) {
                yaml.set("geoResources." + entry.getKey() + "." + value.getKey().name().toLowerCase(), value.getValue());
            }
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save GPS data: " + exception.getMessage());
        }
    }

    synchronized List<SfxGpsWaypoint> waypoints(UUID owner) {
        return List.copyOf(waypoints.getOrDefault(owner, List.of()));
    }

    synchronized void addWaypoint(SfxGpsWaypoint waypoint) {
        waypoints.computeIfAbsent(waypoint.ownerId(), ignored -> new ArrayList<>()).add(waypoint);
        save();
    }

    synchronized void removeWaypoint(UUID owner, String name) {
        List<SfxGpsWaypoint> list = waypoints.get(owner);
        if (list == null) {
            return;
        }
        list.removeIf(waypoint -> waypoint.name().equalsIgnoreCase(name));
        save();
    }

    synchronized boolean isScanned(SfxGeoChunkKey key) {
        return scannedChunks.containsKey(key.pathKey());
    }

    synchronized void markScanned(SfxGeoChunkKey key) {
        scannedChunks.put(key.pathKey(), Instant.now().toEpochMilli());
        geoResources.computeIfAbsent(key.pathKey(), ignored -> generateResources(key));
        save();
    }

    synchronized Map<SfxGeoResourceType, Integer> resources(SfxGeoChunkKey key) {
        return new HashMap<>(geoResources.computeIfAbsent(key.pathKey(), ignored -> generateResources(key)));
    }

    synchronized boolean consume(SfxGeoChunkKey key, SfxGeoResourceType type, int amount) {
        EnumMap<SfxGeoResourceType, Integer> values = geoResources.computeIfAbsent(key.pathKey(), ignored -> generateResources(key));
        int current = values.getOrDefault(type, 0);
        if (current < amount) {
            return false;
        }
        values.put(type, current - amount);
        save();
        return true;
    }

    private EnumMap<SfxGeoResourceType, Integer> generateResources(SfxGeoChunkKey key) {
        EnumMap<SfxGeoResourceType, Integer> values = new EnumMap<>(SfxGeoResourceType.class);
        for (SfxGeoResourceType type : SfxGeoResourceType.values()) {
            int hash = Objects.hash(key.worldId(), key.chunkX(), key.chunkZ(), type.salt());
            int normalized = Math.floorMod(hash, 1000);
            int amount = switch (type) {
                case OIL -> 40 + normalized % 180;
                case SALT -> normalized % 96;
                case URANIUM -> normalized % 24;
                case NETHER_ICE -> normalized % 36;
            };
            values.put(type, amount);
        }
        return values;
    }

    static SfxGpsWaypoint waypoint(UUID owner, String name, Location location) {
        return new SfxGpsWaypoint(
                owner,
                name,
                location.getWorld().getUID(),
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                Instant.now().toEpochMilli()
        );
    }
}
