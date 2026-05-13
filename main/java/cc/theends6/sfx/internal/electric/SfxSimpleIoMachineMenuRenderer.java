package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class SfxSimpleIoMachineMenuRenderer {
    private static final int STATUS_SLOT = 4;
    private static final int[] IO_SLOTS = SfxElectricMachineDefinition.SIMPLE_IO_SLOTS;

    private final SfxItems items;
    private final SfxLocalization localization;

    SfxSimpleIoMachineMenuRenderer(SfxItems items, SfxLocalization localization) {
        this.items = items;
        this.localization = localization;
    }

    void render(UUID viewerId, SfxElectricMachineDefinition definition, Inventory inventory, SfxElectricMachineState state, SfxElectricMachineRenderStatus status) {
        fillBackground(inventory);
        inventory.setItem(STATUS_SLOT, statusIcon(definition, state, status));
        if (definition.inputSlots().length > 0 && definition.outputSlots().length == 0) {
            for (int index = 0; index < definition.inputSlots().length; index++) {
                int slot = definition.inputSlots()[index];
                inventory.setItem(slot, state.input(index) == null ? null : state.input(index).toItemStack(items));
            }
        } else if (definition.outputSlots().length > 0 && definition.inputSlots().length == 0) {
            for (int index = 0; index < definition.outputSlots().length; index++) {
                int slot = definition.outputSlots()[index];
                inventory.setItem(slot, state.output(index) == null ? null : state.output(index).toItemStack(items));
            }
        }
    }

    private void fillBackground(Inventory inventory) {
        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        for (int slot : IO_SLOTS) {
            inventory.setItem(slot, null);
        }
        inventory.setItem(STATUS_SLOT, null);
    }

    private ItemStack statusIcon(SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricMachineRenderStatus status) {
        SimpleStatus simpleStatus = simpleStatus(definition, state, status);
        Material material = switch (simpleStatus.kind()) {
            case WORKING -> Material.LIME_STAINED_GLASS_PANE;
            case ERROR -> Material.RED_STAINED_GLASS_PANE;
            case WARNING -> Material.ORANGE_STAINED_GLASS_PANE;
            case WAITING -> definition.progressMaterial();
        };
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.displayName(simpleStatus.name());
        List<Component> lore = new ArrayList<>();
        lore.add(simpleStatus.lore());
        lore.add(Component.empty());
        lore.add(localization.component(
                "electric-ui.energy-buffer",
                "<gray>储能: </gray><yellow>{stored}</yellow><gray>/</gray><yellow>{capacity}</yellow><gray> J</gray>",
                Map.of("stored", state.storedEnergy(), "capacity", definition.energyCapacity())));
        lore.add(localization.component(
                "electric-ui.progress.speed",
                "<gray>速度：</gray><aqua>{speed}x</aqua>",
                Map.of("speed", definition.speed())));
        if (isExpCollector(definition)) {
            int xpProgress = Math.min(10, state.specialData());
            lore.add(progressBarLine(xpProgress, 10));
            lore.add(localization.component(
                    "electric-ui.simple-io.xp-progress",
                    "<gray>经验进度：</gray><white>{current}</white><gray>/</gray><white>{total}</white>",
                    Map.of("current", xpProgress, "total", 10)));
        } else if (state.hasProgress()) {
            int totalWork = Math.max(1, state.activeBaseTicks() * 20);
            int currentWork = Math.min(totalWork, state.progressWork());
            int remainingWork = Math.max(0, totalWork - currentWork);
            int remainingTicks = (int) Math.ceil(remainingWork / (double) Math.max(1, definition.speed()));
            lore.add(progressBarLine(currentWork, totalWork));
            lore.add(localization.component(
                    "electric-ui.progress.time-left",
                    "<gray>{time}</gray>",
                    Map.of("time", formatTimeLeft(Math.max(0, remainingTicks / 20)))));
        } else {
            lore.add(localization.component(
                    "electric-ui.simple-io.check-interval",
                    "<gray>检查间隔：</gray><white>{ticks}</white><gray> tick</gray>",
                    Map.of("ticks", 10)));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private SimpleStatus simpleStatus(SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricMachineRenderStatus status) {
        if (status == SfxElectricMachineRenderStatus.NO_POWER) {
            return new SimpleStatus(SimpleStatusKind.ERROR,
                    localization.component("electric-ui.no-power.name", "<red>电力不足</red>"),
                    localization.component("electric-ui.no-power.lore", "<gray>为机器充能后继续工作。</gray>"));
        }
        if (status == SfxElectricMachineRenderStatus.BLOCKED_OUTPUT || status == SfxElectricMachineRenderStatus.OUTPUT_FULL) {
            return new SimpleStatus(SimpleStatusKind.ERROR,
                    localization.component("electric-ui.output-full.name", "<red>输出已满</red>"),
                    localization.component("electric-ui.output-full.lore", "<gray>清理输出槽后才能继续工作。</gray>"));
        }
        if (isExpCollector(definition)) {
            if (status == SfxElectricMachineRenderStatus.WORKING) {
                return new SimpleStatus(SimpleStatusKind.WORKING,
                        localization.component("electric-ui.simple-io.xp-working.name", "<yellow>收集经验中</yellow>"),
                        localization.component("electric-ui.simple-io.xp-working.lore", "<gray>正在收集附近所有经验球。</gray>"));
            }
            return new SimpleStatus(SimpleStatusKind.WAITING,
                    localization.component("electric-ui.simple-io.xp-waiting.name", "<gray>等待经验球中</gray>"),
                    localization.component("electric-ui.simple-io.xp-waiting.lore", "<gray>范围内出现经验球后会自动收集。</gray>"));
        }
        if (status == SfxElectricMachineRenderStatus.NO_INPUT || !state.hasAnyInput()) {
            return new SimpleStatus(SimpleStatusKind.WARNING,
                    localization.component("electric-ui.simple-io.no-input.name", "<gold>缺少输入</gold>"),
                    localization.component("electric-ui.simple-io.no-input.lore", "<gray>放入 {input} 后开始工作。</gray>", Map.of("input", inputName(definition))));
        }
        if (status == SfxElectricMachineRenderStatus.NO_TARGET || status == SfxElectricMachineRenderStatus.NO_RECIPE) {
            return new SimpleStatus(SimpleStatusKind.WARNING,
                    localization.component("electric-ui.simple-io.no-target.name", "<gold>无可作用目标</gold>"),
                    localization.component("electric-ui.simple-io.no-target.lore", "<gray>{target}</gray>", Map.of("target", noTargetText(definition))));
        }
        if (status == SfxElectricMachineRenderStatus.WORKING || state.hasProgress()) {
            return new SimpleStatus(SimpleStatusKind.WORKING,
                    localization.component("electric-ui.progress.name", "<yellow>工作中</yellow>"),
                    localization.component("electric-ui.simple-io.working.lore", "<gray>机器正在处理，完成时会触发效果。</gray>"));
        }
        return new SimpleStatus(SimpleStatusKind.WAITING,
                localization.component("electric-ui.simple-io.waiting.name", "<gray>等待下一次检查</gray>"),
                localization.component("electric-ui.simple-io.waiting.lore", "<gray>机器会在下一次 SFX Tick 检查目标。</gray>"));
    }

    private boolean isExpCollector(SfxElectricMachineDefinition definition) {
        return definition.id().equals("sf:xp_collector");
    }

    private String inputName(SfxElectricMachineDefinition definition) {
        return switch (definition.id()) {
            case "sf:auto_breeder", "sf:animal_growth_accelerator" -> "有机食物";
            case "sf:crop_growth_accelerator", "sf:crop_growth_accelerator_2", "sf:tree_growth_accelerator" -> "有机肥料";
            default -> "输入物品";
        };
    }

    private String noTargetText(SfxElectricMachineDefinition definition) {
        return switch (definition.id()) {
            case "sf:auto_breeder" -> "范围内没有可繁殖动物。";
            case "sf:animal_growth_accelerator" -> "范围内没有幼年动物。";
            case "sf:crop_growth_accelerator", "sf:crop_growth_accelerator_2" -> "范围内没有可生长作物。";
            case "sf:tree_growth_accelerator" -> "范围内没有树苗。";
            default -> "范围内没有可作用目标。";
        };
    }

    private Component progressBarLine(int currentWork, int totalWork) {
        int total = Math.max(1, totalWork);
        float progressPercentage = Math.round(((Math.max(0, currentWork) * 100.0F) / total) * 100.0F) / 100.0F;
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

    private String formatTimeLeft(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds - minutes * 60;
        if (minutes > 0) {
            return minutes + "m " + remainingSeconds + "s";
        }
        return remainingSeconds + "s";
    }

    private enum SimpleStatusKind {
        WAITING,
        WORKING,
        WARNING,
        ERROR
    }

    private record SimpleStatus(SimpleStatusKind kind, Component name, Component lore) {
    }

    private ItemStack namedItem(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
