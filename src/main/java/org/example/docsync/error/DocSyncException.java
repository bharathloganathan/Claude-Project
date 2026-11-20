package org.example.docsync.error;

// Every DocSyncException maps to exit code 2 at the single CLI boundary (E2-2).
public class DocSyncException extends RuntimeException {
    public DocSyncException(String message) {
        super(message);
    }

    public DocSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
