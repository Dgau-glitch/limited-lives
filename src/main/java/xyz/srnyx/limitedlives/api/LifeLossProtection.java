package xyz.srnyx.limitedlives.api;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Idempotent scoped protection handle. */
public interface LifeLossProtection extends AutoCloseable {
    @NotNull UUID playerId();
    @NotNull Plugin owner();
    @NotNull String reason();
    boolean isActive();

    @Override
    void close();
}
