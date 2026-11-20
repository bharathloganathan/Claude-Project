package org.example.docsync.engine;

import org.example.docsync.analysis.JavaAnalyzer;
import org.example.docsync.docs.DocCatalog;
import org.example.docsync.docs.MarkedDocument;
import org.example.docsync.error.DocumentWriteException;
import org.example.docsync.error.MarkerIntegrityException;
import org.example.docsync.io.DocWriter;
import org.example.docsync.io.ProjectFiles;
import org.example.docsync.io.ReportWriter;
import org.example.docsync.io.RootPaths;
import org.example.docsync.model.Findings;
import org.example.docsync.model.SourceModel;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class SyncService {
    private static final Pattern LONE_CR = Pattern.compile("\r(?!\n)");

    private final RootPaths roots;
    private final ProjectFiles files;
    private final JavaAnalyzer analyzer;
    private final DocWriter docWriter;
    private final ReportWriter reportWriter;

    public SyncService(RootPaths roots, ProjectFiles files, JavaAnalyzer analyzer, DocWriter docWriter, ReportWriter reportWriter) {
        this.roots = roots;
        this.files = files;
        this.analyzer = analyzer;
        this.docWriter = docWriter;
        this.reportWriter = reportWriter;
    }

    public int sync(PrintStream out, PrintStream err) {
        try {
            return run(out);
        } catch (Exception e) {
            writeFailedReport(e, err);
            throw e;
        }
    }

    private int run(PrintStream out) {
        SourceModel model = analyzer.analyzeWithUi();
        var generated = DocCatalog.generate(model, roots.display(RootPaths.Name.SERVICE_SOURCE));

        Map<RootPaths.Name, String> existing = new HashMap<>();
        for (DocCatalog.Entry entry : DocCatalog.entries()) {
            if (files.exists(entry.name())) {
                existing.put(entry.name(), loadValidated(entry));
            }
        }

        LinkedHashMap<RootPaths.Name, String> changed = new LinkedHashMap<>();
        List<Findings.FileChange> changes = new ArrayList<>();
        for (DocCatalog.Entry entry : DocCatalog.entries()) {
            String before = existing.get(entry.name());
            LinkedHashMap<String, String> blocks = generated.get(entry.name());
            String after = before == null ? MarkedDocument.create(entry.title(), blocks) : replaceBlocks(entry, before, blocks);
            Findings.SyncStatus status = before == null ? Findings.SyncStatus.CREATED
                : after.equals(before) ? Findings.SyncStatus.UNCHANGED : Findings.SyncStatus.UPDATED;
            if (status != Findings.SyncStatus.UNCHANGED) {
                changed.put(entry.name(), after);
            }
            changes.add(new Findings.FileChange(roots.display(entry.name()), status));
        }

        docWriter.write(changed);
        writeSyncReport(changes);

        for (Findings.FileChange change : changes) {
            out.println("sync: " + change.status().text() + " " + change.path());
        }
        return 0;
    }

    private String loadValidated(DocCatalog.Entry entry) {
        String raw = files.readRawText(entry.name());
        try {
            MarkedDocument.parse(withLoneCrAsLf(raw), DocCatalog.blockIds(entry.name()));
        } catch (MarkerIntegrityException e) {
            throw new MarkerIntegrityException(roots.display(entry.name()) + ": " + e.getMessage(), e);
        }
        return raw;
    }

    // A lone CR is a line break, as it is for verify; the replacement is 1:1, so offsets stay the same.
    private static String withLoneCrAsLf(String text) {
        return LONE_CR.matcher(text).replaceAll("\n");
    }

    private static String replaceBlocks(DocCatalog.Entry entry, String raw, LinkedHashMap<String, String> blocks) {
        String text = raw;
        for (String id : entry.blockIds()) {
            String normalized = withLoneCrAsLf(text);
            MarkedDocument current = MarkedDocument.parse(normalized, DocCatalog.blockIds(entry.name()));
            // Verify compares bodies after line-ending normalization, so a body it judges current is left untouched.
            if (blocks.get(id).equals(current.body(id).orElse(null))) {
                continue;
            }
            String replaced = current.withBlock(id, blocks.get(id));
            text = normalized.equals(text) ? replaced : restoreLoneCr(text, normalized, replaced);
        }
        return text;
    }

    // Only the span between the common prefix and suffix changed, so the text around it keeps its original lone CRs.
    private static String restoreLoneCr(String raw, String normalized, String replaced) {
        int length = normalized.length();
        int limit = Math.min(length, replaced.length());
        int prefix = 0;
        while (prefix < limit && normalized.charAt(prefix) == replaced.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < limit - prefix
            && normalized.charAt(length - 1 - suffix) == replaced.charAt(replaced.length() - 1 - suffix)) {
            suffix++;
        }
        // A kept lone CR directly before a generated LF would fuse into a CRLF, so that one break comes from the generated text.
        if (prefix > 0 && prefix < replaced.length() && raw.charAt(prefix - 1) == '\r' && replaced.charAt(prefix) == '\n') {
            prefix--;
        }
        String result = raw.substring(0, prefix) + replaced.substring(prefix, replaced.length() - suffix) + raw.substring(length - suffix);
        return withLoneCrAsLf(result).equals(replaced) ? result : replaced;
    }

    private void writeSyncReport(List<Findings.FileChange> changes) {
        try {
            reportWriter.write(RootPaths.Name.REPORT_SYNC, ReportRenderer.syncReport(new Findings.SyncResult(changes)));
        } catch (DocumentWriteException e) {
            List<String> written = changes.stream()
                .filter(change -> change.status() != Findings.SyncStatus.UNCHANGED)
                .map(change -> change.path() + " " + change.status().text())
                .toList();
            String documents = written.isEmpty() ? "No document needed changes"
                : "Documents were already written (" + String.join(", ", written) + ")";
            throw new DocumentWriteException(documents + ", but " + roots.display(RootPaths.Name.REPORT_SYNC)
                + " could not be written. The previous report (if any) may be stale.", List.of(), e);
        }
    }

    private void writeFailedReport(Exception failure, PrintStream err) {
        String message = ReportRenderer.failureMessage(failure);
        var outcomes = failure instanceof DocumentWriteException write ? write.outcomes() : List.<DocumentWriteException.FileOutcome>of();
        try {
            reportWriter.write(RootPaths.Name.REPORT_SYNC, ReportRenderer.failedReport("sync", message, outcomes));
        } catch (Exception writeFailure) {
            err.println("sync: the failed-run report could not be written; the previous report (if any) may be stale.");
        }
    }
}
