package com.neurontracer.http;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Turns anything a handler throws into a 500 instead of a dropped connection, so a bad
 * request can never take the response away from the browser without an explanation.
 */
public final class ErrorFilter extends Filter {

    private static final Logger LOG = Logger.getLogger(ErrorFilter.class.getName());

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        try {
            chain.doFilter(exchange);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, e, () -> "Unhandled error serving " + exchange.getRequestURI());
            try {
                Http.sendError(exchange, 500, "Something went wrong handling that request.");
            } catch (IOException | RuntimeException ignored) {
                // The response was already started, so there is nothing left to say.
                exchange.close();
            }
        }
    }

    @Override
    public String description() {
        return "Converts unhandled errors into 500 responses";
    }
}
