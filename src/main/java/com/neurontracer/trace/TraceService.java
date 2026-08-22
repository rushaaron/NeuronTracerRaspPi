package com.neurontracer.trace;

import com.neurontracer.image.ImageUtils;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

/**
 * Runs a trace end to end: decode the upload, trace it, and pack the outputs into a zip.
 * Purely in-memory -- uploads and results are never written to disk.
 */
public final class TraceService {

    /** Longest edge of the preview JPEG returned alongside the archive. */
    private static final int PREVIEW_MAX_EDGE = 700;

    private final long maxPixels;

    public TraceService(long maxPixels) {
        this.maxPixels = maxPixels;
    }

    public TraceResult trace(byte[] upload, TraceOptions options, String baseName) throws IOException {
        BufferedImage image = decode(upload);

        BufferedImage overlay = ImageUtils.copyOf(image);
        BufferedImage onBlack = ImageUtils.copyOf(image);
        BufferedImage onWhite = ImageUtils.whiteCanvas(image);

        Tracer tracer = new Tracer(
                options.branchShade(),
                options.xOffset(),
                options.yOffset(),
                options.cellBranchShade(),
                options.sideToIgnore(),
                image, overlay, onWhite, onBlack);

        List<Branch> branches = tracer.createBranches();
        TraceStats stats = tracer.getStats();

        byte[] archive = pack(baseName, overlay, onWhite, onBlack, branches, stats);
        byte[] preview = jpeg(scaleToFit(overlay));

        return new TraceResult(baseName + "-trace.zip", archive, preview, stats);
    }

    private BufferedImage decode(byte[] upload) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(upload));
        if (image == null) {
            throw new UnsupportedImageException("That file could not be read as an image. Please upload a JPEG or PNG.");
        }

        long pixels = (long) image.getWidth() * image.getHeight();
        if (pixels > maxPixels) {
            throw new UnsupportedImageException(
                    "That image is %d x %d pixels, which is larger than this server will trace. Please scale it down first."
                            .formatted(image.getWidth(), image.getHeight()));
        }
        return image;
    }

    private byte[] pack(String baseName,
                        BufferedImage overlay,
                        BufferedImage onWhite,
                        BufferedImage onBlack,
                        List<Branch> branches,
                        TraceStats stats) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            addEntry(zip, baseName + "-traced.jpg", jpeg(overlay));
            addEntry(zip, baseName + "-traced-on-white.jpg", jpeg(onWhite));
            addEntry(zip, baseName + "-traced-on-black.jpg", jpeg(onBlack));
            addEntry(zip, "Tracings.ndf", NdfWriter.write(branches).getBytes(StandardCharsets.UTF_8));
            addEntry(zip, "stats.txt", stats.toReport().getBytes(StandardCharsets.UTF_8));
        }
        return buffer.toByteArray();
    }

    private static void addEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
    }

    private static byte[] jpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", buffer);
        return buffer.toByteArray();
    }

    private static BufferedImage scaleToFit(BufferedImage source) {
        int longestEdge = Math.max(source.getWidth(), source.getHeight());
        if (longestEdge <= PREVIEW_MAX_EDGE) {
            return source;
        }

        double scale = (double) PREVIEW_MAX_EDGE / longestEdge;
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    /** Thrown when the upload is not an image this server is willing to trace. */
    public static class UnsupportedImageException extends IOException {
        private static final long serialVersionUID = 1L;

        public UnsupportedImageException(String message) {
            super(message);
        }
    }
}
