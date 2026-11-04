package scheduler;

import database.KeyValueStore;
import persistence.PersistenceManager;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AutoSaveScheduler {
    private final KeyValueStore store;
    private final PersistenceManager persistence;
    private final long intervalSeconds;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    public AutoSaveScheduler(KeyValueStore store, PersistenceManager persistence) {
        this(store, persistence, 60);
    }

    public AutoSaveScheduler(KeyValueStore store, PersistenceManager persistence,
                             long intervalSeconds) {
        this.store = store;
        this.persistence = persistence;
        this.intervalSeconds = intervalSeconds;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(
                () -> {
                    try{
                        persistence.save(store);
                    }
                    catch(IOException e){
                        System.err.println("Autosave failed: " + e.getMessage());
                    }
                },
                intervalSeconds,
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
