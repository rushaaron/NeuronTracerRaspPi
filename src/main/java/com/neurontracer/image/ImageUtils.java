package com.neurontracer.image;

import java.awt.Color;
import java.awt.image.BufferedImage;

/** Small pixel helpers shared by the tracer and the service that drives it. */
public final class ImageUtils {

    private static final int WHITE = Color.WHITE.getRGB();

    private ImageUtils() {
    }

    /** Red channel of a packed RGB value. */
    public static int getRed(int rgb) {
        return (rgb >> 16) & 0xFF;
    }

    /** True when a packed RGB value is one of the pink shades used to fill in the cell body. */
    public static boolean isPink(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return red > 200 && green < 100 && blue > 200;
    }

    /**
     * Row of the first pink pixel in the image, or {@code -1} when the cell body was not
     * filled in. The tracer uses this to decide between the pink and the greyscale search.
     */
    public static int firstPinkRow(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (isPink(image.getRGB(x, y))) {
                    return y;
                }
            }
        }
        return -1;
    }

    /** An RGB copy of {@code source}, used as a canvas the tracer can draw its overlay onto. */
    public static BufferedImage copyOf(BufferedImage source) {
        BufferedImage copy = blank(source);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                copy.setRGB(x, y, source.getRGB(x, y));
            }
        }
        return copy;
    }

    /** A blank white canvas the size of {@code source}. */
    public static BufferedImage whiteCanvas(BufferedImage source) {
        BufferedImage canvas = blank(source);
        for (int y = 0; y < canvas.getHeight(); y++) {
            for (int x = 0; x < canvas.getWidth(); x++) {
                canvas.setRGB(x, y, WHITE);
            }
        }
        return canvas;
    }

    private static BufferedImage blank(BufferedImage source) {
        return new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
    }
}
