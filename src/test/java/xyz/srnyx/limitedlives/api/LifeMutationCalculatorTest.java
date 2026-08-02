package xyz.srnyx.limitedlives.api;

import org.junit.jupiter.api.Test;
import xyz.srnyx.limitedlives.api.internal.LifeMutationCalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifeMutationCalculatorTest {
    @Test
    void clampsAndReportsActuallyAppliedAmount() {
        final LifeMutationResult result = LifeMutationCalculator.add(8, 5, 0, 10, LifeOverflowPolicy.CLAMP);
        assertEquals(8, result.oldLives());
        assertEquals(10, result.newLives());
        assertEquals(2, result.appliedAmount());
        assertTrue(result.limitedByMaximum());
        assertFalse(result.rejected());
    }

    @Test
    void rejectsOverflowWithoutPartialMutation() {
        final LifeMutationResult result = LifeMutationCalculator.add(8, 5, 0, 10, LifeOverflowPolicy.REJECT);
        assertEquals(8, result.newLives());
        assertEquals(0, result.appliedAmount());
        assertTrue(result.rejected());
    }

    @Test
    void reportsRevivalAcrossMinimumBoundary() {
        final LifeMutationResult result = LifeMutationCalculator.add(0, 1, 0, 10, LifeOverflowPolicy.CLAMP);
        assertTrue(result.revived());
    }

    @Test
    void rejectsNonPositiveAmounts() {
        assertThrows(IllegalArgumentException.class,
                () -> LifeMutationCalculator.add(5, 0, 0, 10, LifeOverflowPolicy.CLAMP));
    }

    @Test
    void rejectsMissingOverflowPolicy() {
        assertThrows(NullPointerException.class,
                () -> LifeMutationCalculator.add(5, 1, 0, 10, null));
    }

    @Test
    void additionNeverReducesLegacyValueAboveMaximum() {
        final LifeMutationResult result = LifeMutationCalculator.add(12, 1, 0, 10, LifeOverflowPolicy.CLAMP);
        assertEquals(12, result.newLives());
        assertEquals(0, result.appliedAmount());
        assertTrue(result.limitedByMaximum());
    }
}
