package xyz.srnyx.limitedlives.services.player;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.srnyx.limitedlives.LimitedLives;
import xyz.srnyx.limitedlives.managers.player.PlayerManager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/** Entity-context permission snapshots used by potentially asynchronous PAPI callbacks. */
public final class PlaceholderSnapshotService {
    @NotNull private final LimitedLives plugin;
    @NotNull private final ConcurrentHashMap<UUID, PlayerSnapshot> snapshots = new ConcurrentHashMap<>();
    @NotNull private final Map<Player, UUID> identities = Collections.synchronizedMap(new IdentityHashMap<>());

    public PlaceholderSnapshotService(@NotNull LimitedLives plugin) {
        this.plugin = plugin;
    }

    public void capture(@NotNull Player player) {
        final PlayerManager manager = new PlayerManager(plugin, player);
        snapshots.put(player.getUniqueId(), new PlayerSnapshot(
                manager.getMaxLives(),
                player.hasPermission("limitedlives.bypass"),
                manager.getLives(),
                manager.getGraceLeft()));
        identities.put(player, player.getUniqueId());
    }

    public void remove(@NotNull UUID uuid) {
        snapshots.remove(uuid);
        synchronized (identities) {
            identities.values().removeIf(uuid::equals);
        }
    }

    @NotNull
    public PlayerSnapshot get(@NotNull UUID uuid) {
        final PlayerSnapshot snapshot = snapshots.get(uuid);
        return snapshot != null ? snapshot : new PlayerSnapshot(plugin.config.lives.max, false, plugin.config.lives.def, 0);
    }

    @Nullable
    public UUID resolve(@Nullable Player player, @NotNull String explicitName) {
        return player != null ? identities.get(player) : plugin.onlinePlayers.uuid(explicitName);
    }

    public record PlayerSnapshot(int maxLives, boolean bypass, int lives, long graceLeft) {
        public boolean graceActive() {
            return graceLeft > 0;
        }
    }
}
