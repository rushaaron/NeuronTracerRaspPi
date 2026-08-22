package com.neurontracer.http;

import com.neurontracer.trace.TraceJob;
import com.neurontracer.trace.TraceJobs;
import com.neurontracer.trace.TraceOptions;
import com.neurontracer.trace.TraceResult;
import com.neurontracer.trace.TraceService;
import com.neurontracer.trace.TraceStats;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * The tracing endpoint, in two halves.
 *
 * <p>{@code POST /api/trace} takes the raw image bytes as the request body with the tuning
 * knobs as query parameters, which keeps the server free of any multipart or JSON parsing.
 * It answers immediately with a job id rather than holding the connection open, because a
 * trace can easily outlast a proxy's idle timeout.
 *
 * <p>{@code GET /api/trace/{id}} reports progress and, once the trace is done, returns the
 * zip and a preview image base64 encoded in a JSON envelope. Results live in memory only.
 */
public final class TraceHandler implements HttpHandler {

    private static final String BASE_PATH = "/api/trace";
    private static final String DEFAULT_BASE_NAME = "neuron";
    private static final int MAX_BASE_NAME_LENGTH = 60;
    private static final int POLL_SECONDS = 2;

    private final TraceService service;
    private final TraceJobs jobs;
    private final ExecutorService executor;
    private final long maxUploadBytes;

    public TraceHandler(TraceService service,
                        TraceJobs jobs,
                        ExecutorService executor,
                        long maxUploadBytes) {
        this.service = service;
        this.jobs = jobs;
        this.executor = executor;
        this.maxUploadBytes = maxUploadBytes;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");

        String jobId = jobIdFrom(exchange.getRequestURI().getPath());
        String method = exchange.getRequestMethod();

        if (jobId.isEmpty()) {
            if (!"POST".equalsIgnoreCase(method)) {
                exchange.getResponseHeaders().set("Allow", "POST");
                Http.sendError(exchange, 405, "Submit an image with POST.");
                return;
            }
            submit(exchange);
        } else {
            if (!"GET".equalsIgnoreCase(method)) {
                exchange.getResponseHeaders().set("Allow", "GET");
                Http.sendError(exchange, 405, "Collect a result with GET.");
                return;
            }
            collect(exchange, jobId);
        }
    }

    // ---- submitting -------------------------------------------------------

    private void submit(HttpExchange exchange) throws IOException {
        Map<String, String> params = Http.queryParams(exchange.getRequestURI().getRawQuery());
        TraceOptions options = new TraceOptions(
                Http.intParam(params, "branchShade", TraceOptions.DEFAULT_BRANCH_SHADE),
                Http.intParam(params, "cellBranchShade", TraceOptions.DEFAULT_CELL_BRANCH_SHADE),
                Http.intParam(params, "xOffset", TraceOptions.DEFAULT_OFFSET),
                Http.intParam(params, "yOffset", TraceOptions.DEFAULT_OFFSET),
                TraceOptions.sideFromCompass(params.get("ignore")));
        String baseName = baseNameOf(params.get("name"));

        byte[] upload;
        try {
            upload = Http.readBody(exchange, maxUploadBytes);
        } catch (Http.PayloadTooLargeException e) {
            Http.sendError(exchange, 413, "That image is larger than the %d MB upload limit."
                    .formatted(maxUploadBytes / (1024 * 1024)));
            return;
        }

        if (upload.length == 0) {
            Http.sendError(exchange, 400, "No image was uploaded.");
            return;
        }

        TraceJob job = jobs.submit(executor, service, upload, options, baseName);
        if (job == null) {
            busy(exchange);
            return;
        }

        String statusUrl = BASE_PATH + "/" + job.id();
        exchange.getResponseHeaders().set("Location", statusUrl);
        Http.sendJson(exchange, 202, Map.of(
                "jobId", job.id(),
                "statusUrl", statusUrl,
                "pollAfterSeconds", POLL_SECONDS));
    }

    private static void busy(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Retry-After", "30");
        Http.sendError(exchange, 503, "The tracer is busy with other images right now. Please try again in a moment.");
    }

    // ---- collecting -------------------------------------------------------

    private void collect(HttpExchange exchange, String jobId) throws IOException {
        // Polling is the most frequent traffic this server sees, so it is a good moment to
        // time out stalled traces and let go of results that have aged out.
        jobs.sweep();

        TraceJob job = jobs.find(jobId);
        if (job == null) {
            Http.sendError(exchange, 404, "That trace has expired. Please upload the image again.");
            return;
        }

        if (!job.isComplete()) {
            exchange.getResponseHeaders().set("Retry-After", String.valueOf(POLL_SECONDS));
            Http.sendJson(exchange, 202, Map.of(
                    "state", "running",
                    "elapsedSeconds", job.runningMillis() / 1000,
                    "pollAfterSeconds", POLL_SECONDS));
            return;
        }

        TraceJob.Failure failure = job.failure();
        if (failure != null) {
            jobs.drop(job);
            Http.sendError(exchange, failure.status(), failure.message());
            return;
        }

        // Kept until it ages out, so a dropped download can simply be retried.
        Http.sendJson(exchange, 200, envelope(job.result()));
    }

    private static Map<String, Object> envelope(TraceResult result) {
        Base64.Encoder base64 = Base64.getEncoder();
        TraceStats stats = result.stats();

        Map<String, Object> statsJson = new LinkedHashMap<>();
        statsJson.put("branches", stats.branches());
        statsJson.put("pixels", stats.pixels());
        statsJson.put("microns", stats.microns());
        statsJson.put("averageMicrons", stats.averageMicrons());
        statsJson.put("pixelsPerMicron", TraceStats.PIXELS_PER_MICRON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state", "done");
        body.put("filename", result.archiveName());
        body.put("stats", statsJson);
        body.put("preview", base64.encodeToString(result.preview()));
        body.put("archive", base64.encodeToString(result.archive()));
        return body;
    }

    // ---- helpers ----------------------------------------------------------

    /** Everything after {@code /api/trace/}, or an empty string for the collection itself. */
    private static String jobIdFrom(String path) {
        if (path == null || path.length() <= BASE_PATH.length()) {
            return "";
        }
        String remainder = path.substring(BASE_PATH.length());
        return remainder.startsWith("/") ? remainder.substring(1) : "";
    }

    /** Turns the uploaded file name into a safe stem for the files inside the archive. */
    private static String baseNameOf(String uploadedName) {
        if (uploadedName == null || uploadedName.isBlank()) {
            return DEFAULT_BASE_NAME;
        }

        String name = uploadedName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);

        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }

        String cleaned = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        if (cleaned.isEmpty() || cleaned.chars().allMatch(c -> c == '.')) {
            return DEFAULT_BASE_NAME;
        }
        return cleaned.length() > MAX_BASE_NAME_LENGTH ? cleaned.substring(0, MAX_BASE_NAME_LENGTH) : cleaned;
    }
}
