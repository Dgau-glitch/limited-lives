package xyz.srnyx.limitedlives.services.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** Pure rules deciding whether a death participates in the life-loss flow. */
public final class LifeLossPolicy {
    private LifeLossPolicy() {}

    public static boolean shouldLoseLife(boolean playerKill, boolean loseOnPlayerKill,
                                         boolean playerKillAllowed,
                                         @Nullable String cause, @NotNull Set<String> enabledCauses) {
        if (playerKill && !loseOnPlayerKill && !playerKillAllowed) return false;
        return cause == null || enabledCauses.isEmpty() || enabledCauses.contains(cause);
    }
}
