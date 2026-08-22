package com.neurontracer.trace;

import com.neurontracer.image.ImageDistanceUtils;
import com.neurontracer.image.ImageUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

public class Tracer {
    private final int red;
    private final int xOffsetFromEye;
    private final int yOffsetFromEye;
    private final int startOfBranchRed;
    private final int sideToRemove;
    private final BufferedImage image;
    private final BufferedImage output;
    private final BufferedImage whiteOutput;
    private final BufferedImage blackOutput;
    private final ArrayList<Pair> directions = new ArrayList<>();
    private final int maxPixelSize = 100000;
    private final Color blue = new Color(50,5,200);
    private TraceStats stats = TraceStats.of(0, 0);

    public Tracer(int red, int xOffsetFromEye, int yOffsetFromEye, int startOfBranchRed, int sideToRemove, BufferedImage image, BufferedImage output, BufferedImage whiteOutput, BufferedImage blackOutput) {
        this.red = red;
        this.xOffsetFromEye = xOffsetFromEye;
        this.yOffsetFromEye = yOffsetFromEye;
        this.startOfBranchRed = startOfBranchRed;
        this.sideToRemove = sideToRemove;
        this.image = image;
        this.output = output;
        this.whiteOutput = whiteOutput;
        this.blackOutput = blackOutput;

        Pair N = new Pair(0,-1);
        Pair NE = new Pair(1,-1);
        Pair E = new Pair(1,0);
        Pair SE = new Pair(1,1);
        Pair S = new Pair(0,1);
        Pair SW = new Pair(-1,1);
        Pair W = new Pair(-1,0);
        Pair NW = new Pair(-1,-1);
        //setting the direction array
        //start from index 1 through 8,  indexes 0 and 9 are extra for the plus 1, minus 1
        //if index == 0, set to 8 / if index == 9, set to 1
        directions.add(NW); directions.add(N); directions.add(NE); directions.add(E); directions.add(SE); directions.add(S); directions.add(SW); directions.add(W); directions.add(NW); directions.add(N);
    }


    // Summary numbers for the most recent createBranches() call
    public TraceStats getStats() {
        return stats;
    }

    // Main method that'll trace and create the branches off the passed in image
    public ArrayList<Branch> createBranches() {
        // Used to sort how the starting branches
        ArrayList<Trips> tempPoints = new ArrayList<Trips>();
        ArrayList<ArrayList<Trips>> listOfStartingPoints = new ArrayList<ArrayList<Trips>>();
        ArrayList<Trips> startingBranchPoints = new ArrayList<Trips>();

        // Used to hold the full starting points for validation against the offset branches at the beginning
        ArrayList<Trips> knownPoints = new ArrayList<>();

        // Holds all the branches
        ArrayList<Branch> branches = new ArrayList<>();

        ArrayList<Branch> startingBranches;
        int y = ImageUtils.firstPinkRow(image);
        // If image doesn't contain pink, then y will equal -1
        if (y != -1)
            startingBranches = getStartingBranchesPink(directions, knownPoints, image, output, xOffsetFromEye, yOffsetFromEye, startOfBranchRed, 0, y);
        else
            startingBranches = getStartingBranches(directions, knownPoints, image, output, xOffsetFromEye, yOffsetFromEye, startOfBranchRed);


        if (sideToRemove != 0) {
            for (int i = 0; i < startingBranches.size(); i++) {
                if (startingBranches.get(i).getSide() == sideToRemove) {
                    startingBranches.remove(i);
                    i--;
                }
            }
        }

        //cleans up overlapping and repeated branches
        if (startingBranches.size() > 1)
            startingBranches = validateStartingBranches(startingBranches, image);

        for (Branch b : startingBranches) {
            b.printBranch(output, false);
            branches.add(b);
        }

        for (Branch i : startingBranches) {
            listOfStartingPoints.clear();
            int dir = 7;
            switch (i.getSide()) {
                case 1: dir = 7;
                    break;
                case 2: dir = 3;
                    break;
                case 3: dir = 1;
                    break;
                case 4: dir = 5;
                    break;
            }
            if (i.getSide() != 0) {
                startingBranchPoints = (findBranches(directions, null, i.getStartingPoint().x, i.getStartingPoint().y, dir,image, red, 0));
                knownPoints.addAll(startingBranchPoints);
                knownPoints.addAll(i.getPoints());

                //don't want to add the branch we want removed to the rest of the image, but we want to add it to the known points
                if (i.getSide() != sideToRemove) {
                    //going through each point on the found starting branch and trying to find separate branches
                    for (Trips point : startingBranchPoints) {
                        for (int d = 1; d <= 8; d++) {
                            tempPoints = findBranches(directions, knownPoints, point.x, point.y, d,image, red, 0);
                            if (tempPoints.size() > 20 && validateSeparateBranch(knownPoints, tempPoints)) {
                                listOfStartingPoints.add(tempPoints);
                            }
                        }
                    }

                    //removing overlapping offset branches
                    listOfStartingPoints = removeRepeatStartingPoints(listOfStartingPoints, image);

                    //populating list of Trips for starting points for the offsets
                    for (ArrayList<Trips> list : listOfStartingPoints) {
                        i.addBreakPoint(list.get(0));
                    }

                    //adding back the beginning points of the starting points we didn't want to find offsets for
                    i.addToPoints(startingBranchPoints);

                    //breaking up main branch based off starting points found from it
                    branches.addAll(i.breakUpBranch());
                }

            }
        }

        //--------------------------------------------------------------------------Main loop--------------------------------------------------------------------------
        ArrayList<Branch> branchesToBreak = new ArrayList<Branch>();
        ArrayList<Branch> nextRoundOfBranchesToBreak = new ArrayList<Branch>();

        branchesToBreak.addAll(branches);

        do {
            //finding new branches from the existing ones
            while (!branchesToBreak.isEmpty()) {
                for (Trips s : branchesToBreak.get(0).getBreakPoints()) {
                    Branch tempB = new Branch(findBranches(directions, knownPoints, s.x, s.y, s.d,image, red, 0));
                    if (tempB.getPoints().size() > 5 && validateSeparateBranch(branchesToBreak.get(0).getBreakPoints(),tempB.getPoints())){
                        nextRoundOfBranchesToBreak.add(tempB); //temp array to hold set of branches for the next round
                        knownPoints.addAll(tempB.getPoints());
                    }
                }
                branchesToBreak.remove(0);
            }

            for (Branch b : nextRoundOfBranchesToBreak) {
                listOfStartingPoints.clear();

                for (Trips point : b.getPoints()) {
                    for (int d = 1; d <= 8; d++) {
                        tempPoints = findBranches(directions, knownPoints, point.x, point.y, d,image, red, 0);
                        if (tempPoints.size() > 20 && validateSeparateBranch(knownPoints, tempPoints)) {
                            listOfStartingPoints.add(tempPoints);
                        }
                    }
                }
                // Removing overlapping offset branches
                listOfStartingPoints = removeRepeatStartingPoints(listOfStartingPoints, image);

                // Populating list of Trips for starting points for the offsets
                for (ArrayList<Trips> list : listOfStartingPoints) {
                    b.addBreakPoint(list.get(0));
                }

                // Breaking up main branch based off starting points found from it
                branches.addAll(b.breakUpBranch());
            }

            branchesToBreak.addAll(nextRoundOfBranchesToBreak);
            nextRoundOfBranchesToBreak.clear();
        } while (!branchesToBreak.isEmpty());

        // Removing tiny branches that somehow got in there
        removeTinyBranches(branches);

        // Combining split branches
        mergeSplitBranches(branches);

        // Removing overlapping points
        removeOverlappingPoints(branches);


        int totalPixels = 0;

        for (Branch b : branches) {
            b.printBranch(output, false);
            b.printBranch(whiteOutput, false);
            b.printBranch(blackOutput, true);
            totalPixels += b.getPoints().size();
        }

        stats = TraceStats.of(branches.size(), totalPixels);

        return branches;
    }


    // If the image has pink on it, then it should have a pink eye. Gets main branches from the pink eye
    private ArrayList<Branch> getStartingBranchesPink(ArrayList<Pair> directions, ArrayList<Trips> eyeOutline, BufferedImage img, BufferedImage output, int xOffset, int yOffset, int red, int x, int y) {
        // Gets an array of the outline of the eye
        final ArrayList<Pair> outline = getPinkOutline(directions, x, y, img, output);

        Pair minX = new Pair(maxPixelSize,0); Pair maxX = new Pair(0,0); Pair minY = new Pair(0,maxPixelSize); Pair maxY = new Pair(0,0);
        for (Pair i : outline) {
            if (i.x < minX.x)
                minX = new Pair(i.x,i.y);
            if (i.x > maxX.x)
                maxX = new Pair(i.x,i.y);
            if (i.y < minY.y)
                minY = new Pair(i.x,i.y);
            if (i.y > maxY.y)
                maxY = new Pair(i.x,i.y);
            output.setRGB(i.x, i.y, blue.getRGB());
        }

        // Passing back the know points of the eye
        for (Pair p : outline)
            eyeOutline.add(new Trips(p.x,p.y,0));

        return getBranchesFromCenter(directions, outline, minX, maxX, minY, maxY, img, output, xOffset, yOffset, red);
    }

    // Gets the outline of the pink eye
    private ArrayList<Pair> getPinkOutline(ArrayList<Pair> directions, int startX, int startY, BufferedImage img, BufferedImage output) {
        ArrayList<Pair> outline = new ArrayList<Pair>();
        boolean foundMin;
        Pair prevMin = new Pair(-1,-1);
        Pair prevMax = new Pair(-1,-1);
        Pair startMin = new Pair(-1,-1); //recording the starting pair of the min value

        for (int y = startY; y < img.getHeight() -1; y++) {
            foundMin = false;

            for (int x = 0; x < img.getWidth()-1; x++) {
                if (foundMin) { //if we've already found the min value the left boundary, checking for right side
                    if (!ImageUtils.isPink(img.getRGB(x, y))){
                        outline.add(new Pair(x-1,y));
                        if (prevMax.x != -1)
                            outline.addAll(fillInPinkOutline(x-1, y, prevMax));
                        else
                            outline.addAll(fillInPinkOutline(x-1, y, startMin));
                        prevMax = new Pair(x-1,y);
                        break;
                    }
                } else { //if we haven't found the min value the left boundary, than first pink value is the min
                    if (ImageUtils.isPink(img.getRGB(x, y))){
                        foundMin = true;
                        outline.add(new Pair(x,y));
                        if (prevMin.x != -1)
                            outline.addAll(fillInPinkOutline(x, y, prevMin));
                        else
                            startMin = new Pair(x,y);
                        prevMin = new Pair(x,y);
                        //break;
                    }
                }
            }
            if (!foundMin)
                break;
        }
        outline.addAll(fillInPinkOutline(prevMin.x, prevMin.y, prevMax));

        return outline;
    }

    //fills in the points between 2 neighboring y values
    private ArrayList<Pair> fillInPinkOutline(int x, int y, Pair prev) {
        ArrayList<Pair> outline = new ArrayList<Pair>();
        while (x != prev.x) {
            if (x > prev.x)
                x--;
            else
                x++;
            outline.add(new Pair(x, y));
        }
        return outline;
    }

    // Get main branches from the starting point when there's no pink outline
    private ArrayList<Branch> getStartingBranches(ArrayList<Pair> directions, ArrayList<Trips> eyeOutline, BufferedImage img, BufferedImage output, int xOffset, int yOffset, int red) {
        int count = 0; int x = 0; int y = 0;

        // Getting starting point
        outerloop:
        for (y = 0; y < img.getHeight(); y++){
            for (x = 0; x < img.getWidth(); x++){
                count = 0;

                if (ImageUtils.getRed(img.getRGB(x, y)) < 50) {
                    for (int i = 1; i <= 8; i++)
                        if (ImageDistanceUtils.getDistance(directions, x, y, i, img, 200) > 7)
                            count ++;
                    if (count > 7)
                        break outerloop;
                }
            }
        }

        //
        // Finding the min/max of (x,y) for the center
        //
        ArrayList<Pair> minMaxXs = findStartingXs(x, y, directions, true, img);
        ArrayList<Pair> minXs = findStartingXs(minMaxXs.get(0).x, minMaxXs.get(0).y, directions, false, img);
        ArrayList<Pair> maxXs = findStartingXs(minMaxXs.get(1).x, minMaxXs.get(1).y, directions, true, img);

        Pair minX = new Pair(minXs.get(0).x,minXs.get(0).y);
        Pair maxX = new Pair(maxXs.get(1).x,maxXs.get(1).y);

        if (minXs.get(0).x > maxXs.get(0).x)
            minX = new Pair(maxXs.get(0).x,maxXs.get(0).y);
        if (minXs.get(1).x > maxXs.get(1).x)
            maxX = new Pair(minXs.get(1).x,minXs.get(1).y);

        //
        // Getting an array of the outline of the eye
        //
        ArrayList<Pair> outline1 = getStartingYs(directions, minX, maxX, true, img, output);
        ArrayList<Pair> outline2 = getStartingYs(directions, minX, maxX, false, img, output);

        ArrayList<Pair> outline3 = getStartingYsInverted(directions, minX, maxX, true, img, output);
        ArrayList<Pair> outline4 = getStartingYsInverted(directions, minX, maxX, false, img, output);

        //
        // Getting max y pairs
        //
        Pair minYs = outline1.remove(1);
        outline1.remove(0);
        Pair maxYs = outline2.remove(0);
        outline2.remove(0);

        Pair minY = new Pair(minYs.x,minYs.y);
        Pair maxY = new Pair(maxYs.x,maxYs.y);

        if (minYs.y > maxYs.y)
            minX = new Pair(maxYs.x,maxYs.y);
        if (minYs.y > maxYs.y)
            maxX = new Pair(minYs.x,minYs.y);

        //
        // Need to get outline points into one arraylist
        //
        outline1.addAll(outline2);
        outline1.addAll(outline3);
        outline1.addAll(outline4);

        //
        // Removing duplicates from arraylist
        //
        ArrayList<Pair> outline = new ArrayList<Pair>();
        for (Pair i : outline1)
            if (!outline.contains(i))
                outline.add(i);

        // Printing out the eye
        for (Pair i : outline)
            output.setRGB(i.x, i.y, blue.getRGB());

        // Passing back the know points of the eye
        for (Pair p : outline)
            eyeOutline.add(new Trips(p.x,p.y,0));

        // Gets the starting branches
        return getBranchesFromCenter(directions, outline, minX, maxX, minY, maxY, img, output, xOffset, yOffset, red);
    }

    // First will outline a box around the eye based off the offset. While looking at each point, if the point is < shade, adds the points to an array of branches
    // each branch created will be given a branch number. The branch number increments every time a branch is found and moved on from. This number will be used to
    // remove duplicates
    private ArrayList<Branch> getBranchesFromCenter(ArrayList<Pair> directions, ArrayList<Pair> outline, Pair minXs, Pair maxXs, Pair minYs, Pair maxYs, BufferedImage img, BufferedImage output, int xOffset, int yOffset, int red) {
        int shade = 240;
        //red was 220
        boolean prev = false; //will be used to indicate if the previous point found a starting branch. If so and current point is true, then we don't want to record that point
        ArrayList<Trips> ret = new ArrayList<Trips>();
        ArrayList<Branch> startingBranches = new ArrayList<Branch>();
        int branchNum = 1;

        //finding min/max for outline
        int minX = minXs.x - xOffset;
        if (minX < 0)
            minX = 0;
        int maxX = maxXs.x + xOffset;
        if (maxX >= img.getWidth())
            maxX = img.getWidth()-1;
        int minY = minYs.y - yOffset;
        if (minY < 0)
            minY = 0;
        int maxY = maxYs.y + yOffset;
        if (maxY >= img.getHeight())
            maxY = img.getHeight()-1;

        // Looping through an offset from the min/max x/y values, then search back towards the eye to determine starting branches
        for (int i = minY; i < maxY; i++) { //looping through for the Y values
            output.setRGB(minX, i, blue.getRGB());
            output.setRGB(maxX, i, blue.getRGB());

            if (ImageUtils.getRed(img.getRGB(minX, i)) < shade) {//
                ret = startOfBranch(directions, outline, minX, i, 3, img, output, red);
                if (!ret.isEmpty()) {
                    startingBranches.add(new Branch(ret, branchNum,1,true));
                    if (!prev) {//if the previous point didn't find a starting point but the current one did, we want to save it
                        prev = true;
                    } //if previous and current points found starting points, then do nothing because we don't want repeats
                } else {
                    if (prev) //if the current point is not a starting branch but the previous point was, means we're moving on to a new branch
                        branchNum++;
                    prev = false;
                }
            } else {
                if (prev) //if the current point is not a starting branch but the previous point was, means we're moving on to a new branch
                    branchNum++;
                prev = false;
            }
        }

        prev = false;
        for (int i = minY; i < maxY; i++) { //looping through for the Y values
            if (ImageUtils.getRed(img.getRGB(maxX, i)) < shade){
                ret = startOfBranch(directions, outline, maxX, i, 7, img, output, red);
                if (!ret.isEmpty()) {
                    startingBranches.add(new Branch(ret,branchNum,2,true));
                    if (!prev) {//if the previous point didn't find a starting point but the current one did, we want to save it
                        prev = true;
                    }
                } else {
                    if (prev) //if the current point is not a starting branch but the previous point was, means we're moving on to a new branch
                        branchNum++;
                    prev = false;
                }
            } else {
                if (prev) //if the current point is not a starting branch but the previous point was, means we're moving on to a new branch
                    branchNum++;
                prev = false;
            }
        }

        prev = false;
        for (int i = minX; i < maxX; i++) {
            output.setRGB(i, minY, blue.getRGB());
            output.setRGB(i, maxY, blue.getRGB());

            if (ImageUtils.getRed(img.getRGB(i, minY)) < shade){
                ret = startOfBranch(directions, outline, i, minY, 5, img, output, red);
                if (!ret.isEmpty()) {
                    startingBranches.add(new Branch(ret,branchNum,3,true));
                    if (!prev) {//if the previous point didn't find a starting point but the current one did, we want to save it
                        prev = true;
                    }
                } else {
                    if (prev) //if the current point is not a starting branch but the previous point was, means we're moving on to a new branch
                        branchNum++;
                    prev = false;
                }
            } else {
                if (prev) //if the current point is not a starting branch but the previous point was, means we're moving on to a new branch
                    branchNum++;
                prev = false;
            }
        }

        prev = false;
        for (int i = minX; i < maxX; i++) {
            if (ImageUtils.getRed(img.getRGB(i, maxY)) < shade){
                ret = startOfBranch(directions, outline, i, maxY, 1, img, output, red);
                if (!ret.isEmpty()) {
                    startingBranches.add(new Branch(ret,branchNum,4,true));
                    if (!prev) {//if the previous point didn't find a starting point but the current one did, we want to save it
                        prev = true;
                    }
                } else {
                    if (prev) //if the current point is not a starting branch but the previous point was, means we're moving on to a new branch
                        branchNum++;
                    prev = false;
                }
            } else {
                if (prev) //if the current point is not a starting branch but the previous point was, means we're moving on to a new branch
                    branchNum++;
                prev = false;
            }
        }

        //going to be keeping branches with the lowest average red rgb value, may have duplicates so going to remove those later
        int prevNum = 1;
        int minAve = 300; //just needs to be higher than 255
        ArrayList<Branch> toRemove = new ArrayList<Branch>();

        for (Branch i : startingBranches) {
            if (i.getStartingNum() != prevNum) {
                for (Branch k : startingBranches) {
                    if (k.getStartingNum() == prevNum && k.getAveRed(img) > minAve)
                        toRemove.add(k);
                    if (k.getStartingNum() > prevNum)//need to find minAve before can filter through these values
                        break;
                }
                minAve = 300;
            }

            if (i.getAveRed(img) < minAve)
                minAve = i.getAveRed(img);
            prevNum = i.getStartingNum();
        }

        for (Branch k : startingBranches) //adding branches to be removed for the last number in the list
            if (k.getStartingNum() == prevNum && k.getAveRed(img) > minAve)
                toRemove.add(k);

        for (Branch i : toRemove) //removing undesired branches
            startingBranches.remove(i);
        toRemove.clear();

        prevNum = 0;
        for (Branch i : startingBranches) { //removing duplicates of the starting number
            if (i.getStartingNum() == prevNum)
                toRemove.add(i);
            prevNum = i.getStartingNum();
        }

        for (Branch i : toRemove)
            startingBranches.remove(i);

        return startingBranches;
    }

    //starts at a point outside the outline and moves towards the outline to see if the branch is a starting branch
    private ArrayList<Trips> startOfBranch(ArrayList<Pair> directions, ArrayList<Pair> outline, int x, int y, int d, BufferedImage img, BufferedImage output, int red) {
        Trips curPoint = new Trips(-1,-1,-1);
        //int[] possibleDirs = {d-1,d,d+1};
        int[] possibleDirs = {d-2,d-1,d,d+1,d+2};
        if (d == 1)
            possibleDirs[0] = 7;
        int[] dirResults = new int[2];
        ArrayList<Trips> toVisit = new ArrayList<Trips>();
        ArrayList<Trips> visited = new ArrayList<Trips>();
        int maxDir = -1;
        int aveRgb = 300;
        boolean startingBranch = false;
        double curDistance;
        double distanceResult = 1000;


        int count = 0;
        //loop breaks if out of options to explore or found the outline of the eye
        while (count < 100) {
            maxDir = -1;
            aveRgb = 265;
            curDistance = ImageDistanceUtils.getOffsetDistanceFromBranchPair(directions, -1, outline, x, y);//gets the distance of the current point to compare with what to choose next

            //gets distance and average rgb for every possible direction, adds them to the toVisit array and keeps track of the lowest ave rgb to pull out and explore
            for (int i = 0; i < possibleDirs.length; i++) {
                dirResults = ImageDistanceUtils.getDistanceAndAverage(directions, x, y, possibleDirs[i], img, red);
                distanceResult = ImageDistanceUtils.getOffsetDistanceFromBranchPair(directions, possibleDirs[i], outline, x, y);
                if (dirResults[0] >= 0 && !visited.contains(new Trips(x,y,possibleDirs[i]))) {
                    toVisit.add(new Trips(x,y,possibleDirs[i]));
                    if (dirResults[1] < aveRgb && distanceResult <= curDistance) {
                        maxDir = possibleDirs[i];
                        aveRgb = dirResults[1];
                    }
                }
            }
            //fetch above found a direction, going with the direction with the lowest average rbg value
            if (maxDir != -1) {
                curPoint = toVisit.get(toVisit.indexOf(new Trips(x,y,maxDir)));
                toVisit.remove(curPoint);
            } else { //if fetch above is at a dead end, pulling from valid point and direction trips we haven't explored yet
                if (toVisit.isEmpty())
                    break;

                double minDis = 10000;
                double temp = 0;
                //finding the point that's closest to the eye
                for (Trips p : toVisit) {
                    temp = ImageDistanceUtils.getOffsetDistanceFromBranchPair(directions, p.d, outline, p.x, p.y);
                    if (temp < minDis) {
                        curPoint = p;
                        minDis = temp;
                    }
                }
                toVisit.remove(curPoint);
            }

            visited.add(curPoint);
            x = curPoint.x;
            y = curPoint.y;

            x += directions.get(curPoint.d).x;
            y += directions.get(curPoint.d).y;
            //output.setRGB(x, y, black.getRGB());//just printing out point
            Pair tempPair = new Pair(x,y);
            if (outline.contains(tempPair)) {
                visited.add(new Trips(x,y,curPoint.d));
                startingBranch = true;
                break;
            }
            count++;
        }

        if (!startingBranch)
            visited.clear();
        return visited;
    }

    // Going to find if there are multiple branches within a starting branch
    private ArrayList<Branch> validateStartingBranches(ArrayList<Branch> startingBranches, BufferedImage img) {
        for (int i = 0; i < startingBranches.size(); i++) {
            Branch outerBranch = startingBranches.get(i);

            for (int k = 0; k < startingBranches.size(); k++) {
                if (k != i) {
                    Branch cur = startingBranches.get(k);
                    //checking to see if any of the current branches are empty
                    if (cur.getSize() == 0) {
                        startingBranches.remove(cur);
                        k--;
                        continue;
                    } else if (outerBranch.getSize() == 0) {
                        startingBranches.remove(outerBranch);
                        i--;
                        break;
                    }

                    if (outerBranch.getEndingPoint().equals(cur.getEndingPoint()) && outerBranch.getSide() == cur.getSide() && isStartingBranchDuplicate(outerBranch, cur, img)) {
                        if (cur.getAveRed(img) < outerBranch.getAveRed(img)) {
                            startingBranches.remove(outerBranch);
                            i--;
                            break;
                        } else {
                            startingBranches.remove(cur);
                            k--;
                            continue;
                        }
                    }
                    //looping through all the points to see if any conflict
                    for (Trips p : outerBranch.getPoints()) {
                        if (cur.getPoints().contains(p)){
                            startingBranches.add(separateOverlapSecond(outerBranch, cur, p));
                            break;
                        }
                    }


                }
            }
        }
        return startingBranches;
    }


    // Given 2 branches with the same ending point and side, seeing if the starting points are connected by filled in pixels, if so then they're duplicates
    private boolean isStartingBranchDuplicate(Branch b1, Branch b2, BufferedImage img) {
        int x = b1.getStartingPoint().x;
        int y = b1.getStartingPoint().y;
        int xs = 0;//used to modify the x and y values
        int ys = 0;
        int xGoal = b2.getStartingPoint().x;
        int yGoal = b2.getStartingPoint().y;

        if (b1.getSide() == 3 || b1.getSide() == 4)
            xs = 1;
        else
            ys = 1;

        while (x < img.getWidth()-1 && y < img.getHeight()-1 && ImageUtils.getRed(img.getRGB(x, y)) < 250) {
            x += xs;
            y += ys;
            if (x == xGoal && y == yGoal)
                return true;
        }
        return false;
    }

    //branch 1 has already been separated, b2 overlaps so have to cut it down
    private Branch separateOverlapSecond(Branch b1, Branch b2, Trips p) {
        ArrayList<Trips> pointsToRemoveB1 = new ArrayList<Trips>();
        ArrayList<Trips> pointsToRemoveB2 = new ArrayList<Trips>();


        boolean foundPoint = false;
        //going through all the points in the first branch until we hit the common point, then we start keeping track of the rest of the points to remove
        for (Trips point : b1.getPoints()) {
            if (foundPoint || point.equals(p)) {
                foundPoint = true;
                pointsToRemoveB1.add(point);
            }
        }
        b1.removeFromPoints(pointsToRemoveB1);

        foundPoint = false;
        for (Trips point : b2.getPoints()) {
            if (foundPoint || point.equals(p)) {
                foundPoint = true;
                pointsToRemoveB2.add(point);
            }
        }
        b2.removeFromPoints(pointsToRemoveB2);

        return new Branch(pointsToRemoveB2);
    }

    // Sees if a separate branch is valid or just running along the known branch
    private boolean validateSeparateBranch(ArrayList<Trips> branch, ArrayList<Trips> offset) {
        double aveDistance = 0;
        int count = 0;

        for (int i = 1; i <= 15; i++) {
            count++;
            if (count == offset.size())
                break;
            aveDistance += ImageDistanceUtils.getOffsetDistanceFromBranch(branch, null, offset.get(i).x, offset.get(i).y);
        }
        count--;
        if (aveDistance / count < 6)
            return false;
        return true;
    }

    // Removing starting points that are too close to each other
    private ArrayList<ArrayList<Trips>> removeRepeatStartingPoints(ArrayList<ArrayList<Trips>> startingPoints, BufferedImage img) {
        for (int i = 0; i < startingPoints.size()-1; i++) {
            for (int k = i+1; k < startingPoints.size(); k++) {
                if (i != k) {
                    ArrayList<Trips> a1 = startingPoints.get(i);
                    ArrayList<Trips> a2 = startingPoints.get(k);
                    double distance = ImageDistanceUtils.getOffsetDistanceFromBranch(null, a1.get(0), a2.get(0).x, a2.get(0).y);
                    if (distance < 5 || (distance < 15 && overlappingPoints(a1, a2))) {
                        //if (compareRedValues(startingPoints.get(i), startingPoints.get(k), img) == 1) {
                        if (a1.size() < a2.size()) {
                            startingPoints.remove(a1);
                            i--;
                            break;
                        } else {
                            startingPoints.remove(a2);
                            k--;
                        }
                    }
                }
            }
        }
        return startingPoints;
    }


    // Getting the direction with the greatest weighted distance / returns next direction, and it's distance
    // Branch is an array of points of a known branch, close enough to the branch, will go straight for it
    private double[] getNextDirection(ArrayList<Pair> directions, int x, int y, int[] d, BufferedImage img, int red) {
        int dMax = -1;
        int countMax = -1;
        double[] temp;
        double minWeight = 10000;

        for (int i = 0; i < d.length; i++) {
            temp = ImageDistanceUtils.getWeightedDistance(directions, x, y, d[i], img, red);
            if (temp[1] > 0 && minWeight > temp[0]) {
                minWeight = temp[0];
                dMax = d[i];
                countMax = (int)temp[1];
            }
        }
        return new double[]{dMax, countMax};
    }

    // Current function for searching through the image for all the branches
    // Branch will equal null when searching through the image, will != null when searching from an offset
    private ArrayList<Trips> findBranches(ArrayList<Pair> directions, ArrayList<Trips> branch, int x, int y, int d, BufferedImage img, int red, int offset) {
        int[] pDirections = branches(d,3);
        double[] nextD;

        Trips curPoint = new Trips(x,y,d);
        ArrayList<Trips> points = new ArrayList<Trips>();

        int sinceChangedDir = 0;
        int prevDir = d;
        int count = 0;

        points.add(curPoint);

        outerloop:
        while (count < 10000) {

            //only want to allow a change of direction every 10 turns
            if (prevDir != d)
                sinceChangedDir = 0;
            //setting an array of the possible directions we can search
            if (sinceChangedDir > 15) { //was 10
                pDirections = branches(d,3);
            }

            nextD = getNextDirection(directions, x, y, pDirections, img, red);

            //break out of the loop clauses
            if (nextD[1] < 1)
                break;
            if (branch != null && count > 5){
                for (Trips b : branch) {
                    if (b.x == x && b.y == y)
                        break outerloop;
                }
            }

            prevDir = d;
            d = (int)nextD[0];
            x += directions.get(d).x;
            y += directions.get(d).y;
            points.add(curPoint = new Trips(x,y,d));
            sinceChangedDir++;
            count++;
        }
        return points;
    }

    // Takes a direction and either 3 or 5 for finding relative directions
    private int[] branches(int d, int size) {
        int[] b;
        if (size == 3 || size == -2)
            b = new int[3];
        else
            b = new int[5];

        int temp = d-1;
        if (temp <= 0)
            temp += 8;
        b[0] = temp;

        b[1] = d;

        temp = d+1;
        if (temp > 8)
            temp -=8;
        b[2] = temp;

        if (size == 5) {
            temp = d-2;
            if (temp <= 0)
                temp += 8;
            b[3] = temp;

            temp = d+2;
            if (temp > 8)
                temp -=8;
            b[4] = temp;
        } else if (size == -2) {
            temp = d-2;
            if (temp <= 0)
                temp += 8;
            b[0] = temp;

            temp = d+2;
            if (temp > 8)
                temp -=8;
            b[2] = temp;
        }
        return b;
    }

    //finding min and max xs with their corresponding y values
    private ArrayList<Pair> findStartingXs(int x, int y, ArrayList<Pair> directions, boolean left, BufferedImage img) {
        ArrayList<Pair> points = new ArrayList<Pair>();
        Pair minX = new Pair(x,y);
        Pair maxX = new Pair(x,y);
        int tempX = -1;
        int maxY = -1;
        int offset = 0;

        do {
            y -= ImageDistanceUtils.getCenterDistance(directions, x, y, 1, img);
            maxY = y + ImageDistanceUtils.getCenterDistance(directions, x, y, 5, img);
            x += offset;
            if (left)
                offset--;
            else
                offset++;
        } while (maxY - y < 5 && offset < 10 && offset > -10);

        while (y < maxY) {

            tempX = x - ImageDistanceUtils.getCenterDistance(directions, x, y, 7, img);
            if (tempX < minX.x)
                minX = new Pair(tempX,y);

            tempX = x + ImageDistanceUtils.getCenterDistance(directions, x, y, 3, img);
            if (tempX > maxX.x)
                maxX = new Pair(tempX,y);
            y++;
        }

        points.add(minX); points.add(maxX);
        return points;
    }

    //getting the outline of the center going right
    private ArrayList<Pair> getStartingYs(ArrayList<Pair> directions, Pair minX, Pair maxX, boolean top, BufferedImage img, BufferedImage output) {
        ArrayList<Pair> points = new ArrayList<Pair>();
        Pair minYs = new Pair(minX.x,minX.y);
        Pair maxYs = new Pair(-1,-1);
        int maxY = -1;
        int minY = minX.y;

        int x = minX.x;
        int y = minX.y;
        int distance = -1;
        int dir1 = 1;
        int dir2 = 5;
        if (top) {
            dir1 = 5;
            dir2 = 1;
        }

        while (x <= maxX.x) {
            //getting next outline value
            if (ImageUtils.getRed(img.getRGB(x, y)) > 5)
                if (top)
                    y += ImageDistanceUtils.getInvertCenterDistance(directions, x, y, dir1, img);
                else
                    y -= ImageDistanceUtils.getInvertCenterDistance(directions, x, y, dir1, img);
            distance = -1;
            int tempX = x;
            int tempY = y;
            while (tempX < img.getWidth()-1 && tempX > 0 && tempY > 0 && tempY < img.getHeight()-1 && ImageUtils.getRed(img.getRGB(tempX, tempY)) < 5) {
                tempX += directions.get(dir2).x;
                tempY += directions.get(dir2).y;
                //output.setRGB(tempX, tempY, blue.getRGB()); //getting the vertical points
                points.add(new Pair(tempX,tempY)); //getting the vertical points
                distance++;
            }
            if (distance != -1)
                if (top)
                    y -= distance;
                else
                    y += distance;
            //output.setRGB(x, y, blue.getRGB()); //getting horizontal points
            points.add(new Pair(x,y)); //getting the horizontal points

            //getting max Y values
            if (y > maxY) {
                maxY = y;
                maxYs = new Pair(x,y);
            }
            if (y < minY) {
                minY = y;
                minYs = new Pair(x,y);
            }
            x++;
        }

        points.add(0,minYs);
        points.add(0,maxYs);
        return points;
    }

    //getting the outline of the center going left
    private ArrayList<Pair> getStartingYsInverted(ArrayList<Pair> directions, Pair minX, Pair maxX, boolean top, BufferedImage img, BufferedImage output) {
        ArrayList<Pair> points = new ArrayList<Pair>();
        int x = maxX.x;
        int y = maxX.y;
        int distance = -1;
        int dir1 = 1;
        int dir2 = 5;
        if (top) {
            dir1 = 5;
            dir2 = 1;
        }

        while (x >= minX.x) {
            //getting next outline value
            if (ImageUtils.getRed(img.getRGB(x, y)) > 5)
                if (top)
                    y += ImageDistanceUtils.getInvertCenterDistance(directions, x, y, dir1, img);
                else
                    y -= ImageDistanceUtils.getInvertCenterDistance(directions, x, y, dir1, img);
            distance = -1;
            int tempX = x;
            int tempY = y;
            while (tempX < img.getWidth()-1 && tempX > 0 && tempY > 0 && tempY < img.getHeight()-1 && ImageUtils.getRed(img.getRGB(tempX, tempY)) < 5) {
                tempX += directions.get(dir2).x;
                tempY += directions.get(dir2).y;
                //output.setRGB(tempX, tempY, blue.getRGB());
                points.add(new Pair(tempX,tempY)); //getting the horizontal points

                distance++;
            }
            if (distance != -1)
                if (top)
                    y -= distance;
                else
                    y += distance;
            //output.setRGB(x, y, blue.getRGB());
            points.add(new Pair(x,y)); //getting the horizontal points

            x--;
        }
        return points;
    }

    // Sees if there's any points that overlap from 2 arrays
    private boolean overlappingPoints(ArrayList<Trips> a1, ArrayList<Trips> a2) {
        for (Trips i : a1) {
            if (containsPair(a2, i))
                return true;
        }
        return false;
    }

    // Ignoring direction when comparing trips arrays
    private boolean containsPair(ArrayList<Trips> branch, Trips point) {
        for (Trips i : branch)
            if (i.x == point.x && i.y == point.y)
                return true;
        return false;
    }

    //removing tiny branches that aren't connected from both sides
    private void removeTinyBranches(ArrayList<Branch> branches) {
        boolean startFound;
        boolean endFound;
        Trips endPoint;
        Trips startPoint;

        //sweeping through and removing branches of size 0
        for (int i = 0; i < branches.size(); i++) {
            if (branches.get(i).getPoints().isEmpty()) {
                branches.remove(i);
                i--;
            }
        }

        int size = 0;
        for (int i = 0; i < branches.size(); i++) {
            size = branches.get(i).getPoints().size();

            //just removing all branches < 5
            if (size < 5) {
                branches.remove(i);
                i--;
                //if the branch is < 10, then we check to see if it's connected at both sizes, if not, then we're removing it
            } else if (size < 10) {
                startPoint = branches.get(i).getStartingPoint();
                endPoint = branches.get(i).getEndingPoint();
                startFound = false;
                endFound = false;
                for (int k = 0; k < branches.size(); k++) {
                    if (k != i) {
                        if (ImageDistanceUtils.getOffsetDistanceFromBranch(null, endPoint, branches.get(k).getStartingPoint().x, branches.get(k).getStartingPoint().y) < 2)
                            endFound = true;
                        if (ImageDistanceUtils.getOffsetDistanceFromBranch(null, startPoint, branches.get(k).getEndingPoint().x, branches.get(k).getEndingPoint().y) < 2)
                            startFound = true;
                        if (startFound && endFound)
                            break;
                    }
                }
                if (!startFound || !endFound) {
                    branches.remove(i);
                    i--;
                }
            }
        }
    }

    // When 2 branches are split but should be just one, this will combine them
    private void mergeSplitBranches(ArrayList<Branch> branches) {
        int count;
        Trips endPoint;
        Branch tempBranch = new Branch();

        for (int i = 0; i < branches.size(); i++) {
            count = 0;
            endPoint = branches.get(i).getEndingPoint();
            //finding all the branches with ending points starting where the i branch ends
            for (int k = i + 1; k < branches.size(); k++) {
                if (ImageDistanceUtils.getOffsetDistanceFromBranch(null, endPoint, branches.get(k).getStartingPoint().x, branches.get(k).getStartingPoint().y) < 2) {
                    count++;
                    tempBranch = branches.get(k); //if the count ends up being one, then this will be the branch to merge with the main one
                }
            }
            //if there's only one branch that starts where the i branch ends, then we know if should just be one branch
            if (count == 1) {
                branches.get(i).addToPoints(tempBranch.getPoints());
                branches.remove(tempBranch);
                i--;
            }
        }
    }

    // Some branches start where the other ends, so we're just going to remove the starting point of the 2nd branch
    private void removeOverlappingPoints(ArrayList<Branch> branches) {
        Trips endPoint;
        for (int i = 0; i < branches.size(); i++) {
            endPoint = branches.get(i).getEndingPoint();
            //finding all the branches with ending points starting where the i branch ends
            for (int k = i + 1; k < branches.size(); k++) {
                if (endPoint.x == branches.get(k).getStartingPoint().x && endPoint.y == branches.get(k).getStartingPoint().y) {
                    branches.get(k).removePoint(branches.get(k).getStartingPoint());
                }
            }
        }
    }
}
