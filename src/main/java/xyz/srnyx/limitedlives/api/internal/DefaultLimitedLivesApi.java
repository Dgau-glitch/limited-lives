package xyz.srnyx.limitedlives.api.internal;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.srnyx.limitedlives.api.LifeLossProtection;
import xyz.srnyx.limitedlives.api.LimitedLivesApi;
import xyz.srnyx.limitedlives.api.LifeMutationResult;
import xyz.srnyx.limitedlives.api.LifeOverflowPolicy;
import xyz.srnyx.limitedlives.api.PlayerKillLifeLossAllowance;
import xyz.srnyx.limitedlives.LimitedLives;
import xyz.srnyx.limitedlives.config.GracePeriodTrigger;
import xyz.srnyx.limitedlives.config.LimitedConfig;
import xyz.srnyx.limitedlives.managers.player.PlayerManager;

import java.util.Set;
import java.util.UUID;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletionStage;

public final class DefaultLimitedLivesApi implements LimitedLivesApi, Listener {
    @Nullable private final LimitedLives plugin;
    @NotNull private final Set<UUID> manuallyDisabled = ConcurrentHashMap.newKeySet();
    @NotNull private final ConcurrentHashMap<UUID, Set<Protection>> protections = new ConcurrentHashMap<>();
    @NotNull private final ConcurrentHashMap<UUID, Set<KillAllowance>> killAllowances = new ConcurrentHashMap<>();

    public DefaultLimitedLivesApi(@NotNull LimitedLives plugin) {
        this.plugin = plugin;
    }

    DefaultLimitedLivesApi() {
        this.plugin = null;
    }

    @Override
    public boolean isDataReady() {
        return plugin != null && plugin.lifeStore.isReady();
    }

    @Override
    public int addLives(@NotNull UUID playerId, int amount) {
        return addLives(playerId, amount, LifeOverflowPolicy.CLAMP).newLives();
    }

    @Override @NotNull
    public LifeMutationResult addLives(@NotNull UUID playerId, int amount,
                                       @NotNull LifeOverflowPolicy overflowPolicy) {
        final LimitedLives plugin = requirePlugin();
        if (!plugin.lifeStore.isReady()) throw new IllegalStateException("LimitedLives data preload is not complete; use addLivesAsync");
        final LimitedConfig config = plugin.config;
        final LifeMutationResult result = plugin.lifeStore.atomic(playerId, () -> {
            final int oldLives = readLives(playerId, config.lives.def);
            final LifeMutationResult calculated = LifeMutationCalculator.add(oldLives, amount,
                    config.lives.min, config.lives.max, overflowPolicy);
            if (!calculated.changed()) return calculated;
            plugin.lifeStore.set(playerId, PlayerManager.LIVES_KEY, calculated.newLives());
            if (calculated.revived()) {
                plugin.lifeStore.remove(playerId, PlayerManager.DEAD_KEY);
                if (config.gracePeriod.triggers.contains(GracePeriodTrigger.REVIVE)) {
                    plugin.lifeStore.set(playerId, PlayerManager.GRACE_START_KEY, System.currentTimeMillis());
                }
            }
            return calculated;
        });
        plugin.placeholders.updateLives(playerId, result.newLives());
        return result;
    }

    @Override @NotNull
    public CompletionStage<LifeMutationResult> addLivesAsync(@NotNull UUID playerId, int amount,
                                                              @NotNull LifeOverflowPolicy overflowPolicy) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        final LimitedLives plugin = requirePlugin();
        return plugin.lifeStore.ready().thenApply(ignored -> addLives(playerId, amount, overflowPolicy));
    }

    private int readLives(@NotNull UUID playerId, int defaultLives) {
        final LimitedLives plugin = requirePlugin();
        final String stored = plugin.lifeStore.get(playerId, PlayerManager.LIVES_KEY);
        if (stored == null) return defaultLives;
        try {
            return Integer.parseInt(stored);
        } catch (final NumberFormatException invalid) {
            plugin.lifeStore.remove(playerId, PlayerManager.LIVES_KEY);
            return defaultLives;
        }
    }

    @NotNull
    private LimitedLives requirePlugin() {
        if (plugin == null) throw new IllegalStateException("API is not attached to LimitedLives");
        return plugin;
    }

    @Override public void enableLifeLoss(@NotNull UUID playerId) { manuallyDisabled.remove(playerId); }
    @Override public void disableLifeLoss(@NotNull UUID playerId) { manuallyDisabled.add(playerId); }

    @Override
    public boolean isLifeLossEnabled(@NotNull UUID playerId) {
        return !manuallyDisabled.contains(playerId)
                && !protections.containsKey(playerId);
    }

    @Override @NotNull
    public LifeLossProtection protect(@NotNull UUID playerId, @NotNull Plugin owner, @NotNull String reason) {
        final Protection protection = new Protection(playerId, owner, reason);
        protections.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet()).add(protection);
        return protection;
    }

    @Override
    public void clearProtections(@NotNull Plugin owner) {
        protections.values().forEach(values -> values.stream()
                .filter(protection -> protection.owner().equals(owner))
                .forEach(Protection::close));
    }

    @Override @NotNull
    public PlayerKillLifeLossAllowance allowPlayerKillLifeLoss(@NotNull UUID killerId,
                                                               @NotNull Plugin owner,
                                                               @NotNull String reason) {
        final KillAllowance allowance = new KillAllowance(killerId, owner, reason);
        killAllowances.computeIfAbsent(killerId, ignored -> ConcurrentHashMap.newKeySet()).add(allowance);
        return allowance;
    }

    @Override
    public boolean isPlayerKillLifeLossAllowed(@NotNull UUID killerId) {
        return killAllowances.containsKey(killerId);
    }

    @Override
    public void clearPlayerKillLifeLossAllowances(@NotNull Plugin owner) {
        killAllowances.values().forEach(values -> values.stream()
                .filter(allowance -> allowance.owner().equals(owner))
                .forEach(KillAllowance::close));
    }

    public void close() {
        manuallyDisabled.clear();
        protections.values().forEach(values -> values.forEach(Protection::close));
        protections.clear();
        killAllowances.values().forEach(values -> values.forEach(KillAllowance::close));
        killAllowances.clear();
    }

    @EventHandler
    public void onPluginDisable(@NotNull PluginDisableEvent event) {
        clearProtections(event.getPlugin());
        clearPlayerKillLifeLossAllowances(event.getPlugin());
    }

    private final class Protection extends ScopedHandle implements LifeLossProtection {
        private Protection(@NotNull UUID playerId, @NotNull Plugin owner, @NotNull String reason) {
            super(playerId, owner, reason);
        }

        @Override @NotNull public UUID playerId() { return subjectId(); }
        @Override protected void remove() {
            protections.computeIfPresent(subjectId(), (ignored, values) -> {
                values.remove(this);
                return values.isEmpty() ? null : values;
            });
        }
    }

    private final class KillAllowance extends ScopedHandle implements PlayerKillLifeLossAllowance {
        private KillAllowance(@NotNull UUID killerId, @NotNull Plugin owner, @NotNull String reason) {
            super(killerId, owner, reason);
        }

        @Override @NotNull public UUID killerId() { return subjectId(); }
        @Override protected void remove() {
            killAllowances.computeIfPresent(subjectId(), (ignored, values) -> {
                values.remove(this);
                return values.isEmpty() ? null : values;
            });
        }
    }

    private abstract static class ScopedHandle implements AutoCloseable {
        @NotNull private final UUID subjectId;
        @NotNull private final Plugin owner;
        @NotNull private final String reason;
        @NotNull private final AtomicBoolean active = new AtomicBoolean(true);

        private ScopedHandle(@NotNull UUID subjectId, @NotNull Plugin owner, @NotNull String reason) {
            this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
            this.owner = Objects.requireNonNull(owner, "owner");
            this.reason = Objects.requireNonNull(reason, "reason");
        }

        @NotNull protected final UUID subjectId() { return subjectId; }
        @NotNull public final Plugin owner() { return owner; }
        @NotNull public final String reason() { return reason; }
        public final boolean isActive() { return active.get(); }
        @Override public final void close() {
            if (active.compareAndSet(true, false)) remove();
        }
        protected abstract void remove();
    }
}
