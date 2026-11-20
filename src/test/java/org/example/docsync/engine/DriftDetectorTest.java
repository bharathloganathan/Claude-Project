package org.example.docsync.engine;

import org.example.docsync.analysis.JavaAnalyzer;
import org.example.docsync.io.ProjectFiles;
import org.example.docsync.io.RootPaths;
import org.example.docsync.io.RootPaths.Name;
import org.example.docsync.model.Findings.DriftFinding;
import org.example.docsync.model.RuleSpec;
import org.example.docsync.model.RuleSpec.Kind;
import org.example.docsync.model.SourceModel;
import org.example.docsync.model.SourceModel.FrontendRule;
import org.example.docsync.model.SourceModel.PayloadEntry;
import org.example.docsync.model.SourceModel.UiForm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task E3-3 (FR-E3-05, FR-E3-06, AC-E3-04, AC-E3-05, S5, S9): drift is reported per field for required, minLength
 * and pattern only. The real sources are read read-only through the project directory (the surefire working
 * directory); fixtures use a TempDir.
 */
class DriftDetectorTest {

    private static final String EMAIL_REGEX = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";

    private static SourceModel model(List<FrontendRule> frontend, List<RuleSpec> backend) {
        return new SourceModel(List.of(), List.of(), List.of(), List.of("name", "email"), List.of(), "digest")
            .withUi(new UiForm(List.of(), "Go"), frontend, List.of(new PayloadEntry("name", "studentName"),
                new PayloadEntry("email", "studentEmail")), backend);
    }

    private static FrontendRule rule(String key, boolean required, Integer minLength, String pattern, String message) {
        return new FrontendRule(key, required, minLength, pattern, message);
    }

    private static final RuleSpec NAME_OK = new RuleSpec("name", true, Kind.NONE, null);
    private static final RuleSpec EMAIL_OK = new RuleSpec("email", true, Kind.REGEX, "a@b");

    private static List<DriftFinding> detect(FrontendRule name, FrontendRule email, RuleSpec backendName, RuleSpec backendEmail) {
        return DriftDetector.detect(model(List.of(name, email), List.of(backendName, backendEmail)));
    }

    private static final FrontendRule NAME_FE = rule("studentName", true, null, null, "m");
    private static final FrontendRule EMAIL_FE = rule("studentEmail", true, null, "^a@b$", "m");

    @Test
    void identicalRulesGiveNoDrift() {
        assertEquals(List.of(), detect(NAME_FE, EMAIL_FE, NAME_OK, EMAIL_OK));
    }

    @Test
    void aModelWithoutTheUiPartsGivesNoDrift() {
        assertEquals(List.of(), DriftDetector.detect(new SourceModel(List.of(), List.of(), List.of(), List.of(), List.of(), "d")));
    }

    @Test
    void aRequiredDifferenceIsReported() {
        assertEquals(List.of(new DriftFinding("studentName", "required", "false", "true")),
            detect(rule("studentName", false, null, null, "m"), EMAIL_FE, NAME_OK, EMAIL_OK));
    }

    @Test
    void aFrontendMinLengthIsReportedBecauseTheBackendHasNone() {
        assertEquals(List.of(new DriftFinding("studentName", "minLength", "2", "none")),
            detect(rule("studentName", true, 2, null, "m"), EMAIL_FE, NAME_OK, EMAIL_OK));
    }

    @Test
    void thePatternsAreComparedAfterStrippingOneLeadingAndOneTrailingAnchor() {
        assertEquals(List.of(), detect(NAME_FE, rule("studentEmail", true, null, "^a@b$", "m"), NAME_OK, EMAIL_OK));
        assertEquals(List.of(), detect(NAME_FE, rule("studentEmail", true, null, "a@b", "m"), NAME_OK, EMAIL_OK));
        assertEquals(List.of(new DriftFinding("studentEmail", "pattern", "^^a@b$$", "a@b")),
            detect(NAME_FE, rule("studentEmail", true, null, "^^a@b$$", "m"), NAME_OK, EMAIL_OK));
        assertEquals(List.of(new DriftFinding("studentEmail", "pattern", "^a@c$", "a@b")),
            detect(NAME_FE, rule("studentEmail", true, null, "^a@c$", "m"), NAME_OK, EMAIL_OK));
    }

    @Test
    void aBackendContainsRuleDiffersFromAnyFrontendPatternAndFromNone() {
        RuleSpec contains = new RuleSpec("email", true, Kind.CONTAINS, "@");
        assertEquals(List.of(new DriftFinding("studentEmail", "pattern", EMAIL_REGEX, "contains(\"@\")")),
            detect(NAME_FE, rule("studentEmail", true, null, EMAIL_REGEX, "m"), NAME_OK, contains));
        assertEquals(List.of(new DriftFinding("studentEmail", "pattern", "none", "contains(\"@\")")),
            detect(NAME_FE, rule("studentEmail", true, null, null, "m"), NAME_OK, contains));
    }

    @Test
    void aFrontendPatternWithNoBackendPatternIsReported() {
        assertEquals(List.of(new DriftFinding("studentName", "pattern", "^x$", "none")),
            detect(rule("studentName", true, null, "^x$", "m"), EMAIL_FE, NAME_OK, EMAIL_OK));
    }

    @Test
    void messageTextAloneNeverCounts() {
        assertEquals(List.of(), detect(rule("studentName", true, null, null, "another message"),
            rule("studentEmail", true, null, "^a@b$", "text"), NAME_OK, EMAIL_OK));
    }

    @Test
    void findingsFollowTheFormParameterOrderAndTheRequiredMinLengthPatternOrder() {
        List<DriftFinding> findings = detect(rule("studentName", false, 3, "^x$", "m"), rule("studentEmail", false, null, null, "m"),
            NAME_OK, EMAIL_OK);

        assertEquals(List.of(
            new DriftFinding("studentName", "required", "false", "true"),
            new DriftFinding("studentName", "minLength", "3", "none"),
            new DriftFinding("studentName", "pattern", "^x$", "none"),
            new DriftFinding("studentEmail", "required", "false", "true"),
            new DriftFinding("studentEmail", "pattern", "none", "a@b")), findings);
    }

    // ---- the real sources and fixtures derived from them ----

    private static List<DriftFinding> analyzeRealProject() {
        RootPaths roots = RootPaths.of(".");
        return DriftDetector.detect(new JavaAnalyzer(roots, new ProjectFiles(roots)).analyzeWithUi());
    }

    @Test
    void theRealSourcesGiveExactlyTheTwoExpectedFindings() {
        assertEquals(List.of(
            new DriftFinding("studentName", "minLength", "2", "none"),
            new DriftFinding("studentEmail", "pattern", EMAIL_REGEX, "contains(\"@\")")), analyzeRealProject());
    }

    private static RootPaths copyOfRealFrontendAndSources(Path dir) throws IOException {
        RootPaths roots = RootPaths.of(dir.toString());
        for (Name name : List.of(Name.API_ROUTES_SOURCE, Name.ERROR_CODE_SOURCE, Name.STUDENT_RECORD_SOURCE,
            Name.SERVICE_SOURCE, Name.FRONTEND_HTML, Name.FRONTEND_JS)) {
            Path target = roots.path(name);
            Files.createDirectories(target.getParent());
            Files.copy(Path.of(roots.display(name)), target);
        }
        return roots;
    }

    private static void edit(RootPaths roots, Name name, String from, String to) throws IOException {
        Path path = roots.path(name);
        String text = Files.readString(path).replace("\r\n", "\n");
        assertTrue(text.contains(from), "fixture must contain: " + from);
        Files.writeString(path, text.replace(from, to));
    }

    private static List<DriftFinding> findings(RootPaths roots) {
        return DriftDetector.detect(new JavaAnalyzer(roots, new ProjectFiles(roots)).analyzeWithUi());
    }

    @Test
    void changingAFrontendRequiredMinLengthOrPatternCreatesANewFinding(@TempDir Path dir) throws IOException {
        RootPaths roots = copyOfRealFrontendAndSources(dir);
        List<DriftFinding> baseline = findings(roots);

        edit(roots, Name.FRONTEND_JS, "studentDepartment: {\n    required: true,", "studentDepartment: {\n    required: false,");
        edit(roots, Name.FRONTEND_JS, "required: false,\n    pattern: /^(?=.{7,20}$)\\+?[0-9 -]+$/,",
            "required: false,\n    minLength: 7,\n    pattern: /^(?=.{7,20}$)\\+?[0-9]+$/,");

        List<DriftFinding> changed = findings(roots);

        assertEquals(baseline.size() + 3, changed.size());
        assertTrue(changed.contains(new DriftFinding("studentDepartment", "required", "false", "true")));
        assertTrue(changed.contains(new DriftFinding("studentPhone", "minLength", "7", "none")));
        assertTrue(changed.contains(new DriftFinding("studentPhone", "pattern", "^(?=.{7,20}$)\\+?[0-9]+$", "(?=.{7,20}$)\\+?[0-9 -]+")));
    }

    @Test
    void changingOnlyMessagesOptionsMaxlengthOrTypeCreatesNoFinding(@TempDir Path dir) throws IOException {
        RootPaths roots = copyOfRealFrontendAndSources(dir);
        List<DriftFinding> baseline = findings(roots);

        edit(roots, Name.FRONTEND_JS, "'Department is required.'", "'Pick a department.'");
        edit(roots, Name.FRONTEND_JS, "'Student name must be at least 2 characters.'", "'Name too short.'");
        edit(roots, Name.FRONTEND_HTML, "maxlength=\"20\"", "maxlength=\"30\"");
        edit(roots, Name.FRONTEND_HTML, "type=\"tel\"", "type=\"text\"");
        edit(roots, Name.FRONTEND_HTML, "<option value=\"Mathematics\">Mathematics</option>",
            "<option value=\"Mathematics\">Mathematics</option>\n          <option value=\"Physics\">Physics</option>");

        assertEquals(baseline, findings(roots));
    }
}
