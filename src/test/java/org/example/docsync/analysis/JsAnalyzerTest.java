package org.example.docsync.analysis;

import org.example.docsync.error.MissingExpectedException;
import org.example.docsync.error.UnsupportedConstructException;
import org.example.docsync.io.ProjectFiles;
import org.example.docsync.io.RootPaths;
import org.example.docsync.model.SourceModel.FrontendRule;
import org.example.docsync.model.SourceModel.PayloadEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task E3-2 and RV5-02, RV5-05 (FR-E3-03, FR-E3-04, AC-E3-02, AC-E3-03, AC-E3-08, S3): only the validationRules
 * object and the first URLSearchParams object of app.js are lexed. The real app.js is read read-only through the
 * project directory (the surefire working directory); fixtures use a TempDir. This source file is ASCII only.
 */
class JsAnalyzerTest {

    private static final String JS_FILE = "frontend/app.js";
    private static final String BOM = String.valueOf((char) 0xFEFF);
    private static final String PAYLOAD = "  name: formData.studentName,\n  email: formData.studentEmail";
    private static final String RULES = "  studentName: { required: true, minLength: 2, message: 'Name.' },\n"
        + "  studentEmail: { required: true, pattern: /^a@b$/, message: 'Email.' }";

    private static String script(String rules, String payload) {
        return "// header comment\nconst validationRules = {\n" + rules + "\n};\n\n"
            + "function later() { const t = `template ${1} outside the rules`; return t; }\n\n"
            + "const payload = new URLSearchParams({\n" + payload + "\n});\n";
    }

    private static RootPaths fixture(Path dir, String js) throws IOException {
        RootPaths roots = RootPaths.of(dir.toString());
        Path file = roots.path(RootPaths.Name.FRONTEND_JS);
        Files.createDirectories(file.getParent());
        Files.writeString(file, js, StandardCharsets.UTF_8);
        return roots;
    }

    private static JsAnalyzer.Result analyze(RootPaths roots) {
        return new JsAnalyzer(roots, new ProjectFiles(roots)).analyze();
    }

    private static UnsupportedConstructException rejected(Path dir, String js, String fragment) throws IOException {
        RootPaths roots = fixture(dir, js);
        UnsupportedConstructException e = assertThrows(UnsupportedConstructException.class, () -> analyze(roots));
        assertTrue(e.getMessage().contains(JS_FILE), e.getMessage());
        assertTrue(e.getMessage().contains(fragment), "message should contain [" + fragment + "]: " + e.getMessage());
        assertFalse(e.getMessage().contains(dir.toString()), e.getMessage());
        return e;
    }

    @Test
    void theRealAppJsHasTheFourValidationRulesAndThePayloadMapping() {
        JsAnalyzer.Result result = analyze(RootPaths.of("."));

        assertEquals(List.of(
            new FrontendRule("studentName", true, 2, null, "Student name must be at least 2 characters."),
            new FrontendRule("studentEmail", true, null, "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", "Email must be a valid address."),
            new FrontendRule("studentDepartment", true, null, null, "Department is required."),
            new FrontendRule("studentPhone", false, null, "^(?=.{7,20}$)\\+?[0-9 -]+$",
                "Phone must contain 7 to 20 digits, spaces or hyphens, with an optional leading +.")), result.rules());
        assertEquals(List.of(
            new PayloadEntry("name", "studentName"), new PayloadEntry("email", "studentEmail"),
            new PayloadEntry("department", "studentDepartment"), new PayloadEntry("phone", "studentPhone")), result.payload());
    }

    @Test
    void aFixtureIsReadInSourceOrderAndTheRestOfTheFileIsSkipped(@TempDir Path dir) throws IOException {
        JsAnalyzer.Result result = analyze(fixture(dir, script(RULES, PAYLOAD)));

        assertEquals(List.of(
            new FrontendRule("studentName", true, 2, null, "Name."),
            new FrontendRule("studentEmail", true, null, "^a@b$", "Email.")), result.rules());
        assertEquals(List.of(new PayloadEntry("name", "studentName"), new PayloadEntry("email", "studentEmail")), result.payload());
    }

    @Test
    void commentsAndTrailingCommasAndEscapedQuotesAreAccepted(@TempDir Path dir) throws IOException {
        String rules = "  /* block */ a: { // line\n    required: false,\n    pattern: /[/]x\\/y/,\n    message: 'it\\'s',\n  },\n";
        JsAnalyzer.Result result = analyze(fixture(dir, script(rules, "  n: formData.a,")));

        assertEquals(List.of(new FrontendRule("a", false, null, "[/]x\\/y", "it's")), result.rules());
        assertEquals(List.of(new PayloadEntry("n", "a")), result.payload());
    }

    @Test
    void aTemplateLiteralInAValidationRuleIsRejected(@TempDir Path dir) throws IOException {
        rejected(dir, script("  a: { required: true, message: `must be ${2}` }", "  n: formData.a"), "template literal");
    }

    static Stream<Arguments> unsupportedRules() {
        return Stream.of(
            Arguments.of("  a: { required: true, pattern: /x/i }", "flags"),
            Arguments.of("  a: { required: true, message: \"double\" }", "unsupported value"),
            Arguments.of("  a: { required: true, maxLength: 3 }", "unsupported property maxLength"),
            Arguments.of("  a: { required: true, minLength: 2.5 }", "must be an integer"),
            Arguments.of("  a: { required: true, minLength: -1 }", "unsupported value"),
            Arguments.of("  a: { required: 'yes' }", "wrong kind"),
            Arguments.of("  a: { required: maybe }", "unsupported value"),
            Arguments.of("  a: { required: true, minLength: true }", "wrong kind"),
            Arguments.of("  a: { required: true, pattern: 'x' }", "wrong kind"),
            Arguments.of("  a: { required: true, message: 'x\\n' }", "escape sequence"),
            Arguments.of("  a: { required: true, message: 'unterminated }", "not terminated"),
            Arguments.of("  a: { required: true", "was expected"),
            Arguments.of("  a: required", "was expected"));
    }

    @ParameterizedTest
    @MethodSource("unsupportedRules")
    void anUnsupportedRuleShapeIsRejectedForTheStatedReason(String rules, String fragment, @TempDir Path dir) throws IOException {
        rejected(dir, script(rules, "  n: formData.a"), fragment);
    }

    @Test
    void aMissingValidationRulesObjectOrPayloadIsRejected(@TempDir Path dir) throws IOException {
        rejected(dir, "const rules = {};\nconst p = new URLSearchParams({ n: formData.a });\n", "const validationRules is missing");
        Path second = Files.createDirectories(dir.resolve("second"));
        rejected(second, "const validationRules = {\n  a: { required: true }\n};\nconst p = {};\n", "URLSearchParams");
    }

    static Stream<Arguments> unsupportedPayloads() {
        return Stream.of(
            Arguments.of("  n: 'literal'", "must be formData.<key>"),
            Arguments.of("  n: formData['a']", "must be formData.<key>"),
            Arguments.of("  n: other.a", "must be formData.<key>"),
            Arguments.of("  n: formData.a.b", "was expected"),
            Arguments.of("  n formData.a", "was expected"));
    }

    @ParameterizedTest
    @MethodSource("unsupportedPayloads")
    void aPayloadValueThatIsNotFormDataKeyIsRejectedForTheStatedReason(String payload, String fragment, @TempDir Path dir)
        throws IOException {
        rejected(dir, script("  a: { required: true }", payload), fragment);
    }

    @Test
    void aDuplicateRuleKeyOrPayloadEntryIsRejected(@TempDir Path dir) throws IOException {
        rejected(dir, script("  a: { required: true },\n  a: { required: false }", "  n: formData.a"), "validationRules key a is duplicated");
        Path second = Files.createDirectories(dir.resolve("second"));
        rejected(second, script("  a: { required: true },\n  b: { required: true }", "  n: formData.a,\n  n: formData.b"),
            "payload key n is duplicated");
        Path third = Files.createDirectories(dir.resolve("third"));
        rejected(third, script("  a: { required: true }", "  n: formData.a,\n  m: formData.a"),
            "formData.a is read by more than one payload key");
    }

    @Test
    void aMissingAppJsIsReportedAsMissing(@TempDir Path dir) {
        RootPaths roots = RootPaths.of(dir.toString());
        MissingExpectedException e = assertThrows(MissingExpectedException.class, () -> analyze(roots));
        assertTrue(e.getMessage().contains(JS_FILE), e.getMessage());
        assertFalse(e.getMessage().contains(dir.toString()), e.getMessage());
    }

    @Test
    void aLeadingBomAndCrlfDoNotChangeTheResult(@TempDir Path dir) throws IOException {
        JsAnalyzer.Result plain = analyze(fixture(dir, script(RULES, PAYLOAD)));
        Path other = Files.createDirectories(dir.resolve("crlf"));
        JsAnalyzer.Result bom = analyze(fixture(other, BOM + script(RULES, PAYLOAD).replace("\n", "\r\n")));
        assertEquals(plain, bom);
    }
}
