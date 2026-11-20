package org.example.docsync.engine;

import org.example.docsync.analysis.JavaAnalyzer;
import org.example.docsync.docs.DocCatalog;
import org.example.docsync.docs.MarkedDocument;
import org.example.docsync.error.MarkerIntegrityException;
import org.example.docsync.error.MissingExpectedException;
import org.example.docsync.io.ProjectFiles;
import org.example.docsync.io.ReportWriter;
import org.example.docsync.io.RootPaths;
import org.example.docsync.model.Findings;
import org.example.docsync.model.SourceModel;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class VerifyService {
    private final RootPaths roots;
    private final ProjectFiles files;
    private final JavaAnalyzer analyzer;
    private final ReportWriter reportWriter;

    public VerifyService(RootPaths roots, ProjectFiles files, JavaAnalyzer analyzer, ReportWriter reportWriter) {
        this.roots = roots;
        this.files = files;
        this.analyzer = analyzer;
        this.reportWriter = reportWriter;
    }

    public int verify(PrintStream out, PrintStream err) {
        try {
            return compareAndReport(out);
        } catch (Exception e) {
            writeFailedReport(e, err);
            throw e;
        }
    }

    private int compareAndReport(PrintStream out) {
        SourceModel model = analyzer.analyzeWithUi();
        var expected = DocCatalog.generate(model, roots.display(RootPaths.Name.SERVICE_SOURCE));

        LinkedHashMap<RootPaths.Name, MarkedDocument> actual = new LinkedHashMap<>();
        for (DocCatalog.Entry entry : DocCatalog.entries()) {
            actual.put(entry.name(), parse(entry));
        }
        for (DocCatalog.Entry entry : DocCatalog.entries()) {
            for (String id : entry.blockIds()) {
                if (!actual.get(entry.name()).hasBlock(id)) {
                    throw new MissingExpectedException("Block '" + id + "' is missing in "
                        + roots.display(entry.name()) + ". Run sync first; verify never creates or repairs blocks.");
                }
            }
        }

        List<Findings.DocStatus> statuses = new ArrayList<>();
        for (DocCatalog.Entry entry : DocCatalog.entries()) {
            List<String> staleIds = new ArrayList<>();
            for (String id : entry.blockIds()) {
                String wanted = expected.get(entry.name()).get(id);
                if (!wanted.equals(actual.get(entry.name()).body(id).orElseThrow())) {
                    staleIds.add(id);
                }
            }
            statuses.add(new Findings.DocStatus(roots.display(entry.name()), !staleIds.isEmpty(), staleIds));
        }
        Findings.VerifyResult result = new Findings.VerifyResult(statuses, DriftDetector.detect(model));

        reportWriter.write(RootPaths.Name.REPORT_VERIFY, ReportRenderer.verifyReport(result));
        if (!result.stale()) {
            out.println("verify: up to date");
        }
        for (Findings.DocStatus doc : statuses) {
            if (doc.stale()) {
                out.println("verify: STALE " + doc.path() + " (" + String.join(", ", doc.staleBlockIds()) + ")");
            }
        }
        // Drift is report-only: it is printed but never changes the exit code (FR-E3-07).
        for (Findings.DriftFinding finding : result.drift()) {
            out.println("verify: DRIFT " + finding.uiField() + " " + finding.property()
                + " (frontend " + finding.frontend() + ", backend " + finding.backend() + ")");
        }
        return result.stale() ? 1 : 0;
    }

    private MarkedDocument parse(DocCatalog.Entry entry) {
        String display = roots.display(entry.name());
        if (!files.exists(entry.name())) {
            throw new MissingExpectedException("Expected document is missing: " + display
                + ". Run sync first; verify never creates documents.");
        }
        try {
            return MarkedDocument.parse(files.readText(entry.name()), DocCatalog.blockIds(entry.name()));
        } catch (MarkerIntegrityException e) {
            throw new MarkerIntegrityException(display + ": " + e.getMessage(), e);
        }
    }

    private void writeFailedReport(Exception failure, PrintStream err) {
        try {
            reportWriter.write(RootPaths.Name.REPORT_VERIFY,
                ReportRenderer.failedReport("verify", ReportRenderer.failureMessage(failure), List.of()));
        } catch (Exception writeFailure) {
            err.println("verify: the failed-run report could not be written; the previous report (if any) may be stale.");
        }
    }
}
