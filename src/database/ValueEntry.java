package database;

import java.io.Serializable;

public record ValueEntry(String value, Long expiresAt)
        implements Serializable {}
