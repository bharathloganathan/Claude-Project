package org.example.api;

import org.example.service.StudentService;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Test-only child process main for MissingFrontendSubprocessTest (task E1-5). It binds the loopback
 * address on an ephemeral port (never port 8000), prints "PORT n" as its only output, then blocks
 * on standard input until the parent closes it, stops the server and exits. A daemon timer ends the
 * process after 60 seconds, so an orphan can never outlive the test run.
 */
public final class MissingFrontendServerMain {

    private static final long MAX_LIFETIME_MILLIS = 60_000L;

    private MissingFrontendServerMain() {
    }

    public static void main(String[] args) throws Exception {
        StudentApiServer server = new StudentApiServer(new StudentService(),
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        server.start();
        System.out.println("PORT " + server.port());
        System.out.flush();

        Thread cap = new Thread(() -> {
            try {
                Thread.sleep(MAX_LIFETIME_MILLIS);
            } catch (InterruptedException e) {
                return;
            }
            server.stop();
            System.exit(0);
        });
        cap.setDaemon(true);
        cap.start();

        while (System.in.read() != -1) {
            // drain until the parent closes standard input
        }
        server.stop();
        System.exit(0);
    }
}
