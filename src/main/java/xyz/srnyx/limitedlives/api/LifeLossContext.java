package xyz.srnyx.limitedlives.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Immutable cross-plugin death context; it contains no live killer entity. */
public record LifeLossContext(
        @NotNull UUID playerId,
        @NotNull String playerName,
        @Nullable String cause,
        boolean playerKill,
        @Nullable UUID killerId,
        @Nullable String killerName,
        long occurredAtMillis
) {}
