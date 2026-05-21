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
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
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
        markScanned(key, null);
    }

    synchronized void markScanned(SfxGeoChunkKey key, Location scanLocation) {
        scannedChunks.put(key.pathKey(), Instant.now().toEpochMilli());
        geoResources.computeIfAbsent(key.pathKey(), ignored -> generateResources(key, scanLocation));
        save();
    }

    synchronized Map<SfxGeoResourceType, Integer> resources(SfxGeoChunkKey key) {
        return resources(key, null);
    }

    synchronized Map<SfxGeoResourceType, Integer> resources(SfxGeoChunkKey key, Location location) {
        EnumMap<SfxGeoResourceType, Integer> values = geoResources.computeIfAbsent(key.pathKey(), ignored -> generateResources(key, location));
        normalizeResources(values, location);
        return new HashMap<>(values);
    }

    synchronized boolean consume(SfxGeoChunkKey key, SfxGeoResourceType type, int amount) {
        return consume(key, type, amount, null);
    }

    synchronized boolean consume(SfxGeoChunkKey key, SfxGeoResourceType type, int amount, Location location) {
        EnumMap<SfxGeoResourceType, Integer> values = geoResources.computeIfAbsent(key.pathKey(), ignored -> generateResources(key, location));
        normalizeResources(values, location);
        int current = values.getOrDefault(type, 0);
        if (current < amount) {
            return false;
        }
        values.put(type, current - amount);
        save();
        return true;
    }

    private void normalizeResources(EnumMap<SfxGeoResourceType, Integer> values, Location location) {
        World world = location == null ? null : location.getWorld();
        if (world == null) {
            return;
        }
        World.Environment environment = world.getEnvironment();
        if (environment != World.Environment.NORMAL) {
            values.put(SfxGeoResourceType.OIL, 0);
            values.put(SfxGeoResourceType.URANIUM, 0);
        }
        if (environment != World.Environment.NETHER) {
            values.put(SfxGeoResourceType.NETHER_ICE, 0);
        }
        if (environment != World.Environment.NORMAL && environment != World.Environment.NETHER) {
            values.put(SfxGeoResourceType.SALT, 0);
        }
    }

    private EnumMap<SfxGeoResourceType, Integer> generateResources(SfxGeoChunkKey key) {
        return generateResources(key, null);
    }

    private EnumMap<SfxGeoResourceType, Integer> generateResources(SfxGeoChunkKey key, Location location) {
        EnumMap<SfxGeoResourceType, Integer> values = new EnumMap<>(SfxGeoResourceType.class);
        World world = location == null ? null : location.getWorld();
        if (world == null) {
            for (SfxGeoResourceType type : SfxGeoResourceType.values()) {
                values.put(type, 0);
            }
            return values;
        }
        int sampleY = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 1, location.getBlockY()));
        Biome biome = world.getBlockAt(key.chunkX() << 4, sampleY, key.chunkZ() << 4).getBiome();
        for (SfxGeoResourceType type : SfxGeoResourceType.values()) {
            int base = defaultSupply(type, world.getEnvironment(), biome);
            int amount = base <= 0 ? 0 : base + ThreadLocalRandom.current().nextInt(maxDeviation(type));
            values.put(type, Math.max(0, amount));
        }
        return values;
    }

    private int defaultSupply(SfxGeoResourceType type, World.Environment environment, Biome biome) {
        return switch (type) {
            case OIL -> environment == World.Environment.NORMAL ? oilSupply(biome) : 0;
            case SALT -> {
                if (environment == World.Environment.NORMAL) {
                    yield saltSupply(biome, 6);
                }
                if (environment == World.Environment.NETHER) {
                    yield saltSupply(biome, 8);
                }
                yield 0;
            }
            case URANIUM -> environment == World.Environment.NORMAL ? uraniumSupply(biome) : 0;
            case NETHER_ICE -> environment == World.Environment.NETHER ? netherIceSupply(biome) : 0;
        };
    }

    private int maxDeviation(SfxGeoResourceType type) {
        return switch (type) {
            case OIL -> 8;
            case SALT -> 18;
            case URANIUM -> 2;
            case NETHER_ICE -> 6;
        };
    }

    private int oilSupply(Biome biome) {
        return switch (biome) {
            case BEACH, STONY_SHORE -> 6;
            case RIVER -> 16;
            case SWAMP -> 20;
            case ICE_SPIKES, FROZEN_OCEAN, FROZEN_RIVER, FROZEN_PEAKS, SNOWY_SLOPES -> 24;
            case BADLANDS, WOODED_BADLANDS, ERODED_BADLANDS -> 40;
            case DESERT -> 45;
            case OCEAN, COLD_OCEAN, WARM_OCEAN, LUKEWARM_OCEAN -> 64;
            case DEEP_OCEAN, DEEP_COLD_OCEAN, DEEP_LUKEWARM_OCEAN -> 72;
            case WINDSWEPT_HILLS, WINDSWEPT_GRAVELLY_HILLS, JAGGED_PEAKS -> 20;
            case SNOWY_PLAINS, SNOWY_TAIGA -> 16;
            case MUSHROOM_FIELDS -> 20;
            default -> 10;
        };
    }

    private int saltSupply(Biome biome, int fallback) {
        return switch (biome) {
            case SWAMP -> 20;
            case BEACH, WINDSWEPT_GRAVELLY_HILLS, STONY_SHORE, STONY_PEAKS, DRIPSTONE_CAVES -> 40;
            case OCEAN, COLD_OCEAN, WARM_OCEAN, LUKEWARM_OCEAN, DEEP_OCEAN, DEEP_COLD_OCEAN, DEEP_LUKEWARM_OCEAN -> 60;
            default -> fallback;
        };
    }

    private int uraniumSupply(Biome biome) {
        return switch (biome) {
            case DESERT, BEACH, STONY_SHORE -> 5;
            case JAGGED_PEAKS, STONY_PEAKS, WINDSWEPT_HILLS, WINDSWEPT_GRAVELLY_HILLS -> 8;
            case BADLANDS, ERODED_BADLANDS, WOODED_BADLANDS, DRIPSTONE_CAVES -> 12;
            case BASALT_DELTAS -> 16;
            default -> 4;
        };
    }

    private int netherIceSupply(Biome biome) {
        return switch (biome) {
            case NETHER_WASTES, SOUL_SAND_VALLEY -> 32;
            case CRIMSON_FOREST, WARPED_FOREST -> 48;
            case BASALT_DELTAS -> 64;
            default -> 32;
        };
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
