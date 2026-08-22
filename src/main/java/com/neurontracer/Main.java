package com.neurontracer;

import com.neurontracer.http.ErrorFilter;
import com.neurontracer.http.Http;
import com.neurontracer.http.StaticFileHandler;
import com.neurontracer.http.TraceHandler;
import com.neurontracer.trace.TraceJobs;
import com.neurontracer.trace.TraceService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

/** Starts the Neuron Tracer web app: static front end plus the tracing endpoint. */
public final class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());
    private static final int SHUTDOWN_GRACE_SECONDS = 10;

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        // No display on a Pi, and ImageIO must not spill uploads into a temp directory.
        System.setProperty("java.awt.headless", "true");
        ImageIO.setUseCache(false);

        AppConfig config;
        try {
            config = AppConfig.fromEnv();
        } catch (IllegalArgumentException e) {
            LOG.severe("Bad configuration: " + e.getMessage());
            System.exit(2);
            return;
        }

        ThreadPoolExecutor tracePool = new ThreadPoolExecutor(
                config.traceConcurrency(), config.traceConcurrency(),
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(config.traceQueueDepth()),
                namedThreads("trace"),
                new ThreadPoolExecutor.AbortPolicy());

        ExecutorService httpPool = Executors.newFixedThreadPool(config.httpThreads(), namedThreads("http"));

        TraceService traceService = new TraceService(config.maxPixels());
        TraceJobs traceJobs = new TraceJobs(
                config.resultRetention(), config.traceTimeout(), config.maxStoredResults());
        TraceHandler traceHandler = new TraceHandler(traceService, traceJobs, tracePool, config.maxUploadBytes());

        HttpServer server = HttpServer.create(new InetSocketAddress(config.bindAddress(), config.port()), config.backlog());
        ErrorFilter errorFilter = new ErrorFilter();
        server.createContext("/api/trace", traceHandler).getFilters().add(errorFilter);
        server.createContext("/healthz", exchange -> Http.sendJson(exchange, 200, Map.of("status", "ok"))).getFilters().add(errorFilter);
        server.createContext("/", new StaticFileHandler()).getFilters().add(errorFilter);
        server.setExecutor(httpPool);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(server, tracePool, httpPool), "shutdown"));

        server.start();
        LOG.info(() -> "Neuron Tracer listening on %s:%d (%d concurrent traces, %ds timeout, %d MB upload limit)"
                .formatted(config.bindAddress(), config.port(), config.traceConcurrency(),
                        config.traceTimeout().toSeconds(), config.maxUploadBytes() / (1024 * 1024)));
    }

    private static void shutdown(HttpServer server, ExecutorService tracePool, ExecutorService httpPool) {
        LOG.info("Shutting down");
        server.stop(SHUTDOWN_GRACE_SECONDS);
        tracePool.shutdownNow();
        httpPool.shutdownNow();
    }

    private static ThreadFactory namedThreads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
