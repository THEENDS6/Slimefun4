package cc.theends6.sfx.internal.machine;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SfxMachineRuntimeEngineTest {
    @Test
    void rejectsNullDefinitionInsteadOfSilentlyIgnoringIt() {
        SfxMachineRuntimeEngine runtime = new SfxMachineRuntimeEngine();

        assertThrows(IllegalArgumentException.class, () -> runtime.registerDefinition(null));
        assertThrows(IllegalArgumentException.class, () -> runtime.registerDefinitionIfAbsent(null));
    }
}
