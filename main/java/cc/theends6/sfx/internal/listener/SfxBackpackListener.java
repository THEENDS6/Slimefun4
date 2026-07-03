package cc.theends6.sfx.internal.listener;

import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.diagnostics.SfxValidationDiagnostics;
import cc.theends6.sfx.internal.playerdata.SfxBackpackRecord;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.playerdata.SfxPlayerProfile;
import cc.theends6.sfx.internal.research.SfxResearchService;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

public final class SfxBackpackListener implements Listener {
    private static final Set<String> BACKPACK_IDS = Set.of(
            "sf:small_backpack", "sf:medium_backpack", "sf:large_backpack", "sf:woven_backpack",
            "sf:gilded_backpack", "sf:radiant_backpack", "sf:bound_backpack", "sf:cooler", "sf:restored_backpack"
    );
    private static final Set<String> COOLER_DRINK_IDS = Set.of(
            "sf:apple_juice", "sf:melon_juice", "sf:carrot_juice", "sf:pumpkin_juice",
            "sf:sweet_berry_juice", "sf:glow_berry_juice", "sf:golden_apple_juice"
    );

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxPlayerDataService profiles;
    private final SfxResearchService researches;
    private final NamespacedKey backpackIdKey;
    private final NamespacedKey backpackOwnerKey;
    private final Map<UUID, OpenBackpackSession> sessions = new HashMap<>();
    private final Map<String, UUID> openBackpacks = new HashMap<>();

    public SfxBackpackListener(
            JavaPlugin plugin,
            SfxRuntime runtime,
            SfxItems items,
            SfxLocalization localization,
            SfxPlayerDataService profiles,
            SfxResearchService researches
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.researches = Objects.requireNonNull(researches, "researches");
        this.backpackIdKey = new NamespacedKey(plugin, "backpack_id");
        this.backpackOwnerKey = new NamespacedKey(plugin, "backpack_owner");
    }

    public void shutdown() {
        for (Map.Entry<UUID, OpenBackpackSession> entry : List.copyOf(sessions.entrySet())) {
            UUID viewerId = entry.getKey();
            OpenBackpackSession session = entry.getValue();
            openBackpacks.remove(session.uniqueKey());
            persistSession(session);
            Player viewer = plugin.getServer().getPlayer(viewerId);
            if (viewer != null) {
                runtime.executeForPlayer(viewer, viewer::closeInventory);
            }
        }
        sessions.clear();
    }

    public void openBackpackForAdmin(Player viewer, UUID ownerId, String ownerName, int backpackId) {
        profiles.request(ownerId, ownerName, profile -> {
            SfxBackpackRecord backpack = profile.getBackpack(backpackId);
            if (backpack == null) {
                runtime.executeForPlayer(viewer, () -> send(viewer, "messages.backpack.not-found"));
                return;
            }
            runtime.executeForPlayer(viewer, () -> openProfileBackpack(viewer, profile, backpackId, itemIdForSize(backpack.size()),
                    localization.text("messages.backpack.admin-title", Map.of("id", backpackId, "owner", ownerName)),
                    true, false));
        });
    }

    public void giveBackpackItem(Player recipient, UUID ownerId, String ownerName, int backpackId, Integer requestedSize) {
        profiles.request(ownerId, ownerName, profile -> {
            String itemId;
            if (requestedSize == null) {
                if (profile.getBackpack(backpackId) == null) {
                    runtime.executeForPlayer(recipient, () -> send(recipient, "messages.backpack.not-found"));
                    return;
                }
                itemId = "sf:restored_backpack";
            } else {
                int size = normalizeBackpackSize(requestedSize);
                profile.ensureBackpackSized(backpackId, size);
                profile.markDirty();
                profiles.saveAsync(profile);
                itemId = itemIdForSize(size);
            }
            ItemStack stack = createBoundBackpackItem(itemId, profile.ownerId(), backpackId);
            runtime.executeForPlayer(recipient, () -> items.give(recipient, stack));
        });
    }

    boolean handleItemUse(PlayerInteractEvent event, String itemId) {
        if (!BACKPACK_IDS.contains(itemId)) {
            return false;
        }
        denyItemUse(event);
        openBackpack(event.getPlayer(), itemInHand(event), itemId);
        return true;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        OpenBackpackSession session = sessions.remove(player.getUniqueId());
        if (session == null || session.inventory() != event.getInventory()) {
            return;
        }
        openBackpacks.remove(session.uniqueKey());
        persistSession(session);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (sessions.containsKey(event.getPlayer().getUniqueId()) && isBackpackItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        OpenBackpackSession session = sessions.get(player.getUniqueId());
        if (session == null || event.getView().getTopInventory() != session.inventory()) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ItemStack hotbarSwap = event.getHotbarButton() >= 0 ? player.getInventory().getItem(event.getHotbarButton()) : null;
        ItemStack offhandSwap = event.getClick() == ClickType.SWAP_OFFHAND ? player.getInventory().getItemInOffHand() : null;
        boolean topSlot = event.getRawSlot() < session.inventory().getSize();

        if (topSlot && (isBackpackItem(current) || isBackpackItem(cursor) || isBackpackItem(hotbarSwap) || isBackpackItem(offhandSwap))) {
            event.setCancelled(true);
            SfxValidationDiagnostics.log(plugin, "backpack", "blocked nested backpack topSlot=" + event.getRawSlot() + " click=" + event.getClick());
            return;
        }
        if (!topSlot && event.isShiftClick() && isBackpackItem(current)) {
            event.setCancelled(true);
            SfxValidationDiagnostics.log(plugin, "backpack", "blocked shift nested backpack player=" + player.getName());
            return;
        }
        if ("sf:cooler".equals(session.itemId()) && !topSlot && event.isShiftClick() && !isCoolerDrink(current)) {
            event.setCancelled(true);
            SfxValidationDiagnostics.log(plugin, "backpack", "blocked cooler shift item=" + (current == null ? "empty" : current.getType()));
            return;
        }
        if ("sf:cooler".equals(session.itemId()) && !isAllowedInCooler(current, cursor, topSlot, event.getClick())) {
            event.setCancelled(true);
            SfxValidationDiagnostics.log(plugin, "backpack", "blocked cooler click=" + event.getClick() + " topSlot=" + topSlot);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        OpenBackpackSession session = sessions.get(player.getUniqueId());
        if (session == null || event.getView().getTopInventory() != session.inventory()) {
            return;
        }
        int topSize = session.inventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (!touchesTop) {
            return;
        }
        if (event.getNewItems().values().stream().anyMatch(this::isBackpackItem)) {
            event.setCancelled(true);
            return;
        }
        if ("sf:cooler".equals(session.itemId()) && event.getNewItems().values().stream().anyMatch(item -> !isCoolerDrink(item))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodLoss(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getFoodLevel() >= player.getFoodLevel()) {
            return;
        }
        consumeFromCooler(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onStarvationDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.STARVATION) {
            return;
        }
        consumeFromCooler(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        OpenBackpackSession session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            openBackpacks.remove(session.uniqueKey());
            persistSession(session);
        }
    }

    private void openBackpack(Player player, ItemStack item, String itemId) {
        if (!researches.canUse(player, itemId)) {
            send(player, "messages.not-researched-item");
            return;
        }
        if (item == null || item.getAmount() != 1) {
            send(player, "messages.backpack.no-stack");
            return;
        }
        Optional<SfxPlayerProfile> optional = profiles.find(player.getUniqueId());
        if (optional.isEmpty()) {
            profiles.request(player, profile -> {
            });
            send(player, "messages.profile.loading");
            return;
        }
        SfxPlayerProfile profile = optional.get();
        BackpackBinding binding = readBinding(item).orElseGet(() -> bindBackpackItem(item, profile, itemId));
        if (!allowForeignOpen() && !binding.ownerId().equals(player.getUniqueId())) {
            send(player, "messages.backpack.foreign-owner");
            return;
        }
        String ownerName = resolveOwnerName(binding.ownerId(), profile);
        profiles.request(binding.ownerId(), ownerName, ownerProfile ->
                runtime.executeForPlayer(player, () -> openProfileBackpack(player, ownerProfile, binding.backpackId(), itemId, backpackTitle(item, itemId), false, true)));
    }

    private void consumeFromCooler(Player player) {
        Optional<SfxPlayerProfile> optional = profiles.find(player.getUniqueId());
        if (optional.isEmpty()) {
            return;
        }
        SfxPlayerProfile profile = optional.get();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (!matchesId(stack, "sf:cooler")) {
                continue;
            }
            BackpackBinding binding = readBinding(stack).orElse(null);
            if (binding == null || !binding.ownerId().equals(player.getUniqueId())) {
                continue;
            }
            SfxBackpackRecord backpack = profile.getBackpack(binding.backpackId());
            if (backpack == null) {
                continue;
            }
            ItemStack[] contents = backpack.contentsCopy();
            for (int i = 0; i < contents.length; i++) {
                ItemStack drink = contents[i];
                if (!isCoolerDrink(drink)) {
                    continue;
                }
                PotionMeta meta = drink.getItemMeta() instanceof PotionMeta potionMeta ? potionMeta : null;
                if (meta == null) {
                    continue;
                }
                for (PotionEffect effect : meta.getCustomEffects()) {
                    player.addPotionEffect(effect);
                }
                player.setFoodLevel(Math.min(20, player.getFoodLevel() + 6));
                player.setSaturation(Math.min(20.0f, player.getSaturation() + 6.0f));
                if (player.getGameMode() != GameMode.CREATIVE) {
                    int amount = drink.getAmount() - 1;
                    contents[i] = amount <= 0 ? null : withAmount(drink, amount);
                    backpack.setContents(contents);
                    profile.markDirty();
                    profiles.saveAsync(profile);
                }
                return;
            }
        }
    }

    private BackpackBinding bindBackpackItem(ItemStack item, SfxPlayerProfile profile, String itemId) {
        int backpackId = profile.nextBackpackId();
        applyBinding(item, profile.ownerId(), backpackId, itemId);
        profile.getOrCreateBackpack(backpackId, backpackSize(itemId));
        profile.markDirty();
        profiles.saveAsync(profile);
        return new BackpackBinding(profile.ownerId(), backpackId);
    }

    private ItemStack createBoundBackpackItem(String itemId, UUID ownerId, int backpackId) {
        ItemStack item = items.create(itemId);
        applyBinding(item, ownerId, backpackId, itemId);
        return item;
    }

    private void applyBinding(ItemStack item, UUID ownerId, int backpackId, String itemId) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Backpack item meta missing: " + itemId);
        }
        meta.getPersistentDataContainer().set(backpackOwnerKey, PersistentDataType.STRING, ownerId.toString());
        meta.getPersistentDataContainer().set(backpackIdKey, PersistentDataType.INTEGER, backpackId);
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        String idText = ownerId + "#" + backpackId;
        for (int i = 0; i < lore.size(); i++) {
            String plain = PlainTextComponentSerializer.plainText().serialize(lore.get(i));
            if (plain.contains("<ID>")) {
                lore.set(i, Text.legacy(plain.replace("<ID>", idText)));
            }
        }
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private Optional<BackpackBinding> readBinding(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        String owner = meta.getPersistentDataContainer().get(backpackOwnerKey, PersistentDataType.STRING);
        Integer id = meta.getPersistentDataContainer().get(backpackIdKey, PersistentDataType.INTEGER);
        if (owner == null || id == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BackpackBinding(UUID.fromString(owner), id));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private boolean isAllowedInCooler(ItemStack current, ItemStack cursor, boolean topSlot, ClickType clickType) {
        if (!topSlot) {
            return true;
        }
        if (clickType == ClickType.NUMBER_KEY || clickType == ClickType.SWAP_OFFHAND) {
            return false;
        }
        return (current == null || current.getType().isAir() || isCoolerDrink(current))
                && (cursor == null || cursor.getType().isAir() || isCoolerDrink(cursor));
    }

    private boolean isCoolerDrink(ItemStack item) {
        String itemId = items.readMarker(item).map(SfxItemMarker::itemId).orElse(null);
        return itemId != null && COOLER_DRINK_IDS.contains(itemId);
    }

    private boolean isBackpackItem(ItemStack item) {
        String itemId = items.readMarker(item).map(SfxItemMarker::itemId).orElse(null);
        return itemId != null && BACKPACK_IDS.contains(itemId);
    }

    private boolean matchesId(ItemStack item, String expected) {
        return expected.equals(items.readMarker(item).map(SfxItemMarker::itemId).orElse(null));
    }

    private int backpackSize(String itemId) {
        return switch (itemId) {
            case "sf:small_backpack" -> 9;
            case "sf:medium_backpack" -> 18;
            case "sf:large_backpack", "sf:cooler" -> 27;
            case "sf:woven_backpack", "sf:bound_backpack" -> 36;
            case "sf:gilded_backpack" -> 45;
            case "sf:radiant_backpack", "sf:restored_backpack" -> 54;
            default -> 27;
        };
    }

    private int normalizeBackpackSize(int raw) {
        return switch (raw) {
            case 1, 9 -> 9;
            case 2, 18 -> 18;
            case 3, 27 -> 27;
            case 4, 36 -> 36;
            case 5, 45 -> 45;
            case 6, 54 -> 54;
            default -> throw new IllegalArgumentException("Unsupported backpack size: " + raw);
        };
    }

    private String itemIdForSize(int size) {
        return switch (normalizeBackpackSize(size)) {
            case 9 -> "sf:small_backpack";
            case 18 -> "sf:medium_backpack";
            case 27 -> "sf:large_backpack";
            case 36 -> "sf:woven_backpack";
            case 45 -> "sf:gilded_backpack";
            case 54 -> "sf:radiant_backpack";
            default -> "sf:large_backpack";
        };
    }

    private String backpackTitle(ItemStack item, String itemId) {
        return items.readMarker(item)
                .flatMap(marker -> Optional.ofNullable(item.getItemMeta()))
                .flatMap(meta -> Optional.ofNullable(meta.displayName()))
                .map(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()::serialize)
                .orElse(PlainTextComponentSerializer.plainText().serialize(localization.itemName(itemId)));
    }

    private void openProfileBackpack(Player player, SfxPlayerProfile ownerProfile, int backpackId, String itemId, String title, boolean force, boolean createIfMissing) {
        String uniqueKey = uniqueKey(ownerProfile.ownerId(), backpackId);
        UUID viewer = openBackpacks.get(uniqueKey);
        if (viewer != null && !viewer.equals(player.getUniqueId())) {
            if (!force) {
                send(player, "messages.backpack.already-open");
                return;
            }
            Player existingViewer = plugin.getServer().getPlayer(viewer);
            OpenBackpackSession existingSession = sessions.remove(viewer);
            if (existingSession != null) {
                openBackpacks.remove(existingSession.uniqueKey());
                persistSession(existingSession);
            }
            if (existingViewer != null) {
                runtime.executeForPlayer(existingViewer, existingViewer::closeInventory);
            }
        }

        SfxBackpackRecord backpack = createIfMissing
                ? ownerProfile.getOrCreateBackpack(backpackId, backpackSize(itemId))
                : ownerProfile.getBackpack(backpackId);
        if (backpack == null) {
            send(player, "messages.backpack.not-found");
            return;
        }
        OpenBackpackSession previous = sessions.remove(player.getUniqueId());
        if (previous != null) {
            openBackpacks.remove(previous.uniqueKey());
            persistSession(previous);
        }
        Inventory inventory = plugin.getServer().createInventory(new BackpackHolder(ownerProfile.ownerId(), backpackId), backpack.size(), Component.text(title));
        inventory.setContents(backpack.contentsCopy());
        sessions.put(player.getUniqueId(), new OpenBackpackSession(uniqueKey, ownerProfile.ownerId(), backpackId, itemId, inventory));
        openBackpacks.put(uniqueKey, player.getUniqueId());
        player.openInventory(inventory);
    }

    private void persistSession(OpenBackpackSession session) {
        Optional<SfxPlayerProfile> optional = profiles.find(session.ownerId());
        if (optional.isEmpty()) {
            return;
        }
        SfxPlayerProfile profile = optional.get();
        SfxBackpackRecord backpack = profile.getOrCreateBackpack(session.backpackId(), session.inventory().getSize());
        backpack.setContents(session.inventory().getContents());
        profile.markDirty();
        profiles.saveAsync(profile);
    }

    private boolean allowForeignOpen() {
        return plugin.getConfig().getBoolean("backpacks.allow-foreign-open", true);
    }

    private String resolveOwnerName(UUID ownerId, SfxPlayerProfile fallbackProfile) {
        Optional<SfxPlayerProfile> optional = profiles.find(ownerId);
        if (optional.isPresent() && optional.get().lastKnownName() != null && !optional.get().lastKnownName().isBlank()) {
            return optional.get().lastKnownName();
        }
        if (fallbackProfile.ownerId().equals(ownerId) && fallbackProfile.lastKnownName() != null && !fallbackProfile.lastKnownName().isBlank()) {
            return fallbackProfile.lastKnownName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(ownerId);
        return offline.getName() == null ? ownerId.toString() : offline.getName();
    }

    private void denyItemUse(PlayerInteractEvent event) {
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setCancelled(true);
    }

    private ItemStack itemInHand(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null) {
            return item;
        }
        return event.getPlayer().getInventory().getItemInMainHand();
    }

    private String uniqueKey(UUID ownerId, int backpackId) {
        return ownerId + "#" + backpackId;
    }

    private ItemStack withAmount(ItemStack original, int amount) {
        ItemStack copy = original.clone();
        copy.setAmount(Math.max(1, amount));
        return copy;
    }

    private void send(Player player, String key) {
        player.sendMessage(Text.prefixed(plugin, localization.text(key)));
    }


}
