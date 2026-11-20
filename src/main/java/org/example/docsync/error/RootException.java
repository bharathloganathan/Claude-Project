package org.example.docsync.error;

public class RootException extends DocSyncException {
    public RootException(String message) {
        super(message);
    }

    public RootException(String message, Throwable cause) {
        super(message, cause);
    }
}
