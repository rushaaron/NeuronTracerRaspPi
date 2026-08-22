package com.neurontracer.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Serves the single page front end straight out of the jar, so the image stays read-only. */
public final class StaticFileHandler implements HttpHandler {

    private static final String ROOT = "/web";
    private static final String INDEX = "index.html";

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "svg", "image/svg+xml",
            "ico", "image/x-icon",
            "webmanifest", "application/manifest+json");

    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; "
            + "connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            Http.sendError(exchange, 405, "Only GET is supported here.");
            return;
        }

        String resource = resolve(exchange.getRequestURI());
        if (resource == null) {
            Http.send(exchange, 404, Http.TEXT, "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }

        try (InputStream in = StaticFileHandler.class.getResourceAsStream(resource)) {
            if (in == null) {
                Http.send(exchange, 404, Http.TEXT, "Not found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            byte[] body = in.readAllBytes();
            exchange.getResponseHeaders().set("Content-Security-Policy", CONTENT_SECURITY_POLICY);
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
            exchange.getResponseHeaders().set("Cache-Control", resource.endsWith(".html") ? "no-cache" : "public, max-age=3600");
            Http.send(exchange, 200, contentType(resource), body);
        }
    }

    /** Maps a request path to a classpath resource, or {@code null} if it escapes the web root. */
    private static String resolve(URI requestUri) {
        String path = requestUri.getPath();
        if (path == null || !path.startsWith("/") || path.contains("..") || path.contains("\\")) {
            return null;
        }
        if (path.endsWith("/")) {
            path += INDEX;
        }
        return ROOT + path;
    }

    private static String contentType(String resource) {
        int dot = resource.lastIndexOf('.');
        String extension = dot < 0 ? "" : resource.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        return CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
    }
}
