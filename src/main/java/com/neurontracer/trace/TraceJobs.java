package com.neurontracer.trace;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * In-memory registry of tracing jobs. Ids are random enough to act as the only token
 * needed to collect a result, and finished jobs are dropped once they age out so an
 * image never lingers longer than it takes the browser to download it.
 */
public final class TraceJobs {

    private static final Logger LOG = Logger.getLogger(TraceJobs.class.getName());

    private static final int ID_BYTES = 18;

    private final Map<String, TraceJob> jobs = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder ids = Base64.getUrlEncoder().withoutPadding();

    private final Duration retention;
    private final Duration traceTimeout;
    private final int maxJobs;

    public TraceJobs(Duration retention, Duration traceTimeout, int maxJobs) {
        this.retention = retention;
        this.traceTimeout = traceTimeout;
        this.maxJobs = maxJobs;
    }

    /**
     * Registers a job and starts it running.
     *
     * @return the job, or {@code null} when the tracer has no room to take it on
     */
    public TraceJob submit(ExecutorService executor,
                           TraceService service,
                           byte[] upload,
                           TraceOptions options,
                           String baseName) {
        TraceJob job = create();
        if (job == null) {
            return null;
        }

        try {
            Future<?> worker = executor.submit(() -> run(job, service, upload, options, baseName));
            job.attach(worker);
            return job;
        } catch (RejectedExecutionException e) {
            drop(job);
            return null;
        }
    }

    private static void run(TraceJob job, TraceService service, byte[] upload, TraceOptions options, String baseName) {
        try {
            job.succeed(service.trace(upload, options, baseName));
        } catch (TraceService.UnsupportedImageException e) {
            job.fail(415, e.getMessage());
        } catch (OutOfMemoryError e) {
            LOG.log(Level.SEVERE, "Ran out of memory while tracing", e);
            job.fail(507, "The server ran out of memory tracing that image. Try a smaller one.");
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.SEVERE, "Tracing failed", e);
            job.fail(500, "Something went wrong while tracing that image.");
        }
    }

    private TraceJob create() {
        sweep();
        if (jobs.size() >= maxJobs) {
            return null;
        }

        byte[] entropy = new byte[ID_BYTES];
        random.nextBytes(entropy);
        TraceJob job = new TraceJob(ids.encodeToString(entropy));
        jobs.put(job.id(), job);
        return job;
    }

    public TraceJob find(String id) {
        return id == null ? null : jobs.get(id);
    }

    public void drop(TraceJob job) {
        job.abandon();
        jobs.remove(job.id());
    }

    /**
     * Gives up on jobs that have run past the trace timeout and forgets finished ones that
     * have aged out. Called before taking on new work and on every poll, which is often
     * enough that nothing lingers even if a browser walks away mid-trace.
     */
    public void sweep() {
        long retentionMillis = retention.toMillis();
        long timeoutMillis = traceTimeout.toMillis();

        jobs.values().removeIf(job -> {
            if (!job.isComplete()) {
                if (job.runningMillis() > timeoutMillis) {
                    timeOut(job);
                }
                return false;
            }
            return job.completedMillisAgo() > retentionMillis;
        });
    }

    /** Marks a job as timed out and stops its worker. */
    private void timeOut(TraceJob job) {
        job.abandon();
        job.fail(504, "Tracing this image took longer than %d seconds. Try a smaller image."
                .formatted(traceTimeout.toSeconds()));
    }
}
