package org.example.api;

import org.example.model.Student;
import org.example.service.StudentService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Response contract of the student handlers after E1-4 (FR-E1-02, FR-E1-03, FR-E1-04, FR-E1-05,
 * FR-E1-08, FR-E1-09; AC-E1-02, AC-E1-03, AC-E1-06, AC-E1-07, AC-E1-09). Real HTTP over the loopback
 * address on an ephemeral port, never a wildcard address and never port 8000. Every JSON body is
 * parsed with the strict test-side {@link JsonChecker}. The shared server owns its own service and
 * every test uses unique names, so no test depends on the size of the list. The empty list and the
 * failing service use their own servers.
 */
class ApiResponseTest {

    private static final String NOT_FOUND_BODY = "{\"error\":\"Student not found\",\"code\":\"NOT_FOUND\"}";
    private static final String METHOD_NOT_ALLOWED_BODY = "{\"error\":\"Method not allowed\",\"code\":\"METHOD_NOT_ALLOWED\"}";
    private static final String INTERNAL_ERROR_BODY = "{\"error\":\"Internal server error\",\"code\":\"INTERNAL_SERVER_ERROR\"}";
    private static final String MALFORMED_FORM_BODY = "{\"error\":\"Malformed form data.\",\"code\":\"VALIDATION_ERROR\"}";
    private static final String NAME_MESSAGE = "Student name is required.";
    private static final String EMAIL_MESSAGE = "Email must contain '@'.";
    private static final String DEPARTMENT_MESSAGE = "Department is required.";
    private static final String PHONE_MESSAGE = "Invalid phone number.";
    private static final Set<String> STUDENT_KEYS = Set.of("id", "name", "email", "department", "phone", "status");

    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private static StudentApiServer server;

    private record Resp(int status, String contentType, String body) {
    }

    @BeforeAll
    static void startServer() throws IOException {
        server = startLoopbackServer(new StudentService());
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    // ---- helpers ----

    private static StudentApiServer startLoopbackServer(StudentService service) throws IOException {
        StudentApiServer started = new StudentApiServer(service, new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        started.start();
        return started;
    }

    // Builds http://host:port for the loopback address. An IPv6 literal is bracketed.
    private static String baseUrl(int port) {
        InetAddress address = InetAddress.getLoopbackAddress();
        String host = address.getHostAddress();
        if (address instanceof Inet6Address) {
            int zone = host.indexOf('%');
            if (zone >= 0) {
                host = host.substring(0, zone);
            }
            host = "[" + host + "]";
        }
        return "http://" + host + ":" + port;
    }

    private static Resp send(StudentApiServer target, String method, String pathAndQuery, String body) throws IOException, InterruptedException {
        HttpRequest.BodyPublisher publisher = body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl(target.port()) + pathAndQuery))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .method(method, publisher)
            .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Resp(response.statusCode(), response.headers().firstValue("Content-Type").orElse(null), response.body());
    }

    private static Resp send(String method, String pathAndQuery, String body) throws IOException, InterruptedException {
        return send(server, method, pathAndQuery, body);
    }

    private static Resp send(String method, String pathAndQuery) throws IOException, InterruptedException {
        return send(server, method, pathAndQuery, null);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String form(String name, String email, String department, String phone) {
        return "name=" + enc(name) + "&email=" + enc(email) + "&department=" + enc(department) + "&phone=" + enc(phone);
    }

    private static String uniqueName() {
        return "resp-" + UUID.randomUUID();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseObject(String body) {
        Object parsed = JsonChecker.parse(body);
        assertInstanceOf(Map.class, parsed, "not a JSON object: " + body);
        return (Map<String, Object>) parsed;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> parseArray(String body) {
        Object parsed = JsonChecker.parse(body);
        assertInstanceOf(List.class, parsed, "not a JSON array: " + body);
        return (List<Object>) parsed;
    }

    // The body must be valid JSON and byte-for-byte the expected text, with the JSON content type.
    private static void assertJson(Resp resp, int status, String body) {
        assertEquals(status, resp.status(), resp.body());
        assertEquals("application/json", resp.contentType());
        JsonChecker.parse(resp.body());
        assertEquals(body, resp.body());
    }

    private static void assertValidationError(Resp resp, String message) {
        assertJson(resp, 400, "{\"error\":" + JsonWriter.quote(message) + ",\"code\":\"VALIDATION_ERROR\"}");
        assertEquals(Map.of("error", message, "code", "VALIDATION_ERROR"), parseObject(resp.body()));
    }

    // Creates a student through the API and returns the parsed 201 body.
    private static Map<String, Object> create(String name) throws Exception {
        Resp resp = send("POST", "/students", form(name, "a@b.c", "CS", "1234567"));
        assertEquals(201, resp.status(), resp.body());
        return parseObject(resp.body());
    }

    // ---- list ----

    @Test
    void getStudentsOfAnEmptyServiceIsAnEmptyJsonArray() throws Exception {
        StudentApiServer empty = startLoopbackServer(new StudentService());
        try {
            Resp resp = send(empty, "GET", "/students", null);
            assertJson(resp, 200, "[]");
            assertTrue(parseArray(resp.body()).isEmpty());
        } finally {
            empty.stop();
        }
    }

    @Test
    void getStudentsReturnsAJsonArrayWithTheSixKeysPerStudent() throws Exception {
        String name = uniqueName();
        create(name);
        Resp resp = send("GET", "/students");
        assertEquals(200, resp.status());
        assertEquals("application/json", resp.contentType());
        List<Object> all = parseArray(resp.body());
        boolean found = false;
        for (Object item : all) {
            assertInstanceOf(Map.class, item);
            @SuppressWarnings("unchecked")
            Map<String, Object> student = (Map<String, Object>) item;
            assertEquals(STUDENT_KEYS, student.keySet());
            if (name.equals(student.get("name"))) {
                found = true;
                assertEquals("a@b.c", student.get("email"));
                assertEquals("CS", student.get("department"));
                assertEquals("1234567", student.get("phone"));
                assertEquals("active", student.get("status"));
            }
        }
        assertTrue(found, "the created student is in the list");
    }

    // ---- create ----

    @Test
    void postStudentsReturns201WithAJsonObject() throws Exception {
        String name = uniqueName();
        Resp resp = send("POST", "/students", form(name, "a@b.c", "CS", "1234567"));
        assertEquals(201, resp.status());
        assertEquals("application/json", resp.contentType());
        Map<String, Object> student = parseObject(resp.body());
        assertEquals(STUDENT_KEYS, student.keySet());
        assertEquals(name, student.get("name"));
        assertEquals("a@b.c", student.get("email"));
        assertEquals("CS", student.get("department"));
        assertEquals("1234567", student.get("phone"));
        assertEquals("active", student.get("status"));
        assertFalse(((String) student.get("id")).isEmpty());
    }

    @Test
    void postStudentsEscapesSpecialCharactersIntoValidJson() throws Exception {
        String name = "q\"uote\\back " + UUID.randomUUID();
        Map<String, Object> student = create(name);
        assertEquals(name, student.get("name"));
    }

    // ---- get by id ----

    @Test
    void getStudentByIdReturns200WithTheSameJsonAsCreate() throws Exception {
        Resp created = send("POST", "/students", form(uniqueName(), "a@b.c", "CS", ""));
        assertEquals(201, created.status());
        String id = (String) parseObject(created.body()).get("id");
        Resp fetched = send("GET", "/students/" + id);
        assertJson(fetched, 200, created.body());
    }

    @Test
    void getOfAMissingIdIsAStructured404() throws Exception {
        assertJson(send("GET", "/students/missing-" + UUID.randomUUID()), 404, NOT_FOUND_BODY);
    }

    // ---- 400 with the specific service message ----

    @Test
    void anInvalidNameReturns400WithTheNameMessage() throws Exception {
        assertValidationError(send("POST", "/students", form("", "a@b.c", "CS", "")), NAME_MESSAGE);
    }

    @Test
    void anInvalidEmailReturns400WithTheEmailMessage() throws Exception {
        assertValidationError(send("POST", "/students", form(uniqueName(), "no-at-sign", "CS", "")), EMAIL_MESSAGE);
    }

    @Test
    void anInvalidDepartmentReturns400WithTheDepartmentMessage() throws Exception {
        assertValidationError(send("POST", "/students", form(uniqueName(), "a@b.c", "", "")), DEPARTMENT_MESSAGE);
    }

    @Test
    void anInvalidPhoneReturns400WithThePhoneMessage() throws Exception {
        assertValidationError(send("POST", "/students", form(uniqueName(), "a@b.c", "CS", "abc")), PHONE_MESSAGE);
    }

    @Test
    void whenEveryFieldIsInvalidTheNameIsReportedFirst() throws Exception {
        assertValidationError(send("POST", "/students", form("", "no-at-sign", "", "abc")), NAME_MESSAGE);
    }

    @Test
    void whenOnlyTheLaterFieldsAreInvalidTheEmailIsReportedBeforeTheDepartmentAndPhone() throws Exception {
        assertValidationError(send("POST", "/students", form(uniqueName(), "no-at-sign", "", "abc")), EMAIL_MESSAGE);
    }

    @Test
    void anEmptyPostBodyReturns400WithTheNameMessage() throws Exception {
        assertValidationError(send("POST", "/students", ""), NAME_MESSAGE);
    }

    // ---- malformed form data ----

    @ParameterizedTest
    @ValueSource(strings = {
        "name=%ZZ&email=a%40b.c&department=CS&phone=",
        "name=abc&email=a%40b.c&department=CS&phone=%",
        "name=abc&email=a%40b.c&department=CS&phone=12%4"
    })
    void aMalformedPercentEscapeReturns400WithTheFixedMessageAndTheServerKeepsAnswering(String malformed) throws Exception {
        assertJson(send("POST", "/students", malformed), 400, MALFORMED_FORM_BODY);
        assertEquals(Map.of("error", "Malformed form data.", "code", "VALIDATION_ERROR"), parseObject(MALFORMED_FORM_BODY));

        Resp next = send("GET", "/students");
        assertEquals(200, next.status());
        parseArray(next.body());
    }

    // ---- delete ----

    @Test
    void deleteOfAnExistingStudentReturns200ThenItIsGone() throws Exception {
        String id = (String) create(uniqueName()).get("id");
        Resp deleted = send("DELETE", "/students/" + id);
        assertJson(deleted, 200, "{\"deleted\":true}");
        assertEquals(Map.of("deleted", Boolean.TRUE), parseObject(deleted.body()));

        assertJson(send("GET", "/students/" + id), 404, NOT_FOUND_BODY);
        assertJson(send("DELETE", "/students/" + id), 404, NOT_FOUND_BODY);
    }

    @Test
    void deleteOfAMissingIdIsAStructured404() throws Exception {
        assertJson(send("DELETE", "/students/missing-" + UUID.randomUUID()), 404, NOT_FOUND_BODY);
    }

    // ---- /students/ and /students/a/b keep their characterized method and status behavior ----

    @ParameterizedTest
    @ValueSource(strings = {"/students/", "/students/a/b"})
    void getAndDeleteOnAnEmptyOrNestedIdAreStructured404(String path) throws Exception {
        assertJson(send("GET", path), 404, NOT_FOUND_BODY);
        assertJson(send("DELETE", path), 404, NOT_FOUND_BODY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/students/", "/students/a/b"})
    void otherMethodsOnAnEmptyOrNestedIdAreStructured405(String path) throws Exception {
        for (String method : new String[] {"POST", "PUT", "PATCH", "OPTIONS"}) {
            Resp resp = send(method, path, method.equals("POST") ? form(uniqueName(), "a@b.c", "CS", "") : null);
            assertJson(resp, 405, METHOD_NOT_ALLOWED_BODY);
        }
    }

    // ---- a failing service gives a structured 500 that does not leak the exception message ----

    private static final String SECRET = "secret-failure-detail-" + UUID.randomUUID();

    private static StudentService failingService() {
        return new StudentService() {
            @Override
            public Student createStudent(String name, String email, String department, String phone) {
                throw new RuntimeException(SECRET);
            }

            @Override
            public List<Student> getStudents() {
                throw new RuntimeException(SECRET);
            }

            @Override
            public Optional<Student> getStudent(String id) {
                throw new RuntimeException(SECRET);
            }

            @Override
            public boolean deleteStudent(String id) {
                throw new RuntimeException(SECRET);
            }
        };
    }

    @Test
    void aFailingServiceGivesAStructured500ForListCreateGetAndDelete() throws Exception {
        StudentApiServer failing = startLoopbackServer(failingService());
        try {
            Resp[] responses = {
                send(failing, "GET", "/students", null),
                send(failing, "POST", "/students", form(uniqueName(), "a@b.c", "CS", "")),
                send(failing, "GET", "/students/some-id", null),
                send(failing, "DELETE", "/students/some-id", null)
            };
            for (Resp resp : responses) {
                assertJson(resp, 500, INTERNAL_ERROR_BODY);
                assertFalse(resp.body().contains(SECRET), "the exception message must not be in the body");
            }
        } finally {
            failing.stop();
        }
    }

    // ---- success responses keep their status codes and content type ----

    @Test
    void successResponsesKeepTheirStatusCodesAndJsonContentType() throws Exception {
        Resp created = send("POST", "/students", form(uniqueName(), "a@b.c", "CS", ""));
        assertEquals(201, created.status());
        assertEquals("application/json", created.contentType());
        String id = (String) parseObject(created.body()).get("id");

        Resp list = send("GET", "/students");
        assertEquals(200, list.status());
        assertEquals("application/json", list.contentType());

        Resp fetched = send("GET", "/students/" + id);
        assertEquals(200, fetched.status());
        assertEquals("application/json", fetched.contentType());

        Resp deleted = send("DELETE", "/students/" + id);
        assertEquals(200, deleted.status());
        assertEquals("application/json", deleted.contentType());
    }
}
