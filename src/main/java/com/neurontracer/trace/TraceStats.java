package com.neurontracer.trace;

/**
 * Summary measurements for a single tracing run.
 *
 * @param branches       number of branches the tracer kept
 * @param pixels         total number of traced pixels across all branches
 * @param microns        {@code pixels} converted with {@link #PIXELS_PER_MICRON}
 * @param averageMicrons average branch length in microns, or {@code 0} when nothing was traced
 */
public record TraceStats(int branches, int pixels, double microns, double averageMicrons) {

    /** Imaging scale the source microscope images were captured at. */
    public static final double PIXELS_PER_MICRON = 2.643;

    public static TraceStats of(int branches, int pixels) {
        double microns = pixels / PIXELS_PER_MICRON;
        double average = branches == 0 ? 0 : microns / branches;
        return new TraceStats(branches, pixels, microns, average);
    }

    /** Human readable summary, shipped in the archive as {@code stats.txt}. */
    public String toReport() {
        return """
               Neuron Tracer results
               =====================
               Scale                    : %.3f pixels/micron
               Total number of branches : %d
               Total number of pixels   : %d
               Total number of microns  : %.2f
               Average branch length    : %.2f microns
               """.formatted(PIXELS_PER_MICRON, branches, pixels, microns, averageMicrons);
    }
}
