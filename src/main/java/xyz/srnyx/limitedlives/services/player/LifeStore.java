package xyz.srnyx.limitedlives.services.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import xyz.srnyx.annoyingapi.data.EntityData;
import xyz.srnyx.annoyingapi.data.StringData;
import xyz.srnyx.limitedlives.LimitedLives;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** UUID-only persistence boundary. It never accesses Bukkit players, entities or worlds. */
public final class LifeStore {
    @NotNull private final LimitedLives plugin;
    @NotNull private final UuidLockManager lockManager;
    @NotNull private final AtomicBoolean acceptingOperations = new AtomicBoolean(true);

    public LifeStore(@NotNull LimitedLives plugin) {
        this(plugin, new UuidLockManager());
    }

    LifeStore(@NotNull LimitedLives plugin, @NotNull UuidLockManager lockManager) {
        this.plugin = plugin;
        this.lockManager = lockManager;
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

    @Nullable
    public String get(@NotNull UUID uuid, @NotNull String key) {
        return data(uuid).get(key);
    }

    public boolean set(@NotNull UUID uuid, @NotNull String key, @NotNull Object value) {
        return data(uuid).set(key, value);
    }

    public boolean remove(@NotNull UUID uuid, @NotNull String key) {
        return data(uuid).remove(key);
    }

    /**
     * AnnoyingAPI writes are synchronous when its cache is disabled. If a server
     * administrator enables its cache, this performs the required synchronous flush.
     */
    public void flush() {
        if (plugin.dataManager != null) plugin.dataManager.dialect.saveCache();
    }

    /** Stops new operations. AnnoyingPlugin has already synchronously flushed at this extension point. */
    public void close() {
        acceptingOperations.set(false);
    }

    private void requireOpen() {
        if (!acceptingOperations.get()) throw new IllegalStateException("Life store is closed");
    }

    @NotNull
    private StringData data(@NotNull UUID uuid) {
        return new StringData(plugin, EntityData.TABLE_NAME, uuid.toString());
    }

    @FunctionalInterface
    public interface CheckedSupplier<T, E extends Exception> {
        T get() throws E;
    }

}
