package org.example.docsync.engine;

import org.example.docsync.analysis.JavaAnalyzer;
import org.example.docsync.docs.DocCatalog;
import org.example.docsync.docs.MarkedDocument;
import org.example.docsync.error.DocumentWriteException;
import org.example.docsync.error.DocumentWriteException.FileOutcome;
import org.example.docsync.error.DocumentWriteException.Status;
import org.example.docsync.error.MarkerIntegrityException;
import org.example.docsync.error.MissingExpectedException;
import org.example.docsync.error.UnsupportedConstructException;
import org.example.docsync.io.AtomicWriter;
import org.example.docsync.io.DocWriter;
import org.example.docsync.io.ProjectFiles;
import org.example.docsync.io.ReportWriter;
import org.example.docsync.io.RootPaths;
import org.example.docsync.io.RootPaths.Name;
import org.example.docsync.model.Findings;
import org.example.docsync.model.SourceModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Read-only verification (task E2-8, FR-E2-14 to FR-E2-18, AC-E2-05, AC-E2-06 verify part, AC-E2-08,
 * AC-E2-09, AC-E2-10, AC-E2-11, DR-10). Fixtures are temp roots holding copies of the real sources and
 * in-sync documents generated with the catalog, so verify does not depend on sync. This source file is
 * ASCII only.
 */
class VerifyServiceTest {

    private static final List<Name> SOURCES = List.of(Name.API_ROUTES_SOURCE, Name.ERROR_CODE_SOURCE,
        Name.STUDENT_RECORD_SOURCE, Name.SERVICE_SOURCE, Name.FRONTEND_HTML, Name.FRONTEND_JS);
    private static final List<Name> DOCS = List.of(Name.DOC_ENDPOINTS, Name.DOC_CONTRACT, Name.DOC_SERVICE, Name.DOC_UI);
    private static final String ENDPOINTS = "docs/api/endpoints.md";
    private static final String CONTRACT = "docs/api/student-contract.md";
    private static final String SERVICE = "docs/backend/student-service.md";
    private static final String UI = "docs/ui/student-ui.md";
    private static final String DRIFT_SECTION = "\n## Drift (report only)\n\n| UI field | Property | Frontend | Backend |\n|---|---|---|---|\n"
        + "| studentName | minLength | 2 | none |\n"
        + "| studentEmail | pattern | ^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$ | contains(\"@\") |\n";
    private static final List<Findings.DriftFinding> DRIFT = List.of(
        new Findings.DriftFinding("studentName", "minLength", "2", "none"),
        new Findings.DriftFinding("studentEmail", "pattern", "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", "contains(\"@\")"));
    private static final String SENTENCE = "Replacement is not transactional: files listed as REPLACED remain updated.";
    private static final AtomicWriter.Mover FAILING_MOVER = (source, target) -> {
        throw new IOException("simulated move failure");
    };

    @TempDir
    Path dir;

    private RootPaths roots;
    private ProjectFiles files;
    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
    private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

    @BeforeEach
    void buildFixture() throws IOException {
        roots = RootPaths.of(dir.toString());
        files = new ProjectFiles(roots);
        for (Name source : SOURCES) {
            Path target = roots.path(source);
            Files.createDirectories(target.getParent());
            Files.copy(Path.of(roots.display(source)), target);
        }
        SourceModel model = new JavaAnalyzer(roots, files).analyzeWithUi();
        var generated = DocCatalog.generate(model, roots.display(Name.SERVICE_SOURCE));
        for (DocCatalog.Entry entry : DocCatalog.entries()) {
            Path target = roots.path(entry.name());
            Files.createDirectories(target.getParent());
            Files.writeString(target, MarkedDocument.create(entry.title(), generated.get(entry.name())));
        }
    }

    // ---- helpers ----

    private VerifyService service() {
        return service(new AtomicWriter(roots));
    }

    private VerifyService service(AtomicWriter.Mover mover) {
        return service(new AtomicWriter(roots, mover));
    }

    private VerifyService service(AtomicWriter writer) {
        return new VerifyService(roots, files, new JavaAnalyzer(roots, files), new ReportWriter(writer));
    }

    private Map<Name, String> snapshot() throws IOException {
        Map<Name, String> state = new HashMap<>();
        for (Name doc : DOCS) {
            Path path = roots.path(doc);
            state.put(doc, Files.exists(path) ? new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1) : null);
        }
        return state;
    }

    private int verifyExit() throws IOException {
        return verifyExit(service());
    }

    private int verifyExit(VerifyService service) throws IOException {
        Map<Name, String> before = snapshot();
        int exit = service.verify(out, err);
        assertEquals(before, snapshot(), "verify must never modify a document");
        return exit;
    }

    private <T extends Throwable> T verifyFails(Class<T> type) throws IOException {
        return verifyFails(service(), type);
    }

    private <T extends Throwable> T verifyFails(VerifyService service, Class<T> type) throws IOException {
        Map<Name, String> before = snapshot();
        T thrown = assertThrows(type, () -> service.verify(out, err));
        assertEquals(before, snapshot(), "verify must never modify a document");
        return thrown;
    }

    private void edit(Name name, String from, String to) throws IOException {
        Path path = roots.path(name);
        String text = Files.readString(path);
        assertTrue(text.contains(from), "fixture must contain: " + from);
        Files.writeString(path, text.replace(from, to));
    }

    private String report() throws IOException {
        return Files.readString(roots.path(Name.REPORT_VERIFY));
    }

    // The UI document is up to date in these scenarios, and the real sources always carry the two known drift findings.
    private static String verifyText(String result, String endpoints, String contract, String service) {
        return verifyText(result, endpoints, contract, service, "up to date");
    }

    private static String verifyText(String result, String endpoints, String contract, String service, String ui) {
        return "# DocSync Verify Report\n\nCommand: verify\nResult: " + result + "\n\n| File | Status |\n|---|---|\n"
            + "| " + ENDPOINTS + " | " + endpoints + " |\n"
            + "| " + CONTRACT + " | " + contract + " |\n"
            + "| " + SERVICE + " | " + service + " |\n"
            + "| " + UI + " | " + ui + " |\n"
            + DRIFT_SECTION;
    }

    private void assertNoTempFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> leftovers = walk.filter(p -> p.getFileName().toString().startsWith(".docsync-")).toList();
            assertEquals(List.of(), leftovers);
        }
    }

    private void assertNoAbsolutePath(String text) {
        assertFalse(text.contains(dir.toString()), "must not contain the absolute root");
        assertFalse(text.contains(dir.getFileName().toString()), "must not contain the absolute root");
    }

    // ---- up to date ----

    @Test
    void inSyncFixtureReturnsZeroAndWritesUpToDateReport() throws IOException {
        assertEquals(0, verifyExit());

        assertEquals(verifyText("UP_TO_DATE", "up to date", "up to date", "up to date"), report());
        assertTrue(outBytes.toString(StandardCharsets.UTF_8).contains("verify: up to date"));
        assertEquals("", errBytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void secondVerifyGivesByteIdenticalReport() throws IOException {
        assertEquals(0, verifyExit());
        byte[] first = Files.readAllBytes(roots.path(Name.REPORT_VERIFY));

        assertEquals(0, verifyExit());

        assertArrayEquals(first, Files.readAllBytes(roots.path(Name.REPORT_VERIFY)));
    }

    @Test
    void documentsAreByteIdenticalForExit0Exit1AndExit2() throws IOException {
        Map<Name, String> original = snapshot();
        assertEquals(0, service().verify(out, err));
        assertEquals(original, snapshot());

        edit(Name.API_ROUTES_SOURCE, "\"List all students\"", "\"List every student\"");
        assertEquals(1, service().verify(out, err));
        assertEquals(original, snapshot());

        edit(Name.API_ROUTES_SOURCE, "\"Create a student\"", "SUMMARY");
        assertThrows(UnsupportedConstructException.class, () -> service().verify(out, err));
        assertEquals(original, snapshot());
    }

    // ---- stale ----

    @Test
    void routeSummaryChangeMakesOnlyTheEndpointsDocStale() throws IOException {
        edit(Name.API_ROUTES_SOURCE, "\"List all students\"", "\"List every student\"");

        assertEquals(1, verifyExit());

        assertEquals(verifyText("STALE (exit 1)", "STALE (api-routes)", "up to date", "up to date"), report());
        assertTrue(outBytes.toString(StandardCharsets.UTF_8).contains("verify: STALE " + ENDPOINTS + " (api-routes)"));
    }

    @Test
    void studentRecordComponentChangeMakesTheContractDocStale() throws IOException {
        edit(Name.STUDENT_RECORD_SOURCE, "String phone", "String mobile");

        assertEquals(1, verifyExit());

        assertEquals(verifyText("STALE (exit 1)", "up to date", "STALE (contract-student-fields)", "up to date"), report());
    }

    @Test
    void logicOnlyServiceChangeMakesTheDigestStale() throws IOException {
        edit(Name.SERVICE_SOURCE, "\"active\"", "\"enabled\"");

        assertEquals(1, verifyExit());

        assertEquals(verifyText("STALE (exit 1)", "up to date", "up to date", "STALE (service-digest)"), report());
    }

    @Test
    void handEditInsideABlockIsStale() throws IOException {
        edit(Name.DOC_ENDPOINTS, "List all students", "Edited by hand");

        assertEquals(1, verifyExit());

        assertEquals(verifyText("STALE (exit 1)", "STALE (api-routes)", "up to date", "up to date"), report());
    }

    @Test
    void staleBlocksOfOneDocumentAreListedInCatalogOrder() throws IOException {
        edit(Name.DOC_ENDPOINTS, "| Code | HTTP status |", "| Code | Status |");
        edit(Name.DOC_ENDPOINTS, "List all students", "Edited by hand");

        assertEquals(1, verifyExit());

        assertEquals(verifyText("STALE (exit 1)", "STALE (api-routes, api-errors)", "up to date", "up to date"), report());
        assertTrue(outBytes.toString(StandardCharsets.UTF_8).contains("verify: STALE " + ENDPOINTS + " (api-routes, api-errors)"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"before", "between", "after"})
    void textOutsideBlocksNeverMatters(String where) throws IOException {
        switch (where) {
            case "before" -> edit(Name.DOC_ENDPOINTS, "# API Endpoints\n", "# API Endpoints\n\nHand written intro.\n");
            case "between" -> edit(Name.DOC_ENDPOINTS, "<!-- docsync:end api-routes -->\n",
                "<!-- docsync:end api-routes -->\n\nHand written middle.\n");
            default -> edit(Name.DOC_ENDPOINTS, "<!-- docsync:end api-errors -->\n",
                "<!-- docsync:end api-errors -->\n\nHand written outro.\n");
        }

        assertEquals(0, verifyExit());

        assertEquals(verifyText("UP_TO_DATE", "up to date", "up to date", "up to date"), report());
    }

    @Test
    void crlfDocumentsAreNotStale() throws IOException {
        for (Name doc : DOCS) {
            edit(doc, "\n", "\r\n");
        }

        assertEquals(0, verifyExit());
    }

    // ---- missing expected content ----

    @Test
    void missingDocumentIsMissingExpectedNotStaleAndIsNotCreated() throws IOException {
        Files.delete(roots.path(Name.DOC_CONTRACT));

        MissingExpectedException thrown = verifyFails(MissingExpectedException.class);

        assertTrue(thrown.getMessage().contains(CONTRACT), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("sync"), thrown.getMessage());
        assertNoAbsolutePath(thrown.getMessage());
        assertFalse(Files.exists(roots.path(Name.DOC_CONTRACT)));
        assertFalse(report().contains("STALE"));
        assertTrue(report().contains("Result: FAILED (exit 2)"));
    }

    @Test
    void existingDocumentMissingOneBlockIsMissingExpected() throws IOException {
        Path path = roots.path(Name.DOC_ENDPOINTS);
        String text = Files.readString(path);
        String without = Pattern.compile("<!-- docsync:begin api-errors -->.*?<!-- docsync:end api-errors -->\n",
            Pattern.DOTALL).matcher(text).replaceFirst("");
        assertNotEquals(text, without);
        Files.writeString(path, without);

        MissingExpectedException thrown = verifyFails(MissingExpectedException.class);

        assertTrue(thrown.getMessage().contains(ENDPOINTS), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("api-errors"), thrown.getMessage());
        assertNoAbsolutePath(thrown.getMessage());
        assertEquals(without, Files.readString(path));
    }

    // ---- marker faults and unsupported constructs ----

    @ParameterizedTest
    @ValueSource(strings = {"missing-end", "unknown-id", "duplicate-id", "malformed"})
    void markerFaultsAreExit2AndNeverStale(String fault) throws IOException {
        switch (fault) {
            case "missing-end" -> edit(Name.DOC_ENDPOINTS, "<!-- docsync:end api-errors -->", "");
            case "unknown-id" -> edit(Name.DOC_ENDPOINTS, "docsync:begin api-errors", "docsync:begin api-bogus");
            case "duplicate-id" -> edit(Name.DOC_ENDPOINTS, "<!-- docsync:begin api-errors -->", "<!-- docsync:begin api-routes -->");
            default -> edit(Name.DOC_ENDPOINTS, "<!-- docsync:begin api-routes -->", "<!--docsync:begin api-routes -->");
        }

        MarkerIntegrityException thrown = verifyFails(MarkerIntegrityException.class);

        assertTrue(thrown.getMessage().contains(ENDPOINTS), thrown.getMessage());
        assertNoAbsolutePath(thrown.getMessage());
        assertFalse(report().contains("STALE"));
        assertTrue(report().contains("Result: FAILED (exit 2)"));
    }

    @Test
    void markerFaultInALaterDocumentPreemptsStalenessOfAnEarlierOne() throws IOException {
        edit(Name.API_ROUTES_SOURCE, "\"List all students\"", "\"List every student\"");
        edit(Name.DOC_SERVICE, "<!-- docsync:end service-digest -->", "");

        MarkerIntegrityException thrown = verifyFails(MarkerIntegrityException.class);

        assertTrue(thrown.getMessage().contains(SERVICE), thrown.getMessage());
        assertFalse(report().contains("STALE"));
    }

    @Test
    void unsupportedConstructInASourceIsExit2() throws IOException {
        edit(Name.API_ROUTES_SOURCE, "\"Create a student\"", "SUMMARY");

        UnsupportedConstructException thrown = verifyFails(UnsupportedConstructException.class);

        assertTrue(thrown.getMessage().contains("src/main/java/org/example/api/StudentApiServer.java"), thrown.getMessage());
        assertNoAbsolutePath(thrown.getMessage());
        assertTrue(report().contains("Result: FAILED (exit 2)"));
    }

    // ---- FAILED report (DR-10) ----

    @Test
    void exit2ReplacesAnEarlierUpToDateReportWithAFailedReport() throws IOException {
        assertEquals(0, verifyExit());
        assertTrue(report().contains("Result: UP_TO_DATE"));
        edit(Name.DOC_ENDPOINTS, "<!-- docsync:end api-errors -->", "");

        MarkerIntegrityException thrown = verifyFails(MarkerIntegrityException.class);

        assertEquals("# DocSync Verify Report\n\nCommand: verify\nResult: FAILED (exit 2)\nMessage: "
            + thrown.getMessage() + "\n", report());
        assertNoAbsolutePath(report());
        assertNoTempFiles();
    }

    @Test
    void failedReportWriteFailurePreservesThePreviousReportAndRethrowsTheOriginal() throws IOException {
        assertEquals(0, verifyExit());
        byte[] previous = Files.readAllBytes(roots.path(Name.REPORT_VERIFY));
        edit(Name.DOC_ENDPOINTS, "<!-- docsync:end api-errors -->", "");

        MarkerIntegrityException thrown = verifyFails(service(FAILING_MOVER), MarkerIntegrityException.class);

        assertTrue(thrown.getMessage().contains(ENDPOINTS), thrown.getMessage());
        assertArrayEquals(previous, Files.readAllBytes(roots.path(Name.REPORT_VERIFY)));
        String errText = errBytes.toString(StandardCharsets.UTF_8);
        assertTrue(errText.contains("could not be written"), errText);
        assertTrue(errText.contains("may be stale"), errText);
        assertNoAbsolutePath(errText);
        assertNoTempFiles();
    }

    @Test
    void successfulComparisonWhoseReportCannotBeWrittenIsExit2() throws IOException {
        DocumentWriteException thrown = verifyFails(service(FAILING_MOVER), DocumentWriteException.class);

        assertFalse(Files.exists(roots.path(Name.REPORT_VERIFY)));
        assertTrue(errBytes.toString(StandardCharsets.UTF_8).contains("could not be written"));
        assertNoAbsolutePath(thrown.getMessage());
        assertNoTempFiles();
    }

    // ---- safety properties ----

    @Test
    void verifyServiceHasNoDocumentWriterAndNoAtomicWriter() {
        Constructor<?>[] constructors = VerifyService.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertEquals(List.of(RootPaths.class, ProjectFiles.class, JavaAnalyzer.class, ReportWriter.class),
            Arrays.asList(constructors[0].getParameterTypes()));
        for (Field field : VerifyService.class.getDeclaredFields()) {
            assertNotEquals(DocWriter.class, field.getType());
            assertNotEquals(AtomicWriter.class, field.getType());
        }
    }

    @Test
    void verifyNeverLeavesTempFiles() throws IOException {
        assertEquals(0, verifyExit());
        edit(Name.SERVICE_SOURCE, "\"active\"", "\"enabled\"");
        assertEquals(1, verifyExit());
        assertNoTempFiles();
    }

    // ---- exact report text ----

    @Test
    void verifyReportTextForUpToDate() {
        Findings.VerifyResult result = new Findings.VerifyResult(List.of(
            new Findings.DocStatus(ENDPOINTS, false, List.of()),
            new Findings.DocStatus(CONTRACT, false, List.of()),
            new Findings.DocStatus(SERVICE, false, List.of()),
            new Findings.DocStatus(UI, false, List.of())), DRIFT);

        assertFalse(result.stale());
        assertEquals(verifyText("UP_TO_DATE", "up to date", "up to date", "up to date"), ReportRenderer.verifyReport(result));
    }

    @Test
    void verifyReportTextForStaleWithTwoBlockIds() {
        Findings.VerifyResult result = new Findings.VerifyResult(List.of(
            new Findings.DocStatus(ENDPOINTS, true, List.of("api-routes", "api-errors")),
            new Findings.DocStatus(CONTRACT, false, List.of()),
            new Findings.DocStatus(SERVICE, false, List.of()),
            new Findings.DocStatus(UI, false, List.of())), DRIFT);

        assertTrue(result.stale());
        assertEquals(verifyText("STALE (exit 1)", "STALE (api-routes, api-errors)", "up to date", "up to date"),
            ReportRenderer.verifyReport(result));
    }

    @Test
    void verifyReportTextWithoutDriftSaysSo() {
        Findings.VerifyResult result = new Findings.VerifyResult(List.of(new Findings.DocStatus(UI, false, List.of())));

        assertEquals("# DocSync Verify Report\n\nCommand: verify\nResult: UP_TO_DATE\n\n| File | Status |\n|---|---|\n"
            + "| " + UI + " | up to date |\n\n## Drift (report only)\n\nNo drift found.\n", ReportRenderer.verifyReport(result));
    }

    @Test
    void failedReportTextWithoutOutcomes() {
        assertEquals("# DocSync Verify Report\n\nCommand: verify\nResult: FAILED (exit 2)\nMessage: first line second line\n",
            ReportRenderer.failedReport("verify", "first line\nsecond line", List.of()));
    }

    @Test
    void failedReportTextWithOutcomes() {
        List<FileOutcome> outcomes = List.of(
            new FileOutcome(ENDPOINTS, Status.REPLACED, null),
            new FileOutcome(CONTRACT, Status.FAILED, "IOException"),
            new FileOutcome(SERVICE, Status.NOT_ATTEMPTED, null));

        assertEquals("# DocSync Sync Report\n\nCommand: sync\nResult: FAILED (exit 2)\nMessage: Replacing failed\n\n"
            + SENTENCE + "\n\n| File | Outcome |\n|---|---|\n"
            + "| " + ENDPOINTS + " | REPLACED |\n"
            + "| " + CONTRACT + " | FAILED (IOException) |\n"
            + "| " + SERVICE + " | NOT_ATTEMPTED |\n",
            ReportRenderer.failedReport("sync", "Replacing failed", outcomes));
    }

    // ---- RV3-11: every document, every block, every marker fault ----

    static Stream<Name> documents() {
        return DocCatalog.entries().stream().map(DocCatalog.Entry::name);
    }

    static Stream<Arguments> blocks() {
        return DocCatalog.entries().stream()
            .flatMap(entry -> entry.blockIds().stream().map(id -> Arguments.of(entry.name(), id)));
    }

    static Stream<Arguments> markerFaults() {
        return DocCatalog.entries().stream().flatMap(entry -> Stream.of("missing-end", "unknown-id", "duplicate-id", "malformed")
            .map(fault -> Arguments.of(entry.name(), fault)));
    }

    @ParameterizedTest
    @MethodSource("documents")
    void aMissingDocumentIsMissingExpectedForEveryDocument(Name doc) throws IOException {
        Files.delete(roots.path(doc));

        MissingExpectedException thrown = verifyFails(MissingExpectedException.class);

        assertTrue(thrown.getMessage().contains(roots.display(doc)), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("sync"), thrown.getMessage());
        assertNoAbsolutePath(thrown.getMessage());
        assertFalse(Files.exists(roots.path(doc)));
        assertFalse(report().contains("STALE"));
        assertTrue(report().contains("Result: FAILED (exit 2)"));
    }

    @ParameterizedTest
    @MethodSource("blocks")
    void aMissingBlockIsMissingExpectedForEveryBlockOfEveryDocument(Name doc, String id) throws IOException {
        Path path = roots.path(doc);
        String text = Files.readString(path);
        String without = Pattern.compile("<!-- docsync:begin " + id + " -->.*?<!-- docsync:end " + id + " -->\n", Pattern.DOTALL)
            .matcher(text).replaceFirst("");
        assertNotEquals(text, without);
        Files.writeString(path, without);

        MissingExpectedException thrown = verifyFails(MissingExpectedException.class);

        assertTrue(thrown.getMessage().contains(roots.display(doc)), thrown.getMessage());
        assertTrue(thrown.getMessage().contains(id), thrown.getMessage());
        assertNoAbsolutePath(thrown.getMessage());
        assertEquals(without, Files.readString(path));
        assertFalse(report().contains("STALE"));
    }

    @ParameterizedTest
    @MethodSource("markerFaults")
    void markerFaultsInEveryDocumentAreExit2AndNeverStale(Name doc, String fault) throws IOException {
        List<String> ids = DocCatalog.entries().stream().filter(entry -> entry.name() == doc).findFirst().orElseThrow().blockIds();
        String first = ids.get(0);
        String last = ids.get(ids.size() - 1);
        switch (fault) {
            case "missing-end" -> edit(doc, "<!-- docsync:end " + last + " -->", "");
            case "unknown-id" -> edit(doc, "docsync:begin " + last, "docsync:begin bogus-id");
            case "duplicate-id" -> edit(doc, "<!-- docsync:begin " + last + " -->", "<!-- docsync:begin " + first + " -->");
            default -> edit(doc, "<!-- docsync:begin " + first + " -->", "<!--docsync:begin " + first + " -->");
        }

        MarkerIntegrityException thrown = verifyFails(MarkerIntegrityException.class);

        assertTrue(thrown.getMessage().contains(roots.display(doc)), thrown.getMessage());
        assertNoAbsolutePath(thrown.getMessage());
        assertFalse(report().contains("STALE"));
        assertTrue(report().contains("Result: FAILED (exit 2)"));
    }

    // ---- RV3-02 and RV3-11: foreign exceptions are rethrown and recorded safely ----

    private static PrintStream throwingOut(RuntimeException failure) {
        return new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8) {
            @Override
            public void println(String text) {
                throw failure;
            }
        };
    }

    private static AtomicWriter.Mover succeedTimes(int times) {
        int[] calls = {0};
        return (source, target) -> {
            if (++calls[0] > times) {
                throw new IOException("simulated move failure");
            }
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        };
    }

    @Test
    void aForeignRuntimeExceptionIsRethrownUnchangedAndRecordedWithASafeMessage() throws IOException {
        assertEquals(0, verifyExit());
        assertTrue(report().contains("Result: UP_TO_DATE"));
        Map<Name, String> before = snapshot();
        IllegalStateException failure = new IllegalStateException("cannot open " + dir + "\\secret.txt");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> service().verify(throwingOut(failure), err));

        assertSame(failure, thrown);
        assertEquals("# DocSync Verify Report\n\nCommand: verify\nResult: FAILED (exit 2)\n"
            + "Message: unexpected failure (IllegalStateException); details are not recorded.\n", report());
        assertNoAbsolutePath(report());
        assertEquals(before, snapshot());
        assertEquals("", errBytes.toString(StandardCharsets.UTF_8));
        assertNoTempFiles();
    }

    @Test
    void aForeignRuntimeExceptionWhoseFailedReportCannotBeWrittenKeepsThePreviousReport() throws IOException {
        IllegalStateException failure = new IllegalStateException("cannot open " + dir + "\\secret.txt");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
            () -> service(succeedTimes(1)).verify(throwingOut(failure), err));

        assertSame(failure, thrown);
        assertEquals(verifyText("UP_TO_DATE", "up to date", "up to date", "up to date"), report());
        String errText = errBytes.toString(StandardCharsets.UTF_8);
        assertTrue(errText.contains("could not be written"), errText);
        assertTrue(errText.contains("may be stale"), errText);
        assertNoAbsolutePath(errText);
        assertNoTempFiles();
    }

    // ---- E3: the UI document and drift (FR-E3-01, FR-E3-07, AC-E3-04, AC-E3-06, AC-E3-08, AC-E3-09, DR-15) ----

    @Test
    void driftOnlyExitsZeroAndIsPrintedAndReportedSeparatelyFromStale() throws IOException {
        assertEquals(0, verifyExit());

        String stdout = outBytes.toString(StandardCharsets.UTF_8);
        assertTrue(stdout.contains("verify: up to date"), stdout);
        assertTrue(stdout.contains("verify: DRIFT studentName minLength (frontend 2, backend none)"), stdout);
        assertTrue(stdout.contains("verify: DRIFT studentEmail pattern (frontend ^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$, backend contains(\"@\"))"), stdout);
        assertFalse(stdout.contains("STALE"), stdout);
        assertEquals(verifyText("UP_TO_DATE", "up to date", "up to date", "up to date"), report());
        assertFalse(report().contains("STALE"));
    }

    @Test
    void staleTogetherWithDriftExitsOneAndDriftIsNeverLabeledStale() throws IOException {
        edit(Name.DOC_UI, "Submit button: Add student", "Submit button: Edited");

        assertEquals(1, verifyExit());

        assertEquals(verifyText("STALE (exit 1)", "up to date", "up to date", "up to date", "STALE (ui-controls)"), report());
        String stdout = outBytes.toString(StandardCharsets.UTF_8);
        assertTrue(stdout.contains("verify: STALE " + UI + " (ui-controls)"), stdout);
        assertTrue(stdout.contains("verify: DRIFT studentName minLength"), stdout);
        assertFalse(stdout.contains("STALE studentName"), stdout);
    }

    @Test
    void aFrontendRuleChangeMakesTheUiDocumentStaleAndChangesTheDrift() throws IOException {
        edit(Name.FRONTEND_JS, "minLength: 2,", "minLength: 3,");

        assertEquals(1, verifyExit());

        String text = report();
        assertTrue(text.contains("| " + UI + " | STALE (ui-rules, ui-drift) |"), text);
        assertTrue(text.contains("| studentName | minLength | 3 | none |"), text);
        assertFalse(text.contains("| studentName | minLength | 2 | none |"), text);
    }

    @Test
    void aBackendRuleChangeMakesTheDriftBlockOfTheUiDocumentStaleAndTheOtherUiBlocksStayCurrent() throws IOException {
        edit(Name.SERVICE_SOURCE, "!email.contains(\"@\")", "!email.contains(\"@x\")");

        assertEquals(1, verifyExit());

        assertTrue(report().contains("| " + UI + " | STALE (ui-drift) |"), report());
        assertTrue(report().contains("| studentEmail | pattern |"), report());
        assertTrue(report().contains("contains(\"@x\")"), report());
    }

    @Test
    void aTemplateLiteralInAValidationRuleIsExit2AndNotStale() throws IOException {
        edit(Name.FRONTEND_JS, "'Department is required.'", "`Department is ${1}`");

        UnsupportedConstructException thrown = verifyFails(UnsupportedConstructException.class);

        assertTrue(thrown.getMessage().contains("frontend/app.js"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("template literal"), thrown.getMessage());
        assertFalse(report().contains("STALE"));
        assertTrue(report().contains("Result: FAILED (exit 2)"));
    }

    @Test
    void aMissingIndexHtmlIsExit2() throws IOException {
        Files.delete(roots.path(Name.FRONTEND_HTML));

        MissingExpectedException thrown = verifyFails(MissingExpectedException.class);

        assertTrue(thrown.getMessage().contains("frontend/index.html"), thrown.getMessage());
        assertNoAbsolutePath(thrown.getMessage());
    }

    // ---- RV3-08 and RV3-03: line endings and a BOM never make a document stale ----

    @Test
    void aLoneCrDocumentIsNotStale() throws IOException {
        edit(Name.DOC_ENDPOINTS, "\n", "\r");

        assertEquals(0, verifyExit());
    }

    @Test
    void aDocumentWithALeadingBomIsNotStale() throws IOException {
        Path path = roots.path(Name.DOC_ENDPOINTS);
        byte[] text = Files.readAllBytes(path);
        byte[] withBom = new byte[text.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(text, 0, withBom, 3, text.length);
        Files.write(path, withBom);

        assertEquals(0, verifyExit());
        assertArrayEquals(withBom, Files.readAllBytes(path));
    }

    // ---- RV3-02: the safe failure message ----

    @Test
    void failureMessageKeepsTheMessageOfAnApplicationExceptionAndHidesAForeignOne() {
        assertEquals("docs/api/endpoints.md: broken", ReportRenderer.failureMessage(new MarkerIntegrityException("docs/api/endpoints.md: broken")));
        assertEquals("MarkerIntegrityException", ReportRenderer.failureMessage(new MarkerIntegrityException((String) null)));
        assertEquals("unexpected failure (IllegalStateException); details are not recorded.",
            ReportRenderer.failureMessage(new IllegalStateException("C:\\Users\\someone\\secret.txt")));
        assertEquals("unexpected failure (UncheckedIOException); details are not recorded.",
            ReportRenderer.failureMessage(new java.io.UncheckedIOException(new IOException("C:\\Users\\someone\\secret.txt"))));
    }
}
