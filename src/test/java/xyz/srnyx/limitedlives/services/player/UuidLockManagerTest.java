package xyz.srnyx.limitedlives.services.player;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UuidLockManagerTest {
    @Test
    void serializesConcurrentUpdatesForOneUuid() throws Exception {
        final UuidLockManager locks = new UuidLockManager();
        final UUID uuid = UUID.randomUUID();
        final AtomicInteger value = new AtomicInteger();
        final int workers = 8;
        final int updatesPerWorker = 500;
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(workers);
        final List<java.util.concurrent.Future<?>> futures = new ArrayList<>();

        for (int worker = 0; worker < workers; worker++) {
            futures.add(executor.submit(() -> {
                start.await();
                for (int update = 0; update < updatesPerWorker; update++) {
                    locks.withLock(uuid, () -> {
                        final int current = value.get();
                        Thread.yield();
                        value.set(current + 1);
                        return null;
                    });
                }
                return null;
            }));
        }

        start.countDown();
        for (final java.util.concurrent.Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(workers * updatesPerWorker, value.get());
    }

    @Test
    void stableOrderingPreventsOppositeOrderDeadlock() throws Exception {
        final UuidLockManager locks = new UuidLockManager();
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger completed = new AtomicInteger();

        final java.util.concurrent.Future<?> forward = executor.submit(() -> {
            start.await();
            locks.withLocksChecked(List.of(first, second), () -> completed.incrementAndGet());
            return null;
        });
        final java.util.concurrent.Future<?> reverse = executor.submit(() -> {
            start.await();
            locks.withLocksChecked(List.of(second, first), () -> completed.incrementAndGet());
            return null;
        });

        start.countDown();
        forward.get(5, TimeUnit.SECONDS);
        reverse.get(5, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(2, completed.get());
    }
}
