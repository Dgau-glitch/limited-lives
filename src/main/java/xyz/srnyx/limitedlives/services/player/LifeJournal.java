package xyz.srnyx.limitedlives.services.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Atomic, fsync-backed write-ahead snapshot for life mutations. The database
 * remains the primary store; this journal is replayed after crashes and is
 * intentionally independent of Bukkit and the AnnoyingAPI SQL connection.
 */
public final class LifeJournal {
    @NotNull private final Path file;
    @NotNull private final Path temporaryFile;
    @NotNull private final LongSupplier flushDelayMillis;
    @NotNull private final Logger logger;
    @NotNull private final Map<EntryKey, Mutation> entries = new HashMap<>();
    @NotNull private final AtomicBoolean flushScheduled = new AtomicBoolean();
    @NotNull private final AtomicLong revision = new AtomicLong();
    private ScheduledExecutorService executor;

    public LifeJournal(@NotNull Path file, @NotNull LongSupplier flushDelayMillis, @NotNull Logger logger) {
        this.file = file;
        this.temporaryFile = file.resolveSibling(file.getFileName() + ".tmp");
        this.flushDelayMillis = flushDelayMillis;
        this.logger = logger;
    }

    public synchronized void start() {
        if (executor != null) throw new IllegalStateException("Life journal already started");
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "LimitedLives-LifeJournal");
            thread.setDaemon(false);
            return thread;
        });
    }

    public synchronized void load() throws IOException {
        if (!Files.exists(file)) return;
        final Properties properties = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        for (final String serializedKey : properties.stringPropertyNames()) {
            final EntryKey key = EntryKey.parse(serializedKey);
            entries.putIfAbsent(key, Mutation.parse(properties.getProperty(serializedKey)));
        }
    }

    @NotNull
    public synchronized Map<EntryKey, Mutation> snapshot() {
        return Map.copyOf(entries);
    }

    public void set(@NotNull UUID uuid, @NotNull String key, @NotNull String value) {
        record(new EntryKey(uuid, key), new Mutation(value, false));
    }

    public void remove(@NotNull UUID uuid, @NotNull String key) {
        record(new EntryKey(uuid, key), new Mutation(null, true));
    }

    public void close() {
        final ScheduledExecutorService current;
        synchronized (this) {
            current = executor;
        }
        if (current == null) return;
        current.execute(this::persistSafely);
        current.shutdown();
        try {
            if (!current.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.severe("Timed out while flushing the life write-ahead journal");
            }
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            logger.log(Level.SEVERE, "Interrupted while flushing the life write-ahead journal", interrupted);
        }
    }

    private void record(@NotNull EntryKey key, @NotNull Mutation mutation) {
        synchronized (this) {
            entries.put(key, mutation);
        }
        revision.incrementAndGet();
        scheduleFlush();
    }

    private void scheduleFlush() {
        final ScheduledExecutorService current;
        synchronized (this) {
            current = executor;
        }
        if (current == null || current.isShutdown() || !flushScheduled.compareAndSet(false, true)) return;
        current.schedule(this::persistSafely, Math.max(0, flushDelayMillis.getAsLong()), TimeUnit.MILLISECONDS);
    }

    private void persistSafely() {
        final long persistedRevision = revision.get();
        try {
            persist();
        } catch (final IOException exception) {
            logger.log(Level.SEVERE, "Failed to persist the life write-ahead journal", exception);
        } finally {
            flushScheduled.set(false);
            if (revision.get() != persistedRevision) scheduleFlush();
        }
    }

    private void persist() throws IOException {
        final Map<EntryKey, Mutation> snapshot = snapshot();
        Files.createDirectories(file.getParent());
        final Properties properties = new Properties();
        snapshot.forEach((key, mutation) -> properties.setProperty(key.serialize(), mutation.serialize()));
        try (BufferedWriter writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            properties.store(writer, "LimitedLives crash-recovery journal; managed automatically");
        }
        try (FileChannel channel = FileChannel.open(temporaryFile, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        try {
            Files.move(temporaryFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (final AtomicMoveNotSupportedException unsupported) {
            Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record EntryKey(@NotNull UUID uuid, @NotNull String key) {
        @NotNull private String serialize() {
            return uuid + "|" + key;
        }

        @NotNull private static EntryKey parse(@NotNull String serialized) throws IOException {
            final int separator = serialized.indexOf('|');
            if (separator <= 0 || separator == serialized.length() - 1) throw new IOException("Invalid journal key: " + serialized);
            try {
                return new EntryKey(UUID.fromString(serialized.substring(0, separator)), serialized.substring(separator + 1));
            } catch (final IllegalArgumentException exception) {
                throw new IOException("Invalid journal UUID: " + serialized, exception);
            }
        }
    }

    public record Mutation(@Nullable String value, boolean removed) {
        @NotNull private String serialize() {
            return removed ? "R" : "S:" + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }

        @NotNull private static Mutation parse(@NotNull String serialized) throws IOException {
            if (serialized.equals("R")) return new Mutation(null, true);
            if (!serialized.startsWith("S:")) throw new IOException("Invalid journal mutation");
            try {
                return new Mutation(new String(Base64.getDecoder().decode(serialized.substring(2)), StandardCharsets.UTF_8), false);
            } catch (final IllegalArgumentException exception) {
                throw new IOException("Invalid journal value", exception);
            }
        }
    }
}
