package persistence;

import database.KeyValueStore;
import database.ValueEntry;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public class PersistenceManager {

    private static final int MAGIC = 0x4B564442; // "KVDB"
    private static final int VERSION = 1;

    private final Path filePath;

    public PersistenceManager() {
        this(Path.of("database.dat"));
    }

    public PersistenceManager(Path filePath) {
        this.filePath = filePath;
    }

    public synchronized void save(KeyValueStore store) throws IOException {
        Map<String, ValueEntry> snapshot = store.snapshot();

        try (DataOutputStream out =
                     new DataOutputStream(new FileOutputStream(filePath.toString() + ".tmp"))) {

            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(snapshot.size());

            for (Map.Entry<String, ValueEntry> entry : snapshot.entrySet()) {
                writeString(out, entry.getKey());
                writeString(out, entry.getValue().value());

                Long expiresAt = entry.getValue().expiresAt();
                out.writeLong(expiresAt == null ? -1L : expiresAt);
            }
        }

        Files.move(
                Path.of(filePath + ".tmp"),
                filePath,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
        );
    }

    private void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

        out.writeInt(bytes.length);
        out.write(bytes);
    }

    public void load(KeyValueStore store) throws IOException {
        try (DataInputStream in =
                     new DataInputStream(Files.newInputStream(filePath))) {

            int magic = in.readInt();
            int version = in.readInt();
            int entryCount = in.readInt();

            if (magic != MAGIC || version != VERSION) {
                throw new IOException("Invalid database file format");
            }

            Map<String, ValueEntry> entries = new HashMap<>();

            for (int i = 0; i < entryCount; i++) {
                String key = readString(in);
                String value = readString(in);
                long expiration = in.readLong();

                Long expiresAt = expiration == -1L ? null : expiration;

                if (expiresAt == null || expiresAt > System.currentTimeMillis()) {
                    entries.put(key, new ValueEntry(value, expiresAt));
                }
            }

            store.restore(entries);
        }
    }

    private String readString(DataInputStream in) throws IOException {
        int length = in.readInt();

        if (length < 0 || length > 16 * 1024 * 1024) {
            throw new IOException("Invalid string length");
        }

        byte[] bytes = new byte[length];
        in.readFully(bytes);

        return new String(bytes, StandardCharsets.UTF_8);
    }
}