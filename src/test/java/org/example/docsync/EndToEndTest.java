package org.example.docsync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * End to end behaviour of the documentation sync engine (task E2-9, FR-E2-01, FR-E2-17 to FR-E2-20,
 * AC-E2-01, AC-E2-02, AC-E2-05 to AC-E2-14). The process exit codes are asserted on real child JVMs
 * started from a launch directory that holds decoy files; the remaining cases run in-process. Every
 * run targets a temp fixture root holding a read-only copy of the real sources, never the real root.
 * This source file is ASCII only.
 */
class EndToEndTest {

    record Result(int exit, String out, String err) {
    }

    private static final String SERVER = "src/main/java/org/example/api/StudentApiServer.java";
    private static final String STUDENT = "src/main/java/org/example/model/Student.java";
    private static final String SERVICE_SOURCE = "src/main/java/org/example/service/StudentService.java";
    private static final String ENDPOINTS = "docs/api/endpoints.md";
    private static final String CONTRACT = "docs/api/student-contract.md";
    private static final String SERVICE = "docs/backend/student-service.md";
    private static final String UI = "docs/ui/student-ui.md";
    private static final String ARROW = String.valueOf((char) 0x2192);
    private static final String SYNC_REPORT = "docs/reports/sync-report.md";
    private static final String VERIFY_REPORT = "docs/reports/verify-report.md";
    private static final String DECOY_SERVER = "package org.example.api;\n\nclass StudentApiServer {\n"
        + "    List<Route> routes() {\n"
        + "        return List.of(new Route(\"GET\", \"/decoy\", \"Decoy\", \"none\", \"none\", this::decoy));\n"
        + "    }\n}\n";
    private static final Pattern ERRORS_BLOCK = Pattern.compile(
        "<!-- docsync:begin api-errors -->.*?<!-- docsync:end api-errors -->\n", Pattern.DOTALL);

    @TempDir
    Path temp;

    private Path fixture;
    private Path launch;
    private Path logs;

    // ---- shared helpers (also used by NoGitUseTest and ProjectCopySelfCheckTest) ----

    static void copyProject(Path targetRoot) throws IOException {
        for (Path tree : List.of(Path.of("src", "main", "java"), Path.of("frontend"))) {
            assertTrue(Files.isDirectory(tree), "the project tree must be found relative to the project directory: " + tree);
            try (Stream<Path> walk = Files.walk(tree)) {
                for (Path source : (Iterable<Path>) walk::iterator) {
                    Path target = targetRoot.resolve(source);
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(source, target);
                    }
                }
            }
        }
    }

    static Map<String, String> snapshot(Path directory) throws IOException {
        Map<String, String> state = new TreeMap<>();
        if (!Files.exists(directory)) {
            return state;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            for (Path path : (Iterable<Path>) walk::iterator) {
                String name = directory.relativize(path).toString().replace('\\', '/');
                if (Files.isDirectory(path)) {
                    state.put(name, "dir");
                } else {
                    byte[] bytes = Files.readAllBytes(path);
                    state.put(name, bytes.length + " bytes sha256 " + sha256Hex(bytes));
                }
            }
        }
        return state;
    }

    static Result inProcess(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = DocSyncCli.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    // ---- fixture and process helpers ----

    @BeforeEach
    void buildFixtureAndLaunchDirectory() throws IOException {
        fixture = temp.resolve("fixture");
        launch = temp.resolve("launch");
        logs = temp.resolve("logs");
        Files.createDirectories(fixture);
        Files.createDirectories(logs);
        copyProject(fixture);
        Path decoySource = launch.resolve(SERVER);
        Files.createDirectories(decoySource.getParent());
        Files.writeString(decoySource, DECOY_SERVER);
        Files.createDirectories(launch.resolve("frontend"));
        Files.writeString(launch.resolve("frontend").resolve("app.js"), "// decoy frontend\n");
        Files.createDirectories(launch.resolve("docs"));
        Files.writeString(launch.resolve("docs").resolve("decoy.md"), "decoy docs\n");
    }

    private Result subprocess(Path launchDirectory, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", System.getProperty("java.class.path"), "org.example.docsync.DocSyncCli"));
        command.addAll(List.of(args));
        Path outFile = Files.createTempFile(logs, "out-", ".txt");
        Path errFile = Files.createTempFile(logs, "err-", ".txt");
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(launchDirectory.toFile());
        builder.redirectOutput(outFile.toFile());
        builder.redirectError(errFile.toFile());
        Process process = builder.start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("the DocSyncCli process did not finish within 120 seconds: " + String.join(" ", args));
        }
        return new Result(process.exitValue(), new String(Files.readAllBytes(outFile), StandardCharsets.UTF_8),
            new String(Files.readAllBytes(errFile), StandardCharsets.UTF_8));
    }

    // A real child process from the launch directory, which must stay byte-identical (FR-E2-20).
    private Result cli(String... args) throws IOException, InterruptedException {
        Map<String, String> launchBefore = snapshot(launch);
        Result result = subprocess(launch, args);
        assertEquals(launchBefore, snapshot(launch), "the launch directory must stay byte-identical");
        return result;
    }

    private void syncFixture() {
        Result result = inProcess("sync", "--root", fixture.toString());
        assertEquals(0, result.exit(), result.err());
    }

    private Result verifyFixture() {
        return inProcess("verify", "--root", fixture.toString());
    }

    private String read(String relative) throws IOException {
        return Files.readString(fixture.resolve(relative));
    }

    private void edit(String relative, String from, String to) throws IOException {
        Path path = fixture.resolve(relative);
        String text = Files.readString(path);
        assertTrue(text.contains(from), "fixture must contain: " + from);
        Files.writeString(path, text.replace(from, to));
    }

    private Map<String, String> withoutReports() throws IOException {
        Map<String, String> state = snapshot(fixture);
        state.keySet().removeIf(key -> key.equals("docs/reports") || key.startsWith("docs/reports/"));
        return state;
    }

    private void assertNoAbsolutePath(String text) {
        assertFalse(text.contains(temp.toString()), "must not contain an absolute path: " + text);
        assertFalse(text.contains(temp.getFileName().toString()), "must not contain an absolute path: " + text);
    }

    private void assertExitTwo(Result result) {
        assertEquals(2, result.exit(), result.err());
        assertTrue(result.err().lines().anyMatch(line -> line.startsWith("error: ")), result.err());
        assertEquals("", result.out());
        assertNoAbsolutePath(result.err());
    }

    // The UI document is created together with the other three and is otherwise unchanged in these scenarios.
    private static String syncReport(String endpoints, String contract, String service) {
        boolean allCreated = endpoints.equals("created") && contract.equals("created") && service.equals("created");
        return "# DocSync Sync Report\n\nCommand: sync\nResult: SUCCESS\n\n| File | Status |\n|---|---|\n"
            + "| " + ENDPOINTS + " | " + endpoints + " |\n"
            + "| " + CONTRACT + " | " + contract + " |\n"
            + "| " + SERVICE + " | " + service + " |\n"
            + "| " + UI + " | " + (allCreated ? "created" : "unchanged") + " |\n";
    }

    // The real sources always carry the two known drift findings (AC-E3-04).
    private static String verifyReport(String result, String endpoints, String contract, String service) {
        return "# DocSync Verify Report\n\nCommand: verify\nResult: " + result + "\n\n| File | Status |\n|---|---|\n"
            + "| " + ENDPOINTS + " | " + endpoints + " |\n"
            + "| " + CONTRACT + " | " + contract + " |\n"
            + "| " + SERVICE + " | " + service + " |\n"
            + "| " + UI + " | up to date |\n"
            + "\n## Drift (report only)\n\n| UI field | Property | Frontend | Backend |\n|---|---|---|---|\n"
            + "| studentName | minLength | 2 | none |\n"
            + "| studentEmail | pattern | ^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$ | contains(\"@\") |\n";
    }

    private static String block(String document, String id) {
        String begin = "<!-- docsync:begin " + id + " -->\n";
        int start = document.indexOf(begin);
        assertTrue(start >= 0, "missing block " + id);
        int bodyStart = start + begin.length();
        int end = document.indexOf("<!-- docsync:end " + id + " -->", bodyStart);
        assertTrue(end > 0, "missing end of block " + id);
        return document.substring(bodyStart, end);
    }

    private static List<String> dataRows(String block) {
        return block.lines().filter(line -> line.startsWith("| ")).skip(1).toList();
    }

    // ---- real processes: exit 0 from another launch directory ----

    @Test
    void syncAndVerifyFromAnotherLaunchDirectoryTouchOnlyTheFixtureRoot() throws Exception {
        Result sync = cli("sync", "--root", fixture.toString());

        assertEquals(0, sync.exit(), sync.err());
        assertEquals("", sync.err());
        assertTrue(sync.out().contains("sync: created " + ENDPOINTS), sync.out());
        for (String path : List.of(ENDPOINTS, CONTRACT, SERVICE, UI, SYNC_REPORT)) {
            assertTrue(Files.isRegularFile(fixture.resolve(path)), path);
        }
        String endpoints = read(ENDPOINTS);
        for (String row : List.of(
            "| GET | /students | List all students | none | Student[] |",
            "| POST | /students | Create a student | StudentRequest | Student |",
            "| GET | /students/{id} | Fetch a student by id | none | Student |",
            "| DELETE | /students/{id} | Delete a student | none | DeleteResponse |")) {
            assertTrue(endpoints.contains(row), row);
        }
        assertFalse(endpoints.contains("/decoy"));
        assertEquals(syncReport("created", "created", "created"), read(SYNC_REPORT));

        // The relative root is resolved against the launch directory (FR-E2-20).
        Result verify = cli("verify", "--root", Path.of("..", "fixture").toString());

        assertEquals(0, verify.exit(), verify.err());
        assertEquals("", verify.err());
        assertTrue(verify.out().contains("verify: up to date"), verify.out());
        assertEquals(verifyReport("UP_TO_DATE", "up to date", "up to date", "up to date"), read(VERIFY_REPORT));
        assertFalse(Files.exists(launch.resolve("docs").resolve("api")));
    }

    // ---- real processes: exit 1 ----

    @Test
    void aStaleDocumentIsProcessExitOneAndVerifyNeverModifiesTheDocuments() throws Exception {
        syncFixture();
        edit(SERVER, "\"List all students\"", "\"List every student\"");
        Map<String, String> before = withoutReports();
        assertFalse(Files.exists(fixture.resolve(VERIFY_REPORT)));

        Result verify = cli("verify", "--root", fixture.toString());

        assertEquals(1, verify.exit(), verify.err());
        assertEquals("", verify.err());
        assertTrue(verify.out().contains("verify: STALE " + ENDPOINTS + " (api-routes)"), verify.out());
        assertEquals(verifyReport("STALE (exit 1)", "STALE (api-routes)", "up to date", "up to date"), read(VERIFY_REPORT));
        assertEquals(before, withoutReports(), "verify must leave the documents byte-identical");
    }

    // ---- real processes: exit 2 ----

    @Test
    void usageErrorsAndAnInvalidRootAreProcessExitTwoWithAnErrorLine() throws Exception {
        Path missing = temp.resolve("does-not-exist");
        List<String[]> cases = List.of(
            new String[0],
            new String[] {"publish", "--root", fixture.toString()},
            new String[] {"sync"},
            new String[] {"verify", "--root", missing.toString()});

        for (String[] args : cases) {
            Result result = cli(args);
            assertExitTwo(result);
        }
        assertFalse(Files.exists(missing));
        assertFalse(Files.exists(fixture.resolve("docs")));
    }

    @Test
    void aMarkerFaultIsProcessExitTwoForSyncAndVerifyAndTheFileIsUnchanged() throws Exception {
        syncFixture();
        edit(ENDPOINTS, "<!-- docsync:end api-errors -->", "");
        Map<String, String> before = withoutReports();

        Result sync = cli("sync", "--root", fixture.toString());

        assertExitTwo(sync);
        assertTrue(sync.err().contains(ENDPOINTS), sync.err());
        assertTrue(sync.err().contains("Marker integrity fault"), sync.err());
        assertEquals(before, withoutReports());
        assertTrue(read(SYNC_REPORT).contains("Result: FAILED (exit 2)"));

        Result verify = cli("verify", "--root", fixture.toString());

        assertExitTwo(verify);
        assertTrue(verify.err().contains(ENDPOINTS), verify.err());
        assertTrue(verify.err().contains("Marker integrity fault"), verify.err());
        assertEquals(before, withoutReports());
        assertTrue(read(VERIFY_REPORT).contains("Result: FAILED (exit 2)"));
        assertFalse(read(VERIFY_REPORT).contains("STALE"));
    }

    @Test
    void verifyWithAMissingDocumentIsProcessExitTwoAndCreatesNothing() throws Exception {
        syncFixture();
        Files.delete(fixture.resolve(CONTRACT));
        Map<String, String> before = withoutReports();

        Result verify = cli("verify", "--root", fixture.toString());

        assertExitTwo(verify);
        assertTrue(verify.err().contains(CONTRACT), verify.err());
        assertFalse(Files.exists(fixture.resolve(CONTRACT)));
        assertEquals(before, withoutReports());
        assertTrue(read(VERIFY_REPORT).contains("Result: FAILED (exit 2)"));
        assertFalse(read(VERIFY_REPORT).contains("STALE"));
    }

    @Test
    void verifyWithAMissingBlockIsProcessExitTwoAndRepairsNothing() throws Exception {
        syncFixture();
        Path endpoints = fixture.resolve(ENDPOINTS);
        String without = ERRORS_BLOCK.matcher(Files.readString(endpoints)).replaceFirst("");
        Files.writeString(endpoints, without);
        Map<String, String> before = withoutReports();

        Result verify = cli("verify", "--root", fixture.toString());

        assertExitTwo(verify);
        assertTrue(verify.err().contains(ENDPOINTS), verify.err());
        assertTrue(verify.err().contains("api-errors"), verify.err());
        assertEquals(before, withoutReports());
        assertEquals(without, Files.readString(endpoints));
        assertFalse(read(VERIFY_REPORT).contains("STALE"));
    }

    @Test
    void anUnsupportedConstructIsProcessExitTwoAndNeverStale() throws Exception {
        syncFixture();
        edit(SERVER, "\"Create a student\"", "SUMMARY");
        Map<String, String> before = withoutReports();

        Result sync = cli("sync", "--root", fixture.toString());

        assertExitTwo(sync);
        assertTrue(sync.err().contains(SERVER), sync.err());
        assertEquals(before, withoutReports());
        assertTrue(read(SYNC_REPORT).contains("Result: FAILED (exit 2)"));

        Result verify = cli("verify", "--root", fixture.toString());

        assertExitTwo(verify);
        assertTrue(verify.err().contains(SERVER), verify.err());
        assertEquals(before, withoutReports());
        assertTrue(read(VERIFY_REPORT).contains("Result: FAILED (exit 2)"));
        assertFalse(read(VERIFY_REPORT).contains("STALE"));
    }

    // ---- in-process: sync ----

    @Test
    void theSecondAndThirdSyncReportUnchangedAndAreByteIdentical() throws IOException {
        Result first = inProcess("sync", "--root", fixture.toString());
        assertEquals(0, first.exit(), first.err());
        assertEquals(syncReport("created", "created", "created"), read(SYNC_REPORT));
        Map<String, String> afterFirst = withoutReports();

        Result second = inProcess("sync", "--root", fixture.toString());
        assertEquals(0, second.exit(), second.err());
        assertEquals(syncReport("unchanged", "unchanged", "unchanged"), read(SYNC_REPORT));
        assertEquals(afterFirst, withoutReports());
        Map<String, String> afterSecond = snapshot(fixture);

        Result third = inProcess("sync", "--root", fixture.toString());
        assertEquals(0, third.exit(), third.err());
        assertEquals(afterSecond, snapshot(fixture));
    }

    @Test
    void aDeletedBlockIsAppendedBySyncAndReportedAsUpdated() throws IOException {
        syncFixture();
        Path endpoints = fixture.resolve(ENDPOINTS);
        Files.writeString(endpoints, ERRORS_BLOCK.matcher(Files.readString(endpoints)).replaceFirst(""));
        assertFalse(Files.readString(endpoints).contains("docsync:begin api-errors"));

        Result sync = inProcess("sync", "--root", fixture.toString());

        assertEquals(0, sync.exit(), sync.err());
        assertTrue(Files.readString(endpoints).contains("<!-- docsync:begin api-errors -->"));
        assertEquals(syncReport("updated", "unchanged", "unchanged"), read(SYNC_REPORT));
        assertEquals(0, verifyFixture().exit());
    }

    // ---- in-process: verify ----

    @Test
    void aStudentRecordChangeMakesTheContractDocStale() throws IOException {
        syncFixture();
        edit(STUDENT, "String phone", "String mobile");

        Result verify = verifyFixture();

        assertEquals(1, verify.exit(), verify.err());
        assertEquals(verifyReport("STALE (exit 1)", "up to date", "STALE (contract-student-fields)", "up to date"),
            read(VERIFY_REPORT));
    }

    @Test
    void aLogicOnlyServiceChangeMakesTheServiceDocStale() throws IOException {
        syncFixture();
        edit(SERVICE_SOURCE, "\"active\"", "\"enabled\"");

        Result verify = verifyFixture();

        assertEquals(1, verify.exit(), verify.err());
        assertEquals(verifyReport("STALE (exit 1)", "up to date", "up to date", "STALE (service-digest)"),
            read(VERIFY_REPORT));
    }

    @Test
    void aHandEditInsideABlockIsStale() throws IOException {
        syncFixture();
        edit(ENDPOINTS, "List all students", "Edited by hand");

        Result verify = verifyFixture();

        assertEquals(1, verify.exit(), verify.err());
        assertEquals(verifyReport("STALE (exit 1)", "STALE (api-routes)", "up to date", "up to date"), read(VERIFY_REPORT));
    }

    @Test
    void anEditOutsideTheBlocksKeepsExitZero() throws IOException {
        syncFixture();
        Path endpoints = fixture.resolve(ENDPOINTS);
        Files.writeString(endpoints, Files.readString(endpoints) + "\nManual note outside the blocks.\n");

        Result verify = verifyFixture();

        assertEquals(0, verify.exit(), verify.err());
        assertEquals(verifyReport("UP_TO_DATE", "up to date", "up to date", "up to date"), read(VERIFY_REPORT));
    }

    @Test
    void aCrlfConversionOfTheDocumentsKeepsExitZero() throws IOException {
        syncFixture();
        for (String doc : List.of(ENDPOINTS, CONTRACT, SERVICE, UI)) {
            edit(doc, "\n", "\r\n");
        }

        Result verify = verifyFixture();

        assertEquals(0, verify.exit(), verify.err());
        assertEquals(verifyReport("UP_TO_DATE", "up to date", "up to date", "up to date"), read(VERIFY_REPORT));
    }

    // ---- in-process: generated content (AC-E2-02) ----

    @Test
    void theGeneratedDocumentsCoverTheRoutesTheContractAndTheService() throws IOException {
        syncFixture();

        String endpoints = read(ENDPOINTS);
        assertEquals(List.of(
            "| GET | /students | List all students | none | Student[] |",
            "| POST | /students | Create a student | StudentRequest | Student |",
            "| GET | /students/{id} | Fetch a student by id | none | Student |",
            "| DELETE | /students/{id} | Delete a student | none | DeleteResponse |"),
            dataRows(block(endpoints, "api-routes")));

        String contract = read(CONTRACT);
        assertEquals(List.of("| id | String |", "| name | String |", "| email | String |",
            "| department | String |", "| phone | String |", "| status | String |"),
            dataRows(block(contract, "contract-student-fields")));
        assertEquals(List.of("| name | 1 |", "| email | 2 |", "| department | 3 |", "| phone | 4 |"),
            dataRows(block(contract, "contract-create-params")));

        String service = read(SERVICE);
        List<String> methods = dataRows(block(service, "service-methods"));
        List<String> names = List.of("createStudent", "createStudent", "getStudents", "getStudent", "deleteStudent",
            "validateStudentInput", "validateStudentInput");
        assertEquals(names.size(), methods.size());
        for (int i = 0; i < names.size(); i++) {
            assertTrue(methods.get(i).startsWith("| " + names.get(i) + " |"), methods.get(i));
        }
        String lfSource = new String(Files.readAllBytes(fixture.resolve(SERVICE_SOURCE)), StandardCharsets.UTF_8)
            .replace("\r\n", "\n").replace('\r', '\n');
        assertEquals("Source: `" + SERVICE_SOURCE + "` sha256: `" + sha256Hex(lfSource.getBytes(StandardCharsets.UTF_8)) + "`",
            block(service, "service-digest").strip());
    }

    // ---- in-process: the UI document (AC-E3-01 to AC-E3-04, AC-E3-09) ----

    @Test
    void theUiDocumentListsTheControlsTheRulesTheMappingAndTheDrift() throws IOException {
        syncFixture();

        String ui = read(UI);
        assertEquals(List.of(
            "| studentName | studentName | text | yes | none |",
            "| studentEmail | studentEmail | email | yes | none |",
            "| studentDepartment | studentDepartment | select | yes | none |",
            "| studentPhone | studentPhone | tel | no | 20 |"), dataRows(block(ui, "ui-controls").split("\n\nOptions of")[0]));
        assertTrue(block(ui, "ui-controls").contains("| \"\" | Select department |\n| Computer Science | Computer Science |\n| Mathematics | Mathematics |"));
        assertTrue(block(ui, "ui-controls").endsWith("Submit button: Add student\n"), block(ui, "ui-controls"));
        assertEquals(List.of(
            "| studentName | yes | 2 | none |",
            "| studentEmail | yes | none | ^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$ |",
            "| studentDepartment | yes | none | none |",
            "| studentPhone | no | none | ^(?=.{7,20}$)\\+?[0-9 -]+$ |"), dataRows(block(ui, "ui-rules")));
        assertEquals(List.of(
            "| studentName | name | studentName" + ARROW + "name |",
            "| studentEmail | email | studentEmail" + ARROW + "email |",
            "| studentDepartment | department | studentDepartment" + ARROW + "department |",
            "| studentPhone | phone | studentPhone" + ARROW + "phone |"), dataRows(block(ui, "ui-field-mapping")));
        assertEquals(List.of(
            "| studentName | minLength | 2 | none |",
            "| studentEmail | pattern | ^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$ | contains(\"@\") |"), dataRows(block(ui, "ui-drift")));
    }

    @Test
    void aHandEditInsideAUiBlockIsStaleAndAMissingUiDocumentOrBlockIsExitTwo() throws IOException {
        syncFixture();
        edit(UI, "Submit button: Add student", "Submit button: Edited");
        Result stale = verifyFixture();
        assertEquals(1, stale.exit(), stale.err());
        assertTrue(read(VERIFY_REPORT).contains("| " + UI + " | STALE (ui-controls) |"));

        syncFixture();
        assertEquals(0, verifyFixture().exit());
        Files.delete(fixture.resolve(UI));
        Result missing = verifyFixture();
        assertEquals(2, missing.exit(), missing.err());
        assertTrue(missing.err().contains(UI), missing.err());
        assertFalse(Files.exists(fixture.resolve(UI)));
    }
}
