package combinedstrategies;

import battlecode.common.*;

public class Scout extends RobotPlayer implements RobotHandler {
	
		static int strategy = 0; // Defining which state the robot is in. 
		// 0 for harassing enemiesInSight gardener, 
		// 1 for scouting around map and pick up bullets, 
		// 2 for hide and observe at enemy tree
		static int myTreeID=0; // ID of the tree it tries to hide in
		static int myOpponentID = 0; // Keep chasing the same gardener
		static int idle=0;
		static TreeInfo[] trees = null;
		static MapLocation my_loc = null;
		static BulletInfo[] bullets = null;
		static float SENSEBULLETRANGE = 8; // Dont consider bullets far away
	    static int MAPXLIMITCHANNEL = 5;
	    static int MAPYLIMITCHANNEL = 6;
	    static int MAPXLOWERLIMITCHANNEL = 9;
	    static int MAPYLOWERLIMITCHANNEL = 10;
	    static int SCOUTMAPEXPLORERCHANNEL1 = 7;
	    static int SCOUTMAPEXPLORERCHANNEL2 = 8;
	    static int SCOUTFOUNDFARMERXCHANNEL = 11;
	    static int SCOUTFOUNDFARMERYCHANNEL = 12;
	    static int XUPPERBOUND = 0; // The size of the map
	    static int YUPPERBOUND = 0;
	    static int XLOWERBOUND = 0; // The size of the map
	    static int YLOWERBOUND = 0;
		@Override
	    public void init() throws GameActionException {

	    }

	    @Override
	    public void onLoop() throws GameActionException {
        	trees = rc.senseNearbyTrees(10);
        	my_loc= rc.getLocation();
        	bullets = rc.senseNearbyBullets(SENSEBULLETRANGE);
	        if (strategy == 0){
	        	tryHarassGardener(); // Move and attack command
	        	System.out.println("I harass worker!");

	        	//rc.setIndicatorDot(my_loc, 255, 0, 0); // debug
	        	
	        } 
	        else if (strategy == 1){
	        	System.out.println("I explore the map!");
	        	
	        	exploreMap(); // Explores around the map and pick up bullets from trees
		        //rc.setIndicatorDot(my_loc, 0, 255, 0); // debug
	        }
	        else{
	        	System.out.println("I observe opponent!");
	        	observeOpponent();
	        	//rc.setIndicatorDot(my_loc, 0, 0, 255); // debug
	        }
            checkIdle();
            if (XUPPERBOUND == 0 || YUPPERBOUND == 0 || XLOWERBOUND == 0 || YLOWERBOUND == 0){ // Find out the map bound
            listenToScout();
            }
	    }

	    @Override
	    public void reportUnitCount() throws GameActionException {
	        if (Messaging.shouldSendHeartbeat()) {
	            Messaging.sendHeartbeatSignal(0, 0, 0, 1, 0, 0, 0, 0f);
	        }
	    }


	    
	    static int IDLETOOLONG = 300;
	    static void checkIdle() {
			if (enemiesInSight.length == 0 && !rc.hasAttacked()){
				// no enemy found and no shots fired 
				idle ++;
			}
			else {idle --;}
			idle = Math.max(0, idle);
			if (idle > IDLETOOLONG && strategy ==0){
				strategy++;
			}
			
		}

		static void observeOpponent() throws GameActionException {
			// When scout has rather low health and has explored most of the map
	    	// go towards enemy and try stay on top of their trees and just be annoying
	    	// run away from all attacking enemy units
	    	// Not yet implemented
			strategy = 0;
			
		}
	    static void listenToScout() throws GameActionException{
	    	XUPPERBOUND = rc.readBroadcast(MAPXLIMITCHANNEL);
	    	YUPPERBOUND = rc.readBroadcast(MAPYLIMITCHANNEL);
	    	XLOWERBOUND = rc.readBroadcast(MAPXLOWERLIMITCHANNEL);
	    	YLOWERBOUND = rc.readBroadcast(MAPYLOWERLIMITCHANNEL);
	    }
	    
	    static MapLocation nextTargetLocation = null;
	    static int scoutID = 0; // 1 for begin at upper left, 2 for begin at bottom right
	    static float gridSize=0;
		static void exploreMap() throws GameActionException {
			// Major goal of this function is to find out the size of the map and broadcast
			// And pick up bullets along the way
			Direction[] fourDirections = {Direction.EAST,Direction.SOUTH,Direction.WEST,Direction.NORTH};
			
			// TODO Change to general DETECT MAP BOUNDARY function
			senseAndBroadcastGardener();
			
			if (XUPPERBOUND == 0 || YUPPERBOUND == 0|| XLOWERBOUND == 0 || YLOWERBOUND == 0){ // Need to Find out the map bound
				
				if (rc.onTheMap(my_loc, type.sensorRadius) == false){ // The edge is seen at the moment
					for (Direction dir:fourDirections){
						if (XUPPERBOUND != 0 && dir == Direction.EAST) { // Already done
							continue;
						}
						if (YUPPERBOUND != 0 && dir == Direction.NORTH) { // Already done
							continue;
						}
						if (YLOWERBOUND != 0 && dir == Direction.SOUTH) { // Already done
							continue;
						}
						if (XLOWERBOUND != 0 && dir == Direction.WEST) { // Already done
							continue;
						}
						if (rc.onTheMap(my_loc.add(dir, type.sensorRadius))== false){ // this direction is not inside map
							// run binary search to find out the range
							int lowerbound = 0;
							int upperbound = (int) type.sensorRadius;
							while(lowerbound != upperbound){
								int mid = (lowerbound+upperbound+1) /2;
								if (rc.onTheMap(my_loc.add(dir,(float)mid)) == false){
									upperbound = mid-1;
								}
								else {
									lowerbound = mid;
								}
							}
							if (dir == Direction.EAST){
								rc.broadcast(MAPXLIMITCHANNEL, lowerbound+(int)rc.getLocation().x);
								System.out.println("Find x upper bound "+ (lowerbound+(int)rc.getLocation().x));
								
							}
							else if (dir ==Direction.NORTH){
								rc.broadcast(MAPYLIMITCHANNEL, lowerbound+(int)rc.getLocation().y);
								System.out.println("Find y upper bound "+(lowerbound+(int)rc.getLocation().y));
								
							}
							else if (dir ==Direction.WEST){
								rc.broadcast(MAPXLOWERLIMITCHANNEL, -lowerbound+(int)rc.getLocation().x);
								System.out.println("Find x lower bound "+(-lowerbound+(int)rc.getLocation().x));
								
							}
							else {
								rc.broadcast(MAPYLOWERLIMITCHANNEL, -lowerbound+(int)rc.getLocation().y);
								System.out.println("Find y lower bound "+(-lowerbound+(int)rc.getLocation().y));
								
							}
							
						}
					}
				}
			}
			
			if (XLOWERBOUND == 0){
				tryMove(Direction.WEST,10,5); // Go figure out x lower bound
			}
			else if (YUPPERBOUND == 0){
				tryMove(Direction.NORTH,10,5); // Go figure out y upper bound
			}
			else if (YLOWERBOUND == 0){
				tryMove(Direction.SOUTH,10,5); // Go figure out y lower bound
			}
			else if (XUPPERBOUND == 0){
				tryMove(Direction.EAST,10,5); // GO figure out x upper bound
			}
			
			else { // Now discover the whole map and shake trees
				// Transfer the map into grid
				gridSize = (float) (0.9 * type.sensorRadius); // Some overlap to make sure everything visible
				
				if (nextTargetLocation == null){  // Initialize first target location 
					// TODO Validate feasibility and test
				// Now we know the map size, enemy span location, and current location
				// define several roadmap locations and pick up bullets along the way

					if (rc.getLocation().distanceTo(new MapLocation(XLOWERBOUND,YLOWERBOUND)) < 
							rc.getLocation().distanceTo(new MapLocation(XUPPERBOUND,YUPPERBOUND))
							&& rc.readBroadcast(SCOUTMAPEXPLORERCHANNEL1)==0){ 
						// Start from bottom left
						nextTargetLocation = new MapLocation(XLOWERBOUND+gridSize,YLOWERBOUND+gridSize);
						rc.broadcast(SCOUTMAPEXPLORERCHANNEL1, 1); 
						scoutID =1;
						
					}
					else if (rc.readBroadcast(SCOUTMAPEXPLORERCHANNEL2)==0){ // Start from upper right
						nextTargetLocation = new MapLocation(XUPPERBOUND-gridSize,YUPPERBOUND-gridSize);
						rc.broadcast(SCOUTMAPEXPLORERCHANNEL2, 2); 
						scoutID =2;
					}
					else {
						// Already two scouts
						strategy = 2;
						return;
					}
				}
				
				tryMoveTargetLocationScout(nextTargetLocation);
				
			}
			
			
		}


		static void senseAndBroadcastGardener() throws GameActionException {
			for (RobotInfo robotSpotted : enemiesInSight){
	    		if (robotSpotted.getType() == RobotType.GARDENER){
	    			rc.broadcast(SCOUTFOUNDFARMERXCHANNEL, (int)robotSpotted.getLocation().x);
	    			rc.broadcast(SCOUTFOUNDFARMERYCHANNEL, (int)robotSpotted.getLocation().y);
	    		}
			}
		}

		static float SCOUTEXPLORETHRESHOLD = (float) 2;
		static void tryMoveTargetLocationScout(MapLocation myNextTargetLocation) throws GameActionException {
			//System.out.println("Trying to move to next location");
			boolean foundTree = false;
			if (trees.length> 0){
				for (TreeInfo nexttree:trees){
					if (nexttree.containedBullets > 0 &&
							nexttree.getTeam() == Team.NEUTRAL
							){ // move to the closest neutral tree containing bullets
						
						foundTree = true;
						if (rc.canShake(nexttree.ID)){
							rc.shake(nexttree.ID);
							System.out.println("Shaking");
							break;
						}
						else {
							System.out.println("moving towards tree");
							tryMove(my_loc.directionTo(nexttree.getLocation()),20,5);
							break;
						}
					}
				}
			}
			if (!foundTree){ // No trees, move towards target location
				
				tryMove(my_loc.directionTo(myNextTargetLocation),20,5); // continue exploring the map
				if (my_loc.isWithinDistance(myNextTargetLocation, SCOUTEXPLORETHRESHOLD)){
					nextTargetLocation = generateNewTargetLocation(myNextTargetLocation);
					
				}
			}
			
		}
		static float CLOSETOENEMYTHRESHOLD = 9;
		static int oddRoute = 0;
		static Direction[] EASTandWEST = {Direction.WEST,Direction.EAST};
		static MapLocation generateNewTargetLocation(MapLocation myCurTargetLocation) {
			// Create next grid point as next target
			MapLocation myNextTargetLocation=myCurTargetLocation;
			if (scoutID == 1){ // Start at lower left 
				//System.out.println("Find out next position");
				Direction curXdir = EASTandWEST[(scoutID+oddRoute) %2];
				while (isValidTargetLoc(myNextTargetLocation.add(curXdir,gridSize*2)) ){
					myNextTargetLocation=myNextTargetLocation.add(curXdir,gridSize*2);
					if (!myNextTargetLocation.isWithinDistance(
							rc.getInitialArchonLocations(them)[0],CLOSETOENEMYTHRESHOLD)){
						return myNextTargetLocation;
					}
					
				}
				if (isValidTargetLoc(myNextTargetLocation.add(Direction.NORTH,gridSize*2))){
					myNextTargetLocation=myNextTargetLocation.add(Direction.NORTH,gridSize*2); 
					oddRoute = (oddRoute+1)%2;
					if (myNextTargetLocation.isWithinDistance(
							rc.getInitialArchonLocations(them)[0],CLOSETOENEMYTHRESHOLD)){					
						myNextTargetLocation=myNextTargetLocation.add(curXdir,gridSize*2);	
					}
					return myNextTargetLocation;
				}
				else { // Exploration done, change states
					strategy = 2;
					return new MapLocation(0,0);
				}	
			}
			else {  // Start at upper Right

				//System.out.println("Find out next position for 2");
				Direction curXdir = EASTandWEST[(scoutID+oddRoute) %2];
				while (isValidTargetLoc(myNextTargetLocation.add(curXdir,gridSize*2))){
					myNextTargetLocation = myNextTargetLocation.add(curXdir,gridSize*2);
					//System.out.println("Test Next Target loction x: " + myNextTargetLocation.x 
					//		+ "y:" + myNextTargetLocation.y);
					if (!myNextTargetLocation.isWithinDistance(
							rc.getInitialArchonLocations(them)[0],CLOSETOENEMYTHRESHOLD)){
						return myNextTargetLocation;
					}
					
				}
				if (isValidTargetLoc(myNextTargetLocation.add(Direction.SOUTH,gridSize*2))){
					myNextTargetLocation=myNextTargetLocation.add(Direction.SOUTH,gridSize*2);
					oddRoute = (oddRoute+1)%2;
					if (myNextTargetLocation.isWithinDistance(
							rc.getInitialArchonLocations(them)[0],CLOSETOENEMYTHRESHOLD)){		
						myNextTargetLocation=myNextTargetLocation.add(curXdir,gridSize*2);	
					}
					return myNextTargetLocation;
				}
				else { // Exploration done, change states
					strategy = 2;
					return new MapLocation(0,0);
				}				
			}
			
		}

		static boolean isValidTargetLoc(MapLocation newloc) {
			
			return (newloc.x <= XUPPERBOUND-gridSize) && (newloc.x >= XLOWERBOUND+gridSize)
					&& (newloc.y <= YUPPERBOUND-gridSize) && (newloc.y >= YLOWERBOUND+gridSize);
		}
		static float CLOSETOGARDNER = (float)4;
		static void tryHarassGardener() throws GameActionException {
			// MOVING PART
			boolean treeDodged = tryTreeDodge();
			if (!treeDodged){ // No dodging necessary
		    	if (myOpponentID != 0 && rc.canSenseRobot(myOpponentID)){ // found opposing gardener
		    		if (myTreeID!= 0 && rc.canSenseTree(myTreeID)){
		    			tryMoveIntoTreeCloseToGardner(); // go up the tree
		    		}
		    		else {
		    			tryAssignTreeID(); // find another tree
		    		}
			
		
		
		    	}
		    	else{ // No opponent ID being registered
		    		
		    		if (enemiesInSight.length == 0){
		    			
		    		}
		    		else{
		    			for (RobotInfo robotSpotted : enemiesInSight){
		    	    		if (robotSpotted.getType() == RobotType.GARDENER){
		    	    			myOpponentID = robotSpotted.getID(); // select target
		    	    			tryMove(new Direction(my_loc,robotSpotted.getLocation()),10,5);
		    	    			// Get as close to enemy gardener as possible
		    	    			return;
		    	    		}
		    	    	}
		    		}
		    		tryFindEnemyFarmers();
		    		
		
		    	}
			}
	    	// ATTACKING PART
	    	//System.out.println("my_loc);
			if (//!(isPathToRobotObstructed(rc.senseRobot(myOpponentID))) &&
					myOpponentID != 0 && rc.canSenseRobot(myOpponentID) &&
					rc.canFireSingleShot() && 
					my_loc.distanceTo(rc.senseRobot(myOpponentID).getLocation())<= CLOSETOGARDNER *type.bodyRadius){
				// Fire shot towards the gardener, if path clear and shots ready and close enough
				System.out.println("Shot fired towards " + myOpponentID + " i am at tree" + myTreeID);
				Direction dir = new Direction(my_loc,rc.senseRobot(myOpponentID).getLocation());
				rc.fireSingleShot(dir);
			} 	
			
	    	if (rc.getHealth() < 0.25*type.maxHealth && strategy == 0 && idle<10){ // Lost major part of life
	    		strategy = 1; // change strategy, go explore map
	    	}
	    	
		}


		static void tryFindEnemyFarmers() throws GameActionException {
			if (rc.readBroadcast(SCOUTFOUNDFARMERXCHANNEL)== 0){
				tryMoveTowardEnemyArchons(8000); // No enemy spotted, go ahead find someone
			}
			else {
				tryMove(my_loc.directionTo(new MapLocation((float)rc.readBroadcast(SCOUTFOUNDFARMERXCHANNEL),
						(float)rc.readBroadcast(SCOUTFOUNDFARMERXCHANNEL))),30,5); // Move towards broadcasted
			}
			
		}

		static float MINIMUMMOV = GameConstants.GENERAL_SPAWN_OFFSET;
		static float DODGEDISTANCE = 2;
		static float AVOIDSOLDIERDISTANCE = 4f;
	    static boolean tryTreeDodge() throws GameActionException {
			// dodging by minimum movement or keep current location
	    	// return true if the scout had to move or the current position is good for shooting
	    	// return false if there is no threat at all
	    	// First avoid come too close to solidier
	    	
	    	//if (avoidSoldier() ){
	    	//	return true;
	    	//}
	    	// TODO I don't think it works so well yet
	    	if (bullets.length == 0){
	    		//System.out.println("No need to dodge");
	    		return false;
	    	}
	    	for (BulletInfo bullet : bullets){ // find the shots that actually would hit
		    	Direction propagationDirection = bullet.dir;
		    	MapLocation bulletLocation = bullet.location;
		    	Direction directionToRobot = bulletLocation.directionTo(my_loc);
		    	float theta = propagationDirection.radiansBetween(directionToRobot);  	    	
				if (Math.abs(theta) < Math.PI/3){ // Get the closest bullet that would hit
					MapLocation dodgingSpot;
					if (myTreeID != 0 || rc.canSenseTree(myTreeID)){ // if currently spotted a tree
						System.out.println("myTree ID tree:" + myTreeID);
						dodgingSpot= rc.senseTree(myTreeID).getLocation().add(directionToRobot, MINIMUMMOV);
						System.out.println("Dodge inside tree");
					}
					else {
						dodgingSpot = my_loc.add(propagationDirection.rotateLeftDegrees(90),2);
						System.out.println("Dodge outside tree");
					}
					if (rc.canMove(dodgingSpot)){
						rc.move(dodgingSpot);
						System.out.println("Dodge successful to wished location");
						return true;
					}
					else{
						tryMove(propagationDirection.rotateRightDegrees(90),20,3);
						System.out.println("Dodge successful to wished direction");
						return true;
					}
						
				}
	    	}
	    	return false;
		}

		static boolean avoidSoldier() throws GameActionException {
			RobotInfo[] awareSolider = rc.senseNearbyRobots(AVOIDSOLDIERDISTANCE, them);
			for (RobotInfo enemy : awareSolider){
				if (enemy.getType() == RobotType.SOLDIER || enemy.getType() == RobotType.TANK){
					tryMove(enemy.getLocation().directionTo(my_loc).rotateLeftDegrees(90),45,5);
					System.out.println("Avoiding enemy soliders");
					return true;
				}
			}
			return false;
		}

		static void tryAssignTreeID() throws GameActionException {
	    	if (trees.length>0){
	            for (TreeInfo tree : trees){
	            	if (tree.team == them){
	            		float dis = tree.getLocation().distanceTo(rc.senseRobot(myOpponentID).getLocation());
	            		if (dis< CLOSETOGARDNER *type.bodyRadius){ // enemy tree close to opponent gardener
	            			myTreeID = tree.getID();
	            			return;
	            		}
	            	}
	            	
	            }
	    	}
			
		}

		static void tryMoveIntoTreeCloseToGardner() throws GameActionException {
			// Move to that tree
			if (myTreeID != 0 && rc.canSenseTree(myTreeID)&& 
					rc.canSenseLocation((rc.senseTree(myTreeID).getLocation()))&& // TODO weired exception
					(!rc.isLocationOccupiedByRobot(rc.senseTree(myTreeID).getLocation())
					|| rc.getLocation()!= rc.senseTree(myTreeID).getLocation())){
				if (rc.canMove(rc.senseTree(myTreeID).getLocation())){
					rc.move(rc.senseTree(myTreeID).getLocation());
				}
			}
			
		}



	    
	    static boolean tryDodge() throws GameActionException {
	        // jd: actually take bullets into account, for now just dodge the closest one
	    	
	    	if (bullets.length >0){
	    		Direction directionToRobot= null;
	    		Direction propagationDirection = null;
	    		float closest = 6;
	    		BulletInfo bulletincoming = bullets[0];   		
	    		for (BulletInfo bullet : bullets){
	    	    	propagationDirection = bullet.dir;
	    	    	MapLocation bulletLocation = bullet.location;
	    	    	directionToRobot = bulletLocation.directionTo(my_loc);
	    	    	float distToRobot = bulletLocation.distanceTo(my_loc);
	    	    	float theta = propagationDirection.radiansBetween(directionToRobot);  	    	
	    			if (distToRobot < closest && Math.abs(theta) < Math.PI/3){ // Get the closest bullet
	    				closest = distToRobot;
	    				bulletincoming = bullet;
	    			}
	    		}
	    		if (! tryMove(propagationDirection.rotateLeftDegrees(90),20,10)){ // Dodge right and left
	    			tryMove(propagationDirection.rotateRightDegrees(90),20,10);
	    		}
	    		
	    		return true;
	    	}
	    	return false;
	    	

	    	
	    }

	}
    