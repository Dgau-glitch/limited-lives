package xyz.srnyx.limitedlives.services.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LifeTransferPolicyTest {
    @Test
    void preservesLastLifeAndClampsEvenlyAcrossTargets() {
        assertEquals(0, LifeTransferPolicy.commonAmount(1, 0, 3, 2));
        assertEquals(2, LifeTransferPolicy.commonAmount(6, 0, 4, 2));
        assertEquals(3, LifeTransferPolicy.commonAmount(10, 0, 3, 2));
    }

    @Test
    void clampsEachTargetToItsOwnMaximum() {
        assertEquals(1, LifeTransferPolicy.targetAmount(4, 5, 3));
        assertEquals(0, LifeTransferPolicy.targetAmount(5, 5, 3));
        assertEquals(3, LifeTransferPolicy.targetAmount(1, 5, 3));
    }
}
