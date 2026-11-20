package org.example.docsync.error;

import java.util.List;

public class DocumentWriteException extends DocSyncException {
    public enum Status { REPLACED, FAILED, NOT_ATTEMPTED }

    // cause is null unless the status is FAILED, and then only the simple class name of the exception.
    public record FileOutcome(String path, Status status, String cause) {
    }

    private final List<FileOutcome> outcomes;

    public DocumentWriteException(String message, List<FileOutcome> outcomes) {
        super(message);
        this.outcomes = List.copyOf(outcomes);
    }

    public DocumentWriteException(String message, List<FileOutcome> outcomes, Throwable cause) {
        super(message, cause);
        this.outcomes = List.copyOf(outcomes);
    }

    public List<FileOutcome> outcomes() {
        return outcomes;
    }
}
