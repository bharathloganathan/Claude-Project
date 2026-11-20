package org.example.docsync.engine;

import org.example.docsync.DocSyncCli;
import org.example.docsync.analysis.JavaAnalyzer;
import org.example.docsync.docs.DocCatalog;
import org.example.docsync.docs.MarkedDocument;
import org.example.docsync.error.DocumentWriteException;
import org.example.docsync.error.DocumentWriteException.FileOutcome;
import org.example.docsync.error.DocumentWriteException.Status;
import org.example.docsync.error.MarkerIntegrityException;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
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
 * Seven-phase sync (task E2-7, FR-E2-08 to FR-E2-13, FR-E2-15, FR-E2-18, NFR-02, NFR-03, AC-E2-03,
 * AC-E2-04, AC-E2-06, AC-E2-07, AC-E2-11, AC-NFR-02, DR-10). Fixtures are temp roots holding copies of
 * the real sources and no documents. This source file is ASCII only.
 */
class SyncServiceTest {

    private static final List<Name> SOURCES = List.of(Name.API_ROUTES_SOURCE, Name.ERROR_CODE_SOURCE,
        Name.STUDENT_RECORD_SOURCE, Name.SERVICE_SOURCE, Name.FRONTEND_HTML, Name.FRONTEND_JS);
    private static final List<Name> DOCS = List.of(Name.DOC_ENDPOINTS, Name.DOC_CONTRACT, Name.DOC_SERVICE, Name.DOC_UI);
    private static final String ENDPOINTS = "docs/api/endpoints.md";
    private static final String CONTRACT = "docs/api/student-contract.md";
    private static final String SERVICE = "docs/backend/student-service.md";
    private static final String UI = "docs/ui/student-ui.md";
    private static final String SENTENCE = "Replacement is not transactional: files listed as REPLACED remain updated.";
    private static final Pattern ERRORS_BLOCK = Pattern.compile(
        "\n(<!-- docsync:begin api-errors -->.*?<!-- docsync:end api-errors -->\n)", Pattern.DOTALL);

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
    }

    // ---- helpers ----

    private static AtomicWriter.Mover failingFor(String fileName) {
        return (source, target) -> {
            if (target.getFileName().toString().equals(fileName)) {
                throw new IOException("simulated move failure");
            }
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        };
    }

    private static final AtomicWriter.Mover FAILING_ALWAYS = (source, target) -> {
        throw new IOException("simulated move failure");
    };

    private SyncService service() {
        return service(new AtomicWriter(roots));
    }

    private SyncService service(AtomicWriter.Mover mover) {
        return service(new AtomicWriter(roots, mover));
    }

    private SyncService service(AtomicWriter writer) {
        return new SyncService(roots, files, new JavaAnalyzer(roots, files), new DocWriter(writer), new ReportWriter(writer));
    }

    private int sync() {
        return service().sync(out, err);
    }

    private int verify() {
        return new VerifyService(roots, files, new JavaAnalyzer(roots, files), new ReportWriter(new AtomicWriter(roots)))
            .verify(out, err);
    }

    private Map<Name, String> snapshot() throws IOException {
        Map<Name, String> state = new HashMap<>();
        for (Name doc : DOCS) {
            Path path = roots.path(doc);
            state.put(doc, Files.exists(path) ? new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1) : null);
        }
        return state;
    }

    private String expectedDoc(Name name) {
        SourceModel model = new JavaAnalyzer(roots, files).analyzeWithUi();
        var generated = DocCatalog.generate(model, roots.display(Name.SERVICE_SOURCE));
        for (DocCatalog.Entry entry : DocCatalog.entries()) {
            if (entry.name() == name) {
                return MarkedDocument.create(entry.title(), generated.get(name));
            }
        }
        throw new IllegalArgumentException(name.toString());
    }

    private void edit(Name name, String from, String to) throws IOException {
        Path path = roots.path(name);
        String text = Files.readString(path);
        assertTrue(text.contains(from), "fixture must contain: " + from);
        Files.writeString(path, text.replace(from, to));
    }

    private String text(Name name) throws IOException {
        return Files.readString(roots.path(name));
    }

    // The UI document is created together with the other three and is otherwise unchanged in these scenarios.
    private static String syncText(String endpoints, String contract, String service) {
        boolean allCreated = endpoints.equals("created") && contract.equals("created") && service.equals("created");
        return syncText(endpoints, contract, service, allCreated ? "created" : "unchanged");
    }

    private static String syncText(String endpoints, String contract, String service, String ui) {
        return "# DocSync Sync Report\n\nCommand: sync\nResult: SUCCESS\n\n| File | Status |\n|---|---|\n"
            + "| " + ENDPOINTS + " | " + endpoints + " |\n"
            + "| " + CONTRACT + " | " + contract + " |\n"
            + "| " + SERVICE + " | " + service + " |\n"
            + "| " + UI + " | " + ui + " |\n";
    }

    private static String failedText(String message) {
        return "# DocSync Sync Report\n\nCommand: sync\nResult: FAILED (exit 2)\nMessage: " + message + "\n";
    }

    private void assertNoTempFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> leftovers = walk.filter(p -> p.getFileName().toString().startsWith(".docsync-")).toList();
            assertEquals(List.of(), leftovers);
        }
    }

    private void assertNoAbsolutePath(String message) {
        assertFalse(message.contains(dir.toString()), "must not contain the absolute root");
        assertFalse(message.contains(dir.getFileName().toString()), "must not contain the absolute root");
    }

    private void applyFault(String fault) throws IOException {
        switch (fault) {
            case "missing-end" -> edit(Name.DOC_ENDPOINTS, "<!-- docsync:end api-errors -->", "");
            case "unknown-id" -> edit(Name.DOC_ENDPOINTS, "docsync:begin api-errors", "docsync:begin api-bogus");
            case "duplicate-id" -> edit(Name.DOC_ENDPOINTS, "<!-- docsync:begin api-errors -->", "<!-- docsync:begin api-routes -->");
            default -> edit(Name.DOC_ENDPOINTS, "<!-- docsync:begin api-routes -->", "<!--docsync:begin api-routes -->");
        }
    }

    // ---- creation and idempotence ----

    @Test
    void emptyDocsDirectoryCreatesAllThreeDocumentsAndTheReport() throws IOException {
        assertFalse(Files.exists(dir.resolve("docs")));

        assertEquals(0, sync());

        for (Name doc : DOCS) {
            assertEquals(expectedDoc(doc), text(doc));
        }
        assertEquals(syncText("created", "created", "created"), text(Name.REPORT_SYNC));
        String stdout = outBytes.toString(StandardCharsets.UTF_8);
        assertTrue(stdout.contains("sync: created " + ENDPOINTS), stdout);
        assertEquals("", errBytes.toString(StandardCharsets.UTF_8));
        assertNoTempFiles();
    }

    @Test
    void syncedDocumentsVerifyAsUpToDate() {
        assertEquals(0, sync());
        assertEquals(0, verify());
    }

    @Test
    void threeRunsGiveByteIdenticalDocumentsAndReports() throws IOException {
        assertEquals(0, sync());
        assertEquals(syncText("created", "created", "created"), text(Name.REPORT_SYNC));

        assertEquals(0, sync());
        Map<Name, String> second = snapshot();
        byte[] secondReport = Files.readAllBytes(roots.path(Name.REPORT_SYNC));
        assertEquals(syncText("unchanged", "unchanged", "unchanged"), text(Name.REPORT_SYNC));

        assertEquals(0, sync());
        assertEquals(second, snapshot());
        assertArrayEquals(secondReport, Files.readAllBytes(roots.path(Name.REPORT_SYNC)));
    }

    @Test
    void anUnchangedRunWritesNothing() throws IOException {
        assertEquals(0, sync());
        assertEquals(0, sync());
        Map<Name, String> docsBefore = snapshot();
        byte[] reportBefore = Files.readAllBytes(roots.path(Name.REPORT_SYNC));
        int[] moves = {0};
        AtomicWriter.Mover counting = (source, target) -> {
            moves[0]++;
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        };

        assertEquals(0, service(counting).sync(out, err));

        assertEquals(0, moves[0], "an unchanged run must not replace any file");
        assertEquals(docsBefore, snapshot());
        assertArrayEquals(reportBefore, Files.readAllBytes(roots.path(Name.REPORT_SYNC)));
        assertNoTempFiles();
    }

    // ---- missing files and blocks ----

    @Test
    void aMissingFileIsCreatedAndTheOthersStayUnchanged() throws IOException {
        assertEquals(0, sync());
        Files.delete(roots.path(Name.DOC_CONTRACT));

        assertEquals(0, sync());

        assertEquals(expectedDoc(Name.DOC_CONTRACT), text(Name.DOC_CONTRACT));
        assertEquals(syncText("unchanged", "created", "unchanged"), text(Name.REPORT_SYNC));
    }

    @Test
    void aMissingBlockIsAppendedAfterTheManualContent() throws IOException {
        assertEquals(0, sync());
        String full = text(Name.DOC_ENDPOINTS);
        Matcher matcher = ERRORS_BLOCK.matcher(full);
        assertTrue(matcher.find());
        String block = matcher.group(1);
        String withoutBlock = full.substring(0, matcher.start()) + "\n" + full.substring(matcher.end());
        String manual = withoutBlock.replaceFirst("\n+$", "\n") + "Manual closing note.\n";
        Files.writeString(roots.path(Name.DOC_ENDPOINTS), manual);

        assertEquals(0, sync());

        assertEquals(manual + "\n" + block, text(Name.DOC_ENDPOINTS));
        assertEquals(syncText("updated", "unchanged", "unchanged"), text(Name.REPORT_SYNC));
        assertEquals(0, verify());
    }

    // ---- manual text and block replacement ----

    @Test
    void manualTextBeforeBetweenAndAfterBlocksIsPreservedByteForByte() throws IOException {
        assertEquals(0, sync());
        edit(Name.DOC_ENDPOINTS, "# API Endpoints\n", "# API Endpoints\n\nHand written intro.\n");
        edit(Name.DOC_ENDPOINTS, "<!-- docsync:end api-routes -->\n",
            "<!-- docsync:end api-routes -->\n\nHand written middle | with a pipe.\n");
        edit(Name.DOC_ENDPOINTS, "<!-- docsync:end api-errors -->\n",
            "<!-- docsync:end api-errors -->\n\nHand written outro.\n");
        String before = text(Name.DOC_ENDPOINTS);
        edit(Name.API_ROUTES_SOURCE, "\"List all students\"", "\"List every student\"");

        assertEquals(0, sync());

        assertEquals(before.replace("List all students", "List every student"), text(Name.DOC_ENDPOINTS));
        assertEquals(syncText("updated", "unchanged", "unchanged"), text(Name.REPORT_SYNC));
        assertEquals(0, verify());
    }

    @Test
    void aHandEditInsideABlockIsRegenerated() throws IOException {
        assertEquals(0, sync());
        edit(Name.DOC_ENDPOINTS, "List all students", "Edited by hand");

        assertEquals(0, sync());

        assertEquals(expectedDoc(Name.DOC_ENDPOINTS), text(Name.DOC_ENDPOINTS));
        assertEquals(syncText("updated", "unchanged", "unchanged"), text(Name.REPORT_SYNC));
    }

    // ---- CRLF ----

    @Test
    void crlfDocumentsStayByteIdenticalAndAreReportedUnchanged() throws IOException {
        assertEquals(0, sync());
        for (Name doc : DOCS) {
            edit(doc, "\n", "\r\n");
        }
        Map<Name, String> crlf = snapshot();

        assertEquals(0, sync());

        assertEquals(crlf, snapshot());
        assertEquals(syncText("unchanged", "unchanged", "unchanged"), text(Name.REPORT_SYNC));
        byte[] report = Files.readAllBytes(roots.path(Name.REPORT_SYNC));

        assertEquals(0, sync());

        assertEquals(crlf, snapshot());
        assertArrayEquals(report, Files.readAllBytes(roots.path(Name.REPORT_SYNC)));
    }

    @Test
    void aCrlfDocumentIsUpdatedWithCrlfAndItsManualTextIsKept() throws IOException {
        assertEquals(0, sync());
        edit(Name.DOC_ENDPOINTS, "# API Endpoints\n", "# API Endpoints\n\nHand written intro.\n");
        String lf = text(Name.DOC_ENDPOINTS);
        Files.writeString(roots.path(Name.DOC_ENDPOINTS), lf.replace("\n", "\r\n"));
        edit(Name.API_ROUTES_SOURCE, "\"List all students\"", "\"List every student\"");

        assertEquals(0, sync());

        assertEquals(lf.replace("List all students", "List every student").replace("\n", "\r\n"), text(Name.DOC_ENDPOINTS));
        assertEquals(syncText("updated", "unchanged", "unchanged"), text(Name.REPORT_SYNC));
        Map<Name, String> after = snapshot();

        assertEquals(0, sync());

        assertEquals(after, snapshot());
        assertEquals(syncText("unchanged", "unchanged", "unchanged"), text(Name.REPORT_SYNC));
    }

    @Test
    void aBlockAppendedToACrlfDocumentUsesCrlf() throws IOException {
        assertEquals(0, sync());
        String full = text(Name.DOC_ENDPOINTS);
        Matcher matcher = ERRORS_BLOCK.matcher(full);
        assertTrue(matcher.find());
        String block = matcher.group(1);
        String withoutBlock = full.substring(0, matcher.start()) + "\n" + full.substring(matcher.end());
        String lfBase = withoutBlock.replaceFirst("\n+$", "\n");
        Files.writeString(roots.path(Name.DOC_ENDPOINTS), lfBase.replace("\n", "\r\n"));

        assertEquals(0, sync());

        assertEquals((lfBase + "\n" + block).replace("\n", "\r\n"), text(Name.DOC_ENDPOINTS));
        assertEquals(syncText("updated", "unchanged", "unchanged"), text(Name.REPORT_SYNC));
    }

    // ---- exit 2 before any write ----

    @ParameterizedTest
    @ValueSource(strings = {"missing-end", "unknown-id", "duplicate-id", "malformed"})
    void aMarkerFaultWritesNothingAndIsReportedAsFailed(String fault) throws IOException {
        assertEquals(0, sync());
        applyFault(fault);
        Files.delete(roots.path(Name.DOC_CONTRACT));
        edit(Name.API_ROUTES_SOURCE, "\"List all students\"", "\"List every student\"");
        Map<Name, String> before = snapshot();

        MarkerIntegrityException thrown = assertThrows(MarkerIntegrityException.class, () -> service().sync(out, err));

        assertEquals(before, snapshot());
        assertFalse(Files.exists(roots.path(Name.DOC_CONTRACT)));
        assertTrue(thrown.getMessage().contains(ENDPOINTS), thrown.getMessage());
        assertNoAbsolutePath(thrown.getMessage());
        assertEquals(failedText(thrown.getMessage()), text(Name.REPORT_SYNC));
        assertNoTempFiles();
    }

    @Test
    void aMarkerFaultInTheLastDocumentStillWritesNothing() throws IOException {
        assertEquals(0, sync());
        edit(Name.DOC_SERVICE, "<!-- docsync:end service-digest -->", "");
        edit(Name.API_ROUTES_SOURCE, "\"List all students\"", "\"List every student\"");
        Map<Name, String> before = snapshot();

        MarkerIntegrityException thrown = assertThrows(MarkerIntegrityException.class, () -> service().sync(out, err));

        assertEquals(before, snapshot());
        assertTrue(thrown.getMessage().contains(SERVICE), thrown.getMessage());
        assertEquals(failedText(thrown.getMessage()), text(Name.REPORT_SYNC));
    }

    @Test
    void anUnsupportedConstructWritesNoDocument() throws IOException {
        edit(Name.API_ROUTES_SOURCE, "\"Create a student\"", "SUMMARY");

        UnsupportedConstructException thrown = assertThrows(UnsupportedConstructException.class, () -> service().sync(out, err));

        for (Name doc : DOCS) {
            assertFalse(Files.exists(roots.path(doc)), doc.toString());
        }
        assertNoAbsolutePath(thrown.getMessage());
        assertEquals(failedText(thrown.getMessage()), text(Name.REPORT_SYNC));
        assertNoTempFiles();
    }

    // ---- partial replacement (phase 6) ----

    @Test
    void aMoveFailureGivesAFailedReportWithExactOutcomesAndARecoveryRunCompletesTheSync() throws IOException {
        DocumentWriteException thrown = assertThrows(DocumentWriteException.class,
            () -> service(failingFor("student-contract.md")).sync(out, err));

        assertEquals(List.of(
            new FileOutcome(ENDPOINTS, Status.REPLACED, null),
            new FileOutcome(CONTRACT, Status.FAILED, "IOException"),
            new FileOutcome(SERVICE, Status.NOT_ATTEMPTED, null),
            new FileOutcome(UI, Status.NOT_ATTEMPTED, null)), thrown.outcomes());
        assertEquals(expectedDoc(Name.DOC_ENDPOINTS), text(Name.DOC_ENDPOINTS));
        assertFalse(Files.exists(roots.path(Name.DOC_CONTRACT)));
        assertFalse(Files.exists(roots.path(Name.DOC_SERVICE)));
        assertFalse(Files.exists(roots.path(Name.DOC_UI)));
        assertEquals(failedText(thrown.getMessage()) + "\n" + SENTENCE + "\n\n| File | Outcome |\n|---|---|\n"
            + "| " + ENDPOINTS + " | REPLACED |\n"
            + "| " + CONTRACT + " | FAILED (IOException) |\n"
            + "| " + SERVICE + " | NOT_ATTEMPTED |\n"
            + "| " + UI + " | NOT_ATTEMPTED |\n", text(Name.REPORT_SYNC));
        assertNoAbsolutePath(text(Name.REPORT_SYNC));
        assertNoTempFiles();

        assertEquals(0, sync());

        for (Name doc : DOCS) {
            assertEquals(expectedDoc(doc), text(doc));
        }
        assertEquals(syncText("unchanged", "created", "created", "created"), text(Name.REPORT_SYNC));
        assertEquals(0, verify());
    }

    // ---- report write failures ----

    @Test
    void whenTheFailedReportCannotBeWrittenThePreviousReportIsPreservedAndTheOriginalIsRethrown() throws IOException {
        assertEquals(0, sync());
        byte[] previous = Files.readAllBytes(roots.path(Name.REPORT_SYNC));
        applyFault("missing-end");
        Map<Name, String> faulty = snapshot();

        MarkerIntegrityException thrown = assertThrows(MarkerIntegrityException.class,
            () -> service(FAILING_ALWAYS).sync(out, err));

        assertTrue(thrown.getMessage().contains(ENDPOINTS), thrown.getMessage());
        assertArrayEquals(previous, Files.readAllBytes(roots.path(Name.REPORT_SYNC)));
        assertEquals(faulty, snapshot());
        String errText = errBytes.toString(StandardCharsets.UTF_8);
        assertTrue(errText.contains("could not be written"), errText);
        assertTrue(errText.contains("may be stale"), errText);
        assertNoAbsolutePath(errText);
        assertNoTempFiles();
    }

    @Test
    void whenOnlyTheSyncReportCannotBeWrittenTheErrorSaysWhichDocumentsWereAlreadyWritten() throws IOException {
        DocumentWriteException thrown = assertThrows(DocumentWriteException.class,
            () -> service(failingFor("sync-report.md")).sync(out, err));

        for (Name doc : DOCS) {
            assertEquals(expectedDoc(doc), text(doc));
        }
        assertFalse(Files.exists(roots.path(Name.REPORT_SYNC)));
        assertEquals(List.of(), thrown.outcomes());
        assertTrue(thrown.getMessage().contains("already written"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains(ENDPOINTS + " created"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("docs/reports/sync-report.md"), thrown.getMessage());
        assertNoAbsolutePath(thrown.getMessage());
        assertTrue(errBytes.toString(StandardCharsets.UTF_8).contains("could not be written"));
        assertNoTempFiles();
    }

    // ---- structure ----

    @Test
    void syncServiceTakesDocWriterAndReportWriterAndNoOtherFileAccess() throws IOException {
        Constructor<?>[] constructors = SyncService.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertEquals(List.of(RootPaths.class, ProjectFiles.class, JavaAnalyzer.class, DocWriter.class, ReportWriter.class),
            Arrays.asList(constructors[0].getParameterTypes()));
        String source = Files.readString(Path.of("src", "main", "java", "org", "example", "docsync", "engine", "SyncService.java"));
        assertFalse(source.contains("java.nio.file"));
        assertFalse(source.contains("java.io.File"));
        assertFalse(source.contains("readAllBytes"));
    }

    @Test
    void syncReportTextForMixedStatuses() {
        Findings.SyncResult result = new Findings.SyncResult(List.of(
            new Findings.FileChange(ENDPOINTS, Findings.SyncStatus.CREATED),
            new Findings.FileChange(CONTRACT, Findings.SyncStatus.UPDATED),
            new Findings.FileChange(SERVICE, Findings.SyncStatus.UNCHANGED),
            new Findings.FileChange(UI, Findings.SyncStatus.UPDATED)));

        assertEquals(syncText("created", "updated", "unchanged", "updated"), ReportRenderer.syncReport(result));
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

    private static String syncTextFor(Name doc, String status) {
        return syncText(doc == Name.DOC_ENDPOINTS ? status : "unchanged", doc == Name.DOC_CONTRACT ? status : "unchanged",
            doc == Name.DOC_SERVICE ? status : "unchanged", doc == Name.DOC_UI ? status : "unchanged");
    }

    @ParameterizedTest
    @MethodSource("documents")
    void aMissingDocumentIsCreatedForEveryDocument(Name doc) throws IOException {
        assertEquals(0, sync());
        Files.delete(roots.path(doc));

        assertEquals(0, sync());

        assertEquals(expectedDoc(doc), text(doc));
        assertEquals(syncTextFor(doc, "created"), text(Name.REPORT_SYNC));
    }

    @ParameterizedTest
    @MethodSource("blocks")
    void aMissingBlockIsAppendedForEveryBlockOfEveryDocument(Name doc, String id) throws IOException {
        assertEquals(0, sync());
        String full = text(doc);
        Matcher matcher = Pattern.compile("\n(<!-- docsync:begin " + id + " -->.*?<!-- docsync:end " + id + " -->\n)", Pattern.DOTALL)
            .matcher(full);
        assertTrue(matcher.find());
        String block = matcher.group(1);
        String manual = (full.substring(0, matcher.start()) + "\n" + full.substring(matcher.end())).replaceFirst("\n+$", "\n");
        Files.writeString(roots.path(doc), manual);

        assertEquals(0, sync());

        assertEquals(manual + "\n" + block, text(doc));
        assertEquals(syncTextFor(doc, "updated"), text(Name.REPORT_SYNC));
        assertEquals(0, verify());
    }

    @ParameterizedTest
    @MethodSource("markerFaults")
    void markerFaultsInEveryDocumentWriteNothing(Name doc, String fault) throws IOException {
        assertEquals(0, sync());
        List<String> ids = DocCatalog.entries().stream().filter(entry -> entry.name() == doc).findFirst().orElseThrow().blockIds();
        String first = ids.get(0);
        String last = ids.get(ids.size() - 1);
        switch (fault) {
            case "missing-end" -> edit(doc, "<!-- docsync:end " + last + " -->", "");
            case "unknown-id" -> edit(doc, "docsync:begin " + last, "docsync:begin bogus-id");
            case "duplicate-id" -> edit(doc, "<!-- docsync:begin " + last + " -->", "<!-- docsync:begin " + first + " -->");
            default -> edit(doc, "<!-- docsync:begin " + first + " -->", "<!--docsync:begin " + first + " -->");
        }
        edit(Name.API_ROUTES_SOURCE, "\"List all students\"", "\"List every student\"");
        Map<Name, String> before = snapshot();

        MarkerIntegrityException thrown = assertThrows(MarkerIntegrityException.class, () -> service().sync(out, err));

        assertEquals(before, snapshot());
        assertTrue(thrown.getMessage().contains(roots.display(doc)), thrown.getMessage());
        assertNoAbsolutePath(thrown.getMessage());
        assertEquals(failedText(thrown.getMessage()), text(Name.REPORT_SYNC));
        assertNoTempFiles();
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

    private static final String SAFE_MESSAGE = "unexpected failure (IllegalStateException); details are not recorded.";

    @Test
    void aForeignRuntimeExceptionIsRethrownUnchangedAndRecordedWithASafeMessage() throws IOException {
        IllegalStateException failure = new IllegalStateException("cannot open " + dir + "\\secret.txt");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> service().sync(throwingOut(failure), err));

        assertSame(failure, thrown);
        assertEquals(failedText(SAFE_MESSAGE), text(Name.REPORT_SYNC));
        assertNoAbsolutePath(text(Name.REPORT_SYNC));
        for (Name doc : DOCS) {
            assertEquals(expectedDoc(doc), text(doc));
        }
        assertNoTempFiles();
    }

    @Test
    void theCliPrintsTheSameSafeMessageOnStderrAsTheFailedReport() throws IOException {
        IllegalStateException failure = new IllegalStateException("cannot open " + dir + "\\secret.txt");

        int exit = DocSyncCli.run(new String[] {"sync", "--root", dir.toString()}, throwingOut(failure), err);

        assertEquals(2, exit);
        String stderr = errBytes.toString(StandardCharsets.UTF_8);
        assertTrue(stderr.startsWith("error: " + SAFE_MESSAGE), stderr);
        assertFalse(stderr.contains("secret"), stderr);
        assertNoAbsolutePath(stderr);
        assertEquals(failedText(SAFE_MESSAGE), text(Name.REPORT_SYNC));
    }

    @Test
    void aForeignRuntimeExceptionWhoseFailedReportCannotBeWrittenKeepsThePreviousReport() throws IOException {
        IllegalStateException failure = new IllegalStateException("cannot open " + dir + "\\secret.txt");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
            () -> service(succeedTimes(5)).sync(throwingOut(failure), err));

        assertSame(failure, thrown);
        assertEquals(syncText("created", "created", "created"), text(Name.REPORT_SYNC));
        String errText = errBytes.toString(StandardCharsets.UTF_8);
        assertTrue(errText.contains("could not be written"), errText);
        assertTrue(errText.contains("may be stale"), errText);
        assertNoAbsolutePath(errText);
        assertNoTempFiles();
    }

    @Test
    void aDocumentReplacementFailureFollowedByAFailedReportFailureKeepsThePreviousReport() throws IOException {
        Files.createDirectories(roots.path(Name.REPORT_SYNC).getParent());
        Files.writeString(roots.path(Name.REPORT_SYNC), "previous report\n");
        byte[] previous = Files.readAllBytes(roots.path(Name.REPORT_SYNC));

        DocumentWriteException thrown = assertThrows(DocumentWriteException.class, () -> service(FAILING_ALWAYS).sync(out, err));

        assertEquals(List.of(
            new FileOutcome(ENDPOINTS, Status.FAILED, "IOException"),
            new FileOutcome(CONTRACT, Status.NOT_ATTEMPTED, null),
            new FileOutcome(SERVICE, Status.NOT_ATTEMPTED, null),
            new FileOutcome(UI, Status.NOT_ATTEMPTED, null)), thrown.outcomes());
        assertArrayEquals(previous, Files.readAllBytes(roots.path(Name.REPORT_SYNC)));
        for (Name doc : DOCS) {
            assertFalse(Files.exists(roots.path(doc)), doc.toString());
        }
        String errText = errBytes.toString(StandardCharsets.UTF_8);
        assertTrue(errText.contains("could not be written"), errText);
        assertTrue(errText.contains("may be stale"), errText);
        assertNoAbsolutePath(errText);
        assertNoAbsolutePath(thrown.getMessage());
        assertNoTempFiles();
    }

    // ---- RV3-08: a lone CR is a line break for sync and verify, and sync never rewrites manual text ----

    @Test
    void anUpToDateLoneCrDocumentIsLeftByteIdenticalAndReportedUnchanged() throws IOException {
        assertEquals(0, sync());
        for (Name doc : DOCS) {
            edit(doc, "\n", "\r");
        }
        Map<Name, String> loneCr = snapshot();
        assertEquals(0, verify());

        assertEquals(0, sync());

        assertEquals(loneCr, snapshot());
        assertEquals(syncText("unchanged", "unchanged", "unchanged"), text(Name.REPORT_SYNC));
    }

    @Test
    void aLoneCrDocumentKeepsAllManualTextWhileAStaleBlockIsRegenerated() throws IOException {
        assertEquals(0, sync());
        edit(Name.DOC_ENDPOINTS, "# API Endpoints\n", "# API Endpoints\n\nHand written intro.\n");
        edit(Name.DOC_ENDPOINTS, "<!-- docsync:end api-routes -->\n", "<!-- docsync:end api-routes -->\n\nHand written middle.\n");
        edit(Name.DOC_ENDPOINTS, "<!-- docsync:end api-errors -->\n", "<!-- docsync:end api-errors -->\n\nHand written outro.\n");
        edit(Name.DOC_ENDPOINTS, "\n", "\r");
        String original = text(Name.DOC_ENDPOINTS);
        edit(Name.API_ROUTES_SOURCE, "\"List all students\"", "\"List every student\"");
        assertEquals(1, verify());

        assertEquals(0, sync());

        assertEquals(original.replace("List all students", "List every student"), text(Name.DOC_ENDPOINTS));
        assertTrue(text(Name.DOC_ENDPOINTS).contains("Hand written intro.\r"));
        assertEquals(syncText("updated", "unchanged", "unchanged"), text(Name.REPORT_SYNC));
        assertEquals(0, verify());
        Map<Name, String> after = snapshot();

        assertEquals(0, sync());

        assertEquals(after, snapshot());
        assertEquals(syncText("unchanged", "unchanged", "unchanged"), text(Name.REPORT_SYNC));
    }

    @Test
    void aBlockAppendedToALoneCrDocumentKeepsTheExistingTextExceptItsFinalLineBreak() throws IOException {
        String[] parts = writeLoneCrEndpointsWithoutErrorsBlock();
        String crBase = parts[0];
        String block = parts[1];

        assertEquals(0, sync());

        // The final lone CR becomes LF: kept as CR it would fuse with the LF of the blank-line separator into CRLF.
        assertEquals(crBase.substring(0, crBase.length() - 1) + "\n\n" + block, text(Name.DOC_ENDPOINTS));
        assertEquals(syncText("updated", "unchanged", "unchanged"), text(Name.REPORT_SYNC));
        assertEquals(0, verify());
    }

    @Test
    void aBlockAppendedToALoneCrDocumentKeepsTheBlankLineSeparatorAndCreatesNoCrlf() throws IOException {
        writeLoneCrEndpointsWithoutErrorsBlock();

        assertEquals(0, sync());

        String result = text(Name.DOC_ENDPOINTS);
        assertTrue(result.contains("<!-- docsync:end api-routes -->\n\n<!-- docsync:begin api-errors -->\n"), result);
        assertFalse(result.contains("\r\n"), "no CRLF may appear");
        assertTrue(result.startsWith("# API Endpoints\r\r<!-- docsync:begin api-routes -->\r"), "the earlier lone CRs are kept");
    }

    @Test
    void theSecondSyncOfAnAppendedLoneCrDocumentIsUnchanged() throws IOException {
        writeLoneCrEndpointsWithoutErrorsBlock();
        assertEquals(0, sync());
        Map<Name, String> afterFirst = snapshot();

        assertEquals(0, sync());

        assertEquals(afterFirst, snapshot());
        assertEquals(syncText("unchanged", "unchanged", "unchanged"), text(Name.REPORT_SYNC));
        assertEquals(0, verify());
    }

    @Test
    void aLoneCrBodyWhoseFirstLineIsCurrentButWhoseBlankLineDiffersIsRegeneratedAndVerifyAgrees() throws IOException {
        assertEquals(0, sync());
        String lf = text(Name.DOC_ENDPOINTS);
        String firstLineOnly = Pattern.compile("(<!-- docsync:begin api-errors -->\n[^\n]*\n)\n\\| Code.*?(<!-- docsync:end api-errors -->)",
            Pattern.DOTALL).matcher(lf).replaceFirst("$1$2");
        assertNotEquals(lf, firstLineOnly);
        Files.writeString(roots.path(Name.DOC_ENDPOINTS), firstLineOnly.replace("\n", "\r"));
        assertEquals(1, verify());

        assertEquals(0, sync());

        String result = text(Name.DOC_ENDPOINTS);
        assertEquals(0, verify());
        assertTrue(result.contains("\"code\":\"<CODE>\"}.\n\n| Code | HTTP status |"), result);
        assertFalse(result.contains("\r\n"), "no CRLF may appear");
        assertTrue(result.startsWith("# API Endpoints\r\r<!-- docsync:begin api-routes -->\r"), "the earlier lone CRs are kept");
        assertEquals(syncText("updated", "unchanged", "unchanged"), text(Name.REPORT_SYNC));
        Map<Name, String> afterFirst = snapshot();

        assertEquals(0, sync());

        assertEquals(afterFirst, snapshot());
        assertEquals(syncText("unchanged", "unchanged", "unchanged"), text(Name.REPORT_SYNC));
    }

    // ---- RV3b-06: sync leaves a block alone when verify judges it current ----

    @Test
    void aBlockThatVerifyJudgesUpToDateIsNotRewrittenBecauseOfItsLineEndings() throws IOException {
        assertEquals(0, sync());
        edit(Name.DOC_ENDPOINTS, "\n", "\r\n");
        edit(Name.DOC_ENDPOINTS, "| GET | /students | List all students | none | Student[] |\r\n",
            "| GET | /students | List all students | none | Student[] |\n");
        Map<Name, String> mixed = snapshot();
        assertEquals(0, verify());

        assertEquals(0, sync());

        assertEquals(mixed, snapshot());
        assertEquals(syncText("unchanged", "unchanged", "unchanged"), text(Name.REPORT_SYNC));
    }

    // Writes the endpoints document with every line break a lone CR and without the api-errors block.
    private String[] writeLoneCrEndpointsWithoutErrorsBlock() throws IOException {
        assertEquals(0, sync());
        String full = text(Name.DOC_ENDPOINTS);
        Matcher matcher = ERRORS_BLOCK.matcher(full);
        assertTrue(matcher.find());
        String block = matcher.group(1);
        String lfBase = (full.substring(0, matcher.start()) + "\n" + full.substring(matcher.end())).replaceFirst("\n+$", "\n");
        String crBase = lfBase.replace("\n", "\r");
        Files.writeString(roots.path(Name.DOC_ENDPOINTS), crBase);
        return new String[] {crBase, block};
    }

    // ---- RV3-03: a leading BOM is removed when a block of the document is rewritten (known behavior) ----

    private void writeWithBom(Name doc) throws IOException {
        byte[] body = Files.readAllBytes(roots.path(doc));
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        Files.write(roots.path(doc), withBom);
    }

    @Test
    void anUpToDateDocumentWithABomIsNotRewrittenAndKeepsItsBom() throws IOException {
        assertEquals(0, sync());
        writeWithBom(Name.DOC_ENDPOINTS);
        byte[] before = Files.readAllBytes(roots.path(Name.DOC_ENDPOINTS));

        assertEquals(0, sync());

        assertArrayEquals(before, Files.readAllBytes(roots.path(Name.DOC_ENDPOINTS)));
        assertEquals(syncText("unchanged", "unchanged", "unchanged"), text(Name.REPORT_SYNC));
    }

    @Test
    void aDocumentWithABomLosesItsBomWhenABlockIsRewrittenAndKeepsTheRestOfItsText() throws IOException {
        assertEquals(0, sync());
        String original = text(Name.DOC_ENDPOINTS);
        writeWithBom(Name.DOC_ENDPOINTS);
        edit(Name.API_ROUTES_SOURCE, "\"List all students\"", "\"List every student\"");

        assertEquals(0, sync());

        byte[] after = Files.readAllBytes(roots.path(Name.DOC_ENDPOINTS));
        assertFalse(after.length >= 3 && after[0] == (byte) 0xEF && after[1] == (byte) 0xBB && after[2] == (byte) 0xBF);
        assertEquals(original.replace("List all students", "List every student"), text(Name.DOC_ENDPOINTS));
        assertEquals(syncText("updated", "unchanged", "unchanged"), text(Name.REPORT_SYNC));
    }
}
