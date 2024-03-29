package combinedstrategies;

import battlecode.common.*;

public class Scout extends RobotPlayer implements RobotHandler {

	static int strategy = 0; // Defining which state the robot is in.
	// 0 for harassing enemiesInSight gardener,
	// 1 for scouting around map and pick up bullets,
	// 2 for hide and observe at enemy tree
	static int myTreeID = 0; // ID of the tree it tries to hide in
	static int myOpponentID = 0; // Keep chasing the same gardener
	static int idle = 0;
	static TreeInfo[] trees = null;
	static MapLocation my_loc = null;
	static BulletInfo[] bullets = null;
	static float SENSEBULLETRANGE = 8; // Dont consider bullets far away

	static int SCOUTMAPEXPLORERCHANNEL1 = 107;
	static int SCOUTMAPEXPLORERCHANNEL2 = 108;
	static int SCOUTFOUNDFARMERXCHANNEL = 111;
	static int SCOUTFOUNDFARMERYCHANNEL = 112;

	// Local structure saving up the broadcasted enemy locations
	static int[] xposRead = null;
	static int[] yposRead = null;
	static int numpos = 0;

	@Override
	public void init() throws GameActionException {

	}

	@Override
	public void onLoop() throws GameActionException {
		trees = rc.senseNearbyTrees(10);
		my_loc = rc.getLocation();
		bullets = rc.senseNearbyBullets(SENSEBULLETRANGE);
		if (strategy == 0) {
			tryHarassGardener(); // Move and attack command
			// System.out.println("I harass worker!");

//			 rc.setIndicatorDot(my_loc, 255, 0, 0); // debug

		} else if (strategy == 1) {
			// System.out.println("I explore the map!");

			exploreMap(); // Explores around the map and pick up bullets from
							// trees
//			 rc.setIndicatorDot(my_loc, 0, 255, 0); // debug
		} else {
			// System.out.println("I observe opponent!");
//			varifyEnemyLocation();
			// This strategy of the scout tries to figure out whether the
			// broadcasted
			// location is actually worth attacking

			// rc.setIndicatorDot(my_loc, 0, 0, 255); // debug

			tryCollectBullets();
		}
		checkIdle();

	}

	private void tryCollectBullets() throws GameActionException {

		TreeInfo[] nearbyTrees = rc.senseNearbyTrees(type.sensorRadius, Team.NEUTRAL);
		float shortestDist = Float.POSITIVE_INFINITY;
		TreeInfo bestTree = null;
		for (TreeInfo tree : nearbyTrees) {
			float dist = rc.getLocation().distanceTo(tree.location);
			if(dist < shortestDist){
				shortestDist = dist;
				bestTree = tree;
			}
		}
		if (bestTree != null) {
			tryMove(rc.getLocation().directionTo(bestTree.location), 10, 5);
		}
	}

	static void varifyEnemyLocation() {
		// Read in the enemy locations and find the latest possible place where
		// its worth attacking
		// Everytime when a new location get broadcasted TODO
		// if (rc.getLocation().distanceTo(new MapLocation(xposRead,yposRead)) <
		// type.strideRadius)
		// Messaging.readEnemyPositions(xposRead, yposRead, numpos);

	}

	@Override
	public void reportUnitCount() throws GameActionException {
		if (Messaging.shouldSendHeartbeat()) {
			Messaging.sendHeartbeatSignal(0, 0, 0, 1, 0, 0, 0, 0f);
		}
	}

	static int IDLETOOLONG = 100;
	static MapLocation prevPosition = null;

	static void checkIdle() {
		if ((enemiesInSight.length == 0 || prevPosition.distanceTo(rc.getLocation()) < 0.1f) && !rc.hasAttacked()) {
			// no enemy found and no shots fired
			idle++;
		} else {
			idle--;
		}
		idle = Math.max(0, idle);
		if (idle > IDLETOOLONG && strategy == 0) {
			strategy++;
		}

		prevPosition = rc.getLocation();

	}

	static void observeOpponent() throws GameActionException {
		// When scout has rather low health and has explored most of the map
		// go towards enemy and try stay on top of their trees and just be
		// annoying
		// run away from all attacking enemy units
		// Not yet implemented
		strategy = 0;

	}

	static MapLocation nextTargetLocation = null;
	static int scoutID = 0; // 1 for begin at upper left, 2 for begin at bottom
							// right
	static float gridSize = 0;

	static void exploreMap() throws GameActionException {
		// Major goal of this function is to find out the size of the map and
		// broadcast
		// And pick up bullets along the way
		senseAndBroadcastGardener();

		if (Messaging.lowerLimitX == 0) {
			tryMove(Direction.WEST, 10, 5); // Go figure out x lower bound
		} else if (Messaging.upperLimitY == 0) {
			tryMove(Direction.NORTH, 10, 5); // Go figure out y upper bound
		} else if (Messaging.lowerLimitY == 0) {
			tryMove(Direction.SOUTH, 10, 5); // Go figure out y lower bound
		} else if (Messaging.upperLimitX == 0) {
			tryMove(Direction.EAST, 10, 5); // GO figure out x upper bound
		}

		else { // Now discover the whole map and shake trees
				// Transfer the map into grid
			gridSize = (float) (0.9 * type.sensorRadius); // Some overlap to
															// make sure
															// everything
															// visible

			if (nextTargetLocation == null) { // Initialize first target
												// location
				// TODO Validate feasibility and test
				// Now we know the map size, enemy span location, and current
				// location
				// define several roadmap locations and pick up bullets along
				// the way

				if (rc.getLocation().distanceTo(new MapLocation(Messaging.lowerLimitX, Messaging.lowerLimitY)) < rc
						.getLocation().distanceTo(new MapLocation(Messaging.upperLimitX, Messaging.upperLimitY))
						&& rc.readBroadcast(SCOUTMAPEXPLORERCHANNEL1) == 0) {
					// Start from bottom left
					nextTargetLocation = new MapLocation(Messaging.lowerLimitX + gridSize,
							Messaging.lowerLimitY + gridSize);
					rc.broadcast(SCOUTMAPEXPLORERCHANNEL1, 1);
					scoutID = 1;

				} else if (rc.readBroadcast(SCOUTMAPEXPLORERCHANNEL2) == 0) { // Start
																				// from
																				// upper
																				// right
					nextTargetLocation = new MapLocation(Messaging.upperLimitX - gridSize,
							Messaging.upperLimitY - gridSize);
					rc.broadcast(SCOUTMAPEXPLORERCHANNEL2, 2);
					scoutID = 2;
				} else {
					// Already two scouts
					strategy = 2;
					return;
				}
			}

			tryMoveTargetLocationScout(nextTargetLocation);

		}

	}

	static void senseAndBroadcastGardener() throws GameActionException {
		for (RobotInfo robotSpotted : enemiesInSight) {
			if (robotSpotted.getType() == RobotType.GARDENER) {
				rc.broadcast(SCOUTFOUNDFARMERXCHANNEL, (int) robotSpotted.getLocation().x);
				rc.broadcast(SCOUTFOUNDFARMERYCHANNEL, (int) robotSpotted.getLocation().y);
			}
		}
	}

	static float SCOUTEXPLORETHRESHOLD = (float) 2;

	static void tryMoveTargetLocationScout(MapLocation myNextTargetLocation) throws GameActionException {
		// System.out.println("Trying to move to next location");
		boolean foundTree = false;
		if (trees.length > 0) {
			for (TreeInfo nexttree : trees) {
				if (nexttree.containedBullets > 0 && nexttree.getTeam() == Team.NEUTRAL) { // move
																							// to
																							// the
																							// closest
																							// neutral
																							// tree
																							// containing
																							// bullets

					foundTree = true;
					if (rc.canShake(nexttree.ID)) {
						rc.shake(nexttree.ID);
						// System.out.println("Shaking");
						break;
					} else {
						// System.out.println("moving towards tree");
						tryMove(my_loc.directionTo(nexttree.getLocation()), 20, 5);
						break;
					}
				}
			}
		}
		if (!foundTree) { // No trees, move towards target location

			tryMove(my_loc.directionTo(myNextTargetLocation), 20, 5); // continue
																		// exploring
																		// the
																		// map
			if (my_loc.isWithinDistance(myNextTargetLocation, SCOUTEXPLORETHRESHOLD)) {
				nextTargetLocation = generateNewTargetLocation(myNextTargetLocation);

			}
		}

	}

	static float CLOSETOENEMYTHRESHOLD = 9;
	static int oddRoute = 0;
	static Direction[] EASTandWEST = { Direction.WEST, Direction.EAST };

	static MapLocation generateNewTargetLocation(MapLocation myCurTargetLocation) {
		// Create next grid point as next target
		MapLocation myNextTargetLocation = myCurTargetLocation;
		if (scoutID == 1) { // Start at lower left
			// System.out.println("Find out next position");
			Direction curXdir = EASTandWEST[(scoutID + oddRoute) % 2];
			while (isValidTargetLoc(myNextTargetLocation.add(curXdir, gridSize * 2))) {
				myNextTargetLocation = myNextTargetLocation.add(curXdir, gridSize * 2);
				if (!myNextTargetLocation.isWithinDistance(rc.getInitialArchonLocations(them)[0],
						CLOSETOENEMYTHRESHOLD)) {
					return myNextTargetLocation;
				}

			}
			if (isValidTargetLoc(myNextTargetLocation.add(Direction.NORTH, gridSize * 2))) {
				myNextTargetLocation = myNextTargetLocation.add(Direction.NORTH, gridSize * 2);
				oddRoute = (oddRoute + 1) % 2;
				if (myNextTargetLocation.isWithinDistance(rc.getInitialArchonLocations(them)[0],
						CLOSETOENEMYTHRESHOLD)) {
					myNextTargetLocation = myNextTargetLocation.add(curXdir, gridSize * 2);
				}
				return myNextTargetLocation;
			} else { // Exploration done, change states
				strategy = 2;
				return new MapLocation(0, 0);
			}
		} else { // Start at upper Right

			// System.out.println("Find out next position for 2");
			Direction curXdir = EASTandWEST[(scoutID + oddRoute) % 2];
			while (isValidTargetLoc(myNextTargetLocation.add(curXdir, gridSize * 2))) {
				myNextTargetLocation = myNextTargetLocation.add(curXdir, gridSize * 2);
				// System.out.println("Test Next Target loction x: " +
				// myNextTargetLocation.x
				// + "y:" + myNextTargetLocation.y);
				if (!myNextTargetLocation.isWithinDistance(rc.getInitialArchonLocations(them)[0],
						CLOSETOENEMYTHRESHOLD)) {
					return myNextTargetLocation;
				}

			}
			if (isValidTargetLoc(myNextTargetLocation.add(Direction.SOUTH, gridSize * 2))) {
				myNextTargetLocation = myNextTargetLocation.add(Direction.SOUTH, gridSize * 2);
				oddRoute = (oddRoute + 1) % 2;
				if (myNextTargetLocation.isWithinDistance(rc.getInitialArchonLocations(them)[0],
						CLOSETOENEMYTHRESHOLD)) {
					myNextTargetLocation = myNextTargetLocation.add(curXdir, gridSize * 2);
				}
				return myNextTargetLocation;
			} else { // Exploration done, change states
				strategy = 2;
				return new MapLocation(0, 0);
			}
		}

	}

	static boolean isValidTargetLoc(MapLocation newloc) {

		return (newloc.x <= Messaging.upperLimitX - gridSize) && (newloc.x >= Messaging.lowerLimitX + gridSize)
				&& (newloc.y <= Messaging.upperLimitY - gridSize) && (newloc.y >= Messaging.lowerLimitY + gridSize);
	}

	static float CLOSETOGARDNER = (float) 4;

	static void tryHarassGardener() throws GameActionException {
		// MOVING PART
		boolean treeDodged = tryTreeDodge();
		if (!tryKiteLumberJacks(5000)) {
			if (!treeDodged) { // No dodging necessary
				if (myOpponentID != 0 && rc.canSenseRobot(myOpponentID)) { // found
																			// opposing
																			// gardener
					if (myTreeID != 0 && rc.canSenseTree(myTreeID)) {
						tryMoveIntoTreeCloseToGardner(); // go up the tree
					} else {
						tryAssignTreeID(); // find another tree
					}

				} else { // No opponent ID being registered

					if (enemiesInSight.length == 0) {

					} else {
						for (RobotInfo robotSpotted : enemiesInSight) {
							if (robotSpotted.getType() == RobotType.GARDENER) {
								myOpponentID = robotSpotted.getID(); // select
																		// target
								tryMove(new Direction(my_loc, robotSpotted.getLocation()), 10, 5);
								// Get as close to enemy gardener as possible
								return;
							}
						}
					}
					tryFindEnemyFarmers();

				}
			}
		}
		// ATTACKING PART
		// System.out.println("my_loc);
		if (// !(isPathToRobotObstructed(rc.senseRobot(myOpponentID))) &&
		myOpponentID != 0 && rc.canSenseRobot(myOpponentID) && rc.canFireSingleShot()
				&& my_loc.distanceTo(rc.senseRobot(myOpponentID).getLocation()) <= CLOSETOGARDNER * type.bodyRadius) {
			// Fire shot towards the gardener, if path clear and shots ready and
			// close enough
			// System.out.println("Shot fired towards " + myOpponentID + " i am
			// at tree" + myTreeID);
			Direction dir = new Direction(my_loc, rc.senseRobot(myOpponentID).getLocation());
			rc.fireSingleShot(dir);
		}

		if (rc.getHealth() < 0.25 * type.maxHealth && strategy == 0 && idle < 10) { // Lost
																					// major
																					// part
																					// of
																					// life
			strategy = 1; // change strategy, go explore map
		}

	}

	static void tryFindEnemyFarmers() throws GameActionException {
		if (rc.readBroadcast(SCOUTFOUNDFARMERXCHANNEL) == 0) {
			tryMoveTowardEnemyArchons(12000); // No enemy spotted, go ahead find
												// someone
		} else {
			tryMove(my_loc.directionTo(new MapLocation((float) rc.readBroadcast(SCOUTFOUNDFARMERXCHANNEL),
					(float) rc.readBroadcast(SCOUTFOUNDFARMERXCHANNEL))), 30, 5); // Move
																					// towards
																					// broadcasted
		}

	}

	static float MINIMUMMOV = GameConstants.GENERAL_SPAWN_OFFSET;
	static float DODGEDISTANCE = 2;
	static float AVOIDSOLDIERDISTANCE = 4f;

	static boolean tryTreeDodge() throws GameActionException {
		// dodging by minimum movement or keep current location
		// return true if the scout had to move or the current position is good
		// for shooting
		// return false if there is no threat at all
		// First avoid come too close to solidier

		// if (avoidSoldier() ){
		// return true;
		// }
		// TODO I don't think it works so well yet
		if (bullets.length == 0) {
			// System.out.println("No need to dodge");
			return false;
		}
		for (BulletInfo bullet : bullets) { // find the shots that actually
											// would hit
			Direction propagationDirection = bullet.dir;
			MapLocation bulletLocation = bullet.location;
			Direction directionToRobot = bulletLocation.directionTo(my_loc);
			float theta = propagationDirection.radiansBetween(directionToRobot);
			if (Math.abs(theta) < Math.PI / 3) { // Get the closest bullet that
													// would hit
				MapLocation dodgingSpot;
				if (myTreeID != 0 || rc.canSenseTree(myTreeID)) { // if
																	// currently
																	// spotted a
																	// tree
					// System.out.println("myTree ID tree:" + myTreeID);
					dodgingSpot = rc.senseTree(myTreeID).getLocation().add(directionToRobot, MINIMUMMOV);
					// System.out.println("Dodge inside tree");
				} else {
					dodgingSpot = my_loc.add(propagationDirection.rotateLeftDegrees(90), 2);
					// System.out.println("Dodge outside tree");
				}
				if (rc.canMove(dodgingSpot)) {
					rc.move(dodgingSpot);
					// System.out.println("Dodge successful to wished
					// location");
					return true;
				} else {
					tryMove(propagationDirection.rotateRightDegrees(90), 20, 3);
					// System.out.println("Dodge successful to wished
					// direction");
					return true;
				}

			}
		}
		return false;
	}

	static boolean avoidSoldier() throws GameActionException {
		RobotInfo[] awareSolider = rc.senseNearbyRobots(AVOIDSOLDIERDISTANCE, them);
		for (RobotInfo enemy : awareSolider) {
			if (enemy.getType() == RobotType.SOLDIER || enemy.getType() == RobotType.TANK) {
				tryMove(enemy.getLocation().directionTo(my_loc).rotateLeftDegrees(90), 45, 5);
				// System.out.println("Avoiding enemy soliders");
				return true;
			}
		}
		return false;
	}

	static void tryAssignTreeID() throws GameActionException {
		if (trees.length > 0) {
			for (TreeInfo tree : trees) {
				if (tree.team == them) {
					float dis = tree.getLocation().distanceTo(rc.senseRobot(myOpponentID).getLocation());
					if (dis < CLOSETOGARDNER * type.bodyRadius) { // enemy tree
																	// close to
																	// opponent
																	// gardener
						myTreeID = tree.getID();
						return;
					}
				}

			}
		}

	}

	static void tryMoveIntoTreeCloseToGardner() throws GameActionException {
		// Move to that tree
		if (myTreeID != 0 && rc.canSenseTree(myTreeID) && rc.canSenseLocation((rc.senseTree(myTreeID).getLocation())) && // TODO
																															// weired
																															// exception
				(!rc.isLocationOccupiedByRobot(rc.senseTree(myTreeID).getLocation())
						|| rc.getLocation() != rc.senseTree(myTreeID).getLocation())) {
			if (rc.canMove(rc.senseTree(myTreeID).getLocation())) {
				rc.move(rc.senseTree(myTreeID).getLocation());
			}
		}

	}

	static boolean tryDodge() throws GameActionException {
		// jd: actually take bullets into account, for now just dodge the
		// closest one

		if (bullets.length > 0) {
			Direction directionToRobot = null;
			Direction propagationDirection = null;
			float closest = 6;
			BulletInfo bulletincoming = bullets[0];
			for (BulletInfo bullet : bullets) {
				propagationDirection = bullet.dir;
				MapLocation bulletLocation = bullet.location;
				directionToRobot = bulletLocation.directionTo(my_loc);
				float distToRobot = bulletLocation.distanceTo(my_loc);
				float theta = propagationDirection.radiansBetween(directionToRobot);
				if (distToRobot < closest && Math.abs(theta) < Math.PI / 3) { // Get
																				// the
																				// closest
																				// bullet
					closest = distToRobot;
					bulletincoming = bullet;
				}
			}
			if (!tryMove(propagationDirection.rotateLeftDegrees(90), 20, 10)) { // Dodge
																				// right
																				// and
																				// left
				tryMove(propagationDirection.rotateRightDegrees(90), 20, 10);
			}

			return true;
		}
		return false;

	}

}
