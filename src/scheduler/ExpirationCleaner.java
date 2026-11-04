package scheduler;

import database.KeyValueStore;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ExpirationCleaner {

    private final KeyValueStore store;
    private final long intervalSeconds;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    public ExpirationCleaner(KeyValueStore store) {
        this(store, 1);
    }

    public ExpirationCleaner(KeyValueStore store, long intervalSeconds){
        this.store = store;
        this.intervalSeconds = intervalSeconds;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(
                store::removeExpiredKeys,
                0,
                intervalSeconds,
                TimeUnit.SECONDS
        );
    }

    public void stop() throws InterruptedException {
        scheduler.shutdown();
        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
            scheduler.shutdownNow();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Scheduler did not terminate");
            }
        }
    }
}