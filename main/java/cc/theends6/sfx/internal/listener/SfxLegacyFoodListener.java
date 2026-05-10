package cc.theends6.sfx.internal.listener;

import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class SfxLegacyFoodListener implements Listener {
    public static final Set<String> VANILLA_CONSUMABLE_IDS = Set.of(
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
    private static final List<String> DEFAULT_FORTUNES = List.of(
            "&7Help me, I am trapped in a Fortune Cookie Factory!",
            "&7You will die tomorrow...     by a Creeper",
            "&7At some point in your Life something bad will happen!!!",
            "&7Next week you will notice that this is not the real world, you are in a computer game",
            "&7This cookie will taste good in a few seconds",
            "&7The last word you will hear is gonna be \"EXTERMINATE!!!\"",
            "&7Whatever you do, do not hug a Creeper... I tried it. It feels good, but it's not worth it.",
            "&742. The answer is 42.",
            "&7A Walshy a day will keep the troubles away.",
            "&7Never dig straight down!",
            "&7Tis but a flesh wound!",
            "&7Always look on the bright side of life!",
            "&7This one was actually a Biscuit and not a Cookie",
            "&7Neon signs are LIT!"
    );

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxLocalization localization;

    public SfxLegacyFoodListener(JavaPlugin plugin, SfxRuntime runtime, SfxItems items, SfxLocalization localization) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
    }

    boolean handleItemUse(PlayerInteractEvent event, String itemId) {
        if (!"sf:magic_sugar".equals(itemId)) {
            return false;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }
        Block clicked = event.getClickedBlock();
        if (clicked != null && clicked.getType().isInteractable()) {
            return true;
        }

        ItemStack item = itemInHand(event);
        denyItemUse(event);
        Player player = event.getPlayer();
        consumeOne(item, player);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.0f, 1.0f);
        player.addPotionEffect(new PotionEffect(resolvePotion("SPEED"), 600, 3));
        return true;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        String itemId = itemId(event.getItem());
        if (itemId == null) {
            return;
        }

        switch (itemId) {
            case "sf:fortune_cookie" -> player.sendMessage(Text.prefixed(plugin, randomFortune()));
            case "sf:diet_cookie" -> {
                player.sendMessage(Text.prefixed(plugin, localization.text("messages.diet-cookie", "&eYou are starting to feel very light...")));
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.0f, 1.0f);
                player.removePotionEffect(resolvePotion("LEVITATION"));
                player.addPotionEffect(new PotionEffect(resolvePotion("LEVITATION"), 60, 1));
            }
            case "sf:monster_jerky" -> runtime.executeForPlayerLater(player, 1L, () -> {
                PotionEffectType hunger = resolvePotion("HUNGER");
                if (hunger != null) {
                    player.removePotionEffect(hunger);
                }
                player.addPotionEffect(new PotionEffect(resolvePotion("SATURATION"), 5, 0));
            });
            case "sf:golden_apple_juice" -> player.addPotionEffect(new PotionEffect(resolvePotion("ABSORPTION"), 20 * 20, 0));
            default -> {
            }
        }
    }

    private String randomFortune() {
        List<String> fortunes = localization.list("messages.fortune-cookie");
        if (fortunes.isEmpty()) {
            fortunes = DEFAULT_FORTUNES;
        }
        return fortunes.get(ThreadLocalRandom.current().nextInt(fortunes.size()));
    }

    private void consumeOne(ItemStack item, Player player) {
        if (item == null || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        int amount = item.getAmount() - 1;
        if (amount <= 0) {
            item.setAmount(0);
        } else {
            item.setAmount(amount);
        }
    }

    private String itemId(ItemStack item) {
        return items.readMarker(item).map(SfxItemMarker::itemId).orElse(null);
    }

    private ItemStack itemInHand(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null) {
            return item;
        }
        return event.getPlayer().getInventory().getItemInMainHand();
    }

    private void denyItemUse(PlayerInteractEvent event) {
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setCancelled(true);
    }

    private PotionEffectType resolvePotion(String rawName) {
        PotionEffectType type = PotionEffectType.getByName(rawName);
        if (type != null) {
            return type;
        }
        String key = switch (rawName.toUpperCase(Locale.ROOT)) {
            case "SLOW" -> "slowness";
            case "JUMP" -> "jump_boost";
            case "SATURATION" -> "saturation";
            case "LEVITATION" -> "levitation";
            case "ABSORPTION" -> "absorption";
            default -> rawName.toLowerCase(Locale.ROOT);
        };
        return PotionEffectType.getByKey(NamespacedKey.minecraft(key));
    }
}
