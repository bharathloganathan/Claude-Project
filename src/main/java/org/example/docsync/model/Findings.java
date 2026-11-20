package org.example.docsync.model;

import java.util.List;

public final class Findings {
    private Findings() {
    }

    public record DocStatus(String path, boolean stale, List<String> staleBlockIds) {
        public DocStatus {
            staleBlockIds = List.copyOf(staleBlockIds);
        }
    }

    public enum SyncStatus {
        CREATED("created"), UPDATED("updated"), UNCHANGED("unchanged");

        private final String text;

        SyncStatus(String text) {
            this.text = text;
        }

        public String text() {
            return text;
        }
    }

    public record FileChange(String path, SyncStatus status) {
    }

    public record SyncResult(List<FileChange> files) {
        public SyncResult {
            files = List.copyOf(files);
        }
    }

    // A frontend and backend rule that differ: report-only, never STALE (FR-E3-07).
    public record DriftFinding(String uiField, String property, String frontend, String backend) {
    }

    public record VerifyResult(List<DocStatus> docs, List<DriftFinding> drift) {
        public VerifyResult {
            docs = List.copyOf(docs);
            drift = List.copyOf(drift);
        }

        public VerifyResult(List<DocStatus> docs) {
            this(docs, List.of());
        }

        public boolean stale() {
            return docs.stream().anyMatch(DocStatus::stale);
        }
    }
}
