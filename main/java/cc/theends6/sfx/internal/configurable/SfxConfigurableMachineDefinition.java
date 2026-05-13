package cc.theends6.sfx.internal.configurable;

import cc.theends6.sfx.internal.electric.SfxElectricStack;
import java.util.List;
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

    private SfxConfigurableMachineDefinition(
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
            boolean witherAura
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
    }

    static SfxConfigurableMachineDefinition ironGolemAssembler() {
        return new SfxConfigurableMachineDefinition(
                "sf:iron_golem_assembler",
                SfxConfigurableMachineKind.ASSEMBLER,
                81920,
                2048,
                0,
                Material.CARVED_PUMPKIN,
                1,
                Material.IRON_BLOCK,
                4,
                EntityType.IRON_GOLEM,
                null,
                List.of(),
                false);
    }

    static SfxConfigurableMachineDefinition witherAssembler() {
        return new SfxConfigurableMachineDefinition(
                "sf:wither_assembler",
                SfxConfigurableMachineKind.ASSEMBLER,
                81920,
                4096,
                0,
                Material.WITHER_SKELETON_SKULL,
                3,
                Material.SOUL_SAND,
                4,
                EntityType.WITHER,
                null,
                List.of(),
                false);
    }

    static SfxConfigurableMachineDefinition nuclearReactor() {
        return new SfxConfigurableMachineDefinition(
                "sf:nuclear_reactor",
                SfxConfigurableMachineKind.REACTOR,
                327680,
                0,
                500,
                null,
                0,
                null,
                0,
                null,
                "sf:reactor_coolant_cell",
                List.of(
                        new ReactorFuel("sf:uranium", null, 1, 1200, SfxElectricStack.sfx("sf:neptunium", 1)),
                        new ReactorFuel("sf:neptunium", null, 1, 600, SfxElectricStack.sfx("sf:plutonium", 1)),
                        new ReactorFuel("sf:boosted_uranium", null, 1, 1500, null)),
                false);
    }

    static SfxConfigurableMachineDefinition netherStarReactor(int energyPerTick) {
        return new SfxConfigurableMachineDefinition(
                "sf:netherstar_reactor",
                SfxConfigurableMachineKind.REACTOR,
                655360,
                0,
                Math.max(1, energyPerTick),
                null,
                0,
                null,
                0,
                null,
                "sf:nether_ice_coolant_cell",
                List.of(new ReactorFuel("nether_star", Material.NETHER_STAR, 1, 1800, null)),
                true);
    }

    static SfxConfigurableMachineDefinition reactorAccessPort() {
        return new SfxConfigurableMachineDefinition(
                "sf:reactor_access_port",
                SfxConfigurableMachineKind.ACCESS_PORT,
                0,
                0,
                0,
                null,
                0,
                null,
                0,
                null,
                null,
                List.of(),
                false);
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
