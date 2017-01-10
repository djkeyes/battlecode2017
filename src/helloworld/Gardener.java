package helloworld;

import battlecode.common.*;

import java.util.Arrays;

strictfp class Gardener extends RobotPlayer {

    static int itemsBuilt = 0;

    static void run() throws GameActionException {
        while (true) {

            // Randomly attempt to build a tree or unit
            RobotType desiredType = null;
            boolean buildTree = false;
            int idx = itemsBuilt % 14;
            if(idx <= 1 || idx == 5 || idx == 7 || idx == 11 || idx == 13){
                buildTree = true;
            } else if(2 <= idx && idx <= 3){
                desiredType = RobotType.LUMBERJACK;
            } else if(idx == 4 || (8 <= idx && idx <= 9)){
                desiredType = RobotType.SOLDIER;
            } else if(idx == 6){
                desiredType = RobotType.TANK;
            } else if(idx == 10){
                desiredType = RobotType.TANK;
            } else if(idx == 12){
                desiredType = RobotType.LUMBERJACK;
            }

            tryBuildOrPlant(desiredType, buildTree);

            tryWateringNearby();

            moveToWitheringTree();

            tryShakeNearby();

            tryTrivialWin();

            Clock.yield();
        }
    }

    static void tryBuildOrPlant(RobotType desiredType, boolean isTree) throws GameActionException {
        // Generate a random direction
        Direction dir = randomDirection();

        if(isTree){
            if(rc.canPlantTree(dir)){
                rc.plantTree(dir);
                itemsBuilt++;
            }
        } else {
            if(rc.canBuildRobot(desiredType, dir)){
                rc.buildRobot(desiredType, dir);
                itemsBuilt++;
            }
        }
    }

    static void tryWateringNearby() throws GameActionException {
        if(!rc.canWater()){
            return;
        }

        // FIXME currently, I believe the docs are wrong
        // they claim the tree must be within the stride radius, but I think it's actually the body radius which the
        // server uses as the threshold.
        //TreeInfo[] nearbyTrees = rc.senseNearbyTrees(type.bodyRadius + 1.0f, us);
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(2.0f*type.bodyRadius, us);
        // find lowest health tree
        float lowestHealth = Float.MAX_VALUE;
        TreeInfo bestTree = null;
        for (TreeInfo tree : nearbyTrees){
            float health = tree.getHealth();
            if(health < lowestHealth){
                lowestHealth = health;
                bestTree = tree;
            }
        }

        // Daniel: I'm getting a rare exception exception here -- call rc.canWater() to double check
        if(bestTree != null && rc.canWater(bestTree.getID())) {
            rc.water(bestTree.getID());
        }

    }
    static void moveToWitheringTree() throws GameActionException {
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(-1, us);
        // find lowest health tree
        float lowestHealth = Float.MAX_VALUE;
        TreeInfo bestTree = null;
        for (TreeInfo tree : nearbyTrees){
            float health = tree.getHealth();
            if(health < lowestHealth){
                lowestHealth = health;
                bestTree = tree;
            }
        }

        if(bestTree != null) {
            tryMove(rc.getLocation().directionTo(bestTree.getLocation()), 20, 20);
        }

    }
}
