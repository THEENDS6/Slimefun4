package cc.theends6.sfx.internal.entity;

import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;


public final class SfxEntityKillAttribution {
    private static final String ANDROID_KILLER = "sfx_android_killer";

    private SfxEntityKillAttribution() {
    }

    public static void damageAsAndroid(JavaPlugin plugin, LivingEntity target, double damage) {
        target.removeMetadata(ANDROID_KILLER, plugin);
        target.setMetadata(ANDROID_KILLER, new FixedMetadataValue(plugin, Boolean.TRUE));
        try {
            target.damage(damage);
        } finally {
            target.removeMetadata(ANDROID_KILLER, plugin);
        }
    }

    public static boolean isAndroidKill(JavaPlugin plugin, LivingEntity entity) {
        for (MetadataValue value : entity.getMetadata(ANDROID_KILLER)) {
            if (value.getOwningPlugin() == plugin && value.asBoolean()) {
                return true;
            }
        }
        return false;
    }
}
