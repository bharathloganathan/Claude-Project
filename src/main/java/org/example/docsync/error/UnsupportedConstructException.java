package org.example.docsync.error;

public class UnsupportedConstructException extends DocSyncException {
    public UnsupportedConstructException(String message) {
        super(message);
    }

    public UnsupportedConstructException(String message, Throwable cause) {
        super(message, cause);
    }
}
