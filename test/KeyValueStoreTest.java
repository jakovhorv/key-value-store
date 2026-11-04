import database.KeyValueStore;
import database.ValueEntry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class KeyValueStoreTest {

    @Test
    void setStoresValueWithoutExpiration() {
        KeyValueStore store = new KeyValueStore();

        store.set("name", "Margery");

        ValueEntry entry = store.snapshot().get("name");

        assertNotNull(entry);
        assertEquals("Margery", entry.value());
        assertNull(entry.expiresAt());
    }

    @Test
    void setSameKeyOverwritesAndSetsExpirationAsNull(){
        KeyValueStore store = new KeyValueStore();

        store.set("name", "Margery");
        store.expire("name", 60L);
        store.set("name", "Jack");

        ValueEntry entry = store.snapshot().get("name");

        assertEquals("Jack", entry.value());
        assertNull(entry.expiresAt());
    }

    @Test
    void getNonexistentKeyIsNull(){
        KeyValueStore store = new KeyValueStore();
        assertNull(store.get("test"));
    }

    @Test
    void getKeyReturnsCorrectValue(){
        KeyValueStore store = new KeyValueStore();
        store.set("name", "Margery");

        assertEquals("Margery", store.get("name"));
    }

    @Test
    void getExpiredKeyIsNullAndDeletesKey(){
        KeyValueStore store = new KeyValueStore();
        store.set("name", "Margery");
        store.expire("name", 0L);

        assertNull(store.get("name"));
        assertFalse(store.snapshot().containsKey("name"));
    }

    @Test
    void getUnexpiredKeyDoesNotChangeExpiration(){
        KeyValueStore store = new KeyValueStore();
        store.set("name", "Margery");
        store.expire("name", 1000L);

        Long before = store.snapshot().get("name").expiresAt();
        store.get("name");
        Long after = store.snapshot().get("name").expiresAt();

        assertEquals(before, after);
    }

    @Test
    void delExistingKeyReturnsTrueAndDeletesKey() {
        KeyValueStore store = new KeyValueStore();
        store.set("name", "Margery");

        assertTrue(store.del("name"));
        assertFalse(store.snapshot().containsKey("name"));
    }

    @Test
    void delExpiredKeyReturnsFalseAndDeletesKey() throws InterruptedException{
        KeyValueStore store = new KeyValueStore();
        store.set("name", "Margery");
        store.expire("name", 1L);

        Thread.sleep(1100);

        assertTrue(store.snapshot().containsKey("name"));

        boolean deleted = store.del("name");

        assertFalse(deleted);
        assertFalse(store.snapshot().containsKey("name"));
    }

    @Test
    void delNonexistentKeyReturnsFalseAndKeepsExistingKey() {
        KeyValueStore store = new KeyValueStore();
        store.set("name", "Margery");

        assertFalse(store.del("age"));
        assertNotNull(store.get("name"));
    }

    @Test
    void expireThrowsExceptionOnOverflow() {
        KeyValueStore store = new KeyValueStore();
        store.set("name", "Margery");

        assertThrows(
                IllegalArgumentException.class,
                () -> store.expire("name", Long.MAX_VALUE)
        );
    }

    @Test
    void expirePositiveDurationSetsExpiration() {
        KeyValueStore store = new KeyValueStore();
        store.set("name", "Margery");

        long before = System.currentTimeMillis();

        assertTrue(store.expire("name", 1000L));

        long after = System.currentTimeMillis();
        Long expiresAt = store.snapshot().get("name").expiresAt();

        assertNotNull(expiresAt);
        assertTrue(expiresAt >= before + 1000_000L);
        assertTrue(expiresAt <= after + 1000_000L);
    }

    @Test
    void expireNonexistentKeyReturnsFalse() {
        KeyValueStore store = new KeyValueStore();

        assertFalse(store.expire("name", 1000L));
        assertFalse(store.snapshot().containsKey("name"));
    }

    @Test
    void expireExistingExpirationUpdatesExpiration() {
        KeyValueStore store = new KeyValueStore();
        store.set("name", "Margery");

        store.expire("name", 1000L);
        Long originalExpiration = store.snapshot().get("name").expiresAt();

        store.expire("name", 2000L);

        Long updatedExpiration = store.snapshot().get("name").expiresAt();

        assertTrue(updatedExpiration > originalExpiration);
    }

    @Test
    void expireZeroOrNegativeDurationDeletesKey() {
        KeyValueStore store = new KeyValueStore();

        store.set("first", "Margery");
        store.set("second", "Jack");

        assertTrue(store.expire("first", 0L));
        assertTrue(store.expire("second", -5L));

        assertFalse(store.snapshot().containsKey("first"));
        assertFalse(store.snapshot().containsKey("second"));
    }

    @Test
    void expireAlreadyExpiredKeyReturnsFalse() throws InterruptedException {
        KeyValueStore store = new KeyValueStore();
        store.set("name", "Margery");

        store.expire("name", 1L);

        Thread.sleep(1100);

        assertTrue(store.snapshot().containsKey("name"));
        assertFalse(store.expire("name", 1000L));
        assertFalse(store.snapshot().containsKey("name"));
    }

    @Test
    void incrIncrementsExistingAndMissingKeys() {
        KeyValueStore store = new KeyValueStore();

        store.set("counter", "10");

        assertEquals(11L, store.incr("counter"));
        assertEquals(12L, store.incr("counter"));
        assertEquals("12", store.get("counter"));

        assertEquals(1L, store.incr("missing"));
        assertEquals("1", store.get("missing"));
    }

    @Test
    void incrRejectsInvalidValuesAndOverflow() {
        KeyValueStore store = new KeyValueStore();

        store.set("name", "Alice");

        assertThrows(NumberFormatException.class,
                () -> store.incr("name"));

        assertEquals("Alice", store.get("name"));

        store.set("counter", Long.toString(Long.MAX_VALUE));

        assertThrows(ArithmeticException.class,
                () -> store.incr("counter"));

        assertEquals(Long.toString(Long.MAX_VALUE),
                store.get("counter"));
    }

    @Test
    void incrPreservesExpiration() {
        KeyValueStore store = new KeyValueStore();

        store.set("counter", "5");
        store.expire("counter", 60L);

        Long before = store.snapshot().get("counter").expiresAt();

        assertEquals(6L, store.incr("counter"));

        Long after = store.snapshot().get("counter").expiresAt();

        assertEquals(before, after);
    }

    @Test
    void incrIsAtomicUnderConcurrentAccess() throws InterruptedException {
        KeyValueStore store = new KeyValueStore();

        int threadCount = 10;
        int incrementsPerThread = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        try {
            for (int i = 0; i < threadCount; i++) {
                executor.execute(() -> {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        store.incr("counter");
                    }
                });
            }
        } finally {
            executor.shutdown();
        }

        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals("10000", store.get("counter"));
    }

}