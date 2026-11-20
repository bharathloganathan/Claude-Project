package org.example.api;

import org.example.service.StudentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lifecycle of {@link StudentApiServer} (AC-E1-08). Every server binds the loopback address on
 * port 0, never a wildcard address and never port 8000, and owns its own service.
 */
class ApiLifecycleTest {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private final List<StudentApiServer> servers = new ArrayList<>();

    @AfterEach
    void stopServers() {
        for (StudentApiServer server : servers) {
            server.stop();
        }
    }

    private StudentApiServer startLoopbackServer() throws IOException {
        StudentApiServer server = new StudentApiServer(new StudentService(), new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        servers.add(server);
        server.start();
        return server;
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

    private static HttpResponse<String> send(String method, int port, String path, String body) throws IOException, InterruptedException {
        HttpRequest.BodyPublisher publisher = body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl(port) + path))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .method(method, publisher)
            .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Test
    void twoServersRunSideBySideWithoutInterfering() throws Exception {
        StudentApiServer a = startLoopbackServer();
        StudentApiServer b = startLoopbackServer();
        assertTrue(a.port() > 0);
        assertTrue(b.port() > 0);
        assertNotEquals(a.port(), b.port());
        assertEquals(200, send("GET", a.port(), "/students", null).statusCode());
        assertEquals(200, send("GET", b.port(), "/students", null).statusCode());

        String name = "lifecycle-" + UUID.randomUUID();
        String form = "name=" + URLEncoder.encode(name, StandardCharsets.UTF_8) + "&email=a%40b.c&department=CS&phone=";
        assertEquals(201, send("POST", a.port(), "/students", form).statusCode());

        assertTrue(send("GET", a.port(), "/students", null).body().contains("\"name\":\"" + name + "\""));
        assertFalse(send("GET", b.port(), "/students", null).body().contains(name));
    }

    @Test
    void portReportsTheActualBoundPort() throws Exception {
        StudentApiServer server = startLoopbackServer();
        assertTrue(server.port() > 0);
        assertEquals(200, send("GET", server.port(), "/students", null).statusCode());
    }

    @Test
    void stopFreesThePort() throws Exception {
        StudentApiServer server = startLoopbackServer();
        int port = server.port();
        assertEquals(200, send("GET", port, "/students", null).statusCode());

        server.stop();
        servers.remove(server);

        try (ServerSocket probe = new ServerSocket()) {
            probe.setReuseAddress(true);
            probe.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            assertEquals(port, probe.getLocalPort());
        }
        assertThrows(IOException.class, () -> send("GET", port, "/students", null));
    }
}
