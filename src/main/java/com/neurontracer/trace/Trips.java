package com.neurontracer.trace;

/** A pixel coordinate plus the direction the tracer was heading when it got there. */
public class Trips {
    public final int x;
    public final int y;
    public final int d;

    public Trips(int x, int y, int d) {
        this.x = x;
        this.y = y;
        this.d = d;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Trips trips && x == trips.x && y == trips.y && d == trips.d;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * x + y) + d;
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + "," + d + ")";
    }
}
