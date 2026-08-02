package xyz.srnyx.limitedlives.services.player;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;

/** Thread-safe immutable-name snapshots for tab completion. */
public final class OnlinePlayerDirectory {
    @NotNull private final ConcurrentHashMap<UUID, String> names = new ConcurrentHashMap<>();
    @NotNull private final ConcurrentHashMap<String, UUID> uuidsByName = new ConcurrentHashMap<>();

    public void joined(@NotNull Player player) {
        names.put(player.getUniqueId(), player.getName());
        uuidsByName.put(player.getName().toLowerCase(Locale.ROOT), player.getUniqueId());
    }

    public void quit(@NotNull Player player) {
        final String removed = names.remove(player.getUniqueId());
        if (removed != null) uuidsByName.remove(removed.toLowerCase(Locale.ROOT), player.getUniqueId());
    }

    @NotNull
    public Collection<String> names() {
        return new ArrayList<>(names.values());
    }

    public UUID uuid(@NotNull String name) {
        return uuidsByName.get(name.toLowerCase(Locale.ROOT));
    }
}
