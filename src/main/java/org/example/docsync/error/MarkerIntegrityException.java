package org.example.docsync.error;

public class MarkerIntegrityException extends DocSyncException {
    public MarkerIntegrityException(String message) {
        super(message);
    }

    public MarkerIntegrityException(String message, Throwable cause) {
        super(message, cause);
    }
}
