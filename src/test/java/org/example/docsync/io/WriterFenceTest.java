package org.example.docsync.io;

import org.example.docsync.error.DocSyncException;
import org.example.docsync.io.RootPaths.Name;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The write fences (task E2-4, DR-11): DocWriter accepts only the four catalog document names and
 * ReportWriter only the two report names. Anything else, including source names, is rejected before
 * anything is written. This source file is ASCII only.
 */
class WriterFenceTest {

    @TempDir
    Path dir;

    private RootPaths roots() {
        return RootPaths.of(".", dir);
    }

    private void assertRootIsEmpty() throws IOException {
        try (Stream<Path> list = Files.list(dir)) {
            assertEquals(0, list.count(), "nothing may be created");
        }
    }

    @ParameterizedTest
    @EnumSource(value = Name.class, names = {"DOC_ENDPOINTS", "DOC_CONTRACT", "DOC_SERVICE", "DOC_UI"}, mode = EnumSource.Mode.EXCLUDE)
    void docWriterRejectsEveryNonDocumentNameBeforeWriting(Name name) throws IOException {
        DocWriter writer = new DocWriter(new AtomicWriter(roots()));
        LinkedHashMap<Name, String> docs = new LinkedHashMap<>();
        docs.put(name, "x\n");

        assertThrows(DocSyncException.class, () -> writer.write(docs));

        assertRootIsEmpty();
    }

    @Test
    void docWriterWritesNothingWhenOneNameOfSeveralIsRejected() throws IOException {
        DocWriter writer = new DocWriter(new AtomicWriter(roots()));
        LinkedHashMap<Name, String> docs = new LinkedHashMap<>();
        docs.put(Name.DOC_ENDPOINTS, "x\n");
        docs.put(Name.FRONTEND_JS, "y\n");

        assertThrows(DocSyncException.class, () -> writer.write(docs));

        assertRootIsEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = Name.class, names = {"DOC_ENDPOINTS", "DOC_CONTRACT", "DOC_SERVICE", "DOC_UI"})
    void docWriterAcceptsTheFourDocumentNames(Name name) throws IOException {
        DocWriter writer = new DocWriter(new AtomicWriter(roots()));
        LinkedHashMap<Name, String> docs = new LinkedHashMap<>();
        docs.put(name, "text for " + name + "\n");

        writer.write(docs);

        assertEquals("text for " + name + "\n", Files.readString(roots().path(name), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @EnumSource(value = Name.class, names = {"REPORT_SYNC", "REPORT_VERIFY"}, mode = EnumSource.Mode.EXCLUDE)
    void reportWriterRejectsEveryNonReportNameBeforeWriting(Name name) throws IOException {
        ReportWriter writer = new ReportWriter(new AtomicWriter(roots()));

        assertThrows(DocSyncException.class, () -> writer.write(name, "x\n"));

        assertRootIsEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = Name.class, names = {"REPORT_SYNC", "REPORT_VERIFY"})
    void reportWriterAcceptsBothReportNamesAndWritesOnlyUnderDocsReports(Name name) throws IOException {
        ReportWriter writer = new ReportWriter(new AtomicWriter(roots()));

        writer.write(name, "report\n");

        Path reports = dir.resolve("docs").resolve("reports");
        assertEquals("report\n", Files.readString(roots().path(name), StandardCharsets.UTF_8));
        assertEquals(reports, roots().path(name).getParent());
        try (Stream<Path> walk = Files.walk(dir)) {
            assertFalse(walk.filter(Files::isRegularFile).anyMatch(p -> !p.startsWith(reports)),
                "only files under docs/reports may be written");
        }
    }
}
