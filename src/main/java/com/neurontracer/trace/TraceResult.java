package com.neurontracer.trace;

/**
 * Everything a finished trace hands back. All of it lives in memory only -- nothing
 * about a submitted image is written to disk.
 *
 * @param archiveName suggested download name for {@code archive}
 * @param archive     zip holding the traced images, the NeuronJ data file and the stats report
 * @param preview     JPEG of the traced overlay, small enough to show in the browser
 * @param stats       measurements for the run
 */
public record TraceResult(String archiveName, byte[] archive, byte[] preview, TraceStats stats) {
}
