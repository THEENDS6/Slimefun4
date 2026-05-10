package cc.theends6.sfx.internal.listener;

import cc.theends6.sfx.api.item.SfxItems;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * First-stage safety layer for SFX identity items.
 *
 * <p>SFX items are plugin-owned logical items. Until a dedicated machine / recipe
 * runtime explicitly consumes them, they should not be silently transformed by
 * vanilla systems. The default policy is deny-by-default for vanilla behavior:
 * if an item has an SFX marker and no SFX subsystem handled the interaction,
 * vanilla should not consume, place, dye, shoot, rename, grind or craft it.</p>
 */
public final class SfxVanillaGuardListener implements Listener {
    private static final Set<String> VANILLA_USE_IDS = Set.of(
            "sf:explosive_bow",
            "sf:icy_bow",
            "sf:fortune_cookie",
            "sf:diet_cookie",
            "sf:monster_jerky",
            "sf:apple_juice",
            "sf:melon_juice",
            "sf:carrot_juice",
            "sf:pumpkin_juice",
            "sf:sweet_berry_juice",
            "sf:glow_berry_juice",
            "sf:golden_apple_juice",
            "sf:beef_jerky",
            "sf:pork_jerky",
            "sf:chicken_jerky",
            "sf:mutton_jerky",
            "sf:rabbit_jerky",
            "sf:fish_jerky",
            "sf:kelp_cookie",
            "sf:christmas_milk",
            "sf:christmas_chocolate_milk",
            "sf:christmas_egg_nog",
            "sf:christmas_apple_cider",
            "sf:christmas_cookie",
            "sf:christmas_fruit_cake",
            "sf:christmas_apple_pie",
            "sf:christmas_hot_chocolate",
            "sf:christmas_cake",
            "sf:christmas_caramel_apple",
            "sf:christmas_chocolate_apple",
            "sf:carrot_pie",
            "sf:easter_apple_pie"
    );
    private static final Set<String> VANILLA_BOW_IDS = Set.of("sf:explosive_bow", "sf:icy_bow");

    private final SfxItems items;

    public SfxVanillaGuardListener(SfxItems items) {
        this.items = Objects.requireNonNull(items, "items");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (!isSfxItem(item) || items.readGuideMode(item).isPresent()) {
            return;
        }
        if (isVanillaUseAllowed(item)) {
            return;
        }

        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_BLOCK) {
            boolean explicitlyDenied = items.readMarker(item)
                    .map(marker -> marker.flags().contains("deny-left-click-block"))
                    .orElse(false);
            if (explicitlyDenied) {
                event.setCancelled(true);
            }
            return;
        }
        if (action == Action.LEFT_CLICK_AIR) {
            return;
        }

        event.setUseItemInHand(Event.Result.DENY);
        if (action == Action.RIGHT_CLICK_BLOCK) {
            event.setUseInteractedBlock(Event.Result.ALLOW);
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack handItem = event.getHand() == EquipmentSlot.OFF_HAND
                ? event.getPlayer().getInventory().getItemInOffHand()
                : event.getPlayer().getInventory().getItemInMainHand();
        if (isSfxItem(handItem)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        if (isSfxItem(event.getItem()) && !isVanillaUseAllowed(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (isVanillaBowAllowed(event.getBow())) {
            return;
        }
        if (isSfxItem(event.getBow()) || isSfxItem(event.getConsumable())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isSfxItem(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        if (isSfxItem(event.getFuel())) {
            event.setCancelled(true);
            event.setBurning(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        if (isSfxItem(event.getSource())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (isSfxItem(event.getInventory().getItem(0)) || isSfxItem(event.getInventory().getItem(1))) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (isSfxItem(event.getInventory().getItem(0)) || isSfxItem(event.getInventory().getItem(1))) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (isSfxItem(event.getInventory().getInputEquipment())
                || isSfxItem(event.getInventory().getInputMineral())
                || isSfxItem(event.getInventory().getInputTemplate())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (containsSfxItem(event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (containsSfxItem(event.getInventory().getMatrix())) {
            event.setCancelled(true);
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(isSmeltingInventory(event.getView().getTopInventory().getType()))) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ItemStack hotbarSwap = event.getHotbarButton() >= 0 ? event.getWhoClicked().getInventory().getItem(event.getHotbarButton()) : null;
        boolean topSlot = event.getRawSlot() < event.getView().getTopInventory().getSize();
        if (topSlot && isProtectedSmeltingSlot(event.getSlot()) && (isSfxItem(cursor) || isSfxItem(current) || isSfxItem(hotbarSwap))) {
            event.setCancelled(true);
            return;
        }
        if (!topSlot && event.isShiftClick() && isSfxItem(current)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(isSmeltingInventory(event.getView().getTopInventory().getType()))) {
            return;
        }
        boolean touchesProtectedSlot = event.getRawSlots().stream().anyMatch(this::isProtectedSmeltingSlot);
        if (!touchesProtectedSlot) {
            return;
        }
        if (event.getNewItems().values().stream().anyMatch(this::isSfxItem)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (isSmeltingInventory(event.getDestination().getType()) && isSfxItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    private boolean isSmeltingInventory(InventoryType type) {
        return type == InventoryType.FURNACE || type == InventoryType.BLAST_FURNACE || type == InventoryType.SMOKER;
    }

    private boolean isProtectedSmeltingSlot(int slot) {
        return slot == 0 || slot == 1;
    }

    private boolean containsSfxItem(ItemStack[] matrix) {
        return matrix != null && Arrays.stream(matrix).anyMatch(this::isSfxItem);
    }

    private boolean isSfxItem(ItemStack item) {
        return items.isSfxItem(item);
    }

    private boolean isVanillaUseAllowed(ItemStack item) {
        return items.readMarker(item)
                .map(marker -> VANILLA_USE_IDS.contains(marker.itemId()))
                .orElse(false);
    }

    private boolean isVanillaBowAllowed(ItemStack item) {
        return items.readMarker(item)
                .map(marker -> VANILLA_BOW_IDS.contains(marker.itemId()))
                .orElse(false);
    }
}
