package cc.theends6.sfx.api.machine.runtime;

import java.util.List;
import org.bukkit.Material;

public record SfxElectricMachineStatusUiTemplate(
        Material material,
        String name,
        List<String> lore,
        String nameKey,
        String loreKey,
        boolean includeDefaultLore,
        SfxDurabilityBarMode durabilityBarMode
) {
    public SfxElectricMachineStatusUiTemplate {
        lore = lore == null ? List.of() : List.copyOf(lore);
        nameKey = blankToNull(nameKey);
        loreKey = blankToNull(loreKey);
        durabilityBarMode = durabilityBarMode == null ? SfxDurabilityBarMode.NONE : durabilityBarMode;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
