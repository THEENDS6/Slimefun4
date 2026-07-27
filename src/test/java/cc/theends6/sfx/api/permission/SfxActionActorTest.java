package cc.theends6.sfx.api.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SfxActionActorTest {
    @Test
    void machinePreservesOfflineOwnerIdentity() {
        UUID machineId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        SfxActionActor actor = SfxActionActor.machine(machineId, ownerId, null);

        assertEquals(SfxActionActor.Kind.MACHINE, actor.kind());
        assertEquals(machineId, actor.machineInstanceId());
        assertEquals(ownerId, actor.ownerId());
        assertFalse(actor.hasOnlinePlayer());
    }

    @Test
    void systemActorHasNoForgedIdentity() {
        SfxActionActor actor = SfxActionActor.system();

        assertEquals(SfxActionActor.Kind.SYSTEM, actor.kind());
        assertNull(actor.ownerId());
        assertNull(actor.onlinePlayer());
        assertNull(actor.machineInstanceId());
    }
}
