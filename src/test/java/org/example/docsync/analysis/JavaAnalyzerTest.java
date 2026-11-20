package org.example.docsync.analysis;

import org.example.docsync.error.MissingExpectedException;
import org.example.docsync.error.UnsupportedConstructException;
import org.example.docsync.io.ProjectFiles;
import org.example.docsync.io.RootPaths;
import org.example.docsync.model.SourceModel;
import org.example.docsync.model.SourceModel.ErrorCodeInfo;
import org.example.docsync.model.SourceModel.Field;
import org.example.docsync.model.SourceModel.Route;
import org.example.docsync.model.SourceModel.ServiceMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task E2-5 (FR-E2-02, FR-E2-04, FR-E2-22 source side, AC-E2-02 source side, AC-E2-10). Part 1 analyzes the
 * real post-E1 sources read-only through the project directory (the surefire working directory). Part 2
 * uses fixture roots in a TempDir and never writes outside it.
 */
class JavaAnalyzerTest {

    private static final String API_FILE = "src/main/java/org/example/api/StudentApiServer.java";
    private static final String ERROR_FILE = "src/main/java/org/example/api/ErrorCode.java";
    private static final String STUDENT_FILE = "src/main/java/org/example/model/Student.java";
    private static final String SERVICE_FILE = "src/main/java/org/example/service/StudentService.java";

    // ---- Part 1: the real post-E1 sources ----

    private static SourceModel analyzeRealProject() {
        RootPaths roots = RootPaths.of(".");
        return new JavaAnalyzer(roots, new ProjectFiles(roots)).analyze();
    }

    @Test
    void realRoutesAreTheFourApiRoutesInSourceOrder() {
        assertEquals(List.of(
            new Route("GET", "/students", "List all students", "none", "Student[]"),
            new Route("POST", "/students", "Create a student", "StudentRequest", "Student"),
            new Route("GET", "/students/{id}", "Fetch a student by id", "none", "Student"),
            new Route("DELETE", "/students/{id}", "Delete a student", "none", "DeleteResponse")),
            analyzeRealProject().routes());
    }

    @Test
    void realFormParametersAreTheFourLiteralKeysInArgumentOrder() {
        assertEquals(List.of("name", "email", "department", "phone"), analyzeRealProject().formParameters());
    }

    @Test
    void realStudentFieldsAreTheSixRecordComponents() {
        assertEquals(List.of(
            new Field("id", "String"), new Field("name", "String"), new Field("email", "String"),
            new Field("department", "String"), new Field("phone", "String"), new Field("status", "String")),
            analyzeRealProject().studentFields());
    }

    @Test
    void realErrorCodesAreTheFourConstantsWithTheirStatuses() {
        assertEquals(List.of(
            new ErrorCodeInfo("VALIDATION_ERROR", 400), new ErrorCodeInfo("NOT_FOUND", 404),
            new ErrorCodeInfo("METHOD_NOT_ALLOWED", 405), new ErrorCodeInfo("INTERNAL_SERVER_ERROR", 500)),
            analyzeRealProject().errorCodes());
    }

    @Test
    void realServiceHasSevenPublicMethodsInSourceOrder() {
        List<String> three = List.of("String name", "String email", "String department");
        List<String> four = List.of("String name", "String email", "String department", "String phone");
        assertEquals(List.of(
            new ServiceMethod("createStudent", three, "Student"),
            new ServiceMethod("createStudent", four, "Student"),
            new ServiceMethod("getStudents", List.of(), "List<Student>"),
            new ServiceMethod("getStudent", List.of("String id"), "Optional<Student>"),
            new ServiceMethod("deleteStudent", List.of("String id"), "boolean"),
            new ServiceMethod("validateStudentInput", three, "void"),
            new ServiceMethod("validateStudentInput", four, "void")),
            analyzeRealProject().serviceMethods());
    }

    @Test
    void realServiceDigestEqualsAnIndependentSha256OfTheNormalizedText() throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of("src", "main", "java", "org", "example", "service", "StudentService.java"));
        int start = bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF ? 3 : 0;
        String text = new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8)
            .replace("\r\n", "\n").replace('\r', '\n');
        assertEquals(sha256Hex(text), analyzeRealProject().serviceDigest());
    }

    @Test
    void analyzingTheRealProjectTwiceGivesEqualModels() {
        assertEquals(analyzeRealProject(), analyzeRealProject());
    }

    @Test
    void theModelListsAreImmutable() {
        SourceModel model = analyzeRealProject();
        assertThrows(UnsupportedOperationException.class, () -> model.routes().add(model.routes().get(0)));
        assertThrows(UnsupportedOperationException.class, () -> model.serviceMethods().get(0).parameters().add("x"));
    }

    // ---- Part 2: fixture sources ----

    private static final String ROUTE_ARGS = "\"GET\", \"/a\", \"Sum A\", \"none\", \"A[]\", this::h";

    private static final String ROUTES_OK =
        "        return List.of(\n"
            + "            new Route(" + ROUTE_ARGS + "),\n"
            + "            new Route(\"POST\", \"/a\", \"Sum B\", \"Req\", \"B\", this::h)\n"
            + "        );";

    private static final String CREATE_OK = createCall(
        getOr("\"name\"", "\"\""), getOr("\"email\"", "\"\""), getOr("\"department\"", "\"\""), getOr("\"phone\"", "\"\""));

    private static String getOr(String key, String fallback) {
        return "p.getOrDefault(" + key + ", " + fallback + ")";
    }

    private static String createCall(String... arguments) {
        return "        service.createStudent(" + String.join(", ", arguments) + ");";
    }

    private static String routeWithArgument(int position, String replacement) {
        String[] arguments = ROUTE_ARGS.split(", ");
        arguments[position - 1] = replacement;
        return "new Route(" + String.join(", ", arguments) + ")";
    }

    private static String api(String routesBody, String createBody) {
        return """
            package fixture;

            import java.util.List;

            public class StudentApiServer {
                private final Object service = null;

                public List<Route> routes() {
            %s
                }

                void create(java.util.Map<String, String> p) {
            %s
                }

                public record Route(String method, String path, String summary, String requestType, String responseType, Object handler) {
                }
            }
            """.formatted(routesBody, createBody);
    }

    private static String errorCode(String constants) {
        return """
            package fixture;

            public enum ErrorCode {
                %s;

                private final int status;

                ErrorCode(int status) {
                    this.status = status;
                }

                public int status() {
                    return status;
                }
            }
            """.formatted(constants);
    }

    private static final String STUDENT_OK = """
        package fixture;

        import java.util.List;

        public record Student(String id, List<String> tags) {
            static final String UNUSED = "x";
        }
        """;

    private static String service(String extraMembers, String listBody) {
        return """
            package fixture;

            import java.util.List;

            public class StudentService {
                public StudentService() {
                }

                public Student create(String name, int count) {
                    return new Student(name);
                }

                public List<Student> list() {
                    %s
                }
            %s}
            """.formatted(listBody, extraMembers);
    }

    private static final String SERVICE_OK = service("", "return List.of();");

    private static RootPaths fixture(Path dir) throws IOException {
        RootPaths roots = RootPaths.of(dir.toString());
        write(roots, RootPaths.Name.API_ROUTES_SOURCE, api(ROUTES_OK, CREATE_OK));
        write(roots, RootPaths.Name.ERROR_CODE_SOURCE, errorCode("FIRST(400), SECOND(500)"));
        write(roots, RootPaths.Name.STUDENT_RECORD_SOURCE, STUDENT_OK);
        write(roots, RootPaths.Name.SERVICE_SOURCE, SERVICE_OK);
        return roots;
    }

    private static void write(RootPaths roots, RootPaths.Name name, String text) throws IOException {
        Path file = roots.path(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static SourceModel analyze(RootPaths roots) {
        return new JavaAnalyzer(roots, new ProjectFiles(roots)).analyze();
    }

    private static void assertUnsupported(RootPaths roots, String... fragments) {
        UnsupportedConstructException e = assertThrows(UnsupportedConstructException.class, () -> analyze(roots));
        for (String fragment : fragments) {
            assertTrue(e.getMessage().contains(fragment), "message should contain [" + fragment + "]: " + e.getMessage());
        }
        assertFalse(e.getMessage().contains(roots.root().toString()), "message must not contain the root path: " + e.getMessage());
    }

    private static String sha256Hex(String text) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static Map<String, String> snapshot(Path root) throws IOException {
        Map<String, String> files = new HashMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                files.put(root.relativize(p).toString(), Files.isRegularFile(p) ? Files.readString(p, StandardCharsets.UTF_8) : "<dir>");
            }
        }
        return files;
    }

    @Test
    void theFixtureAnalyzesToTheExpectedModel(@TempDir Path dir) throws Exception {
        SourceModel model = analyze(fixture(dir));
        assertEquals(List.of(
            new Route("GET", "/a", "Sum A", "none", "A[]"), new Route("POST", "/a", "Sum B", "Req", "B")), model.routes());
        assertEquals(List.of("name", "email", "department", "phone"), model.formParameters());
        assertEquals(List.of(new Field("id", "String"), new Field("tags", "List<String>")), model.studentFields());
        assertEquals(List.of(new ErrorCodeInfo("FIRST", 400), new ErrorCodeInfo("SECOND", 500)), model.errorCodes());
        assertEquals(List.of(
            new ServiceMethod("create", List.of("String name", "int count"), "Student"),
            new ServiceMethod("list", List.of(), "List<Student>")), model.serviceMethods());
        assertEquals(sha256Hex(SERVICE_OK), model.serviceDigest());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void aNonLiteralRouteArgumentIsRejectedAndNamesTheFile(int position, @TempDir Path dir) throws Exception {
        for (String dynamic : List.of("SUMMARY", "\"a\" + \"b\"", "summary()")) {
            RootPaths roots = fixture(dir);
            write(roots, RootPaths.Name.API_ROUTES_SOURCE, api("        return List.of(" + routeWithArgument(position, dynamic) + ");", CREATE_OK));
            assertUnsupported(roots, API_FILE, "Route argument " + position + " is not a string literal");
        }
    }

    @Test
    void theHandlerArgumentOfARouteIsIgnored(@TempDir Path dir) throws Exception {
        RootPaths roots = fixture(dir);
        write(roots, RootPaths.Name.API_ROUTES_SOURCE,
            api("        return List.of(" + routeWithArgument(6, "dynamicHandler(1 + 2)") + ");", CREATE_OK));
        assertEquals(List.of(new Route("GET", "/a", "Sum A", "none", "A[]")), analyze(roots).routes());
    }

    @Test
    void aRoutesBodyOfAnotherShapeIsRejected(@TempDir Path dir) throws Exception {
        for (String body : List.of(
            "        List<Route> r = List.of();\n        return r;",
            "        System.out.println();\n        return List.of(new Route(" + ROUTE_ARGS + "));",
            "        return java.util.Collections.emptyList();",
            "        return new java.util.ArrayList<>();",
            "        if (service == null) { return List.of(); }\n        return List.of(new Route(" + ROUTE_ARGS + "));")) {
            RootPaths roots = fixture(dir);
            write(roots, RootPaths.Name.API_ROUTES_SOURCE, api(body, CREATE_OK));
            assertUnsupported(roots, API_FILE, "routes()");
        }
    }

    @Test
    void aMissingRoutesMethodIsRejected(@TempDir Path dir) throws Exception {
        RootPaths roots = fixture(dir);
        write(roots, RootPaths.Name.API_ROUTES_SOURCE, api(ROUTES_OK, CREATE_OK).replace("List<Route> routes()", "List<Route> otherRoutes()"));
        assertUnsupported(roots, API_FILE, "routes()");
    }

    @Test
    void aRoutesElementThatIsNotANewRouteIsRejected(@TempDir Path dir) throws Exception {
        for (String element : List.of("other()", "route", "new Other(" + ROUTE_ARGS + ")", "null")) {
            RootPaths roots = fixture(dir);
            write(roots, RootPaths.Name.API_ROUTES_SOURCE,
                api("        return List.of(new Route(" + ROUTE_ARGS + "), " + element + ");", CREATE_OK));
            assertUnsupported(roots, API_FILE, "new Route");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"GET\", \"/a\", \"Sum A\", \"none\", \"A[]\"", "\"GET\", \"/a\", \"Sum A\", \"none\", \"A[]\", this::h, 1", ""})
    void aRouteWithTheWrongArgumentCountIsRejected(String arguments, @TempDir Path dir) throws Exception {
        RootPaths roots = fixture(dir);
        write(roots, RootPaths.Name.API_ROUTES_SOURCE, api("        return List.of(new Route(" + arguments + "));", CREATE_OK));
        assertUnsupported(roots, API_FILE, "six arguments");
    }

    @Test
    void aNonLiteralGetOrDefaultKeyIsRejected(@TempDir Path dir) throws Exception {
        for (String key : List.of("key", "\"na\" + \"me\"", "name()")) {
            RootPaths roots = fixture(dir);
            write(roots, RootPaths.Name.API_ROUTES_SOURCE, api(ROUTES_OK, createCall(
                getOr("\"name\"", "\"\""), getOr(key, "\"\""), getOr("\"department\"", "\"\""), getOr("\"phone\"", "\"\""))));
            assertUnsupported(roots, API_FILE, "createStudent", "argument 2");
        }
    }

    @Test
    void aNonEmptyOrNonLiteralDefaultIsRejected(@TempDir Path dir) throws Exception {
        for (String fallback : List.of("\"x\"", "DEFAULT", "null", "\" \"")) {
            RootPaths roots = fixture(dir);
            write(roots, RootPaths.Name.API_ROUTES_SOURCE, api(ROUTES_OK, createCall(
                getOr("\"name\"", fallback), getOr("\"email\"", "\"\""), getOr("\"department\"", "\"\""), getOr("\"phone\"", "\"\""))));
            assertUnsupported(roots, API_FILE, "createStudent", "argument 1");
        }
    }

    @Test
    void anArgumentThatIsNotAGetOrDefaultCallIsRejected(@TempDir Path dir) throws Exception {
        for (String argument : List.of("name", "p.get(\"name\")", "\"name\"", "p.getOrDefault(\"name\")", "p.getOrDefault(\"name\", \"\", 1)")) {
            RootPaths roots = fixture(dir);
            write(roots, RootPaths.Name.API_ROUTES_SOURCE, api(ROUTES_OK, createCall(
                getOr("\"name\"", "\"\""), getOr("\"email\"", "\"\""), getOr("\"department\"", "\"\""), argument)));
            assertUnsupported(roots, API_FILE, "createStudent", "argument 4");
        }
    }

    @Test
    void duplicateFormKeysAreRejected(@TempDir Path dir) throws Exception {
        RootPaths roots = fixture(dir);
        write(roots, RootPaths.Name.API_ROUTES_SOURCE, api(ROUTES_OK, createCall(
            getOr("\"name\"", "\"\""), getOr("\"email\"", "\"\""), getOr("\"name\"", "\"\""), getOr("\"phone\"", "\"\""))));
        assertUnsupported(roots, API_FILE, "createStudent", "duplicate", "name");
    }

    @Test
    void twoMatchingCreateCallsAreRejected(@TempDir Path dir) throws Exception {
        RootPaths roots = fixture(dir);
        write(roots, RootPaths.Name.API_ROUTES_SOURCE, api(ROUTES_OK, CREATE_OK + "\n" + CREATE_OK));
        assertUnsupported(roots, API_FILE, "createStudent", "exactly one");
    }

    @Test
    void noMatchingCreateCallIsRejected(@TempDir Path dir) throws Exception {
        RootPaths roots = fixture(dir);
        write(roots, RootPaths.Name.API_ROUTES_SOURCE, api(ROUTES_OK, "        service.createStudent(a, b, c);"));
        assertUnsupported(roots, API_FILE, "createStudent", "exactly one");
    }

    @Test
    void callsWithAnotherArityOrReceiverAreIgnored(@TempDir Path dir) throws Exception {
        RootPaths roots = fixture(dir);
        String others = createCall("a", "b", "c") + "\n"
            + "        service.createStudent(a, b, c, d, e);\n"
            + "        other.createStudent(a, b, c, d);\n"
            + "        this.service.createStudent(a, b, c, d);\n"
            + "        service.otherMethod(a, b, c, d);";
        write(roots, RootPaths.Name.API_ROUTES_SOURCE, api(ROUTES_OK, others + "\n" + CREATE_OK));
        assertEquals(List.of("name", "email", "department", "phone"), analyze(roots).formParameters());
    }

    @Test
    void aMissingStudentRecordIsRejected(@TempDir Path dir) throws Exception {
        for (String text : List.of(
            "package fixture;\npublic class Student {\n}\n",
            "package fixture;\npublic record Other(String id) {\n}\n",
            "package fixture;\n")) {
            RootPaths roots = fixture(dir);
            write(roots, RootPaths.Name.STUDENT_RECORD_SOURCE, text);
            assertUnsupported(roots, STUDENT_FILE, "record Student");
        }
    }

    @Test
    void aDoubledStudentRecordIsRejected(@TempDir Path dir) throws Exception {
        for (String text : List.of(
            "package fixture;\nrecord Student(String id) {\n}\nrecord Student(String name) {\n}\n",
            "package fixture;\nclass Holder {\n    record Student(String id) {\n    }\n}\nrecord Student(String name) {\n}\n")) {
            RootPaths roots = fixture(dir);
            write(roots, RootPaths.Name.STUDENT_RECORD_SOURCE, text);
            assertUnsupported(roots, STUDENT_FILE, "record Student");
        }
    }

    @Test
    void onlyPublicNonConstructorServiceMethodsAreListed(@TempDir Path dir) throws Exception {
        RootPaths roots = fixture(dir);
        List<ServiceMethod> expected = analyze(roots).serviceMethods();
        String hidden = "\n    String packagePrivate() {\n        return \"\";\n    }\n"
            + "\n    private void hidden(int x) {\n    }\n"
            + "\n    protected void guarded() {\n    }\n";
        write(roots, RootPaths.Name.SERVICE_SOURCE, service(hidden, "return List.of();"));
        assertEquals(expected, analyze(roots).serviceMethods());
        assertEquals(2, expected.size());
    }

    @Test
    void aPublicServiceMethodAddedAtTheEndIsListedLast(@TempDir Path dir) throws Exception {
        RootPaths roots = fixture(dir);
        write(roots, RootPaths.Name.SERVICE_SOURCE, service("\n    public boolean remove(String id) {\n        return true;\n    }\n", "return List.of();"));
        List<ServiceMethod> methods = analyze(roots).serviceMethods();
        assertEquals(new ServiceMethod("remove", List.of("String id"), "boolean"), methods.get(methods.size() - 1));
        assertEquals(3, methods.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"FIRST(CODE)", "FIRST()", "FIRST(1, 2)", "FIRST(\"x\")", "FIRST(1L)", "FIRST(compute())", "FIRST"})
    void anErrorCodeConstantWithoutOneIntegerLiteralIsRejected(String constant, @TempDir Path dir) throws Exception {
        RootPaths roots = fixture(dir);
        write(roots, RootPaths.Name.ERROR_CODE_SOURCE, errorCode(constant + ", SECOND(500)"));
        assertUnsupported(roots, ERROR_FILE, "FIRST");
    }

    @Test
    void aMissingErrorCodeEnumIsRejected(@TempDir Path dir) throws Exception {
        RootPaths roots = fixture(dir);
        write(roots, RootPaths.Name.ERROR_CODE_SOURCE, "package fixture;\npublic class ErrorCode {\n}\n");
        assertUnsupported(roots, ERROR_FILE, "enum");
    }

    @ParameterizedTest
    @EnumSource(value = RootPaths.Name.class, names = {"API_ROUTES_SOURCE", "ERROR_CODE_SOURCE", "STUDENT_RECORD_SOURCE", "SERVICE_SOURCE"})
    void aSourceWithASyntaxErrorIsRejectedByRootRelativeName(RootPaths.Name name, @TempDir Path dir) throws Exception {
        RootPaths roots = fixture(dir);
        write(roots, name, "package fixture;\npublic class Broken {\n    int x = ;\n");
        assertUnsupported(roots, roots.display(name), "could not be parsed");
    }

    @Test
    void aMissingSourceFileIsReportedAsMissing(@TempDir Path dir) throws Exception {
        RootPaths roots = fixture(dir);
        Files.delete(roots.path(RootPaths.Name.SERVICE_SOURCE));
        assertThrows(MissingExpectedException.class, () -> analyze(roots));
    }

    @Test
    void noCompilerIsAnUnsupportedConstructError(@TempDir Path dir) throws Exception {
        RootPaths roots = fixture(dir);
        UnsupportedConstructException e = assertThrows(UnsupportedConstructException.class,
            () -> new JavaAnalyzer(roots, new ProjectFiles(roots), () -> null).analyze());
        assertEquals("No Java compiler is available; run the engine on a JDK.", e.getMessage());
    }

    @Test
    void theDigestIgnoresLineEndingsAndABom(@TempDir Path dir) throws Exception {
        RootPaths roots = fixture(dir);
        String expected = analyze(roots).serviceDigest();

        Files.write(roots.path(RootPaths.Name.SERVICE_SOURCE), SERVICE_OK.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, analyze(roots).serviceDigest());

        Files.write(roots.path(RootPaths.Name.SERVICE_SOURCE), SERVICE_OK.replace("\n", "\r").getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, analyze(roots).serviceDigest());

        byte[] text = SERVICE_OK.getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[text.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(text, 0, withBom, 3, text.length);
        Files.write(roots.path(RootPaths.Name.SERVICE_SOURCE), withBom);
        assertEquals(expected, analyze(roots).serviceDigest());
    }

    @Test
    void aLogicOnlyChangeChangesTheDigestButNotTheMethodList(@TempDir Path dir) throws Exception {
        RootPaths roots = fixture(dir);
        SourceModel before = analyze(roots);
        write(roots, RootPaths.Name.SERVICE_SOURCE, service("", "return new java.util.ArrayList<>();"));
        SourceModel after = analyze(roots);
        assertNotEquals(before.serviceDigest(), after.serviceDigest());
        assertEquals(before.serviceMethods(), after.serviceMethods());
    }

    @Test
    void analysisWritesNothing(@TempDir Path dir) throws Exception {
        RootPaths roots = fixture(dir);
        Map<String, String> before = snapshot(dir);
        analyze(roots);
        assertEquals(before, snapshot(dir));

        write(roots, RootPaths.Name.ERROR_CODE_SOURCE, errorCode("FIRST(CODE)"));
        Map<String, String> beforeFailure = snapshot(dir);
        assertThrows(UnsupportedConstructException.class, () -> analyze(roots));
        assertEquals(beforeFailure, snapshot(dir));
    }

    // ---- RV3-05: an empty result is a failure, never an empty document ----

    @Test
    void aRoutesMethodWithNoRoutesIsRejected(@TempDir Path dir) throws Exception {
        RootPaths roots = fixture(dir);
        write(roots, RootPaths.Name.API_ROUTES_SOURCE, api("        return List.of();", CREATE_OK));
        assertUnsupported(roots, API_FILE, "no Route");
    }

    @Test
    void anErrorCodeEnumWithNoConstantsIsRejected(@TempDir Path dir) throws Exception {
        RootPaths roots = fixture(dir);
        write(roots, RootPaths.Name.ERROR_CODE_SOURCE, errorCode(""));
        assertUnsupported(roots, ERROR_FILE, "no enum constants");
    }

    @Test
    void aStudentRecordWithNoComponentsIsRejected(@TempDir Path dir) throws Exception {
        RootPaths roots = fixture(dir);
        write(roots, RootPaths.Name.STUDENT_RECORD_SOURCE, "package fixture;\n\npublic record Student() {\n}\n");
        assertUnsupported(roots, STUDENT_FILE, "no components");
    }

    @Test
    void aServiceWithNoPublicMethodsIsRejected(@TempDir Path dir) throws Exception {
        RootPaths roots = fixture(dir);
        write(roots, RootPaths.Name.SERVICE_SOURCE,
            "package fixture;\n\npublic class StudentService {\n    private void hidden() {\n    }\n}\n");
        assertUnsupported(roots, SERVICE_FILE, "no public");
    }
}
