package org.example.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Single "/" handler that dispatches only from the route lists it is given. */
final class Router implements HttpHandler {
    private final List<StudentApiServer.Route> routes = new ArrayList<>();

    Router(List<StudentApiServer.Route> apiRoutes, List<StudentApiServer.Route> staticRoutes) {
        routes.addAll(apiRoutes);
        routes.addAll(staticRoutes);
    }

    @Override
    public void handle(HttpExchange exchange) {
        try {
            dispatch(exchange);
        } catch (Exception e) {
            // Never put the exception text or a stack trace into the response.
            fail(exchange);
        }
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        boolean pathKnown = false;
        for (StudentApiServer.Route route : routes) {
            if (!matches(route.path(), path)) {
                continue;
            }
            pathKnown = true;
            if (route.method().equals(method)) {
                route.handler().handle(exchange);
                return;
            }
        }
        if (pathKnown) {
            sendError(exchange, ErrorCode.METHOD_NOT_ALLOWED, "Method not allowed");
        } else {
            sendError(exchange, ErrorCode.NOT_FOUND, "Not found");
        }
    }

    // A "/{id}" route accepts anything after its prefix, including an empty or nested id, as before.
    private static boolean matches(String routePath, String requestPath) {
        if (routePath.equals(requestPath)) {
            return true;
        }
        if (routePath.endsWith("/{id}")) {
            String prefix = routePath.substring(0, routePath.length() - "{id}".length());
            return requestPath.startsWith(prefix);
        }
        return false;
    }

    private static void sendError(HttpExchange exchange, ErrorCode code, String message) throws IOException {
        StudentApiServer.sendJson(exchange, code.status(), JsonWriter.error(code, message));
    }

    private static void fail(HttpExchange exchange) {
        if (exchange.getResponseCode() == -1) {
            try {
                sendError(exchange, ErrorCode.INTERNAL_SERVER_ERROR, "Internal server error");
                return;
            } catch (IOException | RuntimeException ignored) {
                // fall through and close
            }
        }
        exchange.close();
    }
}
