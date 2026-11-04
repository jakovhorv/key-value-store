import database.KeyValueStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import persistence.PersistenceManager;
import scheduler.AutoSaveScheduler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AutoSaveSchedulerTest {

    @TempDir
    Path tempDir;

    @Test
    void autoSavePersistsData() throws IOException, InterruptedException {
        KeyValueStore store = new KeyValueStore();
        Path file = tempDir.resolve("database.dat");

        PersistenceManager persistence = new PersistenceManager(file);
        AutoSaveScheduler autoSave = new AutoSaveScheduler(store, persistence, 1);

        store.set("name", "Margery");

        try {
            assertFalse(Files.exists(file));

            autoSave.start();

            Thread.sleep(1500);

            assertTrue(Files.exists(file));

            KeyValueStore restored = new KeyValueStore();
            persistence.load(restored);

            assertEquals("Margery", restored.get("name"));
        } finally {
            autoSave.stop();
        }
    }
}
