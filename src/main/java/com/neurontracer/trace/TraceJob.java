package com.neurontracer.trace;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * One tracing request, from submission through to a result the browser can collect.
 *
 * <p>Traces routinely outlive the 100 second timeout Cloudflare puts on an origin
 * response, so the browser submits a job, gets an id straight back, and polls for the
 * result. The job and its result live in memory only and are swept once they age out.
 */
public final class TraceJob {

    /** Why a job did not produce a result, in a shape the HTTP layer can hand straight to the client. */
    public record Failure(int status, String message) {
    }

    private final String id;
    private final long startedNanos = System.nanoTime();

    private volatile Future<?> worker;
    private volatile TraceResult result;
    private volatile Failure failure;
    private volatile long completedNanos;

    TraceJob(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public TraceResult result() {
        return result;
    }

    public Failure failure() {
        return failure;
    }

    public boolean isComplete() {
        return result != null || failure != null;
    }

    void attach(Future<?> worker) {
        this.worker = worker;
    }

    // First outcome wins: a worker that finishes after the job was already timed out is ignored.
    synchronized void succeed(TraceResult result) {
        if (isComplete()) {
            return;
        }
        this.result = result;
        this.completedNanos = System.nanoTime();
    }

    synchronized void fail(int status, String message) {
        if (isComplete()) {
            return;
        }
        this.failure = new Failure(status, message);
        this.completedNanos = System.nanoTime();
    }

    /** Milliseconds since the job was submitted. */
    public long runningMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    /** Milliseconds since the job finished, or {@code 0} while it is still running. */
    long completedMillisAgo() {
        return isComplete() ? TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - completedNanos) : 0;
    }

    /** Stops the worker if it is still going. Safe to call more than once. */
    void abandon() {
        Future<?> current = worker;
        if (current != null) {
            current.cancel(true);
        }
    }
}
