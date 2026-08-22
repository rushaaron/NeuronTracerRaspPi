package com.neurontracer.image;

import com.neurontracer.trace.Pair;
import com.neurontracer.trace.Trips;

import java.awt.image.BufferedImage;
import java.util.ArrayList;

public class ImageDistanceUtils {

    private ImageDistanceUtils() {
        //Do nothing
    }

    //getting the distance of the number of pixels that have an rgb value < 50 in the given direction
    public static int getDistance(ArrayList<Pair> directions, int x, int y, int d, BufferedImage img, int red) {
        int count = -1;

        //------------------------------------------------------------------------------------------------------------------------ change the color on this bad boi
        while (x < img.getWidth()-1 && x > 0 && y > 0 && y < img.getHeight()-1 && ImageUtils.getRed(img.getRGB(x, y)) < red) {
            x += directions.get(d).x;
            y += directions.get(d).y;
            count++;
        }
        return count;
    }

    //gets a point and returns how far it is from a known branch
    public static double getOffsetDistanceFromBranchPair(ArrayList<Pair> directions, int d, ArrayList<Pair> branch, int x, int y) {
        double minDistance = 10000;
        double temp;

        //if d == -1, then we don't have a direction
        if ( d!= -1) {
            x += directions.get(d).x;
            y += directions.get(d).y;
        }

        if (branch != null) {
            for (Pair b : branch) {
                temp = Math.sqrt(Math.pow(b.x - x,2) + Math.pow(b.y - y,2));
                if (temp < minDistance)
                    minDistance = temp;
            }
        }
        return minDistance;
    }

    //getting the distance of the number of pixels that have an rgb value < 200 the their average red RGB value (limited to 5 pixels away) in the given direction
    public static int[] getDistanceAndAverage(ArrayList<Pair> directions, int x, int y, int d, BufferedImage img, int red) {
        int count = -1;
        int sumRGB = 0;
        int aveRGB = -1;
        int countLimited = 0;

        //------------------------------------------------------------------------------------------------------------------------ change the color on this bad boi
        while (x < img.getWidth()-1 && x > 0 && y > 0 && y < img.getHeight()-1 && ImageUtils.getRed(img.getRGB(x, y)) < red ) {
            x += directions.get(d).x;
            y += directions.get(d).y;
            count++;
            if (countLimited < 5) //only want to get the average rgb values from 5 pixels away at most
                sumRGB += ImageUtils.getRed(img.getRGB(x, y));
            countLimited++;
        }

        if (countLimited != 0)
            aveRGB = sumRGB / countLimited;
        else
            aveRGB = sumRGB;
        return new int[]{count,aveRGB};
    }


    //gets a point and returns how far it is from a known branch
    public static double getOffsetDistanceFromBranch(ArrayList<Trips> branch, Trips point, int x, int y) {
        double minDistance = 10000;
        double temp;

        if (branch != null) {
            for (Trips b : branch) {
                temp = Math.sqrt(Math.pow(b.x - x,2) + Math.pow(b.y - y,2));
                if (temp < minDistance)
                    minDistance = temp;
            }
        } else
            return Math.sqrt(Math.pow(point.x - x,2) + Math.pow(point.y - y,2));
        return minDistance;
    }

    // Returning the weighted distance of a pixel
    public static double[] getWeightedDistance(ArrayList<Pair> directions, int x, int y, int d, BufferedImage img, int red) {
        int count = -1;
        int totalRed = 0;
        double weight = 1.5;
        int xDir = directions.get(d).x;
        int yDir = directions.get(d).y;

        while (x < img.getWidth()-1 && x > 0 && y > 0 && y < img.getHeight()-1 && ImageUtils.getRed(img.getRGB(x, y)) < red) {
            x += xDir;
            y += yDir;
            count++;
            totalRed += ImageUtils.getRed(img.getRGB(x, y));
        }
        return new double[]{(totalRed / (count * weight)),count};
    }

    // Used to find the boundary of the eye
    public static int getCenterDistance(ArrayList<Pair> directions, int x, int y, int d, BufferedImage img) {
        int count = -1;

        while (x < img.getWidth()-1 && x > 0 && y > 0 && y < img.getHeight()-1 && ImageUtils.getRed(img.getRGB(x, y)) < 5) {
            x += directions.get(d).x;
            y += directions.get(d).y;
            count++;
        }
        return count;
    }

    // Searching while the rgb while is > 5
    public static int getInvertCenterDistance(ArrayList<Pair> directions, int x, int y, int d, BufferedImage img) {
        int count = -1;

        while (x < img.getWidth()-1 && x > 0 && y > 0 && y < img.getHeight()-1 && ImageUtils.getRed(img.getRGB(x, y)) > 5) {
            x += directions.get(d).x;
            y += directions.get(d).y;
            count++;
        }
        return count;
    }
}
