package cc.theends6.sfx.internal.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SfxTimingWheelTest {
    @Test
    void pollsOnlyEntriesDueInTheCurrentBucket() {
        SfxTimingWheel<UUID> wheel = new SfxTimingWheel<>(32);
        UUID first = UUID.randomUUID();
        UUID later = UUID.randomUUID();

        wheel.schedule(first, 5L);
        wheel.schedule(later, 37L);

        assertEquals(List.of(first), wheel.poll(5L));
        assertTrue(wheel.isScheduled(later));
        assertEquals(List.of(later), wheel.poll(37L));
        assertFalse(wheel.isScheduled(later));
    }

    @Test
    void wakeReplacesALaterDeadline() {
        SfxTimingWheel<UUID> wheel = new SfxTimingWheel<>();
        UUID instanceId = UUID.randomUUID();

        wheel.schedule(instanceId, 100L);
        wheel.wake(instanceId, 10L);

        assertEquals(List.of(instanceId), wheel.poll(11L));
    }

    @Test
    void rescheduleReplacesTheDeadlineAfterExecution() {
        SfxTimingWheel<UUID> wheel = new SfxTimingWheel<>();
        UUID instanceId = UUID.randomUUID();

        wheel.schedule(instanceId, 100L);
        wheel.reschedule(instanceId, 20L);

        assertEquals(List.of(instanceId), wheel.poll(20L));
    }
}
