package xyz.srnyx.limitedlives.services.player;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Serializes read-modify-write operations for one player without scheduling Bukkit work. */
public final class UuidLockManager {
    @NotNull private final ConcurrentHashMap<UUID, LockEntry> locks = new ConcurrentHashMap<>();

    public <T> T withLock(@NotNull UUID uuid, @NotNull Supplier<T> operation) {
        return withLockChecked(uuid, operation::get);
    }

    public <T, E extends Exception> T withLockChecked(@NotNull UUID uuid, @NotNull CheckedSupplier<T, E> operation) throws E {
        final LockEntry entry = locks.compute(uuid, (ignored, current) -> {
            final LockEntry selected = current == null ? new LockEntry() : current;
            selected.users++;
            return selected;
        });
        entry.lock.lock();
        try {
            return operation.get();
        } finally {
            entry.lock.unlock();
            locks.computeIfPresent(uuid, (ignored, current) -> {
                if (current != entry) return current;
                current.users--;
                return current.users == 0 ? null : current;
            });
        }
    }

    @FunctionalInterface
    public interface CheckedSupplier<T, E extends Exception> {
        T get() throws E;
    }

    private static final class LockEntry {
        private final ReentrantLock lock = new ReentrantLock();
        private int users;
    }
}
