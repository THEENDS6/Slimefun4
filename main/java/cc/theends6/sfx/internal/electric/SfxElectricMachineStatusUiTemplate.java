package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.internal.ui.SfxDurabilityBarMode;
import java.util.List;
import org.bukkit.Material;

record SfxElectricMachineStatusUiTemplate(
        Material material,
        String name,
        List<String> lore,
        boolean includeDefaultLore,
        SfxDurabilityBarMode durabilityBarMode
) {
    SfxElectricMachineStatusUiTemplate {
        lore = lore == null ? List.of() : List.copyOf(lore);
        durabilityBarMode = durabilityBarMode == null ? SfxDurabilityBarMode.NONE : durabilityBarMode;
    }
}
