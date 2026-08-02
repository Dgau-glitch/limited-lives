package xyz.srnyx.limitedlives.services.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import xyz.srnyx.annoyingapi.data.EntityData;
import xyz.srnyx.annoyingapi.storage.Value;
import xyz.srnyx.limitedlives.LimitedLives;

import java.util.UUID;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** UUID-only persistence boundary. It never accesses Bukkit players, entities or worlds. */
public final class LifeStore {
    @NotNull private final LimitedLives plugin;
    @NotNull private final UuidLockManager lockManager;
    @NotNull private final AtomicBoolean acceptingOperations = new AtomicBoolean(true);
    @NotNull private final Set<CacheKey> dirty = ConcurrentHashMap.newKeySet();

    public LifeStore(@NotNull LimitedLives plugin) {
        this(plugin, new UuidLockManager());
    }

    LifeStore(@NotNull LimitedLives plugin, @NotNull UuidLockManager lockManager) {
        this.plugin = plugin;
        this.lockManager = lockManager;
    }

    /** Loads the complete persistence snapshot away from every Folia tick thread. */
    public void start() {
        plugin.execution.runAsync(() -> {
            if (!acceptingOperations.get() || plugin.dataManager == null) return;
            plugin.dataManager.dialect.getMigrationDataFromDatabase(plugin.dataManager).ifPresent(migration -> {
                final var entities = migration.data.get(plugin.dataManager.getTableName(EntityData.TABLE_NAME));
                if (entities == null) return;
                entities.forEach((target, values) -> values.forEach((key, value) -> {
                    if (!dirty.contains(new CacheKey(target, key))) {
                        plugin.dataManager.dialect.setToCache(plugin.dataManager.getTableName(EntityData.TABLE_NAME), target, key, value);
                    }
                }));
            });
            plugin.execution.runGlobal(() -> plugin.getServer().getOnlinePlayers().forEach(player ->
                    plugin.execution.runForEntityOrNow(player, () -> plugin.placeholders.capture(player), () -> {})));
        });
    }

    public <T> T atomic(@NotNull UUID uuid, @NotNull Supplier<T> operation) {
        requireOpen();
        return lockManager.withLock(uuid, () -> {
            requireOpen();
            return operation.get();
        });
    }

    public <T, E extends Exception> T atomicChecked(@NotNull UUID uuid, @NotNull CheckedSupplier<T, E> operation) throws E {
        requireOpen();
        return lockManager.withLockChecked(uuid, () -> {
            requireOpen();
            return operation.get();
        });
    }

    public <T, E extends Exception> T atomicAllChecked(@NotNull Collection<UUID> uuids, @NotNull CheckedSupplier<T, E> operation) throws E {
        requireOpen();
        return lockManager.withLocksChecked(uuids, () -> {
            requireOpen();
            return operation.get();
        });
    }

    @Nullable
    public String get(@NotNull UUID uuid, @NotNull String key) {
        if (plugin.dataManager == null) return null;
        final Value value = plugin.dataManager.dialect.getFromCache(table(), uuid.toString(), key);
        return value == null ? null : value.value;
    }

    public boolean set(@NotNull UUID uuid, @NotNull String key, @NotNull Object value) {
        requireOpen();
        dirty.add(new CacheKey(uuid.toString(), key));
        plugin.dataManager.dialect.setToCache(table(), uuid.toString(), key, new Value(String.valueOf(value)));
        return true;
    }

    public boolean remove(@NotNull UUID uuid, @NotNull String key) {
        requireOpen();
        dirty.add(new CacheKey(uuid.toString(), key));
        plugin.dataManager.dialect.markRemovedInCache(table(), uuid.toString(), key);
        return true;
    }

    /** Stops new operations. AnnoyingPlugin has already synchronously flushed at this extension point. */
    public void close() {
        acceptingOperations.set(false);
    }

    private void requireOpen() {
        if (!acceptingOperations.get()) throw new IllegalStateException("Life store is closed");
    }

    @NotNull
    private String table() {
        return plugin.dataManager.getTableName(EntityData.TABLE_NAME);
    }

    private record CacheKey(@NotNull String target, @NotNull String key) {}

    @FunctionalInterface
    public interface CheckedSupplier<T, E extends Exception> {
        T get() throws E;
    }

}
