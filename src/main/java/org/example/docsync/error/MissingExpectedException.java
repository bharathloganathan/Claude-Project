package org.example.docsync.error;

public class MissingExpectedException extends DocSyncException {
    public MissingExpectedException(String message) {
        super(message);
    }

    public MissingExpectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
