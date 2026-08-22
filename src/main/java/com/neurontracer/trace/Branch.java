package com.neurontracer.trace;

import java.util.ArrayList;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.lang.Math;

public class Branch {
    int[][] cords;
    ArrayList<Trips> points;
    ArrayList<Trips> breakPoints = new ArrayList<Trips>();
    int startingNum;
    int aveRed;
    int side; //going to be used to indicate which side a starting branch was found
    boolean starter = false;
    Trips startingPoint;

    //go to constructor
    public Branch(ArrayList<Trips> points, int startingNum, int side, boolean starter) {
        this.points = points;
        this.startingNum = startingNum;
        this.aveRed = -1;
        this.side = side;
        this.starter = starter;
        startingPoint = points.get(0);
    }

    public Branch(){
        // Just a placeholder
    }

    public Branch(ArrayList<Trips> points){
        this.points = new ArrayList<Trips>(points);
    }

    public Branch(ArrayList<Trips> points, Trips p){
        this.points = new ArrayList<Trips>(points);
        breakPoints.add(p);
    }

    // Simple get methods---------
    public Trips getStartingPoint(){
        if (startingPoint != null)
            return startingPoint;
        return points.get(0);
    }

    public Trips getEndingPoint(){
        return points.get(points.size()-1);
    }

    public int getSide() {
        return side;
    }

    public int getStartingNum(){
        return startingNum;
    }

    public ArrayList<Trips> getPoints(){
        return points;
    }

    public void removePoint(Trips p) {
        points.remove(p);
    }

    public int getSize() {
        return points.size();
    }

    public boolean getStarter() {
        return starter;
    }

    //more complex methods--------------
    public String toString(){
        return "StartNum: " + startingNum + " Side: " + side + points + "\n";
    }

    public void printBranch(BufferedImage img, int color) {
        Color blue = new Color(248,76,200);
        if (color == 2)
            blue = new Color(42,221,65);
        else if (color == 3)
            blue = new Color(42,209,221);
        else if (color == 4)
            blue = new Color(218,255,1);
        else if (color == 5)
            blue = new Color(255,1,105);
        for (Trips i : points)
            img.setRGB(i.x, i.y, blue.getRGB());
    }

    public void printBranch(BufferedImage img) {
        Color randColor = new Color((int)(Math.random()*(255-1+1)+1),(int)(Math.random()*(255-1+1)+1),(int)(Math.random()*(255-1+1)+1));
        for (Trips i : points)
            img.setRGB(i.x, i.y, randColor.getRGB());
    }

    public void printBranch(BufferedImage img, BufferedImage output) {
        Color randColor = new Color((int)(Math.random()*(255-1+1)+1),(int)(Math.random()*(255-1+1)+1),(int)(Math.random()*(255-1+1)+1));
        for (Trips i : points) {
            img.setRGB(i.x, i.y, randColor.getRGB());
            output.setRGB(i.x, i.y, randColor.getRGB());
        }
    }

    //hightlighting the ending points
    public void printBranch(BufferedImage output, boolean black) {
        Color endPoints;
        Color base;
        if (black) {
            base = new Color(0,0,0);
            endPoints = new Color(0,0,0);
        } else {
            base = new Color(101, 255, 49);
            endPoints = new Color(0, 59, 255);
        }

        for (int i = 0; i < points.size(); i++) {
            if (i == 0 || i == 1 || i == points.size()-1 || i == points.size()-2) {
                output.setRGB(points.get(i).x, points.get(i).y, endPoints.getRGB());
            } else {
                output.setRGB(points.get(i).x, points.get(i).y, base.getRGB());
            }
        }
    }

    public int getAveRed(BufferedImage img) {
        if (aveRed != -1)
            return aveRed;
        int count = 0;

        for (Trips i : points){
            Color c = new Color(img.getRGB(i.x,i.y),true);
            aveRed+= c.getRed();
            count++;
        }
        aveRed /= count;
        return aveRed;
    }

    public void removeFromPoints(ArrayList<Trips> x) {
        for (Trips i : x)
            points.remove(i);
    }

    public void addToPoints(ArrayList<Trips> x) {
        points.addAll(x);
    }

    public void addBreakPoint(Trips p) {
        breakPoints.add(p);
    }

    public ArrayList<Trips> getBreakPoints() {
        return breakPoints;
    }

    // Breaking up branch and turning in into an array of smaller ones
    public ArrayList<Branch> breakUpBranch() {
        ArrayList<Branch> ret = new ArrayList<Branch>();
        ArrayList<Trips> tempPoints = new ArrayList<Trips>();

        for (Trips p : points) {
            tempPoints.add(p);
            Trips temp = containsPair(breakPoints, p);
            if (temp != null) {
                ret.add(new Branch(tempPoints, temp));
                tempPoints.clear();
            }
        }
        ret.add(new Branch(tempPoints));
        return ret;
    }

    private static Trips containsPair(ArrayList<Trips> branch, Trips point) {
        for (Trips i : branch)
            if (i.x == point.x && i.y == point.y)
                return i;
        return null;
    }
}
