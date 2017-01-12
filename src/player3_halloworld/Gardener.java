package player3_halloworld;

import battlecode.common.*;

import java.util.Arrays;

strictfp class Gardener extends RobotPlayer {

    static int itemsBuilt = 0;
    static int treesBuilt = 0;
    static int roundLived = 0;
    static int idle = 0;
    static Direction directBorn = randomDirection();

    static void run() throws GameActionException {
        while (true) {
        	roundLived ++;
            // Randomly attempt to build a tree or unit
            RobotType desiredType = null;
            boolean buildTree = false;
            
            if(itemsBuilt ==0 ){
            	desiredType = RobotType.SCOUT;
            	tryBuildOrPlant(desiredType, buildTree);
            }
            if (roundLived < 10){
        		tryMove(directBorn,20,10); // Move a bit away
        	}
            tryPlantInRing();
            
            double num = Math.random();
            if (num < 0.2 ) {
            	desiredType = RobotType.SCOUT;
            	tryBuildOrPlant(desiredType, buildTree);
            }
            else if (num < 0.8) {
            	desiredType = RobotType.LUMBERJACK;
            	tryBuildOrPlant(desiredType, buildTree);
            }
            else{
            	desiredType = RobotType.TANK;
            	tryBuildOrPlant(desiredType, buildTree);
            }
            // harassement scouts, and later more scouts


                	
            
            
 

            tryWateringNearby();

            //moveToWitheringTree();

            //tryShakeNearby();

            tryTrivialWin();

            Clock.yield();
        }
    }
    
    static boolean tryPlantInRing() throws GameActionException{
    	if (treesBuilt == 5) return false;
    	for (int i = 0; i < 6; i++){
    		Direction dir = new Direction((float) (i*Math.PI/3.0));
    		if(rc.canPlantTree(dir)){
        		rc.plantTree(dir);
        		treesBuilt ++;
        		return true;
        	}
    	}
    	return false;
    	
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