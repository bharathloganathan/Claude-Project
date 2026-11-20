package org.example.docsync.error;

public class SourceReadException extends DocSyncException {
    public SourceReadException(String message) {
        super(message);
    }

    public SourceReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
