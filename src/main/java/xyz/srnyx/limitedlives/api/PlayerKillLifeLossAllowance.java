package xyz.srnyx.limitedlives.api;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Scoped permission for one killer's PvP kills to participate in life loss even
 * when {@code lives.lose-on-player-kill} is disabled globally.
 */
public interface PlayerKillLifeLossAllowance extends AutoCloseable {
    @NotNull UUID killerId();
    @NotNull Plugin owner();
    @NotNull String reason();
    boolean isActive();

    /** Revokes only this allowance. This operation is idempotent. */
    @Override void close();
}
