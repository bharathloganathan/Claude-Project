package org.example.docsync.analysis;

import org.example.docsync.error.UnsupportedConstructException;
import org.example.docsync.io.ProjectFiles;
import org.example.docsync.io.RootPaths;
import org.example.docsync.io.RootPaths.Name;
import org.example.docsync.model.RuleSpec;
import org.example.docsync.model.RuleSpec.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task E3-3 (FR-E3-09, AC-E3-04, S4): the backend rules come from the Java source of validateStudentInput. The
 * real sources are read read-only through the project directory (the surefire working directory); fixtures use
 * a TempDir with copies of the real files and a fixture StudentService.
 */
class BackendRulesTest {

    private static final String SERVICE_FILE = "src/main/java/org/example/service/StudentService.java";
    private static final String PHONE_REGEX = "(?=.{7,20}$)\\+?[0-9 -]+";

    private static final List<RuleSpec> EXPECTED = List.of(
        new RuleSpec("name", true, Kind.NONE, null),
        new RuleSpec("email", true, Kind.CONTAINS, "@"),
        new RuleSpec("department", true, Kind.NONE, null),
        new RuleSpec("phone", false, Kind.REGEX, PHONE_REGEX));

    private static final String NAME_RULE = "        if (name == null || name.isBlank()) {\n"
        + "            throw new IllegalArgumentException(\"Student name is required.\");\n        }\n";
    private static final String EMAIL_RULE = "        if (email == null || !email.contains(\"@\")) {\n"
        + "            throw new IllegalArgumentException(\"Email must contain '@'.\");\n        }\n";
    private static final String DEPARTMENT_RULE = "        if (department == null || department.isBlank()) {\n"
        + "            throw new IllegalArgumentException(\"Department is required.\");\n        }\n";
    private static final String PHONE_RULE = "        if (phone == null || (!phone.isBlank() && !phone.trim().matches(\"(?=.{7,20}$)\\\\+?[0-9 -]+\"))) {\n"
        + "            throw new IllegalArgumentException(\"Invalid phone number.\");\n        }\n";
    private static final String ALL_RULES = NAME_RULE + EMAIL_RULE + DEPARTMENT_RULE + PHONE_RULE;
    private static final String THREE_ARGUMENTS = "    public void validateStudentInput(String name, String email, String department) {\n"
        + "        validateStudentInput(name, email, department, \"\");\n    }\n";

    private static String service(String rules, String overload) {
        return "package fixture;\n\npublic class StudentService {\n"
            + "    public void createStudent(String a, String b, String c, String d) {\n    }\n\n"
            + overload + "\n"
            + "    public void validateStudentInput(String name, String email, String department, String phone) {\n"
            + rules + "    }\n}\n";
    }

    private static RootPaths fixture(Path dir, String serviceSource) throws IOException {
        RootPaths roots = RootPaths.of(dir.toString());
        for (Name name : List.of(Name.API_ROUTES_SOURCE, Name.ERROR_CODE_SOURCE, Name.STUDENT_RECORD_SOURCE,
            Name.FRONTEND_HTML, Name.FRONTEND_JS)) {
            Path target = roots.path(name);
            Files.createDirectories(target.getParent());
            Files.copy(Path.of(roots.display(name)), target);
        }
        Path service = roots.path(Name.SERVICE_SOURCE);
        Files.createDirectories(service.getParent());
        Files.writeString(service, serviceSource, StandardCharsets.UTF_8);
        return roots;
    }

    private static List<RuleSpec> rules(RootPaths roots) {
        return new JavaAnalyzer(roots, new ProjectFiles(roots)).analyzeWithUi().backendRules();
    }

    private static void assertRejected(Path dir, String serviceSource, String fragment) throws IOException {
        RootPaths roots = fixture(dir, serviceSource);
        UnsupportedConstructException e = assertThrows(UnsupportedConstructException.class, () -> rules(roots));
        assertTrue(e.getMessage().contains(SERVICE_FILE), e.getMessage());
        assertTrue(e.getMessage().contains(fragment), e.getMessage());
        assertFalse(e.getMessage().contains(dir.toString()), e.getMessage());
    }

    @Test
    void theRealStudentServiceHasTheFourRulesInFormParameterOrder() {
        assertEquals(EXPECTED, rules(RootPaths.of(".")));
    }

    @Test
    void aFixtureWithTheSameIdiomsGivesTheSameRulesWithOrWithoutBracesAndInAnyOrder(@TempDir Path dir) throws IOException {
        assertEquals(EXPECTED, rules(fixture(dir, service(ALL_RULES, THREE_ARGUMENTS))));

        Path second = Files.createDirectories(dir.resolve("second"));
        String shuffled = PHONE_RULE + DEPARTMENT_RULE + NAME_RULE + EMAIL_RULE;
        assertEquals(EXPECTED, rules(fixture(second, service(shuffled, THREE_ARGUMENTS))));

        Path third = Files.createDirectories(dir.resolve("third"));
        String unbraced = ALL_RULES.replace(") {\n            throw", ")\n            throw").replace(";\n        }\n", ";\n");
        assertEquals(EXPECTED, rules(fixture(third, service(unbraced, ""))));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "        if (name == null || name.length() < 2) {\n            throw new IllegalArgumentException(\"x\");\n        }\n",
        "        if (name == null || name.isEmpty()) {\n            throw new IllegalArgumentException(\"x\");\n        }\n",
        "        if (name.isBlank() || name == null) {\n            throw new IllegalArgumentException(\"x\");\n        }\n",
        "        if (name == null || !name.contains(\"\")) {\n            throw new IllegalArgumentException(\"x\");\n        }\n",
        "        if (name == null || name.isBlank()) {\n            throw new IllegalArgumentException(\"x\");\n        } else {\n            return;\n        }\n",
        "        if (name == null || name.isBlank()) {\n            throw new IllegalArgumentException(\"a\" + email);\n        }\n",
        "        if (name == null || name.isBlank()) {\n            throw new RuntimeException(\"x\");\n        }\n",
        "        if (name == null || name.isBlank()) {\n            throw new IllegalArgumentException();\n        }\n",
        "        if (name == null || name.isBlank()) {\n            System.out.println(\"x\");\n            throw new IllegalArgumentException(\"x\");\n        }\n",
        "        String copy = name;\n",
        "        if (zzz == null || zzz.isBlank()) {\n            throw new IllegalArgumentException(\"x\");\n        }\n"
    })
    void aRuleOutsideTheSupportedIdiomsIsRejected(String extra, @TempDir Path dir) throws IOException {
        assertRejected(dir, service(ALL_RULES.replace(NAME_RULE, "") + extra, THREE_ARGUMENTS), "validateStudentInput");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "        if (name == null || (!name.isBlank() && !name.matches(\"x\"))) {\n            throw new IllegalArgumentException(\"x\");\n        }\n",
        "        if (name == null || (!name.isBlank() && !name.trim().matches(\"x\") && true)) {\n            throw new IllegalArgumentException(\"x\");\n        }\n",
        "        if (name == null || !email.contains(\"@\")) {\n            throw new IllegalArgumentException(\"x\");\n        }\n",
        "        if (name == null || (!email.isBlank() && !name.trim().matches(\"x\"))) {\n            throw new IllegalArgumentException(\"x\");\n        }\n",
        "        if (null == name || name.isBlank()) {\n            throw new IllegalArgumentException(\"x\");\n        }\n"
    })
    void aNearMissOfTheSupportedIdiomsIsRejectedWithTheIdiomMessage(String extra, @TempDir Path dir) throws IOException {
        assertRejected(dir, service(ALL_RULES.replace(NAME_RULE, "") + extra, THREE_ARGUMENTS), "not one of the supported idioms");
    }

    @Test
    void aMappedFieldWithoutARuleOrWithTwoRulesIsRejected(@TempDir Path dir) throws IOException {
        assertRejected(dir, service(NAME_RULE + EMAIL_RULE + PHONE_RULE, THREE_ARGUMENTS), "no rule for the mapped field department");
        Path second = Files.createDirectories(dir.resolve("second"));
        assertRejected(second, service(ALL_RULES + NAME_RULE, THREE_ARGUMENTS), "more than one rule for name");
    }

    @Test
    void theThreeParameterOverloadMustDelegateAndAppendAnEmptyString(@TempDir Path dir) throws IOException {
        String empty = "    public void validateStudentInput(String name, String email, String department) {\n    }\n";
        assertRejected(dir, service(ALL_RULES, empty), "delegating call");
        Path second = Files.createDirectories(dir.resolve("second"));
        String swapped = "    public void validateStudentInput(String name, String email, String department) {\n"
            + "        validateStudentInput(email, name, department, \"\");\n    }\n";
        assertRejected(second, service(ALL_RULES, swapped), "own parameters in order");
        Path third = Files.createDirectories(dir.resolve("third"));
        String nonEmpty = THREE_ARGUMENTS.replace("\"\"", "\"x\"");
        assertRejected(third, service(ALL_RULES, nonEmpty), "delegating call");
    }

    @Test
    void theFourParameterMethodMustExistExactlyOnce(@TempDir Path dir) throws IOException {
        String none = "package fixture;\n\npublic class StudentService {\n    public void createStudent(String a) {\n    }\n}\n";
        assertRejected(dir, none, "exactly one four-parameter");
        Path second = Files.createDirectories(dir.resolve("second"));
        String twice = service(ALL_RULES, "")
            .replace("}\n}\n", "}\n\n    public void validateStudentInput(String a, String b, String c, String d) {\n    }\n}\n");
        assertRejected(second, twice, "exactly one four-parameter");
    }
}
