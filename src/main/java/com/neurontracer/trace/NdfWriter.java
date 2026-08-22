package com.neurontracer.trace;

import java.util.List;

/**
 * Renders traced branches as a NeuronJ data file, so the results can be opened in the
 * NeuronJ plugin for Fiji and hand-corrected from where the tracer left off.
 */
public final class NdfWriter {

    private static final String HEADER = """
            // NeuronJ Data File - DO NOT CHANGE
            1.4.3
            // Parameters
            1
            1.0
            0.7
            2
            1100
            3
            5
            1
            // Type names and colors
            Default
            4
            Axon
            7
            Dendrite
            1
            Primary
            7
            Secondary
            1
            Tertiary
            8
            Type 06
            4
            Type 07
            4
            Type 08
            4
            Type 09
            4
            Type 10
            4
            // Cluster names
            Default
            Cluster 01
            Cluster 02
            Cluster 03
            Cluster 04
            Cluster 05
            Cluster 06
            Cluster 07
            Cluster 08
            Cluster 09
            Cluster 10
            """;

    private static final String FOOTER = "// End of NeuronJ Data File";

    private NdfWriter() {
    }

    public static String write(List<Branch> branches) {
        StringBuilder ndf = new StringBuilder(HEADER);

        int tracing = 1;
        for (Branch branch : branches) {
            ndf.append("// Tracing N").append(tracing).append('\n')
               .append(tracing).append("\n0\n0\nDefault\n")
               .append("// Segment 1 of Tracing N").append(tracing).append('\n');
            for (Trips point : branch.getPoints()) {
                ndf.append(point.x).append('\n').append(point.y).append('\n');
            }
            tracing++;
        }

        return ndf.append(FOOTER).toString();
    }
}
