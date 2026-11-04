package database;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KeyValueStore {

    private final ConcurrentHashMap<String, ValueEntry> storage = new ConcurrentHashMap<>();

    public void set(String key, String value){
        storage.put(key, new ValueEntry(value, null));
    }
    public String get(String key){
        ValueEntry entry = storage.computeIfPresent(key, (k, current) -> {
           if(current.expiresAt() != null &&
           current.expiresAt() <= System.currentTimeMillis()){
               return null;
           }

            return current;
        });

        return entry == null ? null : entry.value();
    }
    public boolean del(String key){
        boolean[] deleted = {false};

        storage.computeIfPresent(key, (k, current) -> {
            if(current.expiresAt() != null &&
                    current.expiresAt() <= System.currentTimeMillis()){
                return null;
            }

            deleted[0] = true;
            return null;
        });

        return deleted[0];
    }
    public boolean expire(String key, Long duration){
        long expiresAt;

        try {
            expiresAt = Math.addExact(
                    System.currentTimeMillis(),
                    Math.multiplyExact(duration, 1000L)
            );
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("EXPIRE value is too large", e);
        }

        boolean[] found = {false};

        storage.computeIfPresent(key, (k, current) -> {
            if(current.expiresAt() != null &&
                    current.expiresAt() <= System.currentTimeMillis()){
                return null;
            }

            found[0] = true;

            if(duration <= 0){
                return null;
            }

            return new ValueEntry(current.value(), expiresAt);
        });

        return found[0];
    }

    public void removeExpiredKeys(){

        for (String key : storage.keySet()) {
            storage.computeIfPresent(key, (k, current) -> {
                Long expiresAt = current.expiresAt();

                if (expiresAt != null && expiresAt <= System.currentTimeMillis()) {
                    System.out.println("Background cleanup removed: " + k);
                    return null;
                }

                return current;
            });
        }
    }

    public Map<String, ValueEntry> snapshot() {
        return new HashMap<>(storage);
    }

    public void restore(Map<String, ValueEntry> entries) {
        storage.clear();
        storage.putAll(entries);
    }

    public long incr(String key) {
        long[] result = new long[1];

        storage.compute(key, (k, entry) -> {
            long current = 0;
            Long expiresAt = null;

            if (entry != null &&
                    (entry.expiresAt() == null ||
                            entry.expiresAt() > System.currentTimeMillis())) {

                current = Long.parseLong(entry.value());
                expiresAt = entry.expiresAt();
            }

            long updated = Math.addExact(current, 1);
            result[0] = updated;

            return new ValueEntry(Long.toString(updated), expiresAt);
        });

        return result[0];
    }
}
