package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.api.item.SfxRecipeSlot;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.inventory.ItemStack;


record ManualRecipeHash(long high, long low) {
    private static final long HIGH_SEED = 0x9E3779B97F4A7C15L;
    private static final long LOW_SEED = 0xC2B2AE3D27D4EB4FL;
    private static final long HIGH_PRIME = 0x100000001B3L;
    private static final long LOW_PRIME = 0x9E3779B185EBCA87L;

    static ManualRecipeHash orderedRecipe(List<SfxRecipeSlot> slots) {
        List<String> identities = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            SfxRecipeSlot slot = i < slots.size() ? slots.get(i) : null;
            identities.add(identity(slot));
        }
        return hash(identities, true);
    }

    static ManualRecipeHash unorderedRecipe(List<SfxRecipeSlot> slots) {
        List<String> identities = new ArrayList<>();
        for (SfxRecipeSlot slot : slots) {
            String identity = identity(slot);
            if (!identity.isEmpty() && !identities.contains(identity)) {
                identities.add(identity);
            }
        }
        Collections.sort(identities);
        return hash(identities, false);
    }

    static ManualRecipeHash orderedInput(ItemStack[] contents, cc.theends6.sfx.api.item.SfxItems items) {
        List<String> identities = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            identities.add(identity(i < contents.length ? contents[i] : null, items));
        }
        return hash(identities, true);
    }

    static ManualRecipeHash unorderedInput(ItemStack[] contents, cc.theends6.sfx.api.item.SfxItems items) {
        List<String> identities = new ArrayList<>();
        for (ItemStack stack : contents) {
            String identity = identity(stack, items);
            if (!identity.isEmpty() && !identities.contains(identity)) {
                identities.add(identity);
            }
        }
        Collections.sort(identities);
        return hash(identities, false);
    }

    private static String identity(SfxRecipeSlot slot) {
        if (slot == null || slot.isEmpty()) return "";
        return slot.isSfxItem() ? "s:" + slot.sfxItemId() : "m:" + slot.material().getKey();
    }

    private static String identity(ItemStack stack, cc.theends6.sfx.api.item.SfxItems items) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) return "";
        return items.readMarker(stack)
                .map(marker -> "s:" + marker.itemId())
                .orElseGet(() -> "m:" + stack.getType().getKey());
    }

    private static ManualRecipeHash hash(List<String> identities, boolean positional) {
        long high = HIGH_SEED ^ identities.size();
        long low = LOW_SEED ^ Long.rotateLeft(identities.size(), 17);
        for (int position = 0; position < identities.size(); position++) {
            byte[] bytes = identities.get(position).getBytes(StandardCharsets.UTF_8);
            high ^= positional ? position * HIGH_SEED : bytes.length;
            low ^= positional ? Long.rotateLeft(position * LOW_SEED, 23) : bytes.length;
            for (byte value : bytes) {
                high = (high ^ (value & 0xffL)) * HIGH_PRIME;
                low = Long.rotateLeft(low ^ (value & 0xffL), 27) * LOW_PRIME;
            }
            high ^= 0xffL;
            low ^= 0x9dL;
        }
        high ^= high >>> 33;
        high *= 0xff51afd7ed558ccdL;
        high ^= high >>> 33;
        low ^= low >>> 29;
        low *= 0xc4ceb9fe1a85ec53L;
        low ^= low >>> 32;
        return new ManualRecipeHash(high, low);
    }
}
