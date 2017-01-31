package combinedstrategies;

import battlecode.common.*;

import java.util.Random;

public strictfp class RobotPlayer {

    static RobotController rc;
    static RobotType type;
    static RobotHandler handler;

    static Team us;
    static Team them;

    static Random gen;

    static RobotInfo[] enemiesInSight = null;
    static RobotInfo[] alliesInSignt = null;

    static SingleArgBooleanMethod<Direction> canMoveDir = null;
    static SingleArgBooleanMethod<MapLocation> canMoveLoc = null;
    static TwoArgBooleanMethod<Direction, Float> canMoveDirDist = null;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) {
        init(rc);
        // loop around try/catch so that we can restart if something breaks.
        while (true) {
            try {
                run();
            } catch (Exception ex) {
                debug_printStackTrace(ex);
                // end the current turn before trying to restart
                Clock.yield();
            }
        }
    }

    static void debug_printStackTrace(Exception ex) {
        ex.printStackTrace();
    }

    static void init(RobotController rc) {
        RobotPlayer.rc = rc;
        type = rc.getType();

        handler = getRobotHandler();

        us = rc.getTeam();
        them = us.opponent();

        if(type == RobotType.TANK){
            canMoveDir = Tank::canMoveDir;
            canMoveLoc = Tank::canMoveLoc;
            canMoveDirDist = Tank::canMoveDirDist;
        } else {
            // it would be nice to reference canMove directly, but instead we have to incur the overhead of an extra
            // method call:
            // "Due to instrumentation limitations, you can't take a reference to battlecode.common.RobotController::canMove;
            // use () -> battlecode.common.RobotController.canMove() instead."
            canMoveDir = (Direction d) -> rc.canMove(d);
            canMoveLoc = (MapLocation m) -> rc.canMove(m);
            canMoveDirDist = (Direction d, Float f) -> rc.canMove(d, f);
        }

        // right now, robot IDs are fixed for each map, because each map has a fixed random seed.
        // to circumvent this, we also take a seed as a command line param.
        // fortunately, IDs are ints, so we can concatenate them into a long.
        String seedArg;
        if (us == Team.A) {
            seedArg = System.getProperty("bc.testing.team-a-seed");
        } else {
            seedArg = System.getProperty("bc.testing.team-b-seed");
        }
        long seed = rc.getID();
        if (seedArg != null) {
            long intSeedArg = Integer.parseInt(seedArg);
            seed |= (intSeedArg << 32);
        }
        gen = new Random(seed);
    }

    static void run() throws GameActionException {
        if (handler == null) {
            System.out.println("no handler assigned to this robot?");
            while (true) {
                Clock.yield();
            }
        }
        handler.init();
        while (true) {
            int startTurn = rc.getRoundNum();
            updateNearby();
            Messaging.tryGetMapSize();
            Messaging.tryGetUnitCounts();
            Messaging.readStrategy();
            determineStrategy();
            handler.onLoop();
            tryShakeNearby();
            donateExcessVictoryPoints();
            handler.reportUnitCount();
            reportMapAwareness();
            int endTurn = rc.getRoundNum();
            if (startTurn != endTurn) {
//                System.out.println("Over bytecode limit!");
            }
            Clock.yield();
        }
    }

    static RobotHandler getRobotHandler() {
        switch (type) {
            case ARCHON:
                return new Archon();
            case GARDENER:
                return new Gardener();
            case LUMBERJACK:
                return new Lumberjack();
            case TANK:
                return new Tank();
            case SCOUT:
                return new Scout();
            case SOLDIER:
                return new Soldier();
            default:
                System.out.println("unknown unit type?");
                break;
        }
        return null;
    }

    static void updateNearby() {
        enemiesInSight = rc.senseNearbyRobots(type.sensorRadius, them);
        alliesInSignt = rc.senseNearbyRobots(type.sensorRadius, us);
    }

    static boolean tryDodgeBullets(int maxBytecodes) throws GameActionException {
        return tryDodgeBulletsInDirection(maxBytecodes, null);
    }
    static boolean tryKiteLumberJacks(int maxBytecodes) throws GameActionException {
    	return tryKiteLumberJacks(maxBytecodes,null);
    }

    static final int MAX_LUMBERJACKS_TO_CONSIDER = 3;
    static final float LUMBERJACK_TO_CLOSE = RobotType.LUMBERJACK.strideRadius+
    			GameConstants.LUMBERJACK_STRIKE_RADIUS + RobotType.LUMBERJACK.bodyRadius+0.001f;
    private static RobotInfo[] relevantLJs = new RobotInfo[MAX_LUMBERJACKS_TO_CONSIDER];
    static boolean tryKiteLumberJacks(int maxBytecodes, Direction desiredMovementDir) throws
    	GameActionException{
    	// to avoid being attacked from the nearby enemy lumberjacks, move away from them when get too close
    	
    	int relevant_LJ_counter = 0;
    	float SaftyDistance = 0;
    	for (RobotInfo enemy : enemiesInSight){
    		if (enemy.getType() == RobotType.LUMBERJACK && enemy.getLocation().distanceTo(rc.getLocation())<=
    		LUMBERJACK_TO_CLOSE)
    		{
    			relevantLJs[relevant_LJ_counter] = enemy;
    			relevant_LJ_counter +=1;
    			SaftyDistance += (enemy.getLocation().distanceTo(rc.getLocation())-
    					LUMBERJACK_TO_CLOSE);
//    			System.out.println("Found Lumberjack with distance " + SaftyDistance);
    		}
    		else if (relevant_LJ_counter >= 2 || enemy.getLocation().distanceTo(rc.getLocation())>
    		LUMBERJACK_TO_CLOSE){
    			// Only consider if the enemy lj would be able to attack in the next round
    			break;
    		}
    	}
    	if (relevant_LJ_counter == 0) {
//    		System.out.println("No relevant enemy LJ");
    		return false;
    	}
    	
    	// Move part, basicly same idea as in bullet dodge, sample and find best spot on the stride edges
        MapLocation bestLocation = rc.getLocation().
        			add(relevantLJs[0].getLocation().directionTo(rc.getLocation()),type.strideRadius);
        if (!canMoveLoc.invoke(bestLocation)){
        	bestLocation = null;
        }
        // Opposite from the closest enemy always a good starting point

        double thetaOffset = 0;
        double maxTheta = 2.0 * StrictMath.PI;
        // if we're trying to move in a particular direction, only sample from that hemisphere
        if (desiredMovementDir != null) {
            thetaOffset = desiredMovementDir.radians - StrictMath.PI / 2.;
            maxTheta = StrictMath.PI;
        } 
        double theta;
        float maxSaftyDistance = SaftyDistance;
        // Now try all directions see if this direction escapes the unit away from LJ
        while (maxSaftyDistance < 0f && Clock.getBytecodeNum() < maxBytecodes) {
            // uniformly sample edges of stride space

            theta = thetaOffset + maxTheta * gen.nextDouble();
            MapLocation next = rc.getLocation().add((float) theta, type.strideRadius);
            if (canMoveLoc.invoke(next)) {
                float distance_from_safe = countDistanceFromLumberJacks(next, relevantLJs, relevant_LJ_counter);
                if (distance_from_safe > maxSaftyDistance) {
                    maxSaftyDistance = distance_from_safe;
                    bestLocation = next;
                }
            }
        }

        if(bestLocation == null){
//        	System.out.println("No escape found from LJ");
            return false;
        }
        rc.move(bestLocation); 
//        System.out.println("Moving to saftey from LJ");
        return true;
    }

    static float countDistanceFromLumberJacks(MapLocation next, RobotInfo[] relevantLJs,
			int relevant_LJ_counter) {
    	float saftydistance = 0;
		for (int i = 0; i < relevant_LJ_counter; i++){
			saftydistance += (relevantLJs[i].getLocation().distanceTo(rc.getLocation())-
					LUMBERJACK_TO_CLOSE);
		}
		return saftydistance;
	}

	static final int MAX_BULLETS_TO_CONSIDER = 25;
    private static BulletInfo[] relevantBullets = new BulletInfo[MAX_BULLETS_TO_CONSIDER];
    static boolean tryDodgeBulletsInDirection(int maxBytecodes, Direction desiredMovementDir) throws
            GameActionException {
        // in the worse case, we could move one stride. then a bullet at highest speed could strike our body.
        float epsilon = 0.001f;
        float maxBulletDist = type.strideRadius + type.bodyRadius + RobotType.TANK.bulletSpeed + epsilon;
        BulletInfo[] possiblyRelevantBullets = rc.senseNearbyBullets();
        if (possiblyRelevantBullets.length == 0) {
            return false;
        }
        MapLocation myLoc = rc.getLocation();

        // only pick the relevant ones--these actually intersect the circle of radius maxBulletDist
        int numRelevantBullets = 0;
        int max = Math.min(possiblyRelevantBullets.length, MAX_BULLETS_TO_CONSIDER);
        for (int i = 0; i < max; i++) {
            BulletInfo bullet = possiblyRelevantBullets[i];

            Direction dir = myLoc.directionTo(bullet.location);
            double dist = myLoc.distanceTo(bullet.location);

            double a = bullet.speed * bullet.speed;
            double b = 2. * StrictMath.cos(bullet.dir.radiansBetween(dir)) * dist * bullet.speed;
            double c = dist * dist - maxBulletDist * maxBulletDist;

            double discr = b * b - 4. * a * c;
            if (discr <= 0) {
                // no intersection (ignoring grazing)
                continue;
            }
            discr = StrictMath.sqrt(discr);

            double t1 = (-b - discr) / (2. * a);
            double t2 = (-b + discr) / (2. * a);
            if ((t1 <= 0. && 0. <= t2)
                    || (t1 <= 1. && 1. <= t2)) {
                // actually has an intersection
                relevantBullets[numRelevantBullets++] = bullet;
            }
        }

        if (numRelevantBullets == 0) {
            return false;
        }

        // now attempt to move

        // try several possible locations, and store the one incurs the least damage.

        double body = type.bodyRadius + epsilon;
        float minDamage = Float.MAX_VALUE;
        MapLocation bestLocation = null;

        double thetaOffset = 0;
        double maxTheta = 2.0 * StrictMath.PI;
        // if we're trying to move in a particular direction, only sample from that hemisphere
        if (desiredMovementDir != null) {
            thetaOffset = desiredMovementDir.radians - StrictMath.PI / 2.;
            maxTheta = StrictMath.PI;
        } else {
            // if we're not trying to go somewhere, first see if not moving helps
            minDamage = countDamageFromBullets(myLoc, body, relevantBullets, numRelevantBullets);
            bestLocation = myLoc;
        }
        double theta;
        float r = type.strideRadius;
        while (minDamage > 0f && Clock.getBytecodeNum() < maxBytecodes) {
            // uniformly sample edges of stride space
            // we could also sample the whole circle, but counting bullets is so costly that we usually only get 10
            // or so iterations in.
            theta = thetaOffset + maxTheta * gen.nextDouble();
            MapLocation next = myLoc.add((float) theta, r);
            if (canMoveLoc.invoke(next)) {
                float damage = countDamageFromBullets(next, body, relevantBullets, numRelevantBullets);
                if (damage < minDamage) {
                    minDamage = damage;
                    bestLocation = next;
                }
            }
        }

        if(bestLocation == null){
            return false;
        }

        rc.move(bestLocation);
        return true;
    }

    static float countDamageFromBullets(MapLocation loc, double bodyRadius, BulletInfo[] bullets, int
            numRelevantBullets) {
        float totalDamage = 0f;
        for (int i = 0; i < numRelevantBullets; i++) {
            BulletInfo bullet = bullets[i];

            Direction dir = loc.directionTo(bullet.location);
            double dist = loc.distanceTo(bullet.location);

            double a = bullet.speed * bullet.speed;
            double b = 2. * StrictMath.cos(bullet.dir.radiansBetween(dir)) * dist * bullet.speed;
            double c = dist * dist - bodyRadius * bodyRadius;

            double discr = b * b - 4. * a * c;
            if (discr <= 0) {
                // no intersection (ignoring grazing)
                continue;
            }
            discr = StrictMath.sqrt(discr);

            double t1 = (-b - discr) / (2. * a);
            double t2 = (-b + discr) / (2. * a);
            if ((t1 <= 0. && 0. <= t2)
                    || (t1 <= 1. && 1. <= t2)) {
                totalDamage += bullet.damage;
            }
        }
        return totalDamage;
    }

    static void donateExcessVictoryPoints() throws GameActionException {
        if (rc.getRoundNum() < 20) {
            return;
        }

        float bullets = rc.getTeamBullets();
        int leftToWin = GameConstants.VICTORY_POINTS_TO_WIN - rc.getTeamVictoryPoints();
        if ((bullets / rc.getVictoryPointCost()) > leftToWin) {
            rc.donate(bullets);
        } else if (rc.getRoundNum() > rc.getRoundLimit() - 2) {
            float roundedBullets = ((int) (bullets / rc.getVictoryPointCost())) * rc.getVictoryPointCost();
            rc.donate(roundedBullets);
        } else if (Messaging.currentStrategy == Messaging.VP_WIN_STRATEGY
                && bullets >= 100f + rc.getVictoryPointCost()) {
            // maintain at least 100
            float excess = bullets - 100f;
            float roundedBullets = ((int) (excess / rc.getVictoryPointCost())) * rc.getVictoryPointCost();
            rc.donate(roundedBullets);
        } else if (Messaging.currentStrategy == Messaging.VP_WIN_STRATEGY
                && bullets > rc.getVictoryPointCost()
                && Gardener.moreEfficientToNotBuildTree()) {
            // if we're so close that we don't need new trees, stop saving any money
            float roundedBullets = ((int) (bullets / rc.getVictoryPointCost())) * rc.getVictoryPointCost();
            rc.donate(roundedBullets);
        } else if(bullets > 1000){
            float excess = bullets - 1000f;
            float roundedBullets = ((int) (excess / rc.getVictoryPointCost())) * rc.getVictoryPointCost();
            rc.donate(roundedBullets);
        }
    }

    static void tryShakeNearby() throws GameActionException {
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(type.bodyRadius + GameConstants.INTERACTION_DIST_FROM_EDGE, Team.NEUTRAL);
        int maxBullets = 0;
        TreeInfo bestTree = null;
        for (TreeInfo tree : nearbyTrees) {
            int curBullets = tree.getContainedBullets();
            if (curBullets > maxBullets) {
                bestTree = tree;
                maxBullets = curBullets;
            }
        }
        if (bestTree != null) {
            rc.shake(bestTree.getID());
        }
    }

    static float estimateTurnsToWin() {
        float defaultIncome = StrictMath.max(0f, GameConstants.ARCHON_BULLET_INCOME
                - GameConstants.BULLET_INCOME_UNIT_PENALTY * StrictMath.max(100f, rc.getTeamBullets()));
        float bulletsPerTurn = Messaging.totalTreeIncome + defaultIncome;
        if (bulletsPerTurn < 0.0001f) {
            return Float.MAX_VALUE;
        }
        // assuming a constant income, we could compute the exact amount needed (since the exchange rate will go up in
        // future turns), but a linear estimate using the current rate is a pretty good approximation.
        float vpLeft = GameConstants.VICTORY_POINTS_TO_WIN - rc.getTeamVictoryPoints();
        return (vpLeft * rc.getVictoryPointCost() - rc.getTeamBullets()) / bulletsPerTurn;
    }

    static int computeEarliestRush() {
        // compare our position to all enemy archons.
        // it takes 1 turn to build a gardener, then one turn to build a scout, then 20 turns for the scout to finish
        // we can ignore the 20 though, because it also takes 20 minutes for us to build a unit
        int scoutCompletionTurn = 1 + 1;
        float minDist = Float.POSITIVE_INFINITY;
        MapLocation[] archonLocs = rc.getInitialArchonLocations(them);
        for (MapLocation loc : archonLocs) {
            float dist = rc.getLocation().distanceTo(loc);
            if (dist < minDist) {
                minDist = dist;
            }
        }

        // let's be pessimistic: the archon could build the gardener toward us, and the gardener could build the
        // scout toward us
        minDist -= RobotType.ARCHON.bodyRadius + 2 * RobotType.GARDENER.bodyRadius + RobotType.SCOUT.bodyRadius;
        // when do we want to have our defence?
        // when they see us? when we see them? when they're next to us?
        minDist -= RobotType.SCOUT.sensorRadius;

        int travelTurns = (int) StrictMath.ceil(minDist / RobotType.SCOUT.strideRadius);

        return scoutCompletionTurn + travelTurns;
    }


    static Direction randomDirection() {
        return new Direction((float) gen.nextDouble() * 2 * (float) Math.PI);
    }


    private static final float FRUSTRATION_DISTANCE_THRESHOLD = 0.1f;

    /**
     * Movement via randomized simulation. Since we're constrained by bytecodes, this performs several trials and
     * pick the best one. This is a greedy strategy though, so it can get stuck in local minima. A better method
     * might be to pair this with some kind of bug-navigation, so we can path around holes in the wall.
     * <p>
     * Tanks should *not* use this, since it doesn't check friendly tree collisions.
     *
     * @param target       The target location
     * @param maxBytecodes The amount of bytecodes at which to terminate. This may use slightly more (TODO: estimate
     *                     bytecodes per iteration and terminate early if necessary).
     * @return
     * @throws GameActionException
     */
    static boolean tryMoveTo(MapLocation target, int maxBytecodes) throws GameActionException {
        // try several possible locations, and store the one that brings us closest.
        // first, try moving straight
        MapLocation start = rc.getLocation();
        Direction targetDir = start.directionTo(target);
        if (canMoveDir.invoke(targetDir)) {
            rc.move(start.directionTo(target));
            return true;
        }

        // TODO: what if the target within our stride radius? just ignore that case for now.

        MapLocation bestLocation = null;
        // in the worse case, we can just stay here
        float minDist = start.distanceTo(target);

        double stride = type.strideRadius;
        double d = minDist;
        double x = stride * stride / (2 * minDist);
        double y = StrictMath.sqrt(stride * stride - x * x);
        double maxTheta = StrictMath.atan2(y, x);
        double minR = 0;

        double theta;
        double r;
        while (Clock.getBytecodeNum() < maxBytecodes) {
            // I would like to uniformly sample from the stride space. However, as soon as we have one possible
            // position, we can eliminate all strides whose distance to the target is larger. In effect, we'd really
            // like to sample from the lens-shape formed by the intersection of two circles.
            // It's possible to compute that (uniformly sample from two circular caps), but it's difficult. instead
            // just do rejection sampling.

            theta = maxTheta * (2. * gen.nextDouble() - 1.);
            r = gen.nextDouble() * (1. - minR) + minR;
            MapLocation next = start.add((float) theta, (float) r * type.strideRadius);
            float dist = target.distanceTo(next);
            if (dist < minDist) {
                if (canMoveLoc.invoke(next)) {
                    minDist = dist;
                    bestLocation = next;

                    x = (stride * stride - dist * dist + d * d) / (2 * d);
                    y = StrictMath.sqrt(stride * stride - x * x);

                    maxTheta = StrictMath.atan2(y, x);
                    minR = d - dist;
                }
            }
        }

        if (d - minDist < FRUSTRATION_DISTANCE_THRESHOLD) {
            // just move randomly, even if it move us farther away
            // TODO: bug pathfinding
            for (int i = 0; i < 10; i++) {
                Direction dir = randomDirection();
                MapLocation next = start.add(dir);
                if (canMoveLoc.invoke(next)) {
                    bestLocation = next;
                    break;
                }

            }
        }

        if (bestLocation == null) {
            return false;
        }

        rc.move(bestLocation);
        return true;
    }

    static Direction previous_dir;
    static int previous_side = -2; // -2 for reset, -1 for left, 1 for right.

    static boolean tryMove(Direction dir, float degreeOffset,
                           int checksPerSide) throws GameActionException {
        // If we can go straight, go straight, but not always. The problem occurs if we are moving
        // alongside an obstacle that makes us move away from the goal, so we try to look ahead
        // before moving forward (that is why these isCircleOccupied checks).
        if (canMoveDir.invoke(dir)) {
            if (rc.getType() == RobotType.SCOUT || previous_side == -2 ||
            (!rc.isCircleOccupiedExceptByThisRobot(rc.getLocation().add(dir,
            0.5f * type.bodyRadius), type.bodyRadius) && (
                Math.abs(previous_dir.radiansBetween(dir)) < Math.PI * 0.4f ||
                !rc.isCircleOccupiedExceptByThisRobot(rc.getLocation().add(dir, 3.5f), 2.5f)))) {
                rc.move(dir);
                if (gen.nextDouble() < 0.3)
                    previous_side = -2;
                return true;
            }
        }

        // Otherwise if we don't have a side, choose a random side.
        if (previous_side == -2) {
            previous_side = (int)((float) gen.nextDouble() + 0.5) * 2 - 1;
            previous_dir = dir;
        }

        // Try to move towards the obstacle first.
        if (canMoveDir.invoke(previous_dir) &&
                !rc.isCircleOccupiedExceptByThisRobot(rc.getLocation().add(
                            previous_dir, type.strideRadius), type.bodyRadius)) {
            int currentCheck = 1;
            Direction new_dir;
            while (currentCheck++ <= checksPerSide) {
                if (previous_side == -1)
                    new_dir = previous_dir.rotateRightDegrees(degreeOffset);
                else if (previous_side == 1)
                    new_dir = previous_dir.rotateLeftDegrees(degreeOffset);
                else
                    break;
                if (!canMoveDir.invoke(new_dir)) {
                    rc.move(previous_dir);
                    return true;
                }
                previous_dir = new_dir;
            }
        } else {
            // Oterwise try to move away.
            int currentCheck = 1;
            Direction new_dir;
            while (currentCheck++ <= checksPerSide) {
                if (previous_side == -1)
                    new_dir = previous_dir.rotateLeftDegrees(degreeOffset);
                else if (previous_side == 1)
                    new_dir = previous_dir.rotateRightDegrees(degreeOffset);
                else
                    break;
                if (canMoveDir.invoke(new_dir)) {
                    rc.move(new_dir);
                    previous_dir = new_dir;
                    return true;
                }
                previous_dir = new_dir;
            }
        }

        // If everything else fails try a random direction.
        int numRetries = 20;
        for (int i = 0; i < numRetries; i++) {
            Direction random_dir = randomDirection();
            if (canMoveDir.invoke(random_dir)) {
                rc.move(random_dir);
                previous_dir = random_dir;
                previous_side = -2;
                return true;
            }
        }
        previous_dir = dir;
        previous_side = -2;
        // A move never happened, so return false.
        return false;
    }

    static boolean tryMove(Direction dir, float dist, float degreeOffset, int checksPerSide) throws
            GameActionException {
        // daniel: wow, this sucks. rewrite it.

        // First, try intended direction
        if (canMoveDirDist.invoke(dir, dist)) {
            rc.move(dir, dist);
            return true;
        }

        // Now try a bunch of similar angles
        boolean moved = false;
        int currentCheck = 1;

        while (currentCheck <= checksPerSide) {
            // Try the offset of the left side
            if (canMoveDirDist.invoke(dir.rotateLeftDegrees(degreeOffset * currentCheck), dist)) {
                rc.move(dir.rotateLeftDegrees(degreeOffset * currentCheck), dist);
                return true;
            }
            // Try the offset on the right side
            if (canMoveDirDist.invoke(dir.rotateRightDegrees(degreeOffset * currentCheck), dist)) {
                rc.move(dir.rotateRightDegrees(degreeOffset * currentCheck), dist);
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

    static final float thresholdDist = 2f * RobotType.GARDENER.bodyRadius
            + 2f * Gardener.computeTreePlantingDist(Gardener.NUM_TREES_PER_GARDENER)
            + 2f * GameConstants.BULLET_TREE_RADIUS;

    static final boolean SAVE_STUCK_PACK_POSITIONS = true;
    static final float THRESHOLD_TO_BE_STUCK = 0.1f;
    static final int STUCK_PACK_POSITIONS_CAPACITY = 5;
    static int stuckPackPositionHead = 0;
    static int stuckPackPositionSize = 0;
    static final MapLocation[] stuckPositions;

    static {
        if (SAVE_STUCK_PACK_POSITIONS) {
            stuckPositions = new MapLocation[STUCK_PACK_POSITIONS_CAPACITY];
        } else {
            stuckPositions = null;
        }
    }

    static boolean needToMoveAwayFromPack() throws GameActionException {
        // only check locally for archons
        for (RobotInfo ally : alliesInSignt) {
            // because these are in sorted order, we can terminate early
            if (ally.location.distanceTo(rc.getLocation())
                    > thresholdDist) {
                break;
            }

            if (ally.type == RobotType.ARCHON) {
                return true;
            }
        }

        // TODO(daniel): check for map edge
        // I'm not sure if moving away from the edge will actually help though, since that indicates that things are
        // pretty crowded already.
        return Messaging.anyGardenersInThreshold();
    }

    static void moveAwayFromPack() throws GameActionException {
        // apply a "force"
        // ask dk if you want to know where these constants came from
        float K = 50.0f;
        float Kstuck = 350.0f;

        Messaging.computeSquareDistanceToGardeners();
        float fx = K*Messaging.gardenerDistX;
        float fy = K*Messaging.gardenerDistY;

        // check locally for archons
        for (RobotInfo ally : alliesInSignt) {
            if (ally.type == RobotType.ARCHON) {
                float distSq = ally.location.distanceSquaredTo(rc.getLocation());
                Direction dir = ally.location.directionTo(rc.getLocation());
                fx += dir.getDeltaX(K) / distSq;
                fy += dir.getDeltaY(K) / distSq;
            }
        }

        if(SAVE_STUCK_PACK_POSITIONS) {
            for (int i = 1; i <= stuckPackPositionSize; ++i) {
                int idx = stuckPackPositionHead - i;
                if (idx < 0) {
                    idx += STUCK_PACK_POSITIONS_CAPACITY;
                }
                MapLocation stuckPos = stuckPositions[idx];

                // use dist^3, so that it falls off faster near the edges
                float dist = stuckPos.distanceTo(rc.getLocation());
                if (dist > type.sensorRadius * 1.5f) {
                    continue;
                }
                dist = dist * dist * dist;
                Direction dir;
                if (dist <= 0.0001f) {
                    dir = randomDirection();
                    dist = 0.0001f;
                } else {
                    dir = stuckPos.directionTo(rc.getLocation());
                }

                fx += dir.getDeltaX(Kstuck) / dist;
                fy += dir.getDeltaY(Kstuck) / dist;
            }
        }

        float mag = (float) StrictMath.sqrt(fx * fx + fy * fy);
        Direction dir = new Direction(fx, fy);

        if (mag > type.strideRadius) {
            mag = type.strideRadius;
        }
        boolean moved = tryMove(dir, mag, 10, 10);

        if(SAVE_STUCK_PACK_POSITIONS) {
            if (!moved || mag <= THRESHOLD_TO_BE_STUCK) {
                // add new stuckPosition
                stuckPositions[stuckPackPositionHead] = rc.getLocation();
                if (stuckPackPositionSize < STUCK_PACK_POSITIONS_CAPACITY) {
                    stuckPackPositionSize++;
                }
                // if we're already at capacity, just overwrite the old stuff.
                stuckPackPositionHead++;
                stuckPackPositionHead %= STUCK_PACK_POSITIONS_CAPACITY;
            }
        }
    }

    static boolean isPathToRobotObstructed(RobotInfo other) throws GameActionException {
        // naive, just sample some points
        // TODO: be smarter
        float EPSILON = 0.0001f;
        MapLocation loc = rc.getLocation();
        MapLocation otherLoc = other.getLocation();
        Direction dir = loc.directionTo(otherLoc);
        float dist = loc.distanceTo(otherLoc) - type.bodyRadius - other.getType().bodyRadius;
        int numSamples = StrictMath.max((int) (3 * dist), 2);
        for (int i = 0; i < numSamples; i++) {
            float curDist = type.bodyRadius + dist * i / (numSamples - 1);
            if (i == 0) {
                curDist += EPSILON;
            } else if (i == numSamples - 1) {
                curDist -= EPSILON;
            }
            MapLocation sampleLoc = loc.add(dir, curDist);

            if (rc.canSenseLocation(sampleLoc) && rc.isLocationOccupied(sampleLoc)) {
                return true;
            }
        }
        return false;
    }

    static boolean tryMoveTowardEnemyArchons(int maxBytecodes) throws GameActionException {
        MapLocation[] archonLocs = rc.getInitialArchonLocations(them);
        // target each one in order

        int idx = (rc.getRoundNum() / 200) % archonLocs.length;
        return tryMove(rc.getLocation().directionTo(archonLocs[idx]), 13.0f, 12);
    }


    private static MapLocation[] prevSensed = null;
    private static MapLocation prevClosestSensed = null;

    static boolean tryMoveTowardDistantEnemies(int maxBytecodes) throws GameActionException {
        // TODO: check locations broadcasted by other robots?
        // ideally, we would like to use both rc.senseBroadcastingRobotLocations() and our own broadcasts, but we
        // would have to dedup our own robots. maybe we should only report every N turns?

        if (!Messaging.shouldSendAdhocMessages()) {
            // only check on turns when there are few enemy heartbeats
            if (!Messaging.sentHeartbeatLastTurn()) {
                prevSensed = rc.senseBroadcastingRobotLocations();
            }
            // find closest (unless they're in vision, in which case they should be visible already
            MapLocation myLoc = rc.getLocation();
            float prevClosestDist;
            if (prevClosestSensed != null) {
                prevClosestDist = prevClosestSensed.distanceTo(myLoc);
                if (prevClosestDist < type.sensorRadius * 0.8f) {
                    prevClosestDist = Float.POSITIVE_INFINITY;
                    prevClosestSensed = null;
                }
            } else {
                prevClosestDist = Float.POSITIVE_INFINITY;
                prevClosestSensed = null;
            }
            if (prevSensed != null) {
                for (MapLocation loc : prevSensed) {
                    float dist = myLoc.distanceTo(loc);
                    if (dist < prevClosestDist && dist > type.sensorRadius) {
                        // gardeners don't pay attention to the ad-hoc state
                        if (!Messaging.isRecentStationaryGardenerPosition(loc)) {
                            prevClosestDist = dist;
                            prevClosestSensed = loc;
                        }
                    }
                }
            }

            if (prevClosestSensed != null) {
                if(tryDodgeBulletsInDirection(maxBytecodes, rc.getLocation().directionTo(prevClosestSensed))){
                    return true;
                } else {
                    return tryMove(rc.getLocation().directionTo(prevClosestSensed), 13.0f, 12);
                }
            }
        }

        // fallback: move toward initial archon positions
        MapLocation[] archonLocs = rc.getInitialArchonLocations(them);
        // target each one in order

        int idx = (rc.getRoundNum() / 200) % archonLocs.length;
        MapLocation target = archonLocs[idx];

        if (tryDodgeBulletsInDirection(maxBytecodes, rc.getLocation().directionTo(target))) {
            return true;
        } else {
            return tryMove(rc.getLocation().directionTo(target), 13.0f, 12);
        }
    }


    static boolean tryMoveTowardEnemy(int maxBytecodes) throws GameActionException {
        // since we're melee, just move toward an enemy
        // preferably one that can't outrun us

        if (enemiesInSight.length == 0) {
            return false;
        }

        RobotInfo closestSlowEnemy = null;
        RobotInfo closestFastEnemy = null;
        float slowEnemyDist = Float.MAX_VALUE;
        float fastEnemyDist = Float.MAX_VALUE;
        for (RobotInfo enemy : enemiesInSight) {
            float dist = rc.getLocation().distanceTo(enemy.getLocation());
            if (enemy.getType().strideRadius > type.strideRadius) {
                if (dist < fastEnemyDist) {
                    fastEnemyDist = dist;
                    closestFastEnemy = enemy;
                }
            } else {
                if (dist < slowEnemyDist) {
                    slowEnemyDist = dist;
                    closestSlowEnemy = enemy;
                }
            }
        }

        RobotInfo target = closestSlowEnemy;
        if (closestFastEnemy != null) {
            target = closestFastEnemy;
        }

        if (target == null) {
            return false;
        }

        // try dodging. if there are no bullets to dodge, just move normally
        if (tryDodgeBulletsInDirection(maxBytecodes, rc.getLocation().directionTo(target.getLocation()))) {
            return true;
        }
        // regardless of whether this successfully computes a move, return true since we're almost out of bytecodes
        tryMove(rc.getLocation().directionTo(target.getLocation()), 13.0f, 12);
        return true;
    }

    static boolean tryMoveWithinDistanceOfEnemy(int maxBytecodes, float desiredDist) throws GameActionException {
        // since we're melee, just move toward an enemy
        // preferably one that can't outrun us

        if (enemiesInSight.length == 0) {
            return false;
        }

        RobotInfo closestEnemy = null;
        float enemyDist = Float.MAX_VALUE;
        for (RobotInfo enemy : enemiesInSight) {
            float dist = rc.getLocation().distanceTo(enemy.getLocation());
            if (dist < enemyDist) {
                enemyDist = dist;
                closestEnemy = enemy;
            }
        }

        if (closestEnemy == null) {
            return false;
        }

        Direction dir;
        // either move to or away, to mantain distance
        if(enemyDist > desiredDist){
            dir = rc.getLocation().directionTo(closestEnemy.location);
        } else {
            dir = closestEnemy.location.directionTo(rc.getLocation());
        }

        // try dodging. if there are no bullets to dodge, just move normally
        if (tryDodgeBulletsInDirection(maxBytecodes, dir)) {
            return true;
        }
        // regardless of whether this successfully computes a move, return true since we're almost out of bytecodes
        tryMove(dir, 13.0f, 12);
        return true;
    }

    static void reportMapAwareness() throws GameActionException{
    	detectMapBoundary();
    	if (Messaging.lowerLimitX!=0){
    		Messaging.reportMapLowerX(Messaging.lowerLimitX);
    	}
    	if (Messaging.upperLimitX!=0){
    		Messaging.reportMapUpperX(Messaging.upperLimitX);
    	}
    	if (Messaging.lowerLimitY!=0){
    		Messaging.reportMapLowerY(Messaging.lowerLimitY);
    	}
    	if (Messaging.upperLimitY!=0){
    		Messaging.reportMapUpperY(Messaging.upperLimitY);
    	}
    }

    static boolean tryAttackEnemy(int maxBytecodes) throws GameActionException {
        if (enemiesInSight.length == 0) {
            return false;
        }

        if (!rc.canFireSingleShot()) {
            return false;
        }

        // pick an enemy somehow
        double maxEnemyScore = Double.NEGATIVE_INFINITY;
        RobotInfo bestTarget = null;
        for (RobotInfo enemy : enemiesInSight) {
            if (Clock.getBytecodeNum() > maxBytecodes) {
                break;
            }
            // we should also check if there is an unobstructed path to the enemy from here
            // unfortunately, that's complicated. maybe collect all nearby robots and sort by angle? that way we can
            // binary search these kinds of queries.
            if (isPathToRobotObstructed(enemy)) {
                continue;
            }
            double score = evaluateEnemy(enemy);
            if (score > maxEnemyScore) {
                maxEnemyScore = score;
                bestTarget = enemy;
            }
        }

        if (bestTarget != null) {
            if (type == RobotType.SOLDIER) {
                Direction dir = rc.getLocation().directionTo(bestTarget.location);
                double dist = rc.getLocation().distanceTo(bestTarget.location);
                // determine best kind of shot to fire
                if (enemiesInSight.length > 3 * alliesInSignt.length && rc.canFirePentadShot()) {
                    rc.firePentadShot(dir);
                } else {
                    double projectedAngle = StrictMath.asin(bestTarget.type.bodyRadius / dist);
                    projectedAngle = StrictMath.toDegrees(projectedAngle);
                    if (projectedAngle >= GameConstants.PENTAD_SPREAD_DEGREES * 2
                            && rc.canFirePentadShot()) {
                        rc.firePentadShot(dir);
                    } else if (projectedAngle >= GameConstants.TRIAD_SPREAD_DEGREES
                            && rc.canFireTriadShot()) {
                        rc.fireTriadShot(dir);
                    } else {
                        rc.fireSingleShot(dir);
                    }
                }
            } else {
                // tank
                Direction dir = rc.getLocation().directionTo(bestTarget.location);
                double dist = rc.getLocation().distanceTo(bestTarget.location);
                double effectiveDist = dist - type.bodyRadius;
                // determine best kind of shot to fire
                if (enemiesInSight.length > 3 * alliesInSignt.length && rc.canFirePentadShot()) {
                    rc.firePentadShot(dir);
                } else {
                    // do we care about friendly fire? I GUESS NOT
                    if (effectiveDist > 5f && rc.canFireTriadShot()) {
                        // tri, unlikely to hit
                        rc.fireTriadShot(dir);
                    } else if (rc.canFirePentadShot()) {
                        // penta
                        rc.firePentadShot(dir);
                    } else if (rc.canFireSingleShot()) {
                        // out of money? not sure
                        rc.fireSingleShot(dir);
                    }
                }
            }
            return true;
        }
        return false;
    }

    static double evaluateEnemy(RobotInfo enemy) {
        // things to consider:
        // -health
        // -value (dps, dps*hp, isArchon) of enemy robot
        // -distance / dodgeabililty
        // -clusters of enemies

        // for now, let's use -health*dist^2*stride*body
        // health for the obvious reason of attacking the weakest
        // dist^2 * stride * body to estimate dodgeability. dodgeability is super important.
        // (minus sign, just to make it max instead of min)
        float score = -enemy.getHealth() * rc.getLocation().distanceSquaredTo(enemy.getLocation())
                * enemy.type.strideRadius * enemy.type.bodyRadius;
        return score;
    }
    static final float SENSOR_EPSILON = 0.00001f;
    static void detectMapBoundary() throws GameActionException {
    	// If robot spots edges of the map, save it
		Direction[] fourDirections = {Direction.EAST,Direction.SOUTH,Direction.WEST,Direction.NORTH};

		// Detect whether the robot is on edge
		if (Messaging.upperLimitX == 0 || Messaging.upperLimitY == 0||
				Messaging.lowerLimitX== 0 || Messaging.lowerLimitY == 0){ // Need to Find out the map bound

			if (rc.onTheMap(rc.getLocation(), type.sensorRadius) == false){ // The edge is seen at the moment
				for (Direction dir:fourDirections){
					if (Messaging.upperLimitX != 0 && dir == Direction.EAST) { // Already done
						continue;
					}
					if (Messaging.upperLimitY != 0 && dir == Direction.NORTH) { // Already done
						continue;
					}
					if (Messaging.lowerLimitY != 0 && dir == Direction.SOUTH) { // Already done
						continue;
					}
					if (Messaging.lowerLimitX!= 0 && dir == Direction.WEST) { // Already done
						continue;
					}
					if (!rc.onTheMap(rc.getLocation().add(dir, type.sensorRadius - SENSOR_EPSILON))){
					    // this direction is not inside map
						// run binary search to find out the range
                        int lowerbound = 0;
                        int upperbound = (int) type.sensorRadius;
                        while (lowerbound != upperbound) {
                            int mid = (lowerbound + upperbound + 1) / 2;
                            if (rc.onTheMap(rc.getLocation().add(dir, ((float) mid) - SENSOR_EPSILON))) {
                                upperbound = mid - 1;
                            } else {
                                lowerbound = mid;
                            }
                        }
                        if (dir == Direction.EAST) {
                            Messaging.upperLimitX = lowerbound+(int)rc.getLocation().x;
//							System.out.println("Find x upper bound "+ (lowerbound+(int)rc.getLocation().x));

						}
						else if (dir ==Direction.NORTH){
							Messaging.upperLimitY = lowerbound+(int)rc.getLocation().y;
//							System.out.println("Find y upper bound "+(lowerbound+(int)rc.getLocation().y));

						}
						else if (dir ==Direction.WEST){
							Messaging.lowerLimitX = -lowerbound+(int)rc.getLocation().x;
//							System.out.println("Find x lower bound "+(-lowerbound+(int)rc.getLocation().x));

						}
						else {
							Messaging.lowerLimitY = -lowerbound+(int)rc.getLocation().y;
//							System.out.println("Find y lower bound "+(-lowerbound+(int)rc.getLocation().y));

						}

					}
				}
			}
		}
    }
    static void determineStrategy() throws GameActionException {
        // TODO: on very large maps (map size doesn't matter so much as inter-archon distance), turtlebot does very
        // well. So we should check that, and avoid rushing if that's the case.

        // on some maps, we make a yuuuuuge army, but don't really use it (due to bad pathing, cluttering, etc)
        // as a result, detect if our army is getting pretty big, and just go for VP win
        float totalArmyValue = Messaging.scoutCount * RobotType.SCOUT.bulletCost
                + Messaging.soldierCount * RobotType.SOLDIER.bulletCost
                + Messaging.tankCount * RobotType.TANK.bulletCost
                + Messaging.lumberjackCount * RobotType.LUMBERJACK.bulletCost;
        if (Messaging.currentStrategy == Messaging.MACRO_ARMY_STRATEGY
                && totalArmyValue >= GameConstants.VICTORY_POINTS_TO_WIN * rc.getVictoryPointCost()) {
            // change strategy
            Messaging.setStrategy(Messaging.VP_WIN_STRATEGY);
        }
    }
}
