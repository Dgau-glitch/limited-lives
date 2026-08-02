package xyz.srnyx.limitedlives.services.player;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifeJournalTest {
    @TempDir Path temporaryDirectory;

    @Test
    void persistsLatestValuesAndRemovalsAcrossRestart() throws Exception {
        final Path file = temporaryDirectory.resolve("journal.properties");
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();
        final LifeJournal writer = new LifeJournal(file, () -> 0, Logger.getAnonymousLogger());
        writer.start();
        writer.set(first, "ll_lives", "4");
        writer.set(first, "ll_lives", "7");
        writer.remove(second, "ll_dead");
        writer.close();

        final LifeJournal reader = new LifeJournal(file, () -> 0, Logger.getAnonymousLogger());
        reader.start();
        reader.load();
        assertEquals("7", reader.snapshot().get(new LifeJournal.EntryKey(first, "ll_lives")).value());
        assertTrue(reader.snapshot().get(new LifeJournal.EntryKey(second, "ll_dead")).removed());
        reader.close();
    }
}
