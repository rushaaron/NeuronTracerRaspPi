package com.neurontracer.trace;

import java.util.Locale;

/**
 * Validated tuning knobs for a tracing run. Every value is clamped to the range the
 * tracer can actually work with, so a hand-crafted request cannot push it out of bounds.
 *
 * @param branchShade     red channel cut-off used while following any branch
 * @param cellBranchShade red channel cut-off used while finding branches leaving the cell body
 * @param xOffset         horizontal distance from the cell body to start searching from
 * @param yOffset         vertical distance from the cell body to start searching from
 * @param sideToIgnore    side of the cell body to drop (the axon), or {@link #SIDE_NONE}
 */
public record TraceOptions(int branchShade, int cellBranchShade, int xOffset, int yOffset, int sideToIgnore) {

    public static final int DEFAULT_BRANCH_SHADE = 230;
    public static final int DEFAULT_CELL_BRANCH_SHADE = 220;
    public static final int DEFAULT_OFFSET = 40;

    private static final int MIN_SHADE = 5;
    private static final int MAX_SHADE = 254;
    private static final int MIN_OFFSET = 5;
    private static final int MAX_OFFSET = 100;

    /**
     * Side numbering used by {@link Tracer}, laid out around the cell body as:
     * <pre>
     *     3
     * 1 -   - 2
     *     4
     * </pre>
     */
    public static final int SIDE_NONE = 0;
    public static final int SIDE_WEST = 1;
    public static final int SIDE_EAST = 2;
    public static final int SIDE_NORTH = 3;
    public static final int SIDE_SOUTH = 4;

    public static TraceOptions defaults() {
        return new TraceOptions(DEFAULT_BRANCH_SHADE, DEFAULT_CELL_BRANCH_SHADE, DEFAULT_OFFSET, DEFAULT_OFFSET, SIDE_NONE);
    }

    public TraceOptions {
        branchShade = clamp(branchShade, MIN_SHADE, MAX_SHADE);
        cellBranchShade = clamp(cellBranchShade, MIN_SHADE, MAX_SHADE);
        xOffset = clamp(xOffset, MIN_OFFSET, MAX_OFFSET);
        yOffset = clamp(yOffset, MIN_OFFSET, MAX_OFFSET);
        sideToIgnore = clamp(sideToIgnore, SIDE_NONE, SIDE_SOUTH);
    }

    /** Maps the compass letter the web form collects onto the tracer's side numbering. */
    public static int sideFromCompass(String compass) {
        if (compass == null || compass.isBlank()) {
            return SIDE_NONE;
        }
        return switch (compass.trim().toUpperCase(Locale.ROOT)) {
            case "W" -> SIDE_WEST;
            case "E" -> SIDE_EAST;
            case "N" -> SIDE_NORTH;
            case "S" -> SIDE_SOUTH;
            default -> SIDE_NONE;
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }
}
