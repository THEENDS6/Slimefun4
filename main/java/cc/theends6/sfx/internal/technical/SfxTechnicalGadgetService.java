package cc.theends6.sfx.internal.technical;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.util.SfxLocalization;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.List;
import java.util.Collection;
import java.util.Objects;
import java.util.logging.Level;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class SfxTechnicalGadgetService implements Listener {
    private static final int DOUBLE_TAP_WINDOW_TICKS = 7;
    private static final int FALLBACK_JUMP_PULSE_TICKS = 6;
    private static final double FUEL_BUCKET_VALUE = 2500.0D;
    private static final int GROUND_JUMP_GRACE_TICKS = 3;
    private static final double JETPACK_VERTICAL_MULTIPLIER = 2.0D;
    private static final double JETPACK_HORIZONTAL_FACTOR = 0.20D;
    private static final double JETPACK_MAX_HORIZONTAL_SPEED = 0.85D;
    private static final double HOVER_HORIZONTAL_FACTOR = 0.20D;
    private static final double HOVER_HOLD_Y = 0.0D;
    private static final double HOVER_UPWARD_DAMPING_Y = -0.020D;
    private static final double HOVER_DOWNWARD_RECOVERY_Y = 0.010D;
    private static final double HOVER_DESCEND_MULTIPLIER = 0.80D;
    private static final double JETBOOTS_HORIZONTAL_MULTIPLIER = 2.0D;
    private static final double JETBOOTS_ASSIST_HORIZONTAL_FACTOR = 0.20D;
    private static final double JETBOOTS_ASSIST_GROUND_HORIZONTAL_FACTOR = 0.40D;
    private static final double JETBOOTS_ASSIST_GROUND_MIN_ACCELERATION = 0.045D;
    private static final double JETBOOTS_MAX_HORIZONTAL_SPEED = 0.95D;
    private static final int JETBOOTS_MODE_TAP_WINDOW_TICKS = 7;
    private static final int JETBOOTS_AIR_JUMP_TRAIL_TICKS = 7;
    private static final int ASSIST_JUMP_GRACE_TICKS = 3;
    private static final int PASSIVE_EFFECT_INTERVAL_TICKS = 3;
    private static final int PASSIVE_GADGET_CHECK_INTERVAL_TICKS = 10;
    private static final float AIR_JUMP_SOUND_VOLUME = 0.38F;
    private static final float AIR_JUMP_SOUND_PITCH = 1.55F;
    private static final double FUEL_AUTO_REFILL_THRESHOLD = 7500.0D;
    private static final String FUEL_BUCKET_ID = "sf:bucket_of_fuel";

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxRechargeableItemService rechargeableItems;
    private final boolean extensionsEnabled;
    private final Map<UUID, Boolean> hoverEnabled = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> previousJumpDown = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> previousShiftDown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastJumpPressTick = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastShiftPressTick = new ConcurrentHashMap<>();
    private final Map<UUID, JetBootsDriveMode> jetBootsDriveModes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> usedAirJumps = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> airborneTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> jetBootsTrailTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> fallbackJumpPulseUntil = new ConcurrentHashMap<>();
    private final Map<UUID, FlightSnapshot> flightSnapshots = new ConcurrentHashMap<>();
    private final Map<UUID, AttributeSnapshot> attributeSnapshots = new ConcurrentHashMap<>();
    private volatile boolean running;
    private volatile long tickCounter;

    public SfxTechnicalGadgetService(JavaPlugin plugin, SfxRuntime runtime, SfxItems items) {
        this(plugin, runtime, items, new SfxLocalization(plugin));
    }

    public SfxTechnicalGadgetService(JavaPlugin plugin, SfxRuntime runtime, SfxItems items, SfxLocalization localization) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.rechargeableItems = new SfxRechargeableItemService(plugin, items);
        this.extensionsEnabled = plugin.getConfig().getBoolean("technical-gadgets.sfx-extensions.jetpacks-and-jetboots.enabled", true);
        this.tickCounter = 0L;
        this.running = true;
        scheduleTick();
    }

    public SfxRechargeableItemService rechargeableItems() {
        return rechargeableItems;
    }

    public void shutdown() {
        running = false;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            restorePlayer(player);
        }
        hoverEnabled.clear();
        previousJumpDown.clear();
        previousShiftDown.clear();
        lastJumpPressTick.clear();
        lastShiftPressTick.clear();
        jetBootsDriveModes.clear();
        usedAirJumps.clear();
        airborneTicks.clear();
        jetBootsTrailTicks.clear();
        fallbackJumpPulseUntil.clear();
        flightSnapshots.clear();
        attributeSnapshots.clear();
    }

    private void scheduleTick() {
        runtime.executeGlobalLater(1, () -> {
            if (!running) {
                return;
            }
            try {
                tickCounter++;
                tickOnlinePlayers();
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.WARNING, "SFX technical gadget scheduler failed", throwable);
            } finally {
                if (running) {
                    scheduleTick();
                }
            }
        });
    }

    private void tickOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            runtime.executeForPlayer(player, () -> tickPlayerSafely(player));
        }
    }

    private void tickPlayerSafely(Player player) {
        try {
            if (player == null || !player.isOnline()) {
                return;
            }
            tickPlayer(player);
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "SFX technical gadget tick failed for " + (player == null ? "unknown" : player.getName()), throwable);
        }
    }

    private void tickPlayer(Player player) {
        UUID id = player.getUniqueId();
        GameMode gameMode = player.getGameMode();
        if (player.isDead()) {
            if (hasRuntimeState(id) || isPassiveGadgetCheckTick(id)) {
                clearRuntimeState(player);
            }
            return;
        }
        if (gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR) {
            if (hasRuntimeState(id) || isPassiveGadgetCheckTick(id)) {
                clearRuntimeState(player, false);
            }
            return;
        }

        if (!hasRuntimeState(id) && !isPassiveGadgetCheckTick(id)) {
            return;
        }

        ItemStack chestplate = player.getInventory().getChestplate();
        ItemStack boots = player.getInventory().getBoots();
        SfxRechargeableItemService.Definition jetpack = jetpackDefinition(chestplate);
        SfxRechargeableItemService.Definition jetBoots = jetBootsDefinition(boots);
        boolean hasJetpack = jetpack != null;
        boolean hasJetBoots = jetBoots != null;
        if (!hasJetpack && !hasJetBoots) {
            if (hasRuntimeState(id) || isPassiveGadgetCheckTick(id)) {
                clearRuntimeState(player);
            }
            return;
        }
        PlayerInput input = readInput(player);
        boolean jumpDown = input.jump() || fallbackJumpPulseUntil.getOrDefault(id, 0L) >= tickCounter;
        boolean shiftDown = input.shift();
        boolean jumpPressed = jumpDown && !previousJumpDown.getOrDefault(id, false);
        boolean shiftPressed = shiftDown && !previousShiftDown.getOrDefault(id, false);
        previousJumpDown.put(id, jumpDown);
        previousShiftDown.put(id, shiftDown);

        if (!hasJetpack) {
            hoverEnabled.remove(id);
        }
        if (!hasJetBoots) {
            jetBootsDriveModes.remove(id);
        }

        if (player.isOnGround()) {
            usedAirJumps.put(id, 0);
            airborneTicks.put(id, 0);
        } else {
            airborneTicks.merge(id, 1, Integer::sum);
        }

        tickSfxFlightPermission(player, hasJetpack, jetBoots);
        boolean suppressShiftAction = handleShiftPress(player, jetBoots, shiftPressed);
        handleJumpPress(player, chestplate, jetpack, boots, jetBoots, hasJetpack, jumpPressed);
        tickPassiveJetBoots(player, jetBoots);
        tickJetBootsTrail(player);

        boolean hovering = hasJetpack && hoverEnabled.getOrDefault(id, false);
        if (hasJetpack) {
            if (hovering) {
                tryHoverJetpack(player, chestplate, jetpack, boots, jetBoots, input, jumpDown, shiftDown);
            } else {
                if (jumpDown) {
                    tryUseJetpack(player, chestplate, jetpack, input);
                }
                if (hasJetBoots && !suppressShiftAction) {
                    JetBootsDriveMode mode = jetBootsMode(player);
                    if (shiftDown && mode == JetBootsDriveMode.THRUST) {
                        tryUseJetBootsThrust(player, boots, jetBoots, mode);
                    } else if (mode == JetBootsDriveMode.ASSIST) {
                        tryUseJetBootsAssist(player, boots, jetBoots, input);
                    }
                }
            }
            return;
        }

        if (hasJetBoots && !suppressShiftAction) {
            JetBootsDriveMode mode = jetBootsMode(player);
            if (shiftDown && mode == JetBootsDriveMode.THRUST) {
                tryUseJetBootsThrust(player, boots, jetBoots, mode);
            } else if (mode == JetBootsDriveMode.ASSIST) {
                tryUseJetBootsAssist(player, boots, jetBoots, input);
            }
        }
    }

    private boolean handleShiftPress(Player player, SfxRechargeableItemService.Definition jetBoots, boolean shiftPressed) {
        if (!shiftPressed || jetBoots == null) {
            return false;
        }
        UUID id = player.getUniqueId();
        long previous = lastShiftPressTick.getOrDefault(id, -100L);
        lastShiftPressTick.put(id, tickCounter);
        if (tickCounter - previous > JETBOOTS_MODE_TAP_WINDOW_TICKS) {
            return false;
        }
        JetBootsDriveMode next = jetBootsMode(player) == JetBootsDriveMode.THRUST
                ? JetBootsDriveMode.ASSIST
                : JetBootsDriveMode.THRUST;
        jetBootsDriveModes.put(id, next);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 0.65F, next == JetBootsDriveMode.ASSIST ? 1.7F : 0.9F);
        sendActionbar(player,
                next == JetBootsDriveMode.ASSIST ? "technical.jetboots.mode.assist" : "technical.jetboots.mode.thrust",
                next == JetBootsDriveMode.ASSIST ? "<aqua>Jet Boots: Assist Mode</aqua>" : "<yellow>Jet Boots: Thrust Mode</yellow>");
        return true;
    }

    private void handleJumpPress(
            Player player,
            ItemStack chestplate,
            SfxRechargeableItemService.Definition jetpack,
            ItemStack boots,
            SfxRechargeableItemService.Definition jetBoots,
            boolean hasJetpack,
            boolean jumpPressed
    ) {
        if (!jumpPressed) {
            return;
        }
        UUID id = player.getUniqueId();
        long previous = lastJumpPressTick.getOrDefault(id, -100L);
        lastJumpPressTick.put(id, tickCounter);

        if (jetBoots != null
                && usedAirJumps.getOrDefault(id, 0) == 0
                && airborneTicks.getOrDefault(id, 0) <= GROUND_JUMP_GRACE_TICKS) {
            playJetBootsAirJumpSound(player);
        }

        if (jetpack != null && jetpack.hoverSupported() && tickCounter - previous <= DOUBLE_TAP_WINDOW_TICKS) {
            boolean enabled = hoverEnabled.compute(id, (ignored, current) -> current == null || !current);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 0.7F, enabled ? 1.6F : 0.8F);
            sendActionbar(player,
                    enabled ? "technical.jetpack.hover.enabled" : "technical.jetpack.hover.disabled",
                    enabled ? "<aqua>Jetpack Hover: Enabled</aqua>" : "<gray>Jetpack Hover: Disabled</gray>");
            return;
        }

        if (!hasJetpack && jetBoots != null && !player.isOnGround()) {
            tryUseJetBootsAirJump(player, boots, jetBoots, false, jetBootsMode(player));
        }
    }

    private void tryUseJetpack(Player player, ItemStack chestplate, SfxRechargeableItemService.Definition definition, PlayerInput input) {
        if (definition == null || !consumeEnergy(player, chestplate, definition, definition.useCost())) {
            return;
        }
        player.getInventory().setChestplate(chestplate);
        player.setFallDistance(0.0F);
        Vector current = player.getVelocity();
        double upward = isAboveHeightLimit(player, definition) ? 0.0D : definition.movementValue() * JETPACK_VERTICAL_MULTIPLIER;
        Vector horizontal = addInputHorizontalVelocity(player, input, current, definition.movementValue() * JETPACK_HORIZONTAL_FACTOR);
        player.setVelocity(limitHorizontal(new Vector(horizontal.getX(), Math.max(current.getY(), upward), horizontal.getZ()), JETPACK_MAX_HORIZONTAL_SPEED));
        playJetpackEffects(player, definition);
    }

    private void tryHoverJetpack(
            Player player,
            ItemStack chestplate,
            SfxRechargeableItemService.Definition definition,
            ItemStack boots,
            SfxRechargeableItemService.Definition jetBoots,
            PlayerInput input,
            boolean jumpDown,
            boolean shiftDown
    ) {
        if (definition == null) {
            return;
        }
        boolean verticalInput = jumpDown || shiftDown;
        double cost = definition.useCost() * (verticalInput ? 1.0D : 0.5D);
        if (!consumeEnergy(player, chestplate, definition, cost)) {
            return;
        }
        player.getInventory().setChestplate(chestplate);
        player.setFallDistance(0.0F);
        Vector current = player.getVelocity();
        double y = hoverHoldVelocity(current.getY());
        if (jumpDown && !isAboveHeightLimit(player, definition)) {
            y = definition.movementValue() * JETPACK_VERTICAL_MULTIPLIER;
        } else if (shiftDown) {
            y = -Math.max(0.10D, definition.movementValue() * JETPACK_VERTICAL_MULTIPLIER * HOVER_DESCEND_MULTIPLIER);
        }

        boolean horizontalInput = input.hasHorizontalMovement();
        Vector horizontal = current.clone();
        horizontal.setY(0.0D);
        if (horizontalInput) {
            if (jetBoots != null) {
                horizontal = tryApplyJetBootsHoverHorizontal(player, boots, jetBoots, input, horizontal);
            } else {
                horizontal = addInputHorizontalVelocity(player, input, horizontal, definition.movementValue() * HOVER_HORIZONTAL_FACTOR);
            }
        }
        player.setVelocity(limitHorizontal(new Vector(horizontal.getX(), y, horizontal.getZ()), JETPACK_MAX_HORIZONTAL_SPEED));
        if (shouldPlayPassiveEffect(verticalInput || horizontalInput)) {
            playJetpackEffects(player, definition);
        }
    }

    private void tryUseJetBootsThrust(Player player, ItemStack boots, SfxRechargeableItemService.Definition definition, JetBootsDriveMode mode) {
        if (definition == null || mode != JetBootsDriveMode.THRUST || !consumeEnergy(player, boots, definition, jetBootsUseCost(definition, mode))) {
            return;
        }
        player.getInventory().setBoots(boots);
        player.setFallDistance(0.0F);
        Vector direction = player.getEyeLocation().getDirection();
        Vector current = player.getVelocity();
        Vector vector = new Vector(
                current.getX() * 0.35D + direction.getX() * definition.movementValue() * JETBOOTS_HORIZONTAL_MULTIPLIER,
                Math.max(current.getY(), 0.06D),
                current.getZ() * 0.35D + direction.getZ() * definition.movementValue() * JETBOOTS_HORIZONTAL_MULTIPLIER
        );
        player.setVelocity(limitHorizontal(vector, JETBOOTS_MAX_HORIZONTAL_SPEED));
        playJetBootsEffects(player);
    }

    private void tryUseJetBootsAssist(Player player, ItemStack boots, SfxRechargeableItemService.Definition definition, PlayerInput input) {
        if (definition == null || !input.hasHorizontalMovement()) {
            return;
        }
        if (!consumeEnergy(player, boots, definition, jetBootsUseCost(definition, JetBootsDriveMode.ASSIST))) {
            return;
        }
        player.getInventory().setBoots(boots);
        Vector current = player.getVelocity();
        double acceleration = jetBootsAssistAcceleration(player, definition);
        Vector horizontal = addInputHorizontalBoostVector(player, input, current, acceleration, JETBOOTS_MAX_HORIZONTAL_SPEED);
        player.setVelocity(new Vector(horizontal.getX(), current.getY(), horizontal.getZ()));
        if (shouldPlayPassiveEffect(false)) {
            playJetBootsEffects(player, player.isOnGround() ? 0.30D : 0.0D);
        }
    }

    private Vector tryApplyJetBootsHoverHorizontal(Player player, ItemStack boots, SfxRechargeableItemService.Definition definition, PlayerInput input, Vector currentHorizontal) {
        if (definition == null || !input.hasHorizontalMovement()) {
            return addInputHorizontalVelocity(player, input, currentHorizontal, 0.0D);
        }
        JetBootsDriveMode mode = jetBootsMode(player);
        if (!consumeEnergy(player, boots, definition, jetBootsUseCost(definition, mode))) {
            return addInputHorizontalVelocity(player, input, currentHorizontal, 0.0D);
        }
        player.getInventory().setBoots(boots);
        playJetBootsEffects(player);
        double acceleration = mode == JetBootsDriveMode.THRUST
                ? definition.movementValue() * JETBOOTS_HORIZONTAL_MULTIPLIER
                : jetBootsAssistAcceleration(player, definition);
        return addInputHorizontalBoostVector(player, input, currentHorizontal, acceleration, JETBOOTS_MAX_HORIZONTAL_SPEED);
    }

    private void tryUseJetBootsAirJump(Player player, ItemStack boots, SfxRechargeableItemService.Definition definition, boolean ignoreGroundJumpGrace, JetBootsDriveMode mode) {
        int maxAirJumps = maxAirJumps(definition);
        UUID id = player.getUniqueId();
        int used = usedAirJumps.getOrDefault(id, 0);
        if ((!ignoreGroundJumpGrace && airborneTicks.getOrDefault(id, 0) <= GROUND_JUMP_GRACE_TICKS) || used >= maxAirJumps) {
            return;
        }
        if (!consumeEnergy(player, boots, definition, jetBootsUseCost(definition, mode) * 50.0D)) {
            return;
        }
        usedAirJumps.put(id, used + 1);
        jetBootsTrailTicks.put(id, JETBOOTS_AIR_JUMP_TRAIL_TICKS);
        player.getInventory().setBoots(boots);
        player.setFallDistance(0.0F);
        Vector current = player.getVelocity();
        player.setVelocity(new Vector(current.getX(), Math.max(0.38D, 0.42D + definition.movementValue()), current.getZ()));
        playJetBootsAirJumpSound(player);
        playJetBootsEffects(player);
    }

    private double jetBootsAssistAcceleration(Player player, SfxRechargeableItemService.Definition definition) {
        double base = definition.movementValue() * JETBOOTS_ASSIST_HORIZONTAL_FACTOR;
        if (!player.isOnGround()) {
            return base;
        }
        return Math.max(base, Math.max(definition.movementValue() * JETBOOTS_ASSIST_GROUND_HORIZONTAL_FACTOR, JETBOOTS_ASSIST_GROUND_MIN_ACCELERATION));
    }

    private double jetBootsUseCost(SfxRechargeableItemService.Definition definition, JetBootsDriveMode mode) {
        if (definition == null) {
            return 0.0D;
        }
        return definition.useCost() * (mode == JetBootsDriveMode.THRUST ? 2.0D : 1.0D);
    }

    private JetBootsDriveMode jetBootsMode(Player player) {
        return jetBootsDriveModes.getOrDefault(player.getUniqueId(), JetBootsDriveMode.THRUST);
    }

    private void tickJetBootsTrail(Player player) {
        UUID id = player.getUniqueId();
        int ticks = jetBootsTrailTicks.getOrDefault(id, 0);
        if (ticks <= 0) {
            return;
        }
        jetBootsTrailTicks.put(id, ticks - 1);
        playJetBootsEffects(player);
    }

    private boolean consumeEnergy(Player holder, ItemStack stack, SfxRechargeableItemService.Definition definition, double amount) {
        if (definition.kind() == SfxRechargeableItemService.RechargeableKind.FUEL_JETPACK) {
            autoRefillFuel(holder, stack, definition);
            return rechargeableItems.removeFuel(stack, amount);
        }
        return rechargeableItems.removeChargeAllowPartial(stack, amount);
    }

    private void autoRefillFuel(Player holder, ItemStack stack, SfxRechargeableItemService.Definition definition) {
        if (stack == null || definition == null || !definition.usesFuel()) {
            return;
        }
        if (rechargeableItems.fuel(stack) > FUEL_AUTO_REFILL_THRESHOLD || rechargeableItems.fuel(stack) + FUEL_BUCKET_VALUE > definition.fuelCapacity() + 0.0001D) {
            return;
        }
        int slot = findFuelBucketSlot(holder);
        if (slot < 0) {
            return;
        }
        ItemStack bucket = holder.getInventory().getItem(slot);
        if (bucket == null || bucket.getAmount() <= 0) {
            return;
        }
        bucket.setAmount(bucket.getAmount() - 1);
        holder.getInventory().setItem(slot, bucket.getAmount() <= 0 ? null : bucket);
        rechargeableItems.addFuel(stack, FUEL_BUCKET_VALUE);
        Map<Integer, ItemStack> leftovers = holder.getInventory().addItem(new ItemStack(Material.BUCKET));
        leftovers.values().forEach(leftover -> holder.getWorld().dropItemNaturally(holder.getLocation(), leftover));
    }

    private int findFuelBucketSlot(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            if (items.readMarker(item).map(marker -> FUEL_BUCKET_ID.equals(marker.itemId())).orElse(false)) {
                return i;
            }
        }
        return -1;
    }

    private void tickPassiveJetBoots(Player player, SfxRechargeableItemService.Definition definition) {
        UUID id = player.getUniqueId();
        if (definition == null || definition.kind() != SfxRechargeableItemService.RechargeableKind.JETBOOTS) {
            if (attributeSnapshots.containsKey(id)) {
                restoreAttributes(player);
            }
            return;
        }
        if (!isPassiveGadgetCheckTick(id) && attributeSnapshots.containsKey(id)) {
            return;
        }
        applyAttribute(player, "GENERIC_JUMP_STRENGTH", "JUMP_STRENGTH", definition.movementValue(), AttributeKind.JUMP_STRENGTH);
        applyAttribute(player, "GENERIC_SAFE_FALL_DISTANCE", "SAFE_FALL_DISTANCE", safeFallBonus(definition), AttributeKind.SAFE_FALL_DISTANCE);
    }

    private void applyAttribute(Player player, String legacyName, String modernName, double bonus, AttributeKind kind) {
        try {
            Class<?> attributeClass = Class.forName("org.bukkit.attribute.Attribute");
            Object attribute = resolveAttribute(attributeClass, legacyName, modernName);
            if (attribute == null) {
                return;
            }
            Method getAttribute = player.getClass().getMethod("getAttribute", attributeClass);
            Object instance = getAttribute.invoke(player, attribute);
            if (instance == null) {
                return;
            }
            removeSfxAttributeModifier(instance, kind);
            cleanupLegacyDirtyBase(instance, kind);
            if (bonus <= 0.0D) {
                return;
            }
            attributeSnapshots.computeIfAbsent(player.getUniqueId(), ignored -> new AttributeSnapshot());
            Object modifier = createAttributeModifier(kind, bonus);
            if (modifier == null) {
                return;
            }
            Method addModifier = instance.getClass().getMethod("addModifier", modifier.getClass());
            addModifier.invoke(instance, modifier);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Older server APIs do not expose these attributes. Active movement and fall-damage reduction still apply.
        }
    }

    private Object createAttributeModifier(AttributeKind kind, double bonus) {
        try {
            Class<?> modifierClass = Class.forName("org.bukkit.attribute.AttributeModifier");
            Class<?> operationClass = Class.forName("org.bukkit.attribute.AttributeModifier$Operation");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object operation = Enum.valueOf((Class<? extends Enum>) operationClass.asSubclass(Enum.class), "ADD_NUMBER");
            NamespacedKey key = attributeModifierKey(kind);
            try {
                Constructor<?> constructor = modifierClass.getConstructor(NamespacedKey.class, double.class, operationClass);
                return constructor.newInstance(key, bonus, operation);
            } catch (NoSuchMethodException ignored) {
                // Paper 1.21+ commonly requires an EquipmentSlotGroup argument.
            }
            try {
                Class<?> slotGroupClass = Class.forName("org.bukkit.inventory.EquipmentSlotGroup");
                Object any = slotGroupClass.getField("ANY").get(null);
                Constructor<?> constructor = modifierClass.getConstructor(NamespacedKey.class, double.class, operationClass, slotGroupClass);
                return constructor.newInstance(key, bonus, operation, any);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Fall through to the legacy UUID/name constructor.
            }
            try {
                Constructor<?> constructor = modifierClass.getConstructor(UUID.class, String.class, double.class, operationClass);
                return constructor.newInstance(UUID.nameUUIDFromBytes((plugin.getName() + ":" + key.getKey()).getBytes(java.nio.charset.StandardCharsets.UTF_8)), key.toString(), bonus, operation);
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private NamespacedKey attributeModifierKey(AttributeKind kind) {
        return new NamespacedKey(plugin, switch (kind) {
            case JUMP_STRENGTH -> "jetboots_jump_strength";
            case SAFE_FALL_DISTANCE -> "jetboots_safe_fall_distance";
        });
    }

    private void removeSfxAttributeModifier(Object attributeInstance, AttributeKind kind) throws ReflectiveOperationException {
        Method getModifiers = attributeInstance.getClass().getMethod("getModifiers");
        Object raw = getModifiers.invoke(attributeInstance);
        if (!(raw instanceof Collection<?> modifiers)) {
            return;
        }
        Method removeModifier = attributeInstance.getClass().getMethod("removeModifier", Class.forName("org.bukkit.attribute.AttributeModifier"));
        for (Object modifier : List.copyOf(modifiers)) {
            if (isSfxAttributeModifier(modifier, kind)) {
                removeModifier.invoke(attributeInstance, modifier);
            }
        }
    }

    private boolean isSfxAttributeModifier(Object modifier, AttributeKind kind) {
        NamespacedKey expected = attributeModifierKey(kind);
        try {
            Method getKey = modifier.getClass().getMethod("getKey");
            Object key = getKey.invoke(modifier);
            if (expected.equals(key)) {
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Try legacy name/UUID based modifiers below.
        }
        try {
            Method getName = modifier.getClass().getMethod("getName");
            Object name = getName.invoke(modifier);
            return expected.toString().equals(String.valueOf(name));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private void cleanupLegacyDirtyBase(Object attributeInstance, AttributeKind kind) {
        if (!plugin.getConfig().getBoolean("technical-gadgets.cleanup-legacy-attribute-base-values", true)) {
            return;
        }
        try {
            Method getBaseValue = attributeInstance.getClass().getMethod("getBaseValue");
            Method setBaseValue = attributeInstance.getClass().getMethod("setBaseValue", double.class);
            double base = ((Number) getBaseValue.invoke(attributeInstance)).doubleValue();
            if (kind == AttributeKind.SAFE_FALL_DISTANCE && base > 3.0001D) {
                setBaseValue.invoke(attributeInstance, 3.0D);
            } else if (kind == AttributeKind.JUMP_STRENGTH && base > 0.4200001D && base <= 1.5D) {
                setBaseValue.invoke(attributeInstance, 0.42D);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Best-effort cleanup for legacy builds that used base values.
        }
    }

    private Object resolveAttribute(Class<?> attributeClass, String... names) throws ReflectiveOperationException {
        if (Enum.class.isAssignableFrom(attributeClass)) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Class<? extends Enum> enumClass = (Class<? extends Enum>) attributeClass.asSubclass(Enum.class);
            return enumValue(enumClass, names);
        }
        for (String name : names) {
            try {
                Field field = attributeClass.getField(name);
                Object value = field.get(null);
                if (attributeClass.isInstance(value)) {
                    return value;
                }
            } catch (NoSuchFieldException ignored) {
                // Try the next API spelling.
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Enum<?> enumValue(Class<? extends Enum> enumClass, String... names) {
        for (String name : names) {
            try {
                return Enum.valueOf(enumClass, name);
            } catch (IllegalArgumentException ignored) {
                // Try the next API spelling.
            }
        }
        return null;
    }

    private void restoreAttributes(Player player) {
        if (attributeSnapshots.remove(player.getUniqueId()) == null) {
            return;
        }
        removeAttribute(player, "GENERIC_JUMP_STRENGTH", "JUMP_STRENGTH", AttributeKind.JUMP_STRENGTH);
        removeAttribute(player, "GENERIC_SAFE_FALL_DISTANCE", "SAFE_FALL_DISTANCE", AttributeKind.SAFE_FALL_DISTANCE);
    }

    private void removeAttribute(Player player, String legacyName, String modernName, AttributeKind kind) {
        try {
            Class<?> attributeClass = Class.forName("org.bukkit.attribute.Attribute");
            Object attribute = resolveAttribute(attributeClass, legacyName, modernName);
            if (attribute == null) {
                return;
            }
            Method getAttribute = player.getClass().getMethod("getAttribute", attributeClass);
            Object instance = getAttribute.invoke(player, attribute);
            if (instance != null) {
                removeSfxAttributeModifier(instance, kind);
                cleanupLegacyDirtyBase(instance, kind);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Attribute not present on this API.
        }
    }


    private boolean isPassiveGadgetCheckTick(UUID id) {
        return Math.floorMod(tickCounter + id.hashCode(), PASSIVE_GADGET_CHECK_INTERVAL_TICKS) == 0;
    }

    private boolean hasRuntimeState(UUID id) {
        return hoverEnabled.containsKey(id)
                || previousJumpDown.containsKey(id)
                || previousShiftDown.containsKey(id)
                || lastJumpPressTick.containsKey(id)
                || lastShiftPressTick.containsKey(id)
                || jetBootsDriveModes.containsKey(id)
                || usedAirJumps.containsKey(id)
                || airborneTicks.containsKey(id)
                || jetBootsTrailTicks.containsKey(id)
                || fallbackJumpPulseUntil.containsKey(id)
                || flightSnapshots.containsKey(id)
                || attributeSnapshots.containsKey(id);
    }

    private boolean isManagedFlightGameMode(GameMode gameMode) {
        return gameMode == GameMode.SURVIVAL || gameMode == GameMode.ADVENTURE;
    }

    private void tickSfxFlightPermission(Player player, boolean hasJetpack, SfxRechargeableItemService.Definition jetBoots) {
        UUID id = player.getUniqueId();
        boolean active = extensionsEnabled && isManagedFlightGameMode(player.getGameMode()) && (hasJetpack || jetBoots != null);
        if (!active) {
            restoreFlight(player);
            return;
        }
        flightSnapshots.computeIfAbsent(id, ignored -> new FlightSnapshot(player.getAllowFlight(), player.isFlying()));
        if (!player.getAllowFlight()) {
            player.setAllowFlight(true);
        }
        if (player.isFlying()) {
            player.setFlying(false);
        }
    }

    private PlayerInput readInput(Player player) {
        boolean shift = player.isSneaking();
        try {
            Method method = player.getClass().getMethod("getCurrentInput");
            Object input = method.invoke(player);
            if (input == null) {
                return new PlayerInput(false, shift, false, false, false, false);
            }
            return new PlayerInput(
                    readBooleanMethod(input, false, "isJump", "jump"),
                    readBooleanMethod(input, shift, "isSneak", "sneak", "isShift", "shift"),
                    readBooleanMethod(input, false, "isForward", "forward"),
                    readBooleanMethod(input, false, "isBackward", "backward"),
                    readBooleanMethod(input, false, "isLeft", "left"),
                    readBooleanMethod(input, false, "isRight", "right")
            );
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return new PlayerInput(false, shift, false, false, false, false);
        }
    }

    private boolean readBooleanMethod(Object target, boolean fallback, String... names) {
        for (String name : names) {
            Boolean value = invokeBooleanMethod(target.getClass(), target, name);
            if (value != null) {
                return value;
            }
            try {
                Class<?> inputClass = Class.forName("org.bukkit.Input");
                if (inputClass.isInstance(target)) {
                    value = invokeBooleanMethod(inputClass, target, name);
                    if (value != null) {
                        return value;
                    }
                }
            } catch (ClassNotFoundException | LinkageError ignored) {
                // Older API without org.bukkit.Input.
            }
        }
        return fallback;
    }

    private Boolean invokeBooleanMethod(Class<?> type, Object target, String name) {
        try {
            Method method = type.getMethod(name);
            Object value = method.invoke(target);
            return value instanceof Boolean bool ? bool : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private boolean isAboveHeightLimit(Player player, SfxRechargeableItemService.Definition definition) {
        int limit = definition.heightLimit();
        if (limit <= 0) {
            return false;
        }
        return distanceToGround(player, limit + 1) > limit;
    }

    private int distanceToGround(Player player, int maxScan) {
        org.bukkit.block.Block block = player.getLocation().getBlock();
        for (int distance = 0; distance <= maxScan; distance++) {
            org.bukkit.block.Block below = block.getRelative(0, -distance - 1, 0);
            if (below.getType().isSolid()) {
                return distance;
            }
        }
        return maxScan + 1;
    }

    private int maxAirJumps(SfxRechargeableItemService.Definition definition) {
        int level = Math.max(1, definition.level());
        if (level >= 7) {
            return 3;
        }
        if (level >= 4) {
            return 2;
        }
        return 1;
    }

    private double safeFallBonus(SfxRechargeableItemService.Definition definition) {
        return switch (Math.max(1, definition.level())) {
            case 1 -> 3.0D;
            case 2 -> 5.0D;
            case 3 -> 7.0D;
            case 4 -> 10.0D;
            case 5 -> 14.0D;
            case 6 -> 20.0D;
            default -> 32.0D;
        };
    }

    private double fallDamageMultiplier(SfxRechargeableItemService.Definition definition) {
        return switch (Math.max(1, definition.level())) {
            case 1 -> 0.80D;
            case 2 -> 0.70D;
            case 3 -> 0.60D;
            case 4 -> 0.50D;
            case 5 -> 0.40D;
            case 6 -> 0.30D;
            default -> 0.20D;
        };
    }

    private SfxRechargeableItemService.Definition jetpackDefinition(ItemStack chestplate) {
        SfxRechargeableItemService.Definition definition = rechargeableItems.definition(chestplate).orElse(null);
        if (definition == null) {
            return null;
        }
        return switch (definition.kind()) {
            case JETPACK, FUEL_JETPACK -> definition;
            default -> null;
        };
    }

    private SfxRechargeableItemService.Definition jetBootsDefinition(ItemStack boots) {
        SfxRechargeableItemService.Definition definition = rechargeableItems.definition(boots).orElse(null);
        return definition != null && definition.kind() == SfxRechargeableItemService.RechargeableKind.JETBOOTS ? definition : null;
    }

    private void clearRuntimeState(Player player) {
        clearRuntimeState(player, true);
    }

    private void clearRuntimeState(Player player, boolean restoreFlight) {
        UUID id = player.getUniqueId();
        hoverEnabled.remove(id);
        previousJumpDown.remove(id);
        previousShiftDown.remove(id);
        lastJumpPressTick.remove(id);
        lastShiftPressTick.remove(id);
        jetBootsDriveModes.remove(id);
        usedAirJumps.remove(id);
        airborneTicks.remove(id);
        jetBootsTrailTicks.remove(id);
        fallbackJumpPulseUntil.remove(id);
        restorePlayer(player, restoreFlight);
    }

    private void restorePlayer(Player player) {
        restorePlayer(player, true);
    }

    private void restorePlayer(Player player, boolean restoreFlight) {
        restoreAttributes(player);
        if (restoreFlight) {
            restoreFlight(player);
        } else {
            flightSnapshots.remove(player.getUniqueId());
        }
    }

    private void restoreFlight(Player player) {
        FlightSnapshot snapshot = flightSnapshots.remove(player.getUniqueId());
        if (snapshot == null) {
            return;
        }
        try {
            if (player.isFlying() && !snapshot.flying()) {
                player.setFlying(false);
            }
            player.setAllowFlight(snapshot.allowFlight());
            if (snapshot.flying() && snapshot.allowFlight()) {
                player.setFlying(true);
            }
        } catch (RuntimeException ignored) {
            // Player may be changing world, quitting, or otherwise unable to accept flight state changes.
        }
    }

    private double hoverHoldVelocity(double currentY) {
        if (currentY > 0.015D) {
            return HOVER_UPWARD_DAMPING_Y;
        }
        if (currentY < -0.070D) {
            return HOVER_DOWNWARD_RECOVERY_Y;
        }
        if (currentY < -0.025D) {
            return HOVER_DOWNWARD_RECOVERY_Y * 0.5D;
        }
        return HOVER_HOLD_Y;
    }

    private Vector addInputHorizontalVelocity(Player player, PlayerInput input, Vector current, double accelerationAmount) {
        Vector result = new Vector(current.getX(), 0.0D, current.getZ());
        if (accelerationAmount <= 0.0D) {
            return result;
        }
        Vector acceleration = inputDirection(player, input);
        if (acceleration.lengthSquared() > 0.000001D) {
            acceleration.normalize().multiply(accelerationAmount);
            result.add(acceleration);
        }
        return result;
    }

    private Vector addInputHorizontalVelocitySoft(Player player, PlayerInput input, Vector current, double accelerationAmount, double maxHorizontalSpeed) {
        Vector result = new Vector(current.getX(), 0.0D, current.getZ());
        if (accelerationAmount <= 0.0D) {
            return result;
        }
        Vector acceleration = inputDirection(player, input);
        if (acceleration.lengthSquared() <= 0.000001D) {
            return result;
        }
        acceleration.normalize();
        double currentHorizontal = Math.sqrt(result.getX() * result.getX() + result.getZ() * result.getZ());
        if (currentHorizontal >= maxHorizontalSpeed) {
            Vector currentDirection = result.clone();
            if (currentDirection.lengthSquared() > 0.000001D) {
                currentDirection.normalize();
                if (currentDirection.dot(acceleration) > 0.0D) {
                    return result;
                }
            }
        }
        result.add(acceleration.multiply(accelerationAmount));
        double newHorizontal = Math.sqrt(result.getX() * result.getX() + result.getZ() * result.getZ());
        if (currentHorizontal <= maxHorizontalSpeed && newHorizontal > maxHorizontalSpeed) {
            double scale = maxHorizontalSpeed / newHorizontal;
            result.setX(result.getX() * scale);
            result.setZ(result.getZ() * scale);
        }
        return result;
    }


    private Vector addInputHorizontalBoostVector(Player player, PlayerInput input, Vector current, double accelerationAmount, double maxHorizontalSpeed) {
        Vector result = new Vector(current.getX(), 0.0D, current.getZ());
        if (accelerationAmount <= 0.0D) {
            return result;
        }
        Vector acceleration = inputDirection(player, input);
        if (acceleration.lengthSquared() <= 0.000001D) {
            return result;
        }
        acceleration.normalize();
        double currentHorizontal = Math.sqrt(result.getX() * result.getX() + result.getZ() * result.getZ());
        if (currentHorizontal >= maxHorizontalSpeed) {
            Vector currentDirection = result.clone();
            if (currentDirection.lengthSquared() > 0.000001D) {
                currentDirection.normalize();
                if (currentDirection.dot(acceleration) > 0.0D) {
                    return result;
                }
            }
        }
        result.add(acceleration.multiply(accelerationAmount));
        double newHorizontal = Math.sqrt(result.getX() * result.getX() + result.getZ() * result.getZ());
        if (newHorizontal > maxHorizontalSpeed && currentHorizontal < newHorizontal) {
            // Keep the player's existing momentum and only trim the newly-added boost vector.
            double allowedDelta = Math.max(0.0D, maxHorizontalSpeed - currentHorizontal);
            if (allowedDelta <= 0.000001D) {
                return new Vector(current.getX(), 0.0D, current.getZ());
            }
            Vector limited = inputDirection(player, input);
            if (limited.lengthSquared() > 0.000001D) {
                limited.normalize().multiply(allowedDelta);
                return new Vector(current.getX(), 0.0D, current.getZ()).add(limited);
            }
        }
        return result;
    }

    private boolean shouldPlayPassiveEffect(boolean forceEveryTick) {
        return forceEveryTick || tickCounter % PASSIVE_EFFECT_INTERVAL_TICKS == 0L;
    }

    private Vector inputDirection(Player player, PlayerInput input) {
        double forwardAmount = (input.forward() ? 1.0D : 0.0D) - (input.backward() ? 1.0D : 0.0D);
        double strafeAmount = (input.right() ? 1.0D : 0.0D) - (input.left() ? 1.0D : 0.0D);
        if (Math.abs(forwardAmount) < 0.000001D && Math.abs(strafeAmount) < 0.000001D) {
            return new Vector();
        }
        Vector forward = player.getEyeLocation().getDirection();
        forward.setY(0.0D);
        if (forward.lengthSquared() <= 0.000001D) {
            return new Vector();
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0.0D, forward.getX());
        return forward.multiply(forwardAmount).add(right.multiply(strafeAmount));
    }

    private Vector limitHorizontal(Vector vector, double maxHorizontalSpeed) {
        double x = vector.getX();
        double z = vector.getZ();
        double horizontal = Math.sqrt(x * x + z * z);
        if (horizontal <= maxHorizontalSpeed || horizontal <= 0.000001D) {
            return vector;
        }
        double scale = maxHorizontalSpeed / horizontal;
        vector.setX(x * scale);
        vector.setZ(z * scale);
        return vector;
    }

    private Location jetpackParticleLocation(Player player) {
        Location location = player.getLocation().clone().add(0.0D, 0.85D, 0.0D);
        Vector backward = player.getEyeLocation().getDirection();
        backward.setY(0.0D);
        if (backward.lengthSquared() > 0.000001D) {
            backward.normalize().multiply(-0.36D);
            location.add(backward);
        }
        return location;
    }

    private Location jetBootsParticleLocation(Player player) {
        return player.getLocation().clone().add(0.0D, 0.18D, 0.0D);
    }

    private void playJetpackEffects(Player player, SfxRechargeableItemService.Definition definition) {
        if (definition != null && definition.kind() == SfxRechargeableItemService.RechargeableKind.FUEL_JETPACK) {
            playFuelJetpackEffects(player);
            return;
        }
        Location location = jetpackParticleLocation(player);
        player.getWorld().spawnParticle(Particle.CLOUD, location, 6, 0.11D, 0.08D, 0.11D, 0.015D);
        player.getWorld().playSound(location, Sound.ENTITY_TNT_PRIMED, SoundCategory.PLAYERS, 0.22F, 1.25F);
    }

    private void playFuelJetpackEffects(Player player) {
        Location location = jetpackParticleLocation(player);
        player.getWorld().spawnParticle(Particle.SMOKE, location, 8, 0.12D, 0.10D, 0.12D, 0.02D);
        player.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.11F, 1.25F);
    }

    private void playJetBootsEffects(Player player) {
        playJetBootsEffects(player, 0.0D);
    }

    private void playJetBootsEffects(Player player, double extraY) {
        Location location = jetBootsParticleLocation(player).add(0.0D, extraY, 0.0D);
        player.getWorld().spawnParticle(Particle.CLOUD, location, 4, 0.18D, 0.04D, 0.18D, 0.012D);
        player.getWorld().playSound(location, Sound.ENTITY_TNT_PRIMED, SoundCategory.PLAYERS, 0.22F, 1.25F);
    }

    private void playJetBootsAirJumpSound(Player player) {
        Location soundLocation = jetBootsParticleLocation(player);
        player.getWorld().playSound(soundLocation, Sound.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, AIR_JUMP_SOUND_VOLUME, AIR_JUMP_SOUND_PITCH);
    }


    private void sendActionbar(Player player, String key, String fallback) {
        player.sendActionBar(localization.component(key, fallback));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!isManagedFlightGameMode(player.getGameMode())) {
            return;
        }
        ItemStack chestplate = player.getInventory().getChestplate();
        ItemStack boots = player.getInventory().getBoots();
        SfxRechargeableItemService.Definition jetpack = jetpackDefinition(chestplate);
        SfxRechargeableItemService.Definition jetBoots = jetBootsDefinition(boots);
        if (jetpack == null && jetBoots == null) {
            return;
        }
        event.setCancelled(true);
        player.setFlying(false);
        UUID id = player.getUniqueId();
        fallbackJumpPulseUntil.put(id, tickCounter + FALLBACK_JUMP_PULSE_TICKS);
        previousJumpDown.put(id, true);
        if (jetpack != null) {
            handleJumpPress(player, chestplate, jetpack, boots, jetBoots, true, true);
        } else if (jetBoots != null) {
            tryUseJetBootsAirJump(player, boots, jetBoots, true, jetBootsMode(player));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        SfxRechargeableItemService.Definition jetBoots = jetBootsDefinition(player.getInventory().getBoots());
        if (jetBoots == null) {
            return;
        }
        double safe = 3.0D + safeFallBonus(jetBoots);
        if (player.getFallDistance() <= safe) {
            event.setCancelled(true);
            player.setFallDistance(0.0F);
            return;
        }
        event.setDamage(event.getDamage() * fallDamageMultiplier(jetBoots));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearRuntimeState(event.getPlayer());
    }

    private enum AttributeKind {
        JUMP_STRENGTH,
        SAFE_FALL_DISTANCE
    }

    private static final class AttributeSnapshot {
        private Double jumpStrengthBase;
        private Double safeFallDistanceBase;

        Double base(AttributeKind kind) {
            return kind == AttributeKind.JUMP_STRENGTH ? jumpStrengthBase : safeFallDistanceBase;
        }

        void setBase(AttributeKind kind, double value) {
            if (kind == AttributeKind.JUMP_STRENGTH) {
                jumpStrengthBase = value;
            } else {
                safeFallDistanceBase = value;
            }
        }

        Double jumpStrengthBase() {
            return jumpStrengthBase;
        }

        Double safeFallDistanceBase() {
            return safeFallDistanceBase;
        }
    }

    private record FlightSnapshot(boolean allowFlight, boolean flying) {
    }

    private enum JetBootsDriveMode {
        THRUST,
        ASSIST
    }

    private record PlayerInput(boolean jump, boolean shift, boolean forward, boolean backward, boolean left, boolean right) {
        boolean hasHorizontalMovement() {
            return forward || backward || left || right;
        }
    }
}
