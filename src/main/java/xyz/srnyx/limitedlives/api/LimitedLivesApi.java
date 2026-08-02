package xyz.srnyx.limitedlives.api;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Thread-safe public integration API for automatic death-driven life loss.
 * Administrative data commands intentionally remain able to change protected players.
 * No method in this interface touches Bukkit entities or storage.
 */
public interface LimitedLivesApi {
    /** Enables life loss after a previous call to {@link #disableLifeLoss(UUID)}. */
    void enableLifeLoss(@NotNull UUID playerId);

    /** Disables life loss until explicitly enabled again. */
    void disableLifeLoss(@NotNull UUID playerId);

    /** Sets the manual life-loss state for a player. */
    default void setLifeLossEnabled(@NotNull UUID playerId, boolean enabled) {
        if (enabled) enableLifeLoss(playerId);
        else disableLifeLoss(playerId);
    }

    /** Returns false when either manual or scoped protection currently applies. */
    boolean isLifeLossEnabled(@NotNull UUID playerId);

    /**
     * Acquires an independent protection owned by another plugin. Closing the
     * returned handle removes only this protection, so integrations cannot
     * accidentally re-enable life loss disabled by somebody else.
     */
    @NotNull LifeLossProtection protect(@NotNull UUID playerId, @NotNull Plugin owner, @NotNull String reason);

    /** Removes every scoped protection owned by the supplied plugin. */
    void clearProtections(@NotNull Plugin owner);
}
