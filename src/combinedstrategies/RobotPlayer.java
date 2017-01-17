package combinedstrategies;

import battlecode.common.*;

import java.util.Random;

public strictfp class RobotPlayer {

    // TODO: which strategy is better: maintain < 200 (for bonus income) or exceed 200 (so we have more leeway
    // for constructing new units / shooting bullets)?
    static final boolean ATTEMPT_VP_WIN = false;

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
            Messaging.tryGetUnitCounts();
            handler.onLoop();
            tryShakeNearby();
            donateExcessVictoryPoints();
            handler.reportUnitCount();
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

    static boolean tryDodgeBullets() {
        // TODO
        return false;
    }

    static void donateExcessVictoryPoints() throws GameActionException {
        if (rc.getRoundNum() < 20) {
            return;
        }

        float bullets = rc.getTeamBullets();
        int leftToWin = GameConstants.VICTORY_POINTS_TO_WIN - rc.getTeamVictoryPoints();
        if (bullets > leftToWin * GameConstants.BULLET_EXCHANGE_RATE) {
            int bulletsToTrade = leftToWin * GameConstants.BULLET_EXCHANGE_RATE;
            rc.donate(bulletsToTrade);
        } else if (rc.getRoundNum() > rc.getRoundLimit() - 2) {
            int roundedBullets = (int) (bullets / GameConstants.BULLET_EXCHANGE_RATE) * GameConstants.BULLET_EXCHANGE_RATE;
            rc.donate(roundedBullets);
        } else if (ATTEMPT_VP_WIN && rc.getTeamBullets() >= 100f + GameConstants.BULLET_EXCHANGE_RATE) {
            // maintain at least 100
            float excess = rc.getTeamBullets() - 100f;
            int roundedBullets = (int) (excess / GameConstants.BULLET_EXCHANGE_RATE) * GameConstants.BULLET_EXCHANGE_RATE;
            rc.donate(roundedBullets);
        }
    }

    static void tryShakeNearby() throws GameActionException {
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(type.bodyRadius + type.strideRadius, Team.NEUTRAL);
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
        int vpLeft = GameConstants.VICTORY_POINTS_TO_WIN - rc.getTeamVictoryPoints();
        return (vpLeft * GameConstants.BULLET_EXCHANGE_RATE - rc.getTeamBullets()) / bulletsPerTurn;
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


    // TODO: completely rewrite these
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

            if (rc.isLocationOccupied(sampleLoc)) {
                return true;
            }
        }
        return false;
    }

    static boolean tryMoveTowardEnemyArchons() throws GameActionException {
        MapLocation[] archonLocs = rc.getInitialArchonLocations(them);
        // target each one in order

        int idx = (rc.getRoundNum() / 300) % archonLocs.length;
        Direction dirToArchons = rc.getLocation().directionTo(archonLocs[idx]);
        return tryMove(dirToArchons, 45, 20);
    }
}
