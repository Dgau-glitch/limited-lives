package xyz.srnyx.limitedlives.services.player;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifeLossPolicyTest {
    @Test
    void playerKillCanBeDisabledIndependently() {
        assertFalse(LifeLossPolicy.shouldLoseLife(true, false, false, "PLAYER_ATTACK", Set.of()));
        assertTrue(LifeLossPolicy.shouldLoseLife(true, true, false, "PLAYER_ATTACK", Set.of()));
        assertTrue(LifeLossPolicy.shouldLoseLife(true, false, true, "PLAYER_ATTACK", Set.of()));
    }

    @Test
    void configuredCausesStillFilterPveAndPvpDeaths() {
        final Set<String> causes = Set.of("FALL", "PLAYER_ATTACK");
        assertTrue(LifeLossPolicy.shouldLoseLife(false, true, false, "FALL", causes));
        assertTrue(LifeLossPolicy.shouldLoseLife(true, true, false, "PLAYER_ATTACK", causes));
        assertFalse(LifeLossPolicy.shouldLoseLife(false, true, false, "LAVA", causes));
    }

    @Test
    void unknownCausePreservesExistingLifeLossBehavior() {
        assertTrue(LifeLossPolicy.shouldLoseLife(false, true, false, null, Set.of("FALL")));
    }
}
