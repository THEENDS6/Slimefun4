package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.block.SfxBlockLifecycleState;

import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.machine.*;
import cc.theends6.sfx.internal.util.SfxBlockDrops;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.api.text.Text;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxSpawnerService implements SfxProgrammaticBlockPlacement {
    public static final String REINFORCED_SPAWNER = "sf:reinforced_spawner";

    private final JavaPlugin plugin;
    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxBlockDataService blockData;
    private final SfxMachineRuntimeEngine machineRuntime;
    private final NamespacedKey spawnerTypeKey;

    public SfxSpawnerService(JavaPlugin plugin, SfxItems items, SfxLocalization localization, SfxBlockDataService blockData, SfxMachineRuntimeEngine machineRuntime) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.machineRuntime = machineRuntime == null ? new SfxMachineRuntimeEngine() : machineRuntime;
        this.spawnerTypeKey = new NamespacedKey(plugin, "spawner_type");
        registerFrameworkDefinitions();
    }

    private void registerFrameworkDefinitions() {
        machineRuntime.registerDefinitionIfAbsent(SfxMachineDefinition.builder(REINFORCED_SPAWNER)
                .displayName(REINFORCED_SPAWNER)
                .category(SfxMachineCategory.SPECIAL)
                .effect(SfxMachineEffect.marker("spawner:restore-entity-type", SfxMachinePhase.ON_PLACE))
                .effect(SfxMachineEffect.marker("spawner:drop-fractured-item", SfxMachinePhase.ON_BREAK))
                .effect(SfxMachineEffect.marker("spawner:repair-to-reinforced", SfxMachinePhase.ON_COMPLETE))
                .build());
    }

    public SfxMachinePhaseResult frameworkEffect(String effectName, SfxMachinePhaseContext context) {
        if (context == null) return SfxMachinePhaseResult.cont();
        context.put("spawner.framework.effect", effectName);
        context.put("spawner.framework.effect.handled", Boolean.TRUE);
        context.attachment("spawner.type", EntityType.class).ifPresent(type -> context.put("spawner.framework.type", type.name()));
        return SfxMachinePhaseResult.cont();
    }

    private Map<String, Object> frameworkAttributes(UUID instanceId, String typeId, Block block, EntityType spawnerType, ItemStack sourceItem) {
        Map<String, Object> attributes = new java.util.HashMap<>();
        attributes.put("spawner.instanceId", instanceId);
        attributes.put("spawner.typeId", typeId);
        attributes.put("spawner.block", block);
        attributes.put("spawner.type", spawnerType);
        attributes.put("spawner.sourceItem", sourceItem);
        attributes.put("framework.effect.dispatcher", (SfxMachineEffectDispatcher) this::frameworkEffect);
        return attributes;
    }

    public NamespacedKey spawnerTypeKey() {
        return spawnerTypeKey;
    }

    public boolean supportsType(String typeId) {
        return REINFORCED_SPAWNER.equals(typeId);
    }

    public void handlePlaced(UUID instanceId, String typeId, ItemStack sourceItem) {
        if (!supportsType(typeId)) {
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            return;
        }
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(instance.anchorKey().worldId());
        if (world == null) {
            return;
        }
        Block block = world.getBlockAt(instance.anchorKey().x(), instance.anchorKey().y(), instance.anchorKey().z());
        EntityType type = readSpawnerType(sourceItem).orElseGet(() -> readSpawnerType(block).orElse(EntityType.PIG));
        Map<String, Object> framework = frameworkAttributes(instanceId, typeId, block, type, sourceItem);
        machineRuntime.runPhase(typeId, SfxMachinePhase.ON_PLACE, instanceId, block.getLocation(), new SfxMachineTickContext(0L, 1L, false), null, SfxMachineStatus.IDLE, framework);
        applySpawnerType(block, type);
        blockData.updateInstanceState(instanceId, encode(type), SfxBlockLifecycleState.IDLE);
        machineRuntime.runPhase(typeId, SfxMachinePhase.AFTER_TICK, instanceId, block.getLocation(), new SfxMachineTickContext(0L, 1L, false), null, SfxMachineStatus.IDLE, framework);
    }

    public void destroyAnchoredBlock(Block block, UUID instanceId, String typeId, boolean containmentPickaxe) {
        if (block == null) {
            return;
        }
        EntityType type = readSpawnerType(block).orElseGet(() -> readSpawnerType(instanceId).orElse(EntityType.PIG));
        Map<String, Object> framework = frameworkAttributes(instanceId, typeId, block, type, null);
        machineRuntime.runPhase(typeId, SfxMachinePhase.ON_BREAK, instanceId, block.getLocation(), new SfxMachineTickContext(0L, 1L, false), null, SfxMachineStatus.IDLE, framework);
        DropMode mode = dropMode();
        if (mode == DropMode.REINFORCED) {
            SfxBlockDrops.dropItem(block, createReinforcedSpawner(type, 1));
        } else if (mode == DropMode.BROKEN) {
            SfxBlockDrops.dropItem(block, createBrokenSpawner(type));
        } else if (mode == DropMode.VANILLA && containmentPickaxe) {
            SfxBlockDrops.dropItem(block, createBrokenSpawner(type));
        }
        blockData.unregisterAt(block.getLocation());
    }

    public ItemStack createBrokenSpawner(EntityType type) {
        ItemStack stack = items.create("sf:broken_spawner");
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        EntityType actual = type == null ? EntityType.PIG : type;
        meta.getPersistentDataContainer().set(spawnerTypeKey, PersistentDataType.STRING, actual.name());
        meta.lore(brokenSpawnerLore(actual));
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack createReinforcedSpawner(EntityType type, int amount) {
        ItemStack stack = items.create(REINFORCED_SPAWNER, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        EntityType actual = type == null ? EntityType.PIG : type;
        meta.getPersistentDataContainer().set(spawnerTypeKey, PersistentDataType.STRING, actual.name());
        meta.lore(reinforcedSpawnerLore(actual));
        if (meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.getBlockState() instanceof CreatureSpawner spawner) {
            spawner.setSpawnedType(actual);
            blockStateMeta.setBlockState(spawner);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public Optional<EntityType> readSpawnerType(UUID instanceId) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            return Optional.empty();
        }
        if (!instance.hasState()) {
            instance = blockData.materializeInstance(instanceId).orElse(instance);
        }
        return decode(instance.stateBlob());
    }

    public Optional<EntityType> readSpawnerType(Block block) {
        if (block == null || block.getType() != Material.SPAWNER) {
            return Optional.empty();
        }
        if (block.getState() instanceof CreatureSpawner spawner && spawner.getSpawnedType() != null) {
            return Optional.of(spawner.getSpawnedType());
        }
        return Optional.empty();
    }

    public Optional<EntityType> readSpawnerType(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        String raw = meta.getPersistentDataContainer().get(spawnerTypeKey, PersistentDataType.STRING);
        if (raw != null) {
            Optional<EntityType> type = parseEntityType(raw);
            if (type.isPresent()) {
                return type;
            }
        }
        if (meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.getBlockState() instanceof CreatureSpawner spawner && spawner.getSpawnedType() != null) {
            return Optional.of(spawner.getSpawnedType());
        }
        if (meta.lore() != null) {
            for (Component line : meta.lore()) {
                String legacy = Text.toLegacy(line);
                int index = legacy.toLowerCase(Locale.ROOT).indexOf("type:");
                if (index < 0) {
                    index = legacy.indexOf("类型：");
                    if (index >= 0) {
                        String candidate = legacy.substring(index + 3).replaceAll("[&§][0-9A-FK-ORa-fk-or]", "").trim();
                        Optional<EntityType> type = parseEntityType(candidate);
                        if (type.isPresent()) {
                            return type;
                        }
                    }
                    continue;
                }
                String candidate = legacy.substring(index + 5).replaceAll("[&§][0-9A-FK-ORa-fk-or]", "").trim();
                Optional<EntityType> type = parseEntityType(candidate);
                if (type.isPresent()) {
                    return type;
                }
            }
        }
        return Optional.empty();
    }

    public void applySpawnerType(Block block, EntityType type) {
        if (block == null || block.getType() != Material.SPAWNER) {
            return;
        }
        if (block.getState() instanceof CreatureSpawner spawner) {
            spawner.setSpawnedType(type == null ? EntityType.PIG : type);
            spawner.update(true, false);
        }
    }

    @Override
    public boolean canPlaceFromBlockPlacer(String itemId, ItemStack stack, Block target, UUID ownerId) {
        return supportsType(itemId) && target != null && target.getType().isAir();
    }

    @Override
    public boolean placeFromBlockPlacer(String itemId, ItemStack stack, Block target, UUID ownerId) {
        if (!canPlaceFromBlockPlacer(itemId, stack, target, ownerId)) {
            return false;
        }
        return SfxProgrammaticPlacementTransactions.place(
                blockData,
                itemId,
                target,
                Material.SPAWNER,
                ownerId,
                stack,
                (context, instanceId) -> {
                    handlePlaced(instanceId, itemId, stack);
                    machineRuntime.recordState(instanceId, itemId, target.getLocation(), SfxMachineStatus.IDLE);
                },
                plugin.getLogger()
        ).isPresent();
    }

    private List<Component> brokenSpawnerLore(EntityType type) {
        String pretty = prettyEnumName(type.name());
        return List.of(
                Text.renderFlexible(localization.text("items.sf.broken_spawner.type-line", Map.of("type", pretty))),
                Component.empty(),
                Text.renderFlexible(localization.text("items.sf.broken_spawner.fractured-line"))
        );
    }

    private List<Component> reinforcedSpawnerLore(EntityType type) {
        String pretty = prettyEnumName(type.name());
        return List.of(Text.renderFlexible(localization.text("items.sf.reinforced_spawner.type-line", Map.of("type", pretty))));
    }

    private DropMode dropMode() {
        String raw = plugin.getConfig().getString("legacy.reinforced-spawner.break-drop-mode", "reinforced");
        if (raw == null) {
            return DropMode.REINFORCED;
        }
        try {
            return DropMode.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return DropMode.REINFORCED;
        }
    }

    private Optional<EntityType> decode(byte[] blob) {
        if (blob == null || blob.length == 0) {
            return Optional.empty();
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(blob))) {
            int version = in.readInt();
            if (version != 1) {
                return Optional.empty();
            }
            return parseEntityType(in.readUTF());
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private byte[] encode(EntityType type) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(1);
            out.writeUTF((type == null ? EntityType.PIG : type).name());
            return bytes.toByteArray();
        } catch (IOException exception) {
            return new byte[0];
        }
    }

    private Optional<EntityType> parseEntityType(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String cleaned = raw.replaceAll("[&§][0-9A-FK-ORa-fk-or]", "").trim();
        String normalized = cleaned.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            return Optional.of(EntityType.valueOf(normalized));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private String prettyEnumName(String raw) {
        String[] words = raw.toLowerCase(Locale.ROOT).split("_");
        List<String> pretty = new ArrayList<>(words.length);
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            pretty.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        return String.join(" ", pretty);
    }

    private enum DropMode {
        REINFORCED,
        BROKEN,
        VANILLA
    }
}
