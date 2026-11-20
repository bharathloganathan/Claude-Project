package org.example.docsync.analysis;

import org.example.docsync.error.UnsupportedConstructException;
import org.example.docsync.io.ProjectFiles;
import org.example.docsync.io.RootPaths;
import org.example.docsync.io.RootPaths.Name;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task RV5-02 and RV5-05 (S6): the validationRules keys, the payload formData keys and the HTML control names must
 * agree, the payload keys must equal the create form parameters, and duplicates are rejected. Fixtures are
 * copies of the real sources in a TempDir (read read-only through the project directory, the surefire working
 * directory) with one change each. This source file is ASCII only.
 */
class UiJoinTest {

    private static final String PHONE_CONTROL = "<input id=\"studentPhone\" name=\"studentPhone\" type=\"tel\" maxlength=\"20\" />";
    private static final String PHONE_PAYLOAD = "phone: formData.studentPhone";

    private static RootPaths copyOfTheRealSources(Path dir) throws IOException {
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

    private static void analyze(RootPaths roots) {
        new JavaAnalyzer(roots, new ProjectFiles(roots)).analyzeWithUi();
    }

    @Test
    void theUnchangedCopyOfTheRealSourcesAnalyzes(@TempDir Path dir) throws IOException {
        analyze(copyOfTheRealSources(dir));
    }

    static Stream<Arguments> disagreements() {
        return Stream.of(
            Arguments.of(Name.FRONTEND_HTML, "name=\"studentPhone\"", "name=\"studentTel\"", "frontend/app.js", "must agree"),
            Arguments.of(Name.FRONTEND_HTML, PHONE_CONTROL, "", "frontend/app.js", "must agree"),
            Arguments.of(Name.FRONTEND_HTML, PHONE_CONTROL, PHONE_CONTROL + "\n<input id=\"extra\" name=\"extra\" />", "frontend/app.js", "must agree"),
            Arguments.of(Name.FRONTEND_JS, "const validationRules = {\n", "const validationRules = {\n  studentExtra: { required: false },\n",
                "frontend/app.js", "must agree"),
            Arguments.of(Name.FRONTEND_JS, "studentPhone: {\n    required: false,", "studentFone: {\n    required: false,", "frontend/app.js", "must agree"),
            Arguments.of(Name.FRONTEND_JS, PHONE_PAYLOAD, "mobile: formData.studentPhone", "frontend/app.js", "payload keys must equal"),
            Arguments.of(Name.FRONTEND_JS, ",\n    " + PHONE_PAYLOAD, "", "frontend/app.js", "payload keys must equal"),
            Arguments.of(Name.FRONTEND_JS, PHONE_PAYLOAD, PHONE_PAYLOAD + ",\n    extra: formData.studentPhoneExtra",
                "frontend/app.js", "payload keys must equal"));
    }

    @ParameterizedTest
    @MethodSource("disagreements")
    void aDisagreementBetweenTheHtmlTheRulesAndThePayloadIsExitTwoNamingTheFile(
        Name source, String from, String to, String file, String fragment, @TempDir Path dir) throws IOException {
        RootPaths roots = copyOfTheRealSources(dir);
        edit(roots, source, from, to);

        UnsupportedConstructException e = assertThrows(UnsupportedConstructException.class, () -> analyze(roots));

        assertTrue(e.getMessage().contains(file), e.getMessage());
        assertTrue(e.getMessage().contains(fragment), e.getMessage());
        assertFalse(e.getMessage().contains(dir.toString()), e.getMessage());
    }

    static Stream<Arguments> duplicates() {
        return Stream.of(
            Arguments.of(Name.FRONTEND_HTML, PHONE_CONTROL, PHONE_CONTROL + "\n" + PHONE_CONTROL, "frontend/index.html", "duplicate control id studentPhone"),
            Arguments.of(Name.FRONTEND_HTML, PHONE_CONTROL,
                PHONE_CONTROL + "\n<input id=\"other\" name=\"studentPhone\" />", "frontend/index.html", "duplicate control name studentPhone"),
            Arguments.of(Name.FRONTEND_JS, "const validationRules = {\n",
                "const validationRules = {\n  studentName: { required: true },\n", "frontend/app.js", "validationRules key studentName is duplicated"),
            Arguments.of(Name.FRONTEND_JS, PHONE_PAYLOAD, PHONE_PAYLOAD + ",\n    phone: formData.studentName", "frontend/app.js",
                "payload key phone is duplicated"),
            Arguments.of(Name.FRONTEND_JS, PHONE_PAYLOAD, PHONE_PAYLOAD + ",\n    other: formData.studentPhone", "frontend/app.js",
                "formData.studentPhone is read by more than one payload key"));
    }

    @ParameterizedTest
    @MethodSource("duplicates")
    void aDuplicateControlRuleKeyOrPayloadTargetIsExitTwoNamingTheDuplicate(
        Name source, String from, String to, String file, String fragment, @TempDir Path dir) throws IOException {
        RootPaths roots = copyOfTheRealSources(dir);
        edit(roots, source, from, to);

        UnsupportedConstructException e = assertThrows(UnsupportedConstructException.class, () -> analyze(roots));

        assertTrue(e.getMessage().contains(file), e.getMessage());
        assertTrue(e.getMessage().contains(fragment), e.getMessage());
        assertFalse(Files.exists(dir.resolve("docs")), "analysis must write nothing");
    }
}
