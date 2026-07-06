package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.ui.SfxMachineStatusDefaults;
import cc.theends6.sfx.internal.ui.SfxMachineStatusIconRenderer;
import cc.theends6.sfx.internal.ui.SfxMachineStatusKey;
import cc.theends6.sfx.internal.ui.SfxMachineStatusView;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

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
        SfxMachineStatusKey statusKey = effectiveStatus.statusKey();
        SfxElectricMachineStatusUiTemplate template = definition.ui().requiredStatusTemplate(statusTemplateKey(effectiveStatus));
        SfxMachineStatusView.Builder view = SfxMachineStatusView.builder(statusKey)
                .material(requireStatusMaterial(definition, effectiveStatus, template))
                .energy(state.storedEnergy(), definition.energyCapacity());

        Component nameOverride = templateName(template) == null
                ? null
                : Text.renderFlexible(applyPlaceholders(templateName(template), statusPlaceholders(definition, state, recipe, effectiveStatus)));
        if (nameOverride != null) {
            view.name(nameOverride);
        }

        if (effectiveStatus == SfxElectricMachineRenderStatus.WORKING) {
            if (isExpCollector(definition)) {
                int current = Math.min(XP_PER_FLASK, Math.max(0, state.specialData() % XP_PER_FLASK));
                if (state.specialData() >= XP_PER_FLASK) {
                    current = XP_PER_FLASK;
                }
                view.progress(current, XP_PER_FLASK, -1, template.durabilityBarMode())
                        .includeDefaultStatusLore(false)
                .statusLore(localization.component(
                                "electric-ui.simple-io.xp-progress",
                                Map.of("current", current, "total", XP_PER_FLASK)));
            } else {
                int totalWork = totalWork(definition, state, recipe);
                int currentWork = Math.min(totalWork, Math.max(0, state.progressWork()));
                int remainingTicks = Math.max(0, (int) Math.ceil((totalWork - currentWork) / (double) Math.max(1, definition.speed())));
                view.progress(currentWork, totalWork, remainingTicks, template.durabilityBarMode())
                        .includeDefaultStatusLore(false)
                .statusLore(workingLore(definition));
            }
        } else {
            List<Component> overrideLore = statusLoreOverride(definition, state, effectiveStatus);
            if (!overrideLore.isEmpty()) {
                view.includeDefaultStatusLore(false)
                .statusLore(overrideLore);
            }
        }
        view.includeDefaultStatusLore(template.includeDefaultLore());
        List<String> templateLore = templateLore(template);
        if (!templateLore.isEmpty()) {
            view.overrideStatusLore(templateLore.stream()
                    .map(line -> Text.renderFlexible(applyPlaceholders(line, statusPlaceholders(definition, state, recipe, effectiveStatus))))
                    .toList());
        }

        if (definition.energyConsumptionPerTick() > 0) {
            view.consumption(definition.energyConsumptionPerTick());
        }
        if (isExtendedUiEnabled(viewerId)) {
            view.extraLore(localization.component(
                    "electric-ui.progress.speed",
                    Map.of("speed", definition.speed())));
            if (effectiveStatus == SfxElectricMachineRenderStatus.WORKING && recipe != null && recipe.output() != null) {
                view.extraLore(localization.component(
                        "electric-ui.progress.recipe",
                        Map.of("recipe", displayStackName(recipe.output()))));
            }
        }
        return commonStatusIcons.render(view.build());
    }

    private String statusTemplateKey(SfxElectricMachineRenderStatus status) {
        return status == null ? "idle" : status.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    private Material requireStatusMaterial(SfxElectricMachineDefinition definition, SfxElectricMachineRenderStatus status, SfxElectricMachineStatusUiTemplate template) {
        if (template.material() == null) {
            throw new IllegalStateException("Missing compiled status material for " + definition.id() + " status " + statusTemplateKey(status));
        }
        return template.material();
    }

    private String templateName(SfxElectricMachineStatusUiTemplate template) {
        if (template.nameKey() != null) {
            return localization.requiredText(template.nameKey());
        }
        if (template.name() == null || template.name().isBlank()) {
            return " ";
        }
        throw new IllegalStateException("Electric status template is missing name-key for non-blank status name: " + template.name());
    }

    private List<String> templateLore(SfxElectricMachineStatusUiTemplate template) {
        if (template.loreKey() != null) {
            return localization.requiredList(template.loreKey());
        }
        if (template.lore() == null || template.lore().isEmpty()) {
            return List.of();
        }
        throw new IllegalStateException("Electric status template is missing lore-key for non-empty status lore");
    }

    private Map<String, ?> statusPlaceholders(SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricRecipe recipe, SfxElectricMachineRenderStatus status) {
        int total = totalWork(definition, state, recipe);
        int current = Math.min(total, Math.max(0, state.progressWork()));
        int remainingTicks = Math.max(0, (int) Math.ceil((total - current) / (double) Math.max(1, definition.speed())));
        return Map.of(
                "status", statusTemplateKey(status),
                "progress_current", current,
                "progress_total", total,
                "progress_percent", Math.round((current * 100.0D / Math.max(1, total)) * 100.0D) / 100.0D,
                "time_left", commonStatusIcons.formatTimeLeft(remainingTicks),
                "stored_energy", state.storedEnergy(),
                "energy_capacity", definition.energyCapacity(),
                "energy_per_tick", definition.energyConsumptionPerTick(),
                "speed", definition.speed());
    }

    private String applyPlaceholders(String text, Map<String, ?> placeholders) {
        if (text == null || placeholders == null || placeholders.isEmpty()) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    private SfxElectricMachineRenderStatus effectiveStatus(SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricMachineRenderStatus status) {
        if (status == SfxElectricMachineRenderStatus.PAUSED) {
            return SfxElectricMachineRenderStatus.PAUSED;
        }
        if (status == SfxElectricMachineRenderStatus.BLOCKED_OUTPUT
                || status == SfxElectricMachineRenderStatus.OUTPUT_FULL
                || status == SfxElectricMachineRenderStatus.OVERLAPPING_AREA
                || status == SfxElectricMachineRenderStatus.NO_TARGET
                || status == SfxElectricMachineRenderStatus.NO_RECIPE
                || status == SfxElectricMachineRenderStatus.NO_BLAZE_FUEL
                || status == SfxElectricMachineRenderStatus.NO_BREWING_INGREDIENT
                || status == SfxElectricMachineRenderStatus.NO_POTION) {
            return status;
        }
        if (status == SfxElectricMachineRenderStatus.NO_POWER) {
            return SfxElectricMachineRenderStatus.NO_POWER;
        }
        if (isAutoCrafter(definition)) {
            if (state.progressWork() > 0) {
                if (definition.energyConsumptionPerTick() > 0 && state.storedEnergy() < definition.energyConsumptionPerTick()) {
                    return SfxElectricMachineRenderStatus.NO_POWER;
                }
                return SfxElectricMachineRenderStatus.WORKING;
            }
            return status;
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

    private List<Component> statusLoreOverride(SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricMachineRenderStatus status) {
        if (isAutoBrewer(definition) && status == SfxElectricMachineRenderStatus.IDLE) {
            return autoBrewerIdleLore(state);
        }
        Component component = switch (status) {
            case NO_TARGET -> localization.component(
                    "electric-ui.no-target.lore",
                    Map.of("target", noTargetText(definition)));
            case NO_BLAZE_FUEL -> localization.component("electric-ui.auto-brewer.blaze.missing-lore");
            case NO_BREWING_INGREDIENT -> localization.component("electric-ui.auto-brewer.ingredient.missing-lore");
            case NO_POTION -> localization.component("electric-ui.auto-brewer.potion.missing-lore");
            case PAUSED -> isAssembler(definition)
                    ? localization.component("configurable-ui.assembler.paused.lore")
                    : Component.empty();
            case IDLE -> isExpCollector(definition)
                    ? localization.component("electric-ui.simple-io.xp-waiting.lore")
                    : Component.empty();
            default -> Component.empty();
        };
        return Component.empty().equals(component) ? List.of() : List.of(component);
    }

    private Component workingLore(SfxElectricMachineDefinition definition) {
        if (definition.hasFunction("auto-brewer")) {
            return localization.component("electric-ui.auto-brewer.progress.brewing");
        }
        return switch (definition.id()) {
            case "sf:produce_collector" -> localization.component("electric-ui.action.produce.working");
            case "sf:auto_breeder" -> localization.component("electric-ui.action.auto-breeder.working");
            case "sf:animal_growth_accelerator" -> localization.component("electric-ui.action.animal-growth.working");
            case "sf:crop_growth_accelerator", "sf:crop_growth_accelerator_2" -> localization.component("electric-ui.action.crop-growth.working");
            case "sf:tree_growth_accelerator" -> localization.component("electric-ui.action.tree-growth.working");
            case "sf:xp_collector" -> localization.component("electric-ui.simple-io.xp-working.lore");
            case "sf:fluid_pump" -> localization.component("electric-ui.action.fluid-pump.working");
            case "sf:geo_miner" -> localization.component("electric-ui.action.geo-miner.working");
            case "sf:oil_pump" -> localization.component("electric-ui.action.oil-pump.working");
            case "sf:iron_golem_assembler", "sf:wither_assembler" -> localization.component("configurable-ui.assembler.working.lore");
            default -> SfxMachineStatusDefaults.lore(localization, SfxMachineStatusKey.WORKING);
        };
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
        return localization.text("electric-ui.target-text." + key);
    }

    private List<Component> autoBrewerIdleLore(SfxElectricMachineState state) {
        List<Component> lore = new ArrayList<>();
        if (state.specialData() <= 0 && state.input(0) == null) {
            lore.add(localization.component("electric-ui.auto-brewer.blaze.missing-lore"));
        }
        if (state.input(1) == null) {
            lore.add(localization.component("electric-ui.auto-brewer.ingredient.missing-lore"));
        }
        boolean hasPotion = false;
        for (int slot = 2; slot < 6; slot++) {
            SfxElectricStack stack = state.input(slot);
            if (stack == null || stack.isSfxItem() || stack.amount() <= 0) {
                continue;
            }
            Material material = stack.material();
            if (material == Material.POTION || material == Material.SPLASH_POTION || material == Material.LINGERING_POTION) {
                hasPotion = true;
                break;
            }
        }
        if (!hasPotion) {
            lore.add(localization.component("electric-ui.auto-brewer.potion.missing-lore"));
        }
        if (lore.isEmpty()) {
            lore.add(SfxMachineStatusDefaults.lore(localization, SfxMachineStatusKey.IDLE));
        }
        return lore;
    }

    private boolean isExpCollector(SfxElectricMachineDefinition definition) {
        return "sf:xp_collector".equals(definition.id());
    }

    private boolean isAssembler(SfxElectricMachineDefinition definition) {
        return definition.hasFunction("assembler");
    }

    private boolean isAutoBrewer(SfxElectricMachineDefinition definition) {
        return definition.hasFunction("auto-brewer");
    }

    private boolean isAutoCrafter(SfxElectricMachineDefinition definition) {
        return definition.hasFunction("auto-crafter");
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
            return plainText(localization.itemName(stack.itemId()));
        }
        return plainText(Component.translatable(stack.material().translationKey()));
    }

    private String plainText(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }
}
