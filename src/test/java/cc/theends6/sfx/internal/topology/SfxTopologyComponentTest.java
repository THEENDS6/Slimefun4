package cc.theends6.sfx.internal.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SfxTopologyComponentTest {
    @Test
    void membersAreUniqueAndImmutable() {
        UUID componentId = UUID.randomUUID();
        UUID terminal = UUID.randomUUID();
        SfxTopologyComponent component = new SfxTopologyComponent(
                componentId, SfxTopologyDomainKey.of("test", "network"), 7L);
        component.addBackbone(componentId);
        component.addTerminal(terminal);
        component.addTerminal(terminal);

        assertEquals(2, component.members().size());
        assertThrows(UnsupportedOperationException.class, () -> component.members().clear());
    }
}
