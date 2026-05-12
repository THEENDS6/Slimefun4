package cc.theends6.sfx.internal.util;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.block.Skull;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

public final class HeadTextures {
    private static final Logger LOGGER = Logger.getLogger(HeadTextures.class.getName());

    private HeadTextures() {
    }

    public static void apply(ItemMeta meta, String textureHash) {
        String normalized = normalize(textureHash);
        if (!(meta instanceof SkullMeta skullMeta) || normalized == null) {
            return;
        }
        try {
            skullMeta.setOwnerProfile(createProfile(normalized));
        } catch (Throwable ex) {
            LOGGER.log(Level.FINE, "Failed to apply head texture", ex);
        }
    }

    public static void apply(Skull skull, String textureHash) {
        String normalized = normalize(textureHash);
        if (skull == null || normalized == null) {
            return;
        }
        try {
            skull.setOwnerProfile(createProfile(normalized));
            skull.update(true, false);
        } catch (Throwable ex) {
            LOGGER.log(Level.FINE, "Failed to apply placed head texture", ex);
        }
    }

    private static String normalize(String textureHash) {
        if (textureHash == null || textureHash.isBlank()) {
            return null;
        }
        String normalized = textureHash.trim().toLowerCase();
        String texturePrefix = "https://textures.minecraft.net/texture/";
        if (normalized.startsWith(texturePrefix)) {
            normalized = normalized.substring(texturePrefix.length());
        }
        if (!normalized.matches("[0-9a-f]{32,128}")) {
            LOGGER.fine("Ignoring invalid SFX head texture hash: " + textureHash);
            return null;
        }
        return normalized;
    }

    private static PlayerProfile createProfile(String textureHash) throws Exception {
        UUID uuid = UUID.nameUUIDFromBytes(textureHash.getBytes(StandardCharsets.UTF_8));
        String name = "SFX-" + textureHash.substring(0, 12);
        PlayerProfile profile = Bukkit.createPlayerProfile(uuid, name);
        PlayerTextures textures = profile.getTextures();
        textures.setSkin(new URL("https://textures.minecraft.net/texture/" + textureHash));
        profile.setTextures(textures);
        return profile;
    }
}
