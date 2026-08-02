package xyz.srnyx.limitedlives.api.internal;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import xyz.srnyx.limitedlives.api.LifeLossProtection;
import xyz.srnyx.limitedlives.api.LimitedLivesApi;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DefaultLimitedLivesApi implements LimitedLivesApi, Listener {
    @NotNull private final Set<UUID> manuallyDisabled = ConcurrentHashMap.newKeySet();
    @NotNull private final ConcurrentHashMap<UUID, Set<Protection>> protections = new ConcurrentHashMap<>();

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

    public void close() {
        manuallyDisabled.clear();
        protections.values().forEach(values -> values.forEach(Protection::close));
        protections.clear();
    }

    @EventHandler
    public void onPluginDisable(@NotNull PluginDisableEvent event) {
        clearProtections(event.getPlugin());
    }

    private final class Protection implements LifeLossProtection {
        @NotNull private final UUID playerId;
        @NotNull private final Plugin owner;
        @NotNull private final String reason;
        @NotNull private final AtomicBoolean active = new AtomicBoolean(true);

        private Protection(@NotNull UUID playerId, @NotNull Plugin owner, @NotNull String reason) {
            this.playerId = playerId;
            this.owner = owner;
            this.reason = reason;
        }

        @Override @NotNull public UUID playerId() { return playerId; }
        @Override @NotNull public Plugin owner() { return owner; }
        @Override @NotNull public String reason() { return reason; }
        @Override public boolean isActive() { return active.get(); }
        @Override
        public void close() {
            if (!active.compareAndSet(true, false)) return;
            protections.computeIfPresent(playerId, (ignored, values) -> {
                values.remove(this);
                return values.isEmpty() ? null : values;
            });
        }
    }
}
