package org.example.docsync.engine;

import org.example.docsync.DocSyncCli;
import org.example.docsync.io.RootPaths;
import org.example.docsync.io.RootPaths.Name;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task E3-3 and E3-4 (FR-E3-05 to FR-E3-07, AC-E3-04 to AC-E3-06): drift through the whole CLI on a fixture root.
 * The real sources are read read-only through the project directory (the surefire working directory).
 */
class DriftEndToEndTest {

    private static final String UI = "docs/ui/student-ui.md";
    private static final String TWO_FINDINGS = "| studentName | minLength | 2 | none |\n"
        + "| studentEmail | pattern | ^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$ | contains(\"@\") |\n";

    @TempDir
    Path dir;

    private RootPaths roots;
    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
    private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

    @BeforeEach
    void copyTheSources() throws IOException {
        roots = RootPaths.of(dir.toString());
        for (Name name : List.of(Name.API_ROUTES_SOURCE, Name.ERROR_CODE_SOURCE, Name.STUDENT_RECORD_SOURCE,
            Name.SERVICE_SOURCE, Name.FRONTEND_HTML, Name.FRONTEND_JS)) {
            Path target = roots.path(name);
            Files.createDirectories(target.getParent());
            Files.copy(Path.of(roots.display(name)), target);
        }
    }

    private int run(String command) {
        outBytes.reset();
        return DocSyncCli.run(new String[] {command, "--root", dir.toString()}, out, err);
    }

    private void edit(Name name, String from, String to) throws IOException {
        Path path = roots.path(name);
        String text = Files.readString(path).replace("\r\n", "\n");
        assertTrue(text.contains(from), "fixture must contain: " + from);
        Files.writeString(path, text.replace(from, to));
    }

    private String verifyReport() throws IOException {
        return Files.readString(roots.path(Name.REPORT_VERIFY));
    }

    @Test
    void aFreshFixtureGivesExactlyTheTwoFindingsInTheReportTheUiDocAndTheOutputWithExitZero() throws IOException {
        assertEquals(0, run("sync"));
        assertEquals(0, run("verify"));

        assertTrue(verifyReport().endsWith("\n## Drift (report only)\n\n| UI field | Property | Frontend | Backend |\n|---|---|---|---|\n" + TWO_FINDINGS));
        assertTrue(Files.readString(dir.resolve(UI)).contains("\n" + TWO_FINDINGS));
        String stdout = outBytes.toString(StandardCharsets.UTF_8);
        assertEquals(2, stdout.lines().filter(line -> line.startsWith("verify: DRIFT ")).count(), stdout);
        assertFalse(verifyReport().contains("STALE"));
    }

    @Test
    void changingAFrontendRequiredMinLengthOrPatternCreatesANewFindingAndTheUiDocIsStaleUntilTheNextSync() throws IOException {
        assertEquals(0, run("sync"));
        edit(Name.FRONTEND_JS, "studentDepartment: {\n    required: true,", "studentDepartment: {\n    required: false,");
        edit(Name.FRONTEND_JS, "required: false,\n    pattern: /^(?=.{7,20}$)\\+?[0-9 -]+$/,",
            "required: false,\n    minLength: 7,\n    pattern: /^(?=.{7,20}$)\\+?[0-9]+$/,");

        assertEquals(1, run("verify"));

        String report = verifyReport();
        assertTrue(report.contains("| " + UI + " | STALE (ui-rules, ui-drift) |"), report);
        assertTrue(report.contains("| studentDepartment | required | false | true |"), report);
        assertTrue(report.contains("| studentPhone | minLength | 7 | none |"), report);
        assertTrue(report.contains("| studentPhone | pattern | ^(?=.{7,20}$)\\+?[0-9]+$ | (?=.{7,20}$)\\+?[0-9 -]+ |"), report);

        assertEquals(0, run("sync"));
        assertEquals(0, run("verify"));
        assertEquals(5, verifyReport().lines().filter(line -> line.startsWith("| student")).count());
    }

    @Test
    void changingOnlyMessagesChangesNeitherTheDocumentsNorTheDrift() throws IOException {
        assertEquals(0, run("sync"));
        String before = Files.readString(dir.resolve(UI));
        edit(Name.FRONTEND_JS, "'Department is required.'", "'Pick a department.'");
        edit(Name.FRONTEND_JS, "'Student name must be at least 2 characters.'", "'Name too short.'");

        assertEquals(0, run("verify"));

        assertEquals(before, Files.readString(dir.resolve(UI)));
        assertTrue(verifyReport().endsWith(TWO_FINDINGS));
    }

    @Test
    void changingOnlyMaxlengthTypeOrOptionsChangesTheUiDocButCreatesNoFinding() throws IOException {
        assertEquals(0, run("sync"));
        edit(Name.FRONTEND_HTML, "maxlength=\"20\"", "maxlength=\"30\"");
        edit(Name.FRONTEND_HTML, "type=\"tel\"", "type=\"text\"");
        edit(Name.FRONTEND_HTML, "<option value=\"Mathematics\">Mathematics</option>",
            "<option value=\"Mathematics\">Mathematics</option>\n          <option value=\"Physics\">Physics</option>");

        assertEquals(1, run("verify"));

        assertTrue(verifyReport().contains("| " + UI + " | STALE (ui-controls) |"), verifyReport());
        assertTrue(verifyReport().endsWith(TWO_FINDINGS));
        assertEquals(0, run("sync"));
        assertEquals(0, run("verify"));
        assertTrue(verifyReport().endsWith(TWO_FINDINGS));
    }

    @Test
    void aTemplateLiteralInARuleOrAMismatchedFieldIsExitTwoWithAMessageOnStderr() throws IOException {
        assertEquals(0, run("sync"));
        edit(Name.FRONTEND_JS, "'Email must be a valid address.'", "`Email ${1}`");
        assertEquals(2, run("verify"));
        assertTrue(errBytes.toString(StandardCharsets.UTF_8).contains("frontend/app.js"), errBytes.toString(StandardCharsets.UTF_8));
        edit(Name.FRONTEND_JS, "`Email ${1}`", "'Email must be a valid address.'");
        assertEquals(0, run("verify"));

        edit(Name.FRONTEND_JS, "email: formData.studentEmail", "email: formData.studentEmailAddress");
        assertEquals(2, run("sync"));
        assertTrue(errBytes.toString(StandardCharsets.UTF_8).contains("must agree"), errBytes.toString(StandardCharsets.UTF_8));
    }
}
