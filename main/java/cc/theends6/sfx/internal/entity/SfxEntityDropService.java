package cc.theends6.sfx.internal.entity;

import cc.theends6.sfx.api.behavior.SfxBehaviorRegistry;
import cc.theends6.sfx.api.behavior.SfxEntityDeathSource;
import cc.theends6.sfx.api.behavior.SfxEntityDropChancePolicy;
import cc.theends6.sfx.api.behavior.SfxEntityDropContext;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.recipe.DefaultSfxRecipeRegistry;
import cc.theends6.sfx.internal.recipe.SfxRecipeDefinition;
import cc.theends6.sfx.internal.recipe.SfxRecipeOutputDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;


public final class SfxEntityDropService implements Listener {
    private static final Enchantment LOOTING = Enchantment.getByKey(NamespacedKey.minecraft("looting"));

    private final JavaPlugin plugin;
    private final SfxItems items;
    private final SfxBehaviorRegistry behaviors;
    private final List<DropDefinition> definitions = new ArrayList<>();

    public SfxEntityDropService(JavaPlugin plugin, SfxItems items, SfxBehaviorRegistry behaviors,
                                DefaultSfxRecipeRegistry recipes) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.items = Objects.requireNonNull(items, "items");
        this.behaviors = Objects.requireNonNull(behaviors, "behaviors");
        Objects.requireNonNull(recipes, "recipes").definitions().stream()
                .filter(SfxRecipeDefinition::runtimeEnabled)
                .filter(definition -> "sf:mob_drop".equals(definition.recipeType()))
                .forEach(this::registerRecipe);
    }

    private void registerRecipe(SfxRecipeDefinition recipe) {
        if (recipe.entityType() == null) {
            throw new IllegalArgumentException("Runtime entity drop lacks entity type: " + recipe.id());
        }
        for (SfxRecipeOutputDefinition output : recipe.allOutputs()) {
            if (!output.isSfxItem()) {
                plugin.getLogger().warning("Skipping unsupported vanilla entity drop output in " + recipe.id());
                continue;
            }
            register(new DropDefinition(recipe.id(), recipe.entityType(), output.sfxItemId(), output.amount(),
                    output.chance() == null ? 1.0D : output.chance()));
        }
    }

    private void register(DropDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (definitions.stream().anyMatch(existing -> existing.id().equals(definition.id())
                && existing.outputItemId().equals(definition.outputItemId()))) {
            throw new IllegalArgumentException("Duplicate entity drop output: " + definition.id()
                    + " -> " + definition.outputItemId());
        }
        definitions.add(definition);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        SfxEntityDeathSource source = deathSource(event, killer);
        int lootingLevel = killer == null || LOOTING == null
                ? 0
                : killer.getInventory().getItemInMainHand().getEnchantmentLevel(LOOTING);

        for (DropDefinition definition : definitions) {
            if (definition.entityType() != event.getEntityType()) {
                continue;
            }
            SfxEntityDropContext context = new SfxEntityDropContext(
                    definition.id(), definition.outputItemId(), definition.entityType(), source, killer, lootingLevel);
            double chance = source == SfxEntityDeathSource.PLAYER
                    ? definition.basePlayerChance()
                    : 0.0D;
            for (SfxEntityDropChancePolicy policy : behaviors.entityDropChancePolicies()) {
                try {
                    chance = policy.chance(context, chance);
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("Entity drop policy failed for " + definition.id() + ": " + exception.getMessage());
                }
                chance = clampChance(chance);
            }
            if (chance > 0.0D && ThreadLocalRandom.current().nextDouble() < chance) {
                ItemStack output = items.create(definition.outputItemId());
                if (output != null && !output.getType().isAir()) {
                    output.setAmount(definition.amount());
                    event.getDrops().add(output);
                }
            }
        }
    }

    private SfxEntityDeathSource deathSource(EntityDeathEvent event, Player killer) {
        if (SfxEntityKillAttribution.isAndroidKill(plugin, event.getEntity())) {
            return SfxEntityDeathSource.SFX_ANDROID;
        }
        return killer == null ? SfxEntityDeathSource.OTHER : SfxEntityDeathSource.PLAYER;
    }

    private double clampChance(double chance) {
        if (!Double.isFinite(chance)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, chance));
    }

    private record DropDefinition(String id, EntityType entityType, String outputItemId, int amount,
                                  double basePlayerChance) {
        public DropDefinition {
            if (id == null || id.isBlank() || outputItemId == null || outputItemId.isBlank()) {
                throw new IllegalArgumentException("Entity drop ids cannot be blank.");
            }
            Objects.requireNonNull(entityType, "entityType");
            amount = Math.max(1, amount);
            basePlayerChance = Math.max(0.0D, Math.min(1.0D, basePlayerChance));
        }
    }
}
