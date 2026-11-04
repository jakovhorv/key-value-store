import database.KeyValueStore;
import org.junit.jupiter.api.Test;
import scheduler.ExpirationCleaner;
import static org.junit.jupiter.api.Assertions.*;

public class ExpirationCleanerTest {

    @Test
    void cleanerRemovesExpiredKeysAndPreservesUnexpiredKeys() throws InterruptedException{
        KeyValueStore store = new KeyValueStore();
        ExpirationCleaner cleaner = new ExpirationCleaner(store);

        store.set("a", "Margery");
        store.set("b", "Jack");
        store.expire("a", 1L);
        store.expire("b", 1000L);

        try{
            cleaner.start();
            Thread.sleep(2200);

            assertFalse(store.snapshot().containsKey("a"));
            assertTrue(store.snapshot().containsKey("b"));
        }
        finally {
            cleaner.stop();
        }
    }
}
