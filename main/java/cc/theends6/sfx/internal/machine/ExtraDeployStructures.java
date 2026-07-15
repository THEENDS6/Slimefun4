package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.api.machine.manual.SfxManualMachineDefinition;
import cc.theends6.sfx.api.machine.manual.SfxManualMachineOperation;

import cc.theends6.sfx.api.text.Text;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

public final class ExtraDeployStructures {
    private static final Map<String, SfxManualMachineDefinition> DEFINITIONS = Map.of(
            "sf:industrial_miner",
            new SfxManualMachineDefinition(
                    "sf:industrial_miner",
                    Text.legacy("&bIndustrial Miner"),
                    Material.GOLDEN_PICKAXE,
                    new Material[]{
                            null, null, null,
                            Material.PISTON, Material.CHEST, Material.PISTON,
                            Material.IRON_BLOCK, Material.BLAST_FURNACE, Material.IRON_BLOCK
                    },
                    BlockFace.SELF,
                    BlockFace.SELF,
                    SfxManualMachineOperation.HAND_INPUT
            ),
            "sf:advanced_industrial_miner",
            new SfxManualMachineDefinition(
                    "sf:advanced_industrial_miner",
                    Text.legacy("&cAdvanced Industrial Miner"),
                    Material.DIAMOND_PICKAXE,
                    new Material[]{
                            null, null, null,
                            Material.PISTON, Material.CHEST, Material.PISTON,
                            Material.DIAMOND_BLOCK, Material.BLAST_FURNACE, Material.DIAMOND_BLOCK
                    },
                    BlockFace.SELF,
                    BlockFace.SELF,
                    SfxManualMachineOperation.HAND_INPUT
            )
    );

    private ExtraDeployStructures() {
    }

    public static Optional<SfxManualMachineDefinition> machine(String id) {
        return Optional.ofNullable(DEFINITIONS.get(id));
    }
}
