package cc.theends6.sfx.internal.ui;

import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public final class SfxMachineStatusIconRenderer {
    private final SfxLocalization localization;

    public SfxMachineStatusIconRenderer(SfxLocalization localization) {
        this.localization = localization;
    }

    public ItemStack render(SfxMachineStatusView view) {
        ItemStack stack = view.icon() == null ? new ItemStack(view.material()) : view.icon();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.displayName(view.name());

        List<Component> lore = new ArrayList<>();
        if (view.showProgress()) {
            lore.add(progressBarLine(view.progressCurrent(), view.progressTotal()));
            if (view.timeLeftTicks() >= 0) {
                lore.add(Component.empty());
                lore.add(localization.component(
                        "electric-ui.progress.time-left",
                        "<gray>{time}</gray>",
                        Map.of("time", formatTimeLeft(view.timeLeftTicks()))));
            }
            if (view.showDurability() && !isGlassPane(view.material())) {
                applyProgressDamage(stack, meta, view.progressCurrent(), view.progressTotal());
            }
        }
        lore.addAll(view.statusLore());
        lore.add(Component.empty());
        lore.add(localization.component(
                "electric-ui.energy-buffer",
                "<gray>Stored: </gray><yellow>{stored}</yellow><gray>/</gray><yellow>{capacity}</yellow><gray> J</gray>",
                Map.of("stored", view.storedEnergy(), "capacity", view.energyCapacity())));
        if (view.energyPerTick() != null) {
            lore.add(localization.component(
                    "electric-ui.energy-consumption",
                    "<gray>Consumption: </gray><yellow>{energy}</yellow><gray> J/t</gray>",
                    Map.of("energy", view.energyPerTick())));
        }
        if (view.generatedPerTick() != null) {
            lore.add(localization.component(
                    "electric-ui.energy-generation",
                    "<gray>Generation: </gray><yellow>{energy}</yellow><gray> J/t</gray>",
                    Map.of("energy", view.generatedPerTick())));
        }
        lore.addAll(view.extraLore());
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    public Component progressBarLine(int currentWork, int totalWork) {
        int total = Math.max(1, totalWork);
        float progressPercentage = Math.round(((Math.max(0, Math.min(currentWork, total)) * 100.0F) / total) * 100.0F) / 100.0F;
        int filled = Math.min(20, Math.max(0, (int) (progressPercentage / 5.0F)));
        StringBuilder builder = new StringBuilder();
        builder.append(progressColor(progressPercentage));
        for (int i = 0; i < filled; i++) {
            builder.append(':');
        }
        builder.append("&7");
        for (int i = filled; i < 20; i++) {
            builder.append(':');
        }
        builder.append(" - ").append(progressPercentage).append('%');
        return Text.legacy(builder.toString());
    }

    public String formatTimeLeft(int ticks) {
        int seconds = Math.max(0, (int) Math.ceil(ticks / 20.0D));
        int minutes = seconds / 60;
        int remainingSeconds = seconds - minutes * 60;
        if (minutes > 0) {
            return localization.text("electric-ui.time.minutes-seconds", "{minutes}m {seconds}s", Map.of("minutes", minutes, "seconds", remainingSeconds));
        }
        return localization.text("electric-ui.time.seconds", "{seconds}s", Map.of("seconds", remainingSeconds));
    }

    private String progressColor(float percentage) {
        if (percentage < 16.0F) {
            return "&4";
        }
        if (percentage < 32.0F) {
            return "&c";
        }
        if (percentage < 48.0F) {
            return "&6";
        }
        if (percentage < 64.0F) {
            return "&e";
        }
        if (percentage < 80.0F) {
            return "&2";
        }
        return "&a";
    }

    private boolean isGlassPane(Material material) {
        return material != null && material.name().endsWith("_STAINED_GLASS_PANE");
    }

    private void applyProgressDamage(ItemStack stack, ItemMeta meta, int current, int total) {
        int max = stack.getType().getMaxDurability();
        if (max <= 0) {
            max = applyCustomMaxDamage(meta, 100);
        }
        if (max <= 0) {
            return;
        }
        int safeTotal = Math.max(1, total);
        int safeCurrent = Math.max(0, Math.min(safeTotal, current));
        int visible = Math.max(1, (int) Math.round((safeCurrent / (double) safeTotal) * max));
        int damage = Math.max(0, Math.min(max - 1, max - visible));
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(damage);
            return;
        }
        applyCustomDamage(meta, damage);
    }

    private int applyCustomMaxDamage(ItemMeta meta, int maxDamage) {
        for (Class<?> type = meta.getClass(); type != null; type = type.getSuperclass()) {
            Integer applied = invokeMaxDamage(type, meta, maxDamage);
            if (applied != null) {
                return applied;
            }
        }
        for (Class<?> type : meta.getClass().getInterfaces()) {
            Integer applied = invokeMaxDamage(type, meta, maxDamage);
            if (applied != null) {
                return applied;
            }
        }
        return 0;
    }

    private Integer invokeMaxDamage(Class<?> type, ItemMeta meta, int maxDamage) {
        try {
            Method method = type.getMethod("setMaxDamage", Integer.class);
            method.invoke(meta, maxDamage);
            return maxDamage;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            try {
                Method method = type.getMethod("setMaxDamage", Integer.TYPE);
                method.invoke(meta, maxDamage);
                return maxDamage;
            } catch (ReflectiveOperationException | RuntimeException ignoredAgain) {
                return null;
            }
        }
    }

    private void applyCustomDamage(ItemMeta meta, int damage) {
        for (Class<?> type = meta.getClass(); type != null; type = type.getSuperclass()) {
            if (invokeDamage(type, meta, damage)) {
                return;
            }
        }
        for (Class<?> type : meta.getClass().getInterfaces()) {
            if (invokeDamage(type, meta, damage)) {
                return;
            }
        }
    }

    private boolean invokeDamage(Class<?> type, ItemMeta meta, int damage) {
        try {
            Method method = type.getMethod("setDamage", Integer.TYPE);
            method.invoke(meta, damage);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            try {
                Method method = type.getMethod("setDamage", Integer.class);
                method.invoke(meta, damage);
                return true;
            } catch (ReflectiveOperationException | RuntimeException ignoredAgain) {
                return false;
            }
        }
    }
}
