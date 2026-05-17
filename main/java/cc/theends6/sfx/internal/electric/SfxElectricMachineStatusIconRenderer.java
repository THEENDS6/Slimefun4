package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.ui.SfxMachineStatusDefaults;
import cc.theends6.sfx.internal.ui.SfxMachineStatusIconRenderer;
import cc.theends6.sfx.internal.ui.SfxMachineStatusKey;
import cc.theends6.sfx.internal.ui.SfxMachineStatusView;
import cc.theends6.sfx.internal.util.SfxLocalization;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
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
        SfxMachineStatusKey statusKey = effectiveStatus.statusKey();
        SfxMachineStatusView.Builder view = SfxMachineStatusView.builder(statusKey)
                .material(material(definition, effectiveStatus, statusKey))
                .energy(state.storedEnergy(), definition.energyCapacity());

        Component nameOverride = displayNameOverride(viewerId, definition, effectiveStatus);
        if (nameOverride != null) {
            view.name(nameOverride);
        }

        if (effectiveStatus == SfxElectricMachineRenderStatus.WORKING) {
            if (isExpCollector(definition)) {
                int current = Math.min(XP_PER_FLASK, Math.max(0, state.specialData() % XP_PER_FLASK));
                if (state.specialData() >= XP_PER_FLASK) {
                    current = XP_PER_FLASK;
                }
                view.progress(current, XP_PER_FLASK, -1, true)
                        .includeDefaultStatusLore(false)
                .statusLore(localization.component(
                                "electric-ui.simple-io.xp-progress",
                                "<gray>Stored XP: </gray><white>{current}</white><gray>/</gray><white>{total}</white>",
                                Map.of("current", current, "total", XP_PER_FLASK)));
            } else {
                int totalWork = totalWork(definition, state, recipe);
                int currentWork = Math.min(totalWork, Math.max(0, state.progressWork()));
                int remainingTicks = Math.max(0, (int) Math.ceil((totalWork - currentWork) / (double) Math.max(1, definition.speed())));
                view.progress(currentWork, totalWork, remainingTicks, true)
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

    private Material material(SfxElectricMachineDefinition definition, SfxElectricMachineRenderStatus status, SfxMachineStatusKey statusKey) {
        if (status == SfxElectricMachineRenderStatus.WORKING) {
            return definition.progressMaterial();
        }
        if (isAutoBrewer(definition)) {
            return switch (status) {
                case PAUSED -> Material.YELLOW_STAINED_GLASS_PANE;
                case NO_POWER, NO_RECIPE, NO_BLAZE_FUEL -> Material.RED_STAINED_GLASS_PANE;
                default -> Material.BLACK_STAINED_GLASS_PANE;
            };
        }
        return SfxMachineStatusDefaults.material(statusKey);
    }

    private Component displayNameOverride(UUID viewerId, SfxElectricMachineDefinition definition, SfxElectricMachineRenderStatus status) {
        return switch (status) {
            case WORKING -> isExtendedUiEnabled(viewerId) ? null : Component.text(" ");
            case NO_BLAZE_FUEL -> localization.component("electric-ui.auto-brewer.blaze.missing-name", "<red>No Blaze Powder</red>");
            case NO_BREWING_INGREDIENT -> localization.component("electric-ui.auto-brewer.ingredient.missing-name", "<red>No Ingredient</red>");
            case NO_POTION -> localization.component("electric-ui.auto-brewer.potion.missing-name", "<red>No Potion</red>");
            case PAUSED -> isAssembler(definition) ? localization.component("configurable-ui.assembler.paused.name", "<yellow>Paused</yellow>") : null;
            default -> null;
        };
    }

    private List<Component> statusLoreOverride(SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricMachineRenderStatus status) {
        if (isAutoBrewer(definition) && status == SfxElectricMachineRenderStatus.IDLE) {
            return autoBrewerIdleLore(state);
        }
        Component component = switch (status) {
            case NO_TARGET -> localization.component(
                    "electric-ui.no-target.lore",
                    "<gray>{target}</gray>",
                    Map.of("target", noTargetText(definition)));
            case NO_BLAZE_FUEL -> localization.component("electric-ui.auto-brewer.blaze.missing-lore", "<gray>Add blaze powder before brewing.</gray>");
            case NO_BREWING_INGREDIENT -> localization.component("electric-ui.auto-brewer.ingredient.missing-lore", "<gray>Add a valid brewing ingredient.</gray>");
            case NO_POTION -> localization.component("electric-ui.auto-brewer.potion.missing-lore", "<gray>Add at least one valid potion bottle.</gray>");
            case PAUSED -> isAssembler(definition)
                    ? localization.component("configurable-ui.assembler.paused.lore", "<gray>Enable this assembler to continue.</gray>")
                    : Component.empty();
            case IDLE -> isExpCollector(definition)
                    ? localization.component("electric-ui.simple-io.xp-waiting.lore", "<gray>Waiting for nearby experience orbs.</gray>")
                    : Component.empty();
            default -> Component.empty();
        };
        return Component.empty().equals(component) ? List.of() : List.of(component);
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
            case "sf:auto_brewer", "sf:auto_brewer_2" -> localization.component("electric-ui.auto-brewer.progress.brewing", "<gray>Brewing potions.</gray>");
            case "sf:iron_golem_assembler", "sf:wither_assembler" -> localization.component("configurable-ui.assembler.working.lore", "<gray>Assembling entity structure.</gray>");
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
        return localization.text("electric-ui.target-text." + key, key);
    }

    private List<Component> autoBrewerIdleLore(SfxElectricMachineState state) {
        List<Component> lore = new ArrayList<>();
        if (state.specialData() <= 0 && state.input(0) == null) {
            lore.add(localization.component("electric-ui.auto-brewer.blaze.missing-lore", "<gray>Add blaze powder before brewing.</gray>"));
        }
        if (state.input(1) == null) {
            lore.add(localization.component("electric-ui.auto-brewer.ingredient.missing-lore", "<gray>Add a valid brewing ingredient.</gray>"));
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
            lore.add(localization.component("electric-ui.auto-brewer.potion.missing-lore", "<gray>Add at least one valid potion bottle.</gray>"));
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
        return definition.menuStyle() == SfxElectricMachineMenuStyle.ASSEMBLER;
    }

    private boolean isAutoBrewer(SfxElectricMachineDefinition definition) {
        return definition.menuStyle() == SfxElectricMachineMenuStyle.AUTO_BREWER;
    }

    private boolean isAutoCrafter(SfxElectricMachineDefinition definition) {
        return definition.menuStyle() == SfxElectricMachineMenuStyle.AUTO_CRAFTER;
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
