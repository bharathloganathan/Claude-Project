package org.example.api;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A missing frontend file must give a structured 500 and must not stop the server (task E1-5,
 * FR-E1-03, FR-E1-04, AC-E1-09). The static handlers read {@code frontend/} relative to the working
 * directory, so the only way to remove those files without touching the repository is a child JVM
 * with another working directory. Two children are started, each running MissingFrontendServerMain
 * on a loopback ephemeral port: a control child in the project directory, which has frontend/, and a
 * child in a fresh temporary directory, which has none. Only the children started here are stopped.
 */
class MissingFrontendSubprocessTest {

    private static final String INTERNAL_ERROR_BODY = "{\"error\":\"Internal server error\",\"code\":\"INTERNAL_SERVER_ERROR\"}";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private static Child control;
    private static Child missing;
    private static Path tempDirectory;

    private record Resp(int status, String contentType, String body) {
    }

    // A started child JVM. The first output line is "PORT n". The rest of its output is drained and kept.
    private static final class Child {
        private final Process process;
        private final StringBuffer output = new StringBuffer();
        private final CompletableFuture<String> firstLine = new CompletableFuture<>();
        private int port;

        Child(Path workingDirectory) throws IOException {
            String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            ProcessBuilder builder = new ProcessBuilder(List.of(
                javaExecutable, "-cp", System.getProperty("java.class.path"), MissingFrontendServerMain.class.getName()));
            builder.directory(workingDirectory.toFile());
            builder.redirectErrorStream(true);
            this.process = builder.start();
            Thread reader = new Thread(this::readOutput, "child-output-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readOutput() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String first = in.readLine();
                if (first != null) {
                    output.append(first).append('\n');
                }
                firstLine.complete(first);
                String line;
                while ((line = in.readLine()) != null) {
                    output.append(line).append('\n');
                }
            } catch (IOException e) {
                firstLine.completeExceptionally(e);
            } finally {
                firstLine.complete(null);
            }
        }

        void awaitPort() throws Exception {
            String first;
            try {
                first = firstLine.get(30, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                first = null;
            }
            if (first == null || !first.matches("PORT [0-9]+")) {
                fail("the child did not report a port, first line: " + first + ", captured output: " + output
                    + ", java.class.path: " + System.getProperty("java.class.path"));
            }
            port = Integer.parseInt(first.substring("PORT ".length()));
        }

        void stop() throws InterruptedException {
            try {
                process.getOutputStream().close();
            } catch (IOException e) {
                // the child is already gone
            }
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor();
            }
        }
    }

    @BeforeAll
    static void startChildren() throws Exception {
        Path projectDirectory = Path.of("").toAbsolutePath();
        tempDirectory = Files.createTempDirectory("missing-frontend-");
        assertFalse(Files.exists(tempDirectory.resolve("frontend")), "the temporary directory must have no frontend/");
        assertTrue(Files.isDirectory(projectDirectory.resolve("frontend")), "the project directory must have frontend/");

        control = new Child(projectDirectory);
        missing = new Child(tempDirectory);
        control.awaitPort();
        missing.awaitPort();
    }

    @AfterAll
    static void stopChildren() throws Exception {
        try {
            if (control != null) {
                control.stop();
            }
            if (missing != null) {
                missing.stop();
            }
        } finally {
            // Only after both children have exited: a working directory is locked while a process uses it.
            if (tempDirectory != null) {
                Files.deleteIfExists(tempDirectory);
            }
        }
    }

    // ---- helpers ----

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

    private static Resp get(Child child, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl(child.port) + path))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Resp(response.statusCode(), response.headers().firstValue("Content-Type").orElse(null), response.body());
    }

    // ---- tests ----

    @Test
    void controlChildWithTheFrontendDirectoryServesIndexHtml() throws Exception {
        Resp resp = get(control, "/");
        assertEquals(200, resp.status());
        assertEquals("text/html; charset=UTF-8", resp.contentType());
        assertFalse(resp.body().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/style.css", "/app.js"})
    void aMissingFrontendFileGivesAStructured500WithoutLeakingPaths(String path) throws Exception {
        Resp resp = get(missing, path);
        assertEquals(500, resp.status(), path);
        assertEquals("application/json", resp.contentType(), path);
        assertEquals(INTERNAL_ERROR_BODY, resp.body(), path);
        assertNotNull(JsonChecker.parse(resp.body()), path);
        for (String forbidden : new String[] {"frontend", "NoSuchFile", "Exception", ".html", ".css", ".js", tempDirectory.toString()}) {
            assertFalse(resp.body().contains(forbidden), path + " body must not contain " + forbidden);
        }
    }

    @Test
    void theServerKeepsAnsweringStudentRequestsAfterTheFilesAreMissing() throws Exception {
        Resp resp = get(missing, "/students");
        assertEquals(200, resp.status());
        assertEquals("application/json", resp.contentType());
        assertEquals("[]", resp.body());
    }
}
