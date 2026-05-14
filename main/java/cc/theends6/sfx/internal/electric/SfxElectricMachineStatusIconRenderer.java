package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import cc.theends6.sfx.internal.ui.SfxMachineStatusIconRenderer;
import cc.theends6.sfx.internal.ui.SfxMachineStatusView;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

final class SfxElectricMachineStatusIconRenderer {
    private static final int XP_PER_FLASK = 10;

    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxPlayerDataService profiles;
    private final SfxMachineStatusIconRenderer commonStatusIcons;

    SfxElectricMachineStatusIconRenderer(SfxItems items, SfxLocalization localization, SfxPlayerDataService profiles) {
        this.items = items;
        this.localization = localization;
        this.profiles = profiles;
        this.commonStatusIcons = new SfxMachineStatusIconRenderer(localization);
    }

    ItemStack render(UUID viewerId, SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricRecipe recipe, SfxElectricMachineRenderStatus status) {
        SfxElectricMachineRenderStatus effectiveStatus = effectiveStatus(definition, state, status);
        Material material = material(definition, effectiveStatus);
        SfxMachineStatusView.Builder view = SfxMachineStatusView.builder(material, displayName(viewerId, definition, effectiveStatus))
                .energy(state.storedEnergy(), definition.energyCapacity());

        if (effectiveStatus == SfxElectricMachineRenderStatus.WORKING) {
            if (isExpCollector(definition)) {
                int current = Math.min(XP_PER_FLASK, Math.max(0, state.specialData() % XP_PER_FLASK));
                if (state.specialData() >= XP_PER_FLASK) {
                    current = XP_PER_FLASK;
                }
                view.progress(current, XP_PER_FLASK, -1, true)
                        .statusLore(localization.component(
                                "electric-ui.simple-io.xp-progress",
                                "<gray>Stored XP: </gray><white>{current}</white><gray>/</gray><white>{total}</white>",
                                Map.of("current", current, "total", XP_PER_FLASK)));
            } else {
                int totalWork = totalWork(definition, state, recipe);
                int currentWork = Math.min(totalWork, Math.max(0, state.progressWork()));
                int remainingTicks = Math.max(0, (int) Math.ceil((totalWork - currentWork) / (double) Math.max(1, definition.speed())));
                view.progress(currentWork, totalWork, remainingTicks, true);
            }
        }

        Component statusLore = statusLore(definition, recipe, effectiveStatus);
        if (!Component.empty().equals(statusLore)) {
            view.statusLore(statusLore);
        }
        if (definition.energyConsumptionPerTick() > 0) {
            view.consumption(definition.energyConsumptionPerTick());
        }
        if (isExtendedUiEnabled(viewerId)) {
            view.extraLore(localization.component(
                    "electric-ui.progress.speed",
                    "<gray>Speed: </gray><aqua>{speed}x</aqua>",
                    Map.of("speed", definition.speed())));
            if (effectiveStatus == SfxElectricMachineRenderStatus.WORKING && recipe != null && recipe.output() != null) {
                view.extraLore(localization.component(
                        "electric-ui.progress.recipe",
                        "<gray>Recipe: </gray><white>{recipe}</white>",
                        Map.of("recipe", displayStackName(recipe.output()))));
            }
        }
        return commonStatusIcons.render(view.build());
    }

    private SfxElectricMachineRenderStatus effectiveStatus(SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricMachineRenderStatus status) {
        if (status == SfxElectricMachineRenderStatus.PAUSED) {
            return SfxElectricMachineRenderStatus.PAUSED;
        }
        if (status == SfxElectricMachineRenderStatus.BLOCKED_OUTPUT
                || status == SfxElectricMachineRenderStatus.OUTPUT_FULL
                || status == SfxElectricMachineRenderStatus.OVERLAPPING_AREA
                || status == SfxElectricMachineRenderStatus.NO_TARGET
                || status == SfxElectricMachineRenderStatus.NO_RECIPE) {
            return status;
        }
        if (status == SfxElectricMachineRenderStatus.NO_POWER) {
            return SfxElectricMachineRenderStatus.NO_POWER;
        }
        if (state.hasProgress()) {
            if (definition.energyConsumptionPerTick() > 0 && state.storedEnergy() < definition.energyConsumptionPerTick()) {
                return SfxElectricMachineRenderStatus.NO_POWER;
            }
            return SfxElectricMachineRenderStatus.WORKING;
        }
        if (isExpCollector(definition) && (status == SfxElectricMachineRenderStatus.IDLE || status == SfxElectricMachineRenderStatus.NO_INPUT || status == SfxElectricMachineRenderStatus.NO_TARGET)) {
            return SfxElectricMachineRenderStatus.WORKING;
        }
        if (status == SfxElectricMachineRenderStatus.NO_INPUT) {
            return SfxElectricMachineRenderStatus.IDLE;
        }
        return status;
    }


    private Material material(SfxElectricMachineDefinition definition, SfxElectricMachineRenderStatus status) {
        return switch (status) {
            case WORKING -> definition.progressMaterial();
            case IDLE, NO_INPUT -> Material.BLACK_STAINED_GLASS_PANE;
            case PAUSED -> Material.YELLOW_STAINED_GLASS_PANE;
            case NO_POWER, NO_TARGET, NO_RECIPE, BLOCKED_OUTPUT, OUTPUT_FULL, OVERLAPPING_AREA -> Material.RED_STAINED_GLASS_PANE;
        };
    }

    private Component displayName(UUID viewerId, SfxElectricMachineDefinition definition, SfxElectricMachineRenderStatus status) {
        return switch (status) {
            case WORKING -> isExtendedUiEnabled(viewerId)
                    ? localization.component("electric-ui.progress.name", "<yellow>Working</yellow>")
                    : Component.text(" ");
            case NO_POWER -> localization.component("electric-ui.no-power.name", "<red>No Power</red>");
            case NO_INPUT -> localization.component("electric-ui.idle.name", "<gray>Idle</gray>");
            case NO_TARGET -> localization.component("electric-ui.no-target.name", "<red>No Target</red>");
            case BLOCKED_OUTPUT -> localization.component("electric-ui.blocked.name", "<red>Blocked</red>");
            case OUTPUT_FULL -> localization.component("electric-ui.output-full.name", "<red>Output Full</red>");
            case NO_RECIPE -> localization.component("electric-ui.no-recipe.name", "<gray>No Recipe</gray>");
            case OVERLAPPING_AREA -> localization.component("electric-ui.overlapping-area.name", "<red>Work Area Conflict</red>");
            case PAUSED -> isAssembler(definition)
                    ? localization.component("configurable-ui.assembler.paused.name", "<yellow>Paused</yellow>")
                    : localization.component("electric-ui.paused.name", "<yellow>Paused</yellow>");
            case IDLE -> localization.component("electric-ui.idle.name", "<gray>Idle</gray>");
        };
    }

    private void addProgressLore(SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricRecipe recipe, SfxElectricMachineRenderStatus status, List<Component> lore, ItemStack stack, ItemMeta meta) {
        if (isExpCollector(definition)) {
            if (status == SfxElectricMachineRenderStatus.WORKING) {
                int current = Math.min(XP_PER_FLASK, Math.max(0, state.specialData() % XP_PER_FLASK));
                if (state.specialData() >= XP_PER_FLASK) {
                    current = XP_PER_FLASK;
                }
                lore.add(progressBarLine(current, XP_PER_FLASK));
                lore.add(localization.component(
                        "electric-ui.simple-io.xp-progress",
                        "<gray>Stored XP: </gray><white>{current}</white><gray>/</gray><white>{total}</white>",
                        Map.of("current", current, "total", XP_PER_FLASK)));
                lore.add(Component.empty());
                applyProgressDamage(stack, meta, current, XP_PER_FLASK);
            }
            return;
        }

        if (status != SfxElectricMachineRenderStatus.WORKING) {
            return;
        }
        int totalWork = totalWork(definition, state, recipe);
        int currentWork = Math.min(totalWork, Math.max(0, state.progressWork()));
        lore.add(progressBarLine(currentWork, totalWork));
        lore.add(Component.text(" "));
        lore.add(localization.component(
                "electric-ui.progress.time-left",
                "<gray>{time}</gray>",
                Map.of("time", formatTimeLeft(remainingSeconds(definition, currentWork, totalWork)))));
        applyProgressDamage(stack, meta, currentWork, totalWork);
    }

    private Component statusLore(SfxElectricMachineDefinition definition, SfxElectricRecipe recipe, SfxElectricMachineRenderStatus status) {
        return switch (status) {
            case WORKING -> workingLore(definition);
            case NO_POWER -> localization.component("electric-ui.no-power.lore", "<gray>Charge this machine to continue.</gray>");
            case NO_INPUT -> localization.component("electric-ui.idle.lore", "<gray>Waiting for input.</gray>");
            case NO_TARGET -> localization.component(
                    "electric-ui.no-target.lore",
                    "<gray>{target}</gray>",
                    Map.of("target", noTargetText(definition)));
            case BLOCKED_OUTPUT -> localization.component("electric-ui.blocked.lore", "<gray>The output is full. Free a slot to commit the finished item.</gray>");
            case OUTPUT_FULL -> localization.component("electric-ui.output-full.lore", "<gray>Free an output slot to continue.</gray>");
            case NO_RECIPE -> localization.component("electric-ui.no-recipe.lore", "<gray>The current input has no matching recipe.</gray>");
            case OVERLAPPING_AREA -> localization.component("electric-ui.overlapping-area.lore", "<gray>Machines of the same type cannot have overlapping work areas.</gray>");
            case PAUSED -> isAssembler(definition)
                    ? localization.component("configurable-ui.assembler.paused.lore", "<gray>Enable this assembler to continue.</gray>")
                    : localization.component("electric-ui.paused.lore", "<gray>This machine is paused.</gray>");
            case IDLE -> isExpCollector(definition)
                    ? localization.component("electric-ui.simple-io.xp-waiting.lore", "<gray>Waiting for nearby experience orbs.</gray>")
                    : localization.component("electric-ui.idle.lore", "<gray>Waiting for input.</gray>");
        };
    }

    private Component workingLore(SfxElectricMachineDefinition definition) {
        return switch (definition.id()) {
            case "sf:produce_collector" -> localization.component("electric-ui.action.produce.working", "<gray>Collecting nearby animal products.</gray>");
            case "sf:auto_breeder" -> localization.component("electric-ui.action.auto-breeder.working", "<gray>Feeding nearby animals.</gray>");
            case "sf:animal_growth_accelerator" -> localization.component("electric-ui.action.animal-growth.working", "<gray>Accelerating nearby baby animals.</gray>");
            case "sf:crop_growth_accelerator", "sf:crop_growth_accelerator_2" -> localization.component("electric-ui.action.crop-growth.working", "<gray>Accelerating nearby crops.</gray>");
            case "sf:tree_growth_accelerator" -> localization.component("electric-ui.action.tree-growth.working", "<gray>Accelerating nearby saplings.</gray>");
            case "sf:xp_collector" -> localization.component("electric-ui.simple-io.xp-working.lore", "<gray>Collecting nearby experience orbs.</gray>");
            case "sf:fluid_pump" -> localization.component("electric-ui.action.fluid-pump.working", "<gray>Pumping fluid from below.</gray>");
            case "sf:iron_golem_assembler", "sf:wither_assembler" -> localization.component("configurable-ui.assembler.working.lore", "<gray>Assembling entity structure.</gray>");
            default -> localization.component("electric-ui.progress.working-lore", "<gray>The machine is working.</gray>");
        };
    }

    private String requiredInputName(SfxElectricMachineDefinition definition) {
        String key = switch (definition.id()) {
            case "sf:produce_collector" -> "produce";
            case "sf:auto_breeder", "sf:animal_growth_accelerator" -> "organic-food";
            case "sf:crop_growth_accelerator", "sf:crop_growth_accelerator_2", "sf:tree_growth_accelerator" -> "organic-fertilizer";
            case "sf:fluid_pump" -> "fluid-pump";
            default -> "generic";
        };
        return localization.text("electric-ui.required-input." + key, key);
    }

    private String noTargetText(SfxElectricMachineDefinition definition) {
        String key = switch (definition.id()) {
            case "sf:produce_collector" -> "produce";
            case "sf:auto_breeder" -> "auto-breeder";
            case "sf:animal_growth_accelerator" -> "animal-growth";
            case "sf:crop_growth_accelerator", "sf:crop_growth_accelerator_2" -> "crop-growth";
            case "sf:tree_growth_accelerator" -> "tree-growth";
            case "sf:fluid_pump" -> "fluid-pump";
            default -> "generic";
        };
        return localization.text("electric-ui.target-text." + key, key);
    }

    private boolean isError(SfxElectricMachineRenderStatus status) {
        return status == SfxElectricMachineRenderStatus.NO_POWER
                || status == SfxElectricMachineRenderStatus.NO_INPUT
                || status == SfxElectricMachineRenderStatus.NO_TARGET
                || status == SfxElectricMachineRenderStatus.NO_RECIPE
                || status == SfxElectricMachineRenderStatus.BLOCKED_OUTPUT
                || status == SfxElectricMachineRenderStatus.OUTPUT_FULL
                || status == SfxElectricMachineRenderStatus.OVERLAPPING_AREA;
    }

    private boolean isExpCollector(SfxElectricMachineDefinition definition) {
        return "sf:xp_collector".equals(definition.id());
    }

    private boolean isAssembler(SfxElectricMachineDefinition definition) {
        return definition.menuStyle() == SfxElectricMachineMenuStyle.ASSEMBLER;
    }

    private int totalWork(SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricRecipe recipe) {
        if ((definition.recipeProvider().hasSpecialTick() || definition.recipeProvider().hasWorldAction()) && state.activeBaseTicks() > 0) {
            return Math.max(1, state.activeBaseTicks());
        }
        if (recipe != null) {
            return Math.max(1, recipe.baseTicks() * 20);
        }
        return Math.max(1, state.activeBaseTicks());
    }

    private int remainingSeconds(SfxElectricMachineDefinition definition, int currentWork, int totalWork) {
        int remainingWork = Math.max(0, totalWork - currentWork);
        int remainingTicks = (int) Math.ceil(remainingWork / (double) Math.max(1, definition.speed()));
        return Math.max(0, (int) Math.ceil(remainingTicks / 20.0D));
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
            return localization.text("electric-ui.time.minutes-seconds", "{minutes}m {seconds}s", Map.of("minutes", minutes, "seconds", remainingSeconds));
        }
        return localization.text("electric-ui.time.seconds", "{seconds}s", Map.of("seconds", remainingSeconds));
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

    private boolean isExtendedUiEnabled(UUID viewerId) {
        return profiles.find(viewerId)
                .map(profile -> profile.machineUiExtended())
                .orElse(true);
    }

    private String displayStackName(SfxElectricStack stack) {
        if (stack == null) {
            return "";
        }
        if (stack.isSfxItem()) {
            ItemStack item = stack.toItemStack(items);
            ItemMeta meta = item.getItemMeta();
            Component fallback = meta != null && meta.hasDisplayName() ? meta.displayName() : Component.text(stack.itemId());
            return plainText(localization.itemName(stack.itemId(), fallback));
        }
        return plainText(Component.translatable(stack.material().translationKey()));
    }

    private String plainText(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }
}
