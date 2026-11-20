package org.example.docsync.engine;

import org.example.docsync.docs.DocCatalog;
import org.example.docsync.error.DocSyncException;
import org.example.docsync.error.DocumentWriteException;
import org.example.docsync.model.Findings;

import java.util.List;

public final class ReportRenderer {
    private ReportRenderer() {
    }

    public static String failureMessage(Exception failure) {
        if (failure instanceof DocSyncException) {
            return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        }
        // The text of a foreign exception can carry absolute paths, so only its class name is recorded.
        return "unexpected failure (" + failure.getClass().getSimpleName() + "); details are not recorded.";
    }

    public static String syncReport(Findings.SyncResult result) {
        StringBuilder out = new StringBuilder("# DocSync Sync Report\n\nCommand: sync\nResult: SUCCESS\n\n| File | Status |\n|---|---|\n");
        for (Findings.FileChange file : result.files()) {
            out.append("| ").append(file.path()).append(" | ").append(file.status().text()).append(" |\n");
        }
        return out.toString();
    }

    public static String verifyReport(Findings.VerifyResult result) {
        StringBuilder out = new StringBuilder("# DocSync Verify Report\n\nCommand: verify\n");
        out.append(result.stale() ? "Result: STALE (exit 1)\n" : "Result: UP_TO_DATE\n");
        out.append("\n| File | Status |\n|---|---|\n");
        for (Findings.DocStatus doc : result.docs()) {
            String status = doc.stale() ? "STALE (" + String.join(", ", doc.staleBlockIds()) + ")" : "up to date";
            out.append("| ").append(doc.path()).append(" | ").append(status).append(" |\n");
        }
        out.append("\n## Drift (report only)\n\n");
        if (result.drift().isEmpty()) {
            out.append("No drift found.\n");
        } else {
            out.append("| UI field | Property | Frontend | Backend |\n|---|---|---|---|\n");
            for (Findings.DriftFinding finding : result.drift()) {
                out.append("| ").append(DocCatalog.escape(finding.uiField())).append(" | ").append(DocCatalog.escape(finding.property()))
                    .append(" | ").append(DocCatalog.escape(finding.frontend())).append(" | ")
                    .append(DocCatalog.escape(finding.backend())).append(" |\n");
            }
        }
        return out.toString();
    }

    public static String failedReport(String command, String message, List<DocumentWriteException.FileOutcome> outcomes) {
        String title = Character.toUpperCase(command.charAt(0)) + command.substring(1);
        StringBuilder out = new StringBuilder("# DocSync ").append(title).append(" Report\n\n");
        out.append("Command: ").append(command).append('\n');
        out.append("Result: FAILED (exit 2)\n");
        out.append("Message: ").append(message.replaceAll("\\R+", " ")).append('\n');
        if (!outcomes.isEmpty()) {
            out.append("\nReplacement is not transactional: files listed as REPLACED remain updated.\n");
            out.append("\n| File | Outcome |\n|---|---|\n");
            for (DocumentWriteException.FileOutcome outcome : outcomes) {
                String text = outcome.status() == DocumentWriteException.Status.FAILED
                    ? "FAILED (" + outcome.cause() + ")" : outcome.status().name();
                out.append("| ").append(outcome.path()).append(" | ").append(text).append(" |\n");
            }
        }
        return out.toString();
    }
}
