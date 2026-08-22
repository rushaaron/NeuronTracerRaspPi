package com.neurontracer;

import java.time.Duration;

/**
 * Runtime settings, all read from the environment so the same image can be tuned per host.
 * The defaults are sized for a Raspberry Pi rather than a big server.
 *
 * @param bindAddress     address the HTTP server listens on
 * @param port            port the HTTP server listens on
 * @param backlog         accept queue depth for the listening socket
 * @param httpThreads     threads serving HTTP requests
 * @param maxUploadBytes  largest upload accepted
 * @param maxPixels       largest image, in pixels, that will be traced
 * @param traceConcurrency traces allowed to run at the same time
 * @param traceQueueDepth  traces allowed to wait for a slot before requests are rejected
 * @param traceTimeout     how long a single trace may run before it is abandoned
 * @param resultRetention  how long a finished result is held for the browser to collect
 * @param maxStoredResults how many results may be held in memory at once
 */
public record AppConfig(String bindAddress,
                        int port,
                        int backlog,
                        int httpThreads,
                        long maxUploadBytes,
                        long maxPixels,
                        int traceConcurrency,
                        int traceQueueDepth,
                        Duration traceTimeout,
                        Duration resultRetention,
                        int maxStoredResults) {

    public static AppConfig fromEnv() {
        int cpus = Runtime.getRuntime().availableProcessors();

        return new AppConfig(
                env("BIND_ADDRESS", "0.0.0.0"),
                intEnv("PORT", 8080),
                intEnv("BACKLOG", 32),
                intEnv("HTTP_THREADS", Math.max(4, cpus)),
                intEnv("MAX_UPLOAD_MB", 25) * 1024L * 1024L,
                intEnv("MAX_IMAGE_MEGAPIXELS", 40) * 1_000_000L,
                intEnv("TRACE_CONCURRENCY", Math.max(1, Math.min(4, cpus / 2))),
                intEnv("TRACE_QUEUE_DEPTH", 4),
                Duration.ofSeconds(intEnv("TRACE_TIMEOUT_SECONDS", 300)),
                Duration.ofSeconds(intEnv("RESULT_RETENTION_SECONDS", 600)),
                intEnv("MAX_STORED_RESULTS", 16));
    }

    public AppConfig {
        port = require(port, 1, 65535, "PORT");
        backlog = require(backlog, 0, 4096, "BACKLOG");
        httpThreads = require(httpThreads, 1, 256, "HTTP_THREADS");
        traceConcurrency = require(traceConcurrency, 1, 32, "TRACE_CONCURRENCY");
        traceQueueDepth = require(traceQueueDepth, 1, 256, "TRACE_QUEUE_DEPTH");
        maxStoredResults = require(maxStoredResults, 1, 256, "MAX_STORED_RESULTS");
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int intEnv(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a whole number, got: " + value, e);
        }
    }

    private static int require(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("%s must be between %d and %d, got: %d".formatted(name, min, max, value));
        }
        return value;
    }
}
