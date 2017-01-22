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
        BulletInfo[] relevantBullets = new BulletInfo[possiblyRelevantBullets.length];
        int numRelevantBullets = 0;
        for (int i = 0; i < possiblyRelevantBullets.length; i++) {
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
            thetaOffset = -StrictMath.PI / 2.;
            maxTheta = StrictMath.PI;
        } else {
            // if we're not trying to go somewhere, first see if not moving helps
            minDamage = countDamageFromBullets(myLoc, body, relevantBullets, numRelevantBullets);
            bestLocation = myLoc;
        }
        double theta;
        float r = type.strideRadius;
        while (minDamage > 0 && Clock.getBytecodeNum() < maxBytecodes) {
            // uniformly sample edges of stride space
            // we could also sample the whole circle, but counting bullets is so costly that we usually only get 10
            // or so iterations in.
            theta = thetaOffset + maxTheta * gen.nextDouble();
            MapLocation next = myLoc.add((float) theta, r);
            if (rc.canMove(next)) {
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
        if (rc.canMove(targetDir)) {
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
                if (rc.canMove(next)) {
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
                if (rc.canMove(next)) {
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

    static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {
        // daniel: wow, this sucks. rewrite it.

        // First, try intended direction
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        // Now try a bunch of similar angles
        boolean moved = false;
        int currentCheck = 1;

        while (currentCheck <= checksPerSide) {
            // Try the offset of the left side
            if (rc.canMove(dir.rotateLeftDegrees(degreeOffset * currentCheck))) {
                rc.move(dir.rotateLeftDegrees(degreeOffset * currentCheck));
                return true;
            }
            // Try the offset on the right side
            if (rc.canMove(dir.rotateRightDegrees(degreeOffset * currentCheck))) {
                rc.move(dir.rotateRightDegrees(degreeOffset * currentCheck));
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

    static boolean tryMove(Direction dir, float dist, float degreeOffset, int checksPerSide) throws
            GameActionException {
        // daniel: wow, this sucks. rewrite it.

        // First, try intended direction
        if (rc.canMove(dir, dist)) {
            rc.move(dir, dist);
            return true;
        }

        // Now try a bunch of similar angles
        boolean moved = false;
        int currentCheck = 1;

        while (currentCheck <= checksPerSide) {
            // Try the offset of the left side
            if (rc.canMove(dir.rotateLeftDegrees(degreeOffset * currentCheck), dist)) {
                rc.move(dir.rotateLeftDegrees(degreeOffset * currentCheck), dist);
                return true;
            }
            // Try the offset on the right side
            if (rc.canMove(dir.rotateRightDegrees(degreeOffset * currentCheck), dist)) {
                rc.move(dir.rotateRightDegrees(degreeOffset * currentCheck), dist);
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

    static boolean needToMoveAwayFromPack() {
        // check if any gardeners are less than 2*bodyRadius + 4*bulletTreeRadius away
        // in fact, we actually need more, but ignore that for now
        for (RobotInfo ally : alliesInSignt) {
            if (ally.type == RobotType.GARDENER || ally.type == RobotType.ARCHON) {
                return true;
            }

            // because these are in sorted order, we can terminate early
            if (ally.location.distanceTo(rc.getLocation())
                    > 2 * RobotType.GARDENER.bodyRadius + 4 * GameConstants.BULLET_TREE_RADIUS + 0.5f) {
                break;
            }
        }

        // TODO(daniel): check for map edge
        // I'm not sure if moving away from the edge will actually help though, since that indicates that things are
        // pretty crowded already.
        return false;
    }

    static void moveAwayFromPack() throws GameActionException {
        // apply a "force"
        float K = 50.0f;
        float fx = 0f;
        float fy = 0f;
        for (RobotInfo ally : alliesInSignt) {
            if (ally.type == RobotType.GARDENER || ally.type == RobotType.ARCHON) {
                float distSq = ally.location.distanceSquaredTo(rc.getLocation());
                Direction dir = ally.location.directionTo(rc.getLocation());
                fx += dir.getDeltaX(K) / distSq;
                fy += dir.getDeltaY(K) / distSq;
            }
        }

        float mag = (float) StrictMath.sqrt(fx * fx + fy * fy);
        Direction dir = new Direction(fx, fy);

        if (mag > type.strideRadius) {
            mag = type.strideRadius;
        }

        tryMove(dir, mag, 10, 10);
    }

    static boolean isPathToRobotObstructed(RobotInfo other) throws GameActionException {
        // naive, just sample some points
        // TODO: be smarter
        MapLocation loc = rc.getLocation();
        MapLocation otherLoc = other.getLocation();
        Direction dir = loc.directionTo(otherLoc);
        float dist = loc.distanceTo(otherLoc) - type.bodyRadius - other.getType().bodyRadius;
        int numSamples = StrictMath.max((int) (3 * dist), 2);
        for (int i = 0; i < numSamples; i++) {
            float curDist = type.bodyRadius + dist * i / (numSamples - 1);
            if (i == 0) {
                curDist = StrictMath.nextUp(curDist);
            } else if (i == numSamples - 1) {
                curDist = StrictMath.nextDown(curDist);
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
        return tryMoveTo(archonLocs[idx], maxBytecodes);
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
        if(tryDodgeBulletsInDirection(maxBytecodes, rc.getLocation().directionTo(target.getLocation()))){
            return true;
        }
        return tryMoveTo(target.getLocation(), maxBytecodes);
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

    static boolean tryAttackEnemy() throws GameActionException {
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
					if (rc.onTheMap(rc.getLocation().add(dir, type.sensorRadius))== false){ // this direction is not inside map
						// run binary search to find out the range
						int lowerbound = 0;
						int upperbound = (int) type.sensorRadius;
						while(lowerbound != upperbound){
							int mid = (lowerbound+upperbound+1) /2;
							if (rc.onTheMap(rc.getLocation().add(dir,(float)mid)) == false){
								upperbound = mid-1;
							}
							else {
								lowerbound = mid;
							}
						}
						if (dir == Direction.EAST){
							Messaging.upperLimitX = lowerbound+(int)rc.getLocation().x;
							System.out.println("Find x upper bound "+ (lowerbound+(int)rc.getLocation().x));

						}
						else if (dir ==Direction.NORTH){
							Messaging.upperLimitY = lowerbound+(int)rc.getLocation().y;
							System.out.println("Find y upper bound "+(lowerbound+(int)rc.getLocation().y));

						}
						else if (dir ==Direction.WEST){
							Messaging.lowerLimitX = -lowerbound+(int)rc.getLocation().x;
							System.out.println("Find x lower bound "+(-lowerbound+(int)rc.getLocation().x));

						}
						else {
							Messaging.lowerLimitY = -lowerbound+(int)rc.getLocation().y;
							System.out.println("Find y lower bound "+(-lowerbound+(int)rc.getLocation().y));

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
