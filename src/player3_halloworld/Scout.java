package player3_halloworld;


import battlecode.common.BulletInfo;
import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.TreeInfo;

strictfp class Scout extends RobotPlayer {
	static int strategy = 0; // Defining which state the robot is in. 
	// 0 for harassing opponents gardener, 1 for hide into enemy trees and shoot, 2 all in attack
	static int myTreeID=0; // ID of the tree it tries to hide in
	static int myOpponentID = 0; // Keep chasing the same gardener
	static int idle=0;
    static void run() throws GameActionException {
        while (true) {

//            if(!tryDodge()){
//                tryMoveTowardEnemyArchons();
//            }
        if (strategy == 0){
        	tryHarassGardener();
        } 
        else if (strategy == 1){
        	tryMoveOntoTree();
        	tryAttackEnemy();
        	if (!rc.hasMoved() && !rc.hasAttacked() ) { 
        		idle ++;
        		if (idle >200){ // Some time it sits there too long
        			strategy = 2;
        		}
        	}
        }
        else{
        	tryMoveTowardEnemy();
        	tryAttackEnemy();
        }
        	
        	//tryMoveOntoTree();

          
            tryShakeNearby();

            tryTrivialWin();

            Clock.yield();
        }
    }
    
    static void tryHarassGardener() throws GameActionException {
    	if (myOpponentID != 0 && rc.canSenseRobot(myOpponentID)){
    		Direction dir = new Direction(rc.getLocation(),rc.senseRobot(myOpponentID).getLocation());
    		if(rc.canFireSingleShot() && Math.random()< 0.8){ 
    			// Fire shot towards the gardener, sometimes not to move without being shot
    			rc.fireSingleShot(dir);
    		}
    		else{
    			tryMove(dir, 10,5); // Move closer towards the gardener
    		}
    	}
    	else{ // No opponent ID being registered
    		RobotInfo[] opponents = rc.senseNearbyRobots(10.0f,them);
    		if (opponents.length == 0){
    			tryMoveTowardEnemyArchons(); // No enemy spotted, go ahead find someone
    		}
    		else{
    			for (RobotInfo robotSpotted : opponents){
    	    		if (robotSpotted.getType() == RobotType.GARDENER){
    	    			myOpponentID = robotSpotted.getID(); // select target
    	    			tryMove(new Direction(rc.getLocation(),robotSpotted.getLocation()),10,5);
    	    			break;
    	    		}
    	    	}
    		}
    		tryAttackEnemy(); // Random attack
    	}
    	if (!rc.hasMoved()) {
    		tryMove(randomDirection(), 20, 10); // no gardener found, try any direction loop around
    	}
    	
    	if (rc.getHealth() < 10 && strategy == 0 && idle<10){ // Lost half of its life
    		strategy = 1; // change strategy, Try hit and run now
    	}
    	
	}


    
    static boolean tryMoveOntoTree() throws GameActionException{
    	if (myTreeID != 0 && rc.canSenseTree(myTreeID)){
    		rc.move(rc.senseTree(myTreeID).getLocation());
    		rc.broadcast(TREEOPPONENTIDCHANNEL, myTreeID); 
    		// TODO: Telling other scout that this tree is occupied
    		// Now just be annoying
    	}
    	else { // Need new TreeID assignment
    		//int otherTreeID = rc.readBroadcast(0);
    	   	TreeInfo[] nearbyTrees = rc.senseNearbyTrees(10);
    	   	rc.broadcast(TREEOPPONENTCOUNTCHANNEL, nearbyTrees.length); // depending on the number of trees, decide whether producing more scouts
        	if (nearbyTrees.length>0){
        		MapLocation my_loc = rc.getLocation();
        		TreeInfo bestTree = nearbyTrees[0];
        		float closest = 10000;
                for (TreeInfo tree : nearbyTrees){
                    float dis = tree.getLocation().distanceTo(my_loc);
                    if(dis< closest && tree.getTeam()==them && !rc.isLocationOccupiedByRobot(tree.getLocation())){
                    	// Good choice if its close, tree belongs to the opponent, and its not occupied
                        closest = dis;
                        bestTree = tree;
                    }
                }
                if (closest != 10000){ // Found a good tree to hide
                    Direction dirToTree = rc.getLocation().directionTo(bestTree.getLocation());
                    rc.setIndicatorLine(bestTree.getLocation(),rc.getLocation(), 0, 0,255);
                    myTreeID = bestTree.getID();
                    rc.move(dirToTree);
                }
                else{ // No tree to hide, let's dodge
                	tryDodge(); 
                }
                
                
        	}
        	
            return true;
    	}
    	return false;
    }

    
    
    static boolean tryDodge() throws GameActionException {
        // jd: actually take bullets into account, for now just dodge the closest one
    	BulletInfo bullets[] = rc.senseNearbyBullets(3); // only when its about to hit
    	if (bullets.length >0){
    		float closest = 3;
    		BulletInfo bulletincoming = bullets[0];
    		MapLocation my_loc = rc.getLocation();
    		for (BulletInfo singlebullet : bullets){
    			float dis = singlebullet.getLocation().distanceTo(my_loc);
    			if (dis < closest){ // Get the closest bullet
    				closest = dis;
    				bulletincoming = singlebullet;
    			}
    		}
    		Direction bulletandme = new Direction(bulletincoming.getLocation(),my_loc);
    		// The robot decides, depending on the first incoming shot, 
    		// to move in the direction orthogonal to the direct line between bullet and itself
    		// It merely decides in which direction along the lines
    		if (bulletincoming.getDir().degreesBetween(bulletandme) > 0){
    			// to determine which direction to hide
    			bulletandme.rotateRightDegrees((float) (Math.PI/2.0));
    		}
    		else{
    			bulletandme.rotateLeftDegrees((float) (Math.PI/2.0));
    		}
    		tryMove(bulletandme,5,2);
    		return true;
    	}
    	return false;
    	
//        if(rc.senseNearbyRobots(7.0f, them).length > 0){
//            // Move randomly
//            tryMove(randomDirection(), 20, 10);
//            return true;
//        }
//        return false;
    	
    	
    }

}