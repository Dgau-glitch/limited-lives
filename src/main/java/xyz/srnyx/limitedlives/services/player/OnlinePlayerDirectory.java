package xyz.srnyx.limitedlives.services.player;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe immutable-name snapshots for tab completion. */
public final class OnlinePlayerDirectory {
    @NotNull private final ConcurrentHashMap<UUID, String> names = new ConcurrentHashMap<>();

    public void joined(@NotNull Player player) {
        names.put(player.getUniqueId(), player.getName());
    }

    public void quit(@NotNull Player player) {
        names.remove(player.getUniqueId());
    }

    @NotNull
    public Collection<String> names() {
        return new ArrayList<>(names.values());
    }
}
