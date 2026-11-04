import database.KeyValueStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import persistence.PersistenceManager;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadPreservesKeysAndValues() throws IOException {
        PersistenceManager persistence =
                new PersistenceManager(tempDir.resolve("database.dat"));

        KeyValueStore original = new KeyValueStore();
        original.set("name", "Margery");
        original.set("city", "London");

        persistence.save(original);

        KeyValueStore restored = new KeyValueStore();
        persistence.load(restored);

        assertEquals("Margery", restored.get("name"));
        assertEquals("London", restored.get("city"));
        assertEquals(2, restored.snapshot().size());
    }

    @Test
    void expiredKeysAreNotLoaded() throws IOException, InterruptedException{
        PersistenceManager persistence =
                new PersistenceManager(tempDir.resolve("database.dat"));

        KeyValueStore original = new KeyValueStore();
        original.set("name", "Margery");
        original.expire("name", 1L);

        persistence.save(original);

        Thread.sleep(1100);

        KeyValueStore restored = new KeyValueStore();
        persistence.load(restored);

        assertEquals(0, restored.snapshot().size());
    }

    @Test
    void loadFileWithInvalidMagicNumberThrowsException() throws IOException{
        Path file = tempDir.resolve("database.dat");

        try (DataOutputStream out =
                     new DataOutputStream(Files.newOutputStream(file))) {
            out.writeInt(12345);
            out.writeInt(1);
            out.writeInt(0);
        }

        PersistenceManager persistence = new PersistenceManager(file);
        KeyValueStore store = new KeyValueStore();

        assertThrows(IOException.class, () -> persistence.load(store));
    }

    @Test
    void unexpiredKeysMaintainExpiration() throws IOException{
        PersistenceManager persistence =
                new PersistenceManager(tempDir.resolve("database.dat"));

        KeyValueStore original = new KeyValueStore();
        original.set("name", "Margery");

        original.expire("name", 1000L);
        Long originalExpiration = original.snapshot().get("name").expiresAt();

        persistence.save(original);

        KeyValueStore restored = new KeyValueStore();
        persistence.load(restored);

        assertEquals(
                originalExpiration,
                restored.snapshot().get("name").expiresAt()
        );
    }

    @Test
    void saveAndLoadUnicodeStringsPreservesThem() throws IOException {
        PersistenceManager persistence =
                new PersistenceManager(tempDir.resolve("database.dat"));

        KeyValueStore original = new KeyValueStore();
        original.set("ččč", "žžž");

        persistence.save(original);

        KeyValueStore restored = new KeyValueStore();
        persistence.load(restored);

        assertEquals("žžž", restored.get("ččč"));
    }
}