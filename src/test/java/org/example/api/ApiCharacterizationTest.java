package org.example.api;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization of the API (task T0.1, requirement FR-E1-09). Every assertion records what
 * the code is expected to do, derived from reading it. It first recorded the behavior of the
 * unchanged code, and since task E1-4 it records the post-E1 contract where gate G0 approved a
 * change (JSON success bodies, structured error bodies with the specific service message, and a
 * 400 for malformed form data). Everything else is unchanged. Any assertion that fails is
 * investigated against the observed behavior and reported. It is not a statement of what is
 * correct.
 *
 * <p>The server is bound to the loopback address on an ephemeral port (never a wildcard address and
 * never port 8000). The server under test owns ONE service for the whole class, shared by all of
 * its tests, so every test uses unique values and makes no assertion about the size or the full
 * content of the list.
 *
 * <p>The static handlers read {@code frontend/} relative to the working directory, which is the
 * project base directory under surefire.
 */
class ApiCharacterizationTest {

    private static final String METHOD_NOT_ALLOWED_BODY = "{\"error\":\"Method not allowed\",\"code\":\"METHOD_NOT_ALLOWED\"}";
    private static final String UNKNOWN_PATH_BODY = "{\"error\":\"Not found\",\"code\":\"NOT_FOUND\"}";
    private static final String NOT_FOUND_BODY = "{\"error\":\"Student not found\",\"code\":\"NOT_FOUND\"}";
    private static final String NAME_REQUIRED_BODY = "{\"error\":\"Student name is required.\",\"code\":\"VALIDATION_ERROR\"}";
    private static final String EMAIL_INVALID_BODY = "{\"error\":\"Email must contain '@'.\",\"code\":\"VALIDATION_ERROR\"}";
    private static final String DEPARTMENT_REQUIRED_BODY = "{\"error\":\"Department is required.\",\"code\":\"VALIDATION_ERROR\"}";
    private static final String PHONE_INVALID_BODY = "{\"error\":\"Invalid phone number.\",\"code\":\"VALIDATION_ERROR\"}";
    private static final String MALFORMED_FORM_BODY = "{\"error\":\"Malformed form data.\",\"code\":\"VALIDATION_ERROR\"}";
    private static final String DELETED_TRUE_BODY = "{\"deleted\":true}";

    private static StudentApiServer server;
    private static HttpClient client;
    private static String base;

    private record Resp(int status, String contentType, String body) {
    }

    @BeforeAll
    static void startServer() throws IOException {
        server = new StudentApiServer(new StudentService(), new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        server.start();
        base = baseUrl(new InetSocketAddress(InetAddress.getLoopbackAddress(), server.port()));
        client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    // ---- helpers ----

    // Builds http://host:port from the address the server actually bound. An IPv6 literal is bracketed.
    private static String baseUrl(InetSocketAddress bound) {
        InetAddress address = bound.getAddress();
        String host = address.getHostAddress();
        if (address instanceof Inet6Address) {
            // A loopback address never needs a zone id. Drop any "%zone" suffix instead of putting a raw '%' in the URL.
            int zone = host.indexOf('%');
            if (zone >= 0) {
                host = host.substring(0, zone);
            }
            host = "[" + host + "]";
        }
        return "http://" + host + ":" + bound.getPort();
    }

    private static Resp send(String method, String pathAndQuery, String body) throws IOException, InterruptedException {
        HttpRequest.BodyPublisher publisher = body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + pathAndQuery))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .method(method, publisher)
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Resp(response.statusCode(), response.headers().firstValue("Content-Type").orElse(null), response.body());
    }

    private static Resp send(String method, String pathAndQuery) throws IOException, InterruptedException {
        return send(method, pathAndQuery, null);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String form(String name, String email, String department, String phone) {
        return "name=" + enc(name) + "&email=" + enc(email) + "&department=" + enc(department) + "&phone=" + enc(phone);
    }

    private static String uniqueName() {
        return "char-" + UUID.randomUUID();
    }

    private static String frontendFile(String name) throws IOException {
        return Files.readString(Path.of("frontend", name), StandardCharsets.UTF_8);
    }

    private static String extractId(String createdBody) {
        Matcher matcher = Pattern.compile("^\\{\"id\":\"([^\"]+)\",").matcher(createdBody);
        assertTrue(matcher.find(), "unexpected create body: " + createdBody);
        return matcher.group(1);
    }

    private static void assertJson(Resp resp, int status, String body) {
        assertEquals(status, resp.status());
        assertEquals("application/json", resp.contentType());
        assertEquals(body, resp.body());
    }

    private static void assertIndexHtml(Resp resp) throws IOException {
        assertEquals(200, resp.status());
        assertEquals("text/html; charset=UTF-8", resp.contentType());
        assertEquals(frontendFile("index.html"), resp.body());
    }

    // ---- /students: every method ----

    @Test
    void getStudentsReturnsTheListAsAJsonArray() throws Exception {
        String name = uniqueName();
        send("POST", "/students", form(name, "a@b.c", "CS", ""));
        Resp resp = send("GET", "/students");
        assertEquals(200, resp.status());
        assertEquals("application/json", resp.contentType());
        assertTrue(resp.body().startsWith("[") && resp.body().endsWith("]"));
        assertTrue(resp.body().contains("\"name\":\"" + name + "\""));
        assertTrue(resp.body().contains("{\"id\":\""), "success bodies are JSON objects");
        assertFalse(resp.body().contains("Student[id="), "success bodies are no longer record syntax");
    }

    @Test
    void postStudentsWithValidFormReturns201AndTheJsonBody() throws Exception {
        String name = uniqueName();
        Resp resp = send("POST", "/students", form(name, "a@b.c", "CS", "1234567"));
        assertEquals(201, resp.status());
        assertEquals("application/json", resp.contentType());
        String id = extractId(resp.body());
        assertEquals("{\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"email\":\"a@b.c\",\"department\":\"CS\","
            + "\"phone\":\"1234567\",\"status\":\"active\"}", resp.body());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "PATCH", "DELETE", "OPTIONS"})
    void unsupportedMethodsOnStudentsReturn405(String method) throws Exception {
        assertJson(send(method, "/students"), 405, METHOD_NOT_ALLOWED_BODY);
    }

    @Test
    void headOnStudentsReturns405() throws Exception {
        Resp resp = send("HEAD", "/students");
        assertEquals(405, resp.status());
    }

    // ---- 400 bodies ----

    @Test
    void invalidInputReturns400WithTheSpecificServiceMessageForEveryField() throws Exception {
        String name = uniqueName();
        assertJson(send("POST", "/students", form("", "a@b.c", "CS", "")), 400, NAME_REQUIRED_BODY);
        assertJson(send("POST", "/students", form(name, "no-at-sign", "CS", "")), 400, EMAIL_INVALID_BODY);
        assertJson(send("POST", "/students", form(name, "a@b.c", "", "")), 400, DEPARTMENT_REQUIRED_BODY);
        assertJson(send("POST", "/students", form(name, "a@b.c", "CS", "abc")), 400, PHONE_INVALID_BODY);
    }

    @Test
    void emptyPostBodyReturns400WithTheNameMessage() throws Exception {
        assertJson(send("POST", "/students", ""), 400, NAME_REQUIRED_BODY);
    }

    @Test
    void malformedPercentEscapeInTheFormBodyReturns400WithTheFixedMessage() throws Exception {
        assertJson(send("POST", "/students", "name=%ZZ&email=a%40b.c&department=CS&phone="), 400, MALFORMED_FORM_BODY);
    }

    // ---- create, get by id, delete ----

    @Test
    void createGetDeleteLifecycle() throws Exception {
        String name = uniqueName();
        Resp created = send("POST", "/students", form(name, "a@b.c", "CS", ""));
        assertEquals(201, created.status());
        String id = extractId(created.body());

        Resp fetched = send("GET", "/students/" + id);
        assertJson(fetched, 200, created.body());

        Resp deleted = send("DELETE", "/students/" + id);
        assertJson(deleted, 200, DELETED_TRUE_BODY);

        assertJson(send("GET", "/students/" + id), 404, NOT_FOUND_BODY);
        assertJson(send("DELETE", "/students/" + id), 404, NOT_FOUND_BODY);
    }

    @Test
    void getUnknownIdReturns404WithTheNotFoundBody() throws Exception {
        assertJson(send("GET", "/students/missing-" + UUID.randomUUID()), 404, NOT_FOUND_BODY);
    }

    @Test
    void deleteOfAMissingIdReturns404WithTheNotFoundBody() throws Exception {
        assertJson(send("DELETE", "/students/missing-" + UUID.randomUUID()), 404, NOT_FOUND_BODY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "OPTIONS"})
    void unsupportedMethodsOnAStudentIdReturn405(String method) throws Exception {
        assertJson(send(method, "/students/some-id"), 405, METHOD_NOT_ALLOWED_BODY);
    }

    @Test
    void headOnAStudentIdReturns405() throws Exception {
        assertEquals(405, send("HEAD", "/students/some-id").status());
    }

    // ---- /students/ with an empty id (DR-01 / decision 4) ----

    @Test
    void getWithAnEmptyIdIsTreatedAsAMissingStudent() throws Exception {
        assertJson(send("GET", "/students/"), 404, NOT_FOUND_BODY);
    }

    @Test
    void deleteWithAnEmptyIdIsTreatedAsAMissingStudent() throws Exception {
        assertJson(send("DELETE", "/students/"), 404, NOT_FOUND_BODY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "OPTIONS"})
    void otherMethodsWithAnEmptyIdReturn405(String method) throws Exception {
        assertJson(send(method, "/students/"), 405, METHOD_NOT_ALLOWED_BODY);
    }

    @Test
    void headWithAnEmptyIdReturns405() throws Exception {
        assertEquals(405, send("HEAD", "/students/").status());
    }

    // ---- /students/a/b: the whole remainder is the id ----

    @Test
    void getOfANestedPathLooksUpTheWholeRemainderAndIsNotFound() throws Exception {
        assertJson(send("GET", "/students/a/b"), 404, NOT_FOUND_BODY);
    }

    @Test
    void deleteOfANestedPathIsNotFound() throws Exception {
        assertJson(send("DELETE", "/students/a/b"), 404, NOT_FOUND_BODY);
    }

    @Test
    void postToANestedPathReturns405() throws Exception {
        assertJson(send("POST", "/students/a/b", form(uniqueName(), "a@b.c", "CS", "")), 405, METHOD_NOT_ALLOWED_BODY);
    }

    // ---- query string and /studentsabc ----

    @Test
    void queryStringIsIgnoredOnStudents() throws Exception {
        String name = uniqueName();
        send("POST", "/students", form(name, "a@b.c", "CS", ""));
        Resp resp = send("GET", "/students?x=1");
        assertEquals(200, resp.status());
        assertEquals("application/json", resp.contentType());
        assertTrue(resp.body().contains("\"name\":\"" + name + "\""));
    }

    // The Router makes /studentsabc an unknown path on every JDK, so there is no JDK branching here.
    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"})
    void studentsabcIsAnUnknownPathWith404(String method) throws Exception {
        String body = method.equals("POST") ? form(uniqueName(), "a@b.c", "CS", "") : null;
        Resp resp = send(method, "/studentsabc", body);
        if (method.equals("HEAD")) {
            assertEquals(404, resp.status());
        } else {
            assertJson(resp, 404, UNKNOWN_PATH_BODY);
        }
    }

    // ---- the remaining methods on /students/a/b and /students?x=1 (plan T0.1: every method) ----

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "PATCH", "OPTIONS"})
    void otherMethodsOnANestedPathReturn405(String method) throws Exception {
        assertJson(send(method, "/students/a/b"), 405, METHOD_NOT_ALLOWED_BODY);
    }

    @Test
    void headOnANestedPathReturns405() throws Exception {
        assertEquals(405, send("HEAD", "/students/a/b").status());
    }

    @Test
    void postWithAQueryStringCreatesAStudentLikePlainStudents() throws Exception {
        String name = uniqueName();
        Resp resp = send("POST", "/students?x=1", form(name, "a@b.c", "CS", ""));
        assertEquals(201, resp.status());
        assertEquals("application/json", resp.contentType());
        extractId(resp.body());
        assertTrue(resp.body().contains("\"name\":\"" + name + "\""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "PATCH", "DELETE", "OPTIONS"})
    void unsupportedMethodsWithAQueryStringReturn405(String method) throws Exception {
        assertJson(send(method, "/students?x=1"), 405, METHOD_NOT_ALLOWED_BODY);
    }

    @Test
    void headWithAQueryStringReturns405() throws Exception {
        assertEquals(405, send("HEAD", "/students?x=1").status());
    }

    // ---- unknown paths are answered by the Router with a structured 404 ----

    @ParameterizedTest
    @ValueSource(strings = {"/nope", "/index.html", "/favicon.ico"})
    void unknownPathsReturnAStructured404(String path) throws Exception {
        assertJson(send("GET", path), 404, UNKNOWN_PATH_BODY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE", "OPTIONS"})
    void unknownPathsReturnAStructured404ForAnyMethod(String method) throws Exception {
        assertJson(send(method, "/nope"), 404, UNKNOWN_PATH_BODY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/nope", "/index.html", "/favicon.ico"})
    void headOnUnknownPathsReturns404(String path) throws Exception {
        assertEquals(404, send("HEAD", path).status());
    }

    // ---- the 3 static routes ----

    @Test
    void getRootServesIndexHtml() throws Exception {
        assertIndexHtml(send("GET", "/"));
    }

    @Test
    void getStyleCssServesTheCssFile() throws Exception {
        Resp resp = send("GET", "/style.css");
        assertEquals(200, resp.status());
        assertEquals("text/css; charset=UTF-8", resp.contentType());
        assertEquals(frontendFile("style.css"), resp.body());
    }

    @Test
    void getAppJsServesTheScriptFile() throws Exception {
        Resp resp = send("GET", "/app.js");
        assertEquals(200, resp.status());
        assertEquals("application/javascript; charset=UTF-8", resp.contentType());
        assertEquals(frontendFile("app.js"), resp.body());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE", "OPTIONS"})
    void staticRoutesAnswerOtherMethodsWith405(String method) throws Exception {
        assertJson(send(method, "/"), 405, METHOD_NOT_ALLOWED_BODY);
        assertJson(send(method, "/style.css"), 405, METHOD_NOT_ALLOWED_BODY);
        assertJson(send(method, "/app.js"), 405, METHOD_NOT_ALLOWED_BODY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/style.css", "/app.js"})
    void headOnStaticRoutesReturns405(String path) throws Exception {
        assertEquals(405, send("HEAD", path).status());
    }
}
