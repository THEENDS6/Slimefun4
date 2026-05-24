package cc.theends6.sfx.api.machine;

import java.util.List;
import java.util.Set;


public record SfxMachineView(
        String id,
        String displayName,
        String category,
        int tickInterval,
        Set<String> capabilities,
        List<String> policyRefs,
        List<String> effects
) {
}
