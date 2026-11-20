package org.example.api;

import org.example.model.Student;
import org.example.service.StudentService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Routing of the Router (AC-E1-01, AC-E1-04, AC-E1-05, AC-E1-09 for handler exceptions, AC-E1-12).
 * Every server binds the loopback address on port 0, never a wildcard address and never port 8000.
 * The missing static file case belongs to task E1-5 and is not tested here.
 */
class ApiRoutingTest {

    private static final String METHOD_NOT_ALLOWED_BODY = "{\"error\":\"Method not allowed\",\"code\":\"METHOD_NOT_ALLOWED\"}";
    private static final String UNKNOWN_PATH_BODY = "{\"error\":\"Not found\",\"code\":\"NOT_FOUND\"}";
    private static final String INTERNAL_ERROR_BODY = "{\"error\":\"Internal server error\",\"code\":\"INTERNAL_SERVER_ERROR\"}";
    private static final String STUDENT_NOT_FOUND_BODY = "{\"error\":\"Student not found\",\"code\":\"NOT_FOUND\"}";
    private static final String[] STATIC_PATHS = {"/", "/style.css", "/app.js"};

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

    private static Resp send(int port, String method, String pathAndQuery, String body) throws IOException, InterruptedException {
        HttpRequest.BodyPublisher publisher = body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl(port) + pathAndQuery))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .method(method, publisher)
            .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Resp(response.statusCode(), response.headers().firstValue("Content-Type").orElse(null), response.body());
    }

    private static Resp send(String method, String pathAndQuery, String body) throws IOException, InterruptedException {
        return send(server.port(), method, pathAndQuery, body);
    }

    private static String validForm() {
        String name = URLEncoder.encode("routing-" + UUID.randomUUID(), StandardCharsets.UTF_8);
        return "name=" + name + "&email=a%40b.c&department=CS&phone=";
    }

    private static String frontendFile(String name) throws IOException {
        return Files.readString(Path.of("frontend", name), StandardCharsets.UTF_8);
    }

    private static void assertJson(Resp resp, int status, String body, String what) {
        assertEquals(status, resp.status(), what);
        assertEquals("application/json", resp.contentType(), what);
        assertEquals(body, resp.body(), what);
    }

    // ---- AC-E1-01: every listed route is dispatched to its handler ----

    @Test
    void everyListedRouteIsDispatchedToItsHandler() throws Exception {
        List<StudentApiServer.Route> all = new java.util.ArrayList<>(server.routes());
        all.addAll(server.staticRoutes());
        assertFalse(all.isEmpty());
        for (StudentApiServer.Route route : all) {
            String path = route.path().replace("{id}", "route-id-" + UUID.randomUUID());
            String body = route.method().equals("POST") ? validForm() : null;
            Resp resp = send(route.method(), path, body);
            String what = route.method() + " " + route.path();
            assertNotEquals(405, resp.status(), what);
            assertNotEquals(METHOD_NOT_ALLOWED_BODY, resp.body(), what);
            assertNotEquals(UNKNOWN_PATH_BODY, resp.body(), what);
        }
    }

    // ---- RV1-01: each listed route reaches its OWN handler (positive proof, AC-E1-01) ----

    @Test
    void everyListedRouteIsConnectedToItsOwnHandler() throws Exception {
        StudentApiServer fresh = startLoopbackServer(new StudentService());
        try {
            java.util.Set<String> listed = new java.util.TreeSet<>();
            for (StudentApiServer.Route route : fresh.routes()) {
                listed.add(route.method() + " " + route.path());
            }
            for (StudentApiServer.Route route : fresh.staticRoutes()) {
                listed.add(route.method() + " " + route.path());
            }
            java.util.Set<String> verified = new java.util.TreeSet<>();
            int port = fresh.port();
            String idPrefix = "{\"id\":\"";

            Resp created = send(port, "POST", "/students", validForm());
            assertEquals(201, created.status(), "POST /students");
            assertTrue(created.body().startsWith(idPrefix), "POST /students returns the created Student object");
            String id = created.body().substring(idPrefix.length(), created.body().indexOf('"', idPrefix.length()));
            verified.add("POST /students");

            Resp list = send(port, "GET", "/students", null);
            assertEquals(200, list.status(), "GET /students");
            assertEquals("[" + created.body() + "]", list.body(), "GET /students lists exactly the created student");
            verified.add("GET /students");

            Resp fetched = send(port, "GET", "/students/" + id, null);
            assertEquals(200, fetched.status(), "GET /students/{id}");
            assertEquals(created.body(), fetched.body(), "GET /students/{id} returns that one student");
            verified.add("GET /students/{id}");

            assertJson(send(port, "DELETE", "/students/" + id, null), 200, "{\"deleted\":true}", "DELETE /students/{id}");
            assertJson(send(port, "GET", "/students/" + id, null), 404, STUDENT_NOT_FOUND_BODY, "the deleted student is gone");
            verified.add("DELETE /students/{id}");

            String[][] statics = {{"/", "index.html"}, {"/style.css", "style.css"}, {"/app.js", "app.js"}};
            for (String[] entry : statics) {
                Resp resp = send(port, "GET", entry[0], null);
                assertEquals(200, resp.status(), "GET " + entry[0]);
                assertEquals(frontendFile(entry[1]), resp.body(), "GET " + entry[0] + " serves " + entry[1]);
                verified.add("GET " + entry[0]);
            }

            assertEquals(listed, verified, "every listed route is verified, and every verified route is listed");
        } finally {
            fresh.stop();
        }
    }

    @Test
    void everyMethodNotListedForAListedPathGives405() throws Exception {
        List<StudentApiServer.Route> all = new java.util.ArrayList<>(server.routes());
        all.addAll(server.staticRoutes());
        java.util.Set<String> paths = new java.util.TreeSet<>();
        for (StudentApiServer.Route route : all) {
            paths.add(route.path());
        }
        assertFalse(paths.isEmpty());
        for (String path : paths) {
            java.util.Set<String> listedMethods = new java.util.TreeSet<>();
            for (StudentApiServer.Route route : all) {
                if (route.path().equals(path)) {
                    listedMethods.add(route.method());
                }
            }
            String concrete = path.replace("{id}", "not-listed-" + UUID.randomUUID());
            for (String method : new String[] {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"}) {
                if (listedMethods.contains(method)) {
                    continue;
                }
                String what = method + " " + path;
                Resp resp = send(method, concrete, method.equals("POST") ? validForm() : null);
                if (method.equals("HEAD")) {
                    assertEquals(405, resp.status(), what);
                } else {
                    assertJson(resp, 405, METHOD_NOT_ALLOWED_BODY, what);
                }
            }
        }
    }

    // ---- every method on the student paths ----

    @ParameterizedTest
    @CsvSource({
        "/students,GET,200", "/students,POST,201", "/students,PUT,405", "/students,PATCH,405",
        "/students,DELETE,405", "/students,HEAD,405", "/students,OPTIONS,405",
        "/students/some-id,GET,404", "/students/some-id,POST,405", "/students/some-id,PUT,405", "/students/some-id,PATCH,405",
        "/students/some-id,DELETE,404", "/students/some-id,HEAD,405", "/students/some-id,OPTIONS,405",
        "/students/,GET,404", "/students/,POST,405", "/students/,PUT,405", "/students/,PATCH,405",
        "/students/,DELETE,404", "/students/,HEAD,405", "/students/,OPTIONS,405",
        "/students/a/b,GET,404", "/students/a/b,POST,405", "/students/a/b,PUT,405", "/students/a/b,PATCH,405",
        "/students/a/b,DELETE,404", "/students/a/b,HEAD,405", "/students/a/b,OPTIONS,405"
    })
    void everyMethodOnTheStudentPathsGivesTheExpectedStatus(String path, String method, int status) throws Exception {
        String what = method + " " + path;
        Resp resp = send(method, path, method.equals("POST") ? validForm() : null);
        assertEquals(status, resp.status(), what);
        if (status == 405 && !method.equals("HEAD")) {
            assertJson(resp, 405, METHOD_NOT_ALLOWED_BODY, what);
        }
    }

    // ---- unknown paths ----

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"})
    void unknownPathsGiveAStructured404ForEveryMethod(String method) throws Exception {
        String[] paths = {"/nope", "/index.html", "/favicon.ico", "/studentsabc", "/studentsabc/x", "/style.cssx", "/app.jsx"};
        for (String path : paths) {
            String what = method + " " + path;
            Resp resp = send(method, path, method.equals("POST") ? validForm() : null);
            if (method.equals("HEAD")) {
                assertEquals(404, resp.status(), what);
            } else {
                assertJson(resp, 404, UNKNOWN_PATH_BODY, what);
            }
        }
    }

    @Test
    void getOfAnUnknownStudentIdIsAStudentLookupNotAnUnknownPath() throws Exception {
        Resp resp = send("GET", "/students/unknown-" + UUID.randomUUID(), null);
        assertJson(resp, 404, STUDENT_NOT_FOUND_BODY, "GET /students/unknown-id");
        assertNotEquals(UNKNOWN_PATH_BODY, resp.body());
    }

    // ---- static routes ----

    @ParameterizedTest
    @CsvSource({
        "/,index.html,text/html; charset=UTF-8",
        "/style.css,style.css,text/css; charset=UTF-8",
        "/app.js,app.js,application/javascript; charset=UTF-8"
    })
    void getOnAStaticRouteServesTheFrontendFile(String path, String file, String contentType) throws Exception {
        Resp resp = send("GET", path, null);
        assertEquals(200, resp.status(), path);
        assertEquals(contentType, resp.contentType(), path);
        assertEquals(frontendFile(file), resp.body(), path);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"})
    void otherMethodsOnAStaticRouteGiveAStructured405(String method) throws Exception {
        for (String path : STATIC_PATHS) {
            String what = method + " " + path;
            Resp resp = send(method, path, null);
            if (method.equals("HEAD")) {
                assertEquals(405, resp.status(), what);
            } else {
                assertJson(resp, 405, METHOD_NOT_ALLOWED_BODY, what);
            }
        }
    }

    // ---- handler exceptions ----

    @Test
    void aThrowingHandlerGivesAStructured500WithoutLeakingTheMessage() throws Exception {
        StudentService failing = new StudentService() {
            @Override
            public List<Student> getStudents() {
                throw new RuntimeException("boom");
            }
        };
        StudentApiServer failingServer = startLoopbackServer(failing);
        try {
            Resp resp = send(failingServer.port(), "GET", "/students", null);
            assertJson(resp, 500, INTERNAL_ERROR_BODY, "GET /students with a throwing service");
            assertFalse(resp.body().contains("boom"));
        } finally {
            failingServer.stop();
        }
    }

    // ---- query string ----

    @Test
    void theQueryStringIsIgnoredForRouting() throws Exception {
        assertEquals(200, send("GET", "/students?x=1", null).status());
        assertEquals(201, send("POST", "/students?x=1", validForm()).status());
        assertEquals(405, send("DELETE", "/students?x=1", null).status());
        assertEquals(404, send("GET", "/nope?x=1", null).status());
    }
}
