package org.example.docsync.io;

import org.example.docsync.error.RootException;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Set;

public final class ReportWriter {
    private static final Set<RootPaths.Name> ALLOWED = EnumSet.of(RootPaths.Name.REPORT_SYNC, RootPaths.Name.REPORT_VERIFY);

    private final AtomicWriter writer;

    public ReportWriter(AtomicWriter writer) {
        this.writer = writer;
    }

    public void write(RootPaths.Name report, String text) {
        if (!ALLOWED.contains(report)) {
            throw new RootException("Only the sync and verify reports may be written as reports, not " + report + ".");
        }
        LinkedHashMap<RootPaths.Name, String> contents = new LinkedHashMap<>();
        contents.put(report, text);
        writer.writeAll(contents);
    }
}
