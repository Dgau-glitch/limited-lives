package xyz.srnyx.limitedlives.services.execution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleGateTest {
    @Test
    void rejectsEverySubmissionBeforeStartAndAfterStopping() {
        final LifecycleGate gate = new LifecycleGate();
        assertFalse(gate.trySubmit(true));
        gate.start();
        assertTrue(gate.trySubmit(true));
        assertFalse(gate.trySubmit(false));
        assertTrue(gate.beginStopping());
        assertFalse(gate.trySubmit(true));
        gate.stopped();
        assertFalse(gate.trySubmit(true));
        assertEquals(4, gate.rejectedSubmissions());
    }
}
