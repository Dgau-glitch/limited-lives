package xyz.srnyx.limitedlives.services.player;

import org.junit.jupiter.api.Test;
import xyz.srnyx.limitedlives.managers.player.exception.LessThanMinLives;
import xyz.srnyx.limitedlives.managers.player.exception.MoreThanMaxLives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifeValuePolicyTest {
    @Test
    void acceptsInclusiveSetBoundaries() throws Exception {
        assertEquals(0, LifeValuePolicy.set(0, 0, 5));
        assertEquals(5, LifeValuePolicy.set(5, 0, 5));
    }

    @Test
    void rejectsSetOutsideBoundaries() {
        assertThrows(LessThanMinLives.class, () -> LifeValuePolicy.set(-1, 0, 5));
        assertThrows(MoreThanMaxLives.class, () -> LifeValuePolicy.set(6, 0, 5));
    }

    @Test
    void validatesAddAndRemoveBoundaries() throws Exception {
        assertEquals(5, LifeValuePolicy.add(3, 2, 5));
        assertThrows(MoreThanMaxLives.class, () -> LifeValuePolicy.add(3, 3, 5));
        assertEquals(0, LifeValuePolicy.remove(2, 2, 0));
        assertThrows(LessThanMinLives.class, () -> LifeValuePolicy.remove(2, 3, 0));
    }

    @Test
    void calculatesAndExpiresGracePeriod() {
        assertEquals(750, LifeValuePolicy.graceLeft(1_000, 1_000, 1_250));
        assertEquals(0, LifeValuePolicy.graceLeft(1_000, 1_000, 2_001));
    }

    @Test
    void detectsKillAndReviveTransitions() {
        assertTrue(LifeValuePolicy.shouldKill(0, 0));
        assertFalse(LifeValuePolicy.shouldKill(1, 0));
        assertTrue(LifeValuePolicy.shouldRevive(0, 1, 0));
        assertFalse(LifeValuePolicy.shouldRevive(1, 2, 0));
    }
}
