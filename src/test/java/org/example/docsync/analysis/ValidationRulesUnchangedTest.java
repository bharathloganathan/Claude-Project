package org.example.docsync.analysis;

import org.example.docsync.io.ProjectFiles;
import org.example.docsync.io.RootPaths;
import org.example.docsync.model.RuleSpec;
import org.example.docsync.model.RuleSpec.Kind;
import org.example.docsync.model.SourceModel;
import org.example.docsync.model.SourceModel.FrontendRule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Task E3-3 (FR-E3-08, AC-E3-07): the existing validation rules are reported, never changed. This golden check
 * pins the validationRules source text of app.js and the rules both analyzers read from the real sources. The
 * backend behavior itself (a 1-character name and a@b are accepted) is pinned by ServiceCharacterizationTest.
 */
class ValidationRulesUnchangedTest {

    private static final String GOLDEN_RULES = "const validationRules = {\n"
        + "  studentName: {\n    required: true,\n    minLength: 2,\n    message: 'Student name must be at least 2 characters.'\n  },\n"
        + "  studentEmail: {\n    required: true,\n    pattern: /^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$/,\n    message: 'Email must be a valid address.'\n  },\n"
        + "  studentDepartment: {\n    required: true,\n    message: 'Department is required.'\n  },\n"
        + "  studentPhone: {\n    required: false,\n    pattern: /^(?=.{7,20}$)\\+?[0-9 -]+$/,\n"
        + "    message: 'Phone must contain 7 to 20 digits, spaces or hyphens, with an optional leading +.'\n  }\n};";

    @Test
    void theValidationRulesTextOfAppJsIsUnchanged() throws IOException {
        String script = Files.readString(Path.of("frontend", "app.js"), StandardCharsets.UTF_8).replace("\r\n", "\n");
        int start = script.indexOf("const validationRules");
        int end = script.indexOf("\n};", start) + 3;

        assertEquals(GOLDEN_RULES, script.substring(start, end));
    }

    @Test
    void theFrontendAndBackendRulesReadFromTheRealSourcesAreUnchanged() {
        RootPaths roots = RootPaths.of(".");

        SourceModel model = new JavaAnalyzer(roots, new ProjectFiles(roots)).analyzeWithUi();

        assertEquals(List.of(
            new FrontendRule("studentName", true, 2, null, "Student name must be at least 2 characters."),
            new FrontendRule("studentEmail", true, null, "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", "Email must be a valid address."),
            new FrontendRule("studentDepartment", true, null, null, "Department is required."),
            new FrontendRule("studentPhone", false, null, "^(?=.{7,20}$)\\+?[0-9 -]+$",
                "Phone must contain 7 to 20 digits, spaces or hyphens, with an optional leading +.")), model.frontendRules());
        assertEquals(List.of(
            new RuleSpec("name", true, Kind.NONE, null),
            new RuleSpec("email", true, Kind.CONTAINS, "@"),
            new RuleSpec("department", true, Kind.NONE, null),
            new RuleSpec("phone", false, Kind.REGEX, "(?=.{7,20}$)\\+?[0-9 -]+")), model.backendRules());
    }
}
