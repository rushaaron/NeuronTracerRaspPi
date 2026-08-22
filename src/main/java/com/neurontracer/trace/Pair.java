package com.neurontracer.trace;

/** An immutable pixel coordinate. */
public class Pair {
    public final int x;
    public final int y;

    public Pair(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Pair pair && x == pair.x && y == pair.y;
    }

    @Override
    public int hashCode() {
        return 31 * x + y;
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
