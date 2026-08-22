package com.neurontracer.http;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Response and request helpers shared by the handlers. */
public final class Http {

    public static final String JSON = "application/json; charset=utf-8";
    public static final String TEXT = "text/plain; charset=utf-8";

    private Http() {
    }

    public static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } else {
            exchange.close();
        }
    }

    public static void sendJson(HttpExchange exchange, int status, Map<String, ?> body) throws IOException {
        send(exchange, status, JSON, Json.write(body).getBytes(StandardCharsets.UTF_8));
    }

    public static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, Map.of("error", message));
    }

    /** Parses a query string into a map, keeping the first occurrence of each key. */
    public static Map<String, String> queryParams(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String key = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            params.putIfAbsent(decode(key), decode(value));
        }
        return params;
    }

    public static int intParam(Map<String, String> params, String name, int fallback) {
        try {
            String raw = params.get(name);
            return raw == null || raw.isBlank() ? fallback : Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Reads the whole request body, refusing anything over {@code maxBytes}.
     *
     * @throws PayloadTooLargeException when the body is larger than the limit
     */
    public static byte[] readBody(HttpExchange exchange, long maxBytes) throws IOException {
        long declared = declaredLength(exchange);
        if (declared > maxBytes) {
            throw new PayloadTooLargeException();
        }
        try (InputStream in = exchange.getRequestBody()) {
            // Reading one byte past the limit is what lets us catch a lying Content-Length.
            byte[] body = in.readNBytes((int) Math.min(maxBytes + 1, Integer.MAX_VALUE));
            if (body.length > maxBytes) {
                throw new PayloadTooLargeException();
            }
            return body;
        }
    }

    private static long declaredLength(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Content-Length");
        try {
            return header == null ? -1 : Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // A malformed percent escape is not worth failing the request over.
            return value;
        }
    }

    public static class PayloadTooLargeException extends IOException {
        private static final long serialVersionUID = 1L;

        public PayloadTooLargeException() {
            super("Request body is larger than the configured upload limit.");
        }
    }
}
