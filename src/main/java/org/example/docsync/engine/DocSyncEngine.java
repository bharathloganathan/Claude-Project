package org.example.docsync.engine;

import org.example.docsync.analysis.JavaAnalyzer;
import org.example.docsync.io.AtomicWriter;
import org.example.docsync.io.DocWriter;
import org.example.docsync.io.ProjectFiles;
import org.example.docsync.io.ReportWriter;
import org.example.docsync.io.RootPaths;

import java.io.PrintStream;

public class DocSyncEngine {

    public int sync(RootPaths roots, PrintStream out, PrintStream err) {
        ProjectFiles files = new ProjectFiles(roots);
        JavaAnalyzer analyzer = new JavaAnalyzer(roots, files);
        AtomicWriter atomicWriter = new AtomicWriter(roots);
        return new SyncService(roots, files, analyzer, new DocWriter(atomicWriter), new ReportWriter(atomicWriter)).sync(out, err);
    }

    public int verify(RootPaths roots, PrintStream out, PrintStream err) {
        ProjectFiles files = new ProjectFiles(roots);
        JavaAnalyzer analyzer = new JavaAnalyzer(roots, files);
        return new VerifyService(roots, files, analyzer, new ReportWriter(new AtomicWriter(roots))).verify(out, err);
    }
}
