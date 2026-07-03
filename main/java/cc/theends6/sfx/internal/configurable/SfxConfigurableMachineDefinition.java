package cc.theends6.sfx.internal.configurable;

import cc.theends6.sfx.internal.electric.SfxElectricStack;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

final class SfxConfigurableMachineDefinition {
    private final String id;
    private final SfxConfigurableMachineKind kind;
    private final int capacity;
    private final int energyPerAction;
    private final int energyPerTick;
    private final Material headMaterial;
    private final int headAmount;
    private final Material bodyMaterial;
    private final int bodyAmount;
    private final EntityType spawnType;
    private final String coolantItemId;
    private final List<ReactorFuel> fuels;
    private final boolean witherAura;
    private final SfxConfigurableMachineUiDefinition ui;

    SfxConfigurableMachineDefinition(
            String id,
            SfxConfigurableMachineKind kind,
            int capacity,
            int energyPerAction,
            int energyPerTick,
            Material headMaterial,
            int headAmount,
            Material bodyMaterial,
            int bodyAmount,
            EntityType spawnType,
            String coolantItemId,
            List<ReactorFuel> fuels,
            boolean witherAura,
            SfxConfigurableMachineUiDefinition ui
    ) {
        this.id = id;
        this.kind = kind;
        this.capacity = capacity;
        this.energyPerAction = energyPerAction;
        this.energyPerTick = energyPerTick;
        this.headMaterial = headMaterial;
        this.headAmount = Math.max(0, headAmount);
        this.bodyMaterial = bodyMaterial;
        this.bodyAmount = Math.max(0, bodyAmount);
        this.spawnType = spawnType;
        this.coolantItemId = coolantItemId;
        this.fuels = fuels == null ? List.of() : List.copyOf(fuels);
        this.witherAura = witherAura;
        this.ui = Objects.requireNonNull(ui, "ui");
    }

    String id() {
        return id;
    }

    SfxConfigurableMachineKind kind() {
        return kind;
    }

    int capacity() {
        return capacity;
    }

    int energyPerAction() {
        return energyPerAction;
    }

    int energyPerTick() {
        return energyPerTick;
    }

    Material headMaterial() {
        return headMaterial;
    }

    int headAmount() {
        return headAmount;
    }

    Material bodyMaterial() {
        return bodyMaterial;
    }

    int bodyAmount() {
        return bodyAmount;
    }

    EntityType spawnType() {
        return spawnType;
    }

    String coolantItemId() {
        return coolantItemId;
    }

    List<ReactorFuel> fuels() {
        return fuels;
    }

    boolean witherAura() {
        return witherAura;
    }

    SfxConfigurableMachineUiDefinition ui() {
        return ui;
    }

    boolean isConsumer() {
        return kind == SfxConfigurableMachineKind.ASSEMBLER;
    }

    boolean isProducer() {
        return kind == SfxConfigurableMachineKind.REACTOR;
    }

    record ReactorFuel(String key, Material material, int amount, int seconds, SfxElectricStack output) {
        boolean matches(SfxElectricStack stack) {
            if (stack == null || stack.amount() < amount) {
                return false;
            }
            if (material != null) {
                return !stack.isSfxItem() && stack.material() == material;
            }
            return stack.isSfxItem() && key.equals(stack.itemId());
        }

        SfxElectricStack inputStack() {
            return material == null ? SfxElectricStack.sfx(key, amount) : SfxElectricStack.vanilla(material, amount);
        }
    }
}
