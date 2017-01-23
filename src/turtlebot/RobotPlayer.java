package turtlebot;

import battlecode.common.*;

import java.util.Random;

public strictfp class RobotPlayer {

    static RobotController rc;
    static RobotType type;

    static Team us;
    static Team them;

    static Random gen;

    static RobotInfo[] enemiesInSight = null;
    static RobotInfo[] alliesInSignt = null;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) {
        init(rc);
        // loop around try/catch so that we can restart if something breaks.
        // actual bots should run their own game loop for each round.
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
        RobotPlayer.type = rc.getType();

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
        switch (type) {
            case ARCHON:
                Archon.run();
                break;
            case GARDENER:
                Gardener.run();
                break;
            case LUMBERJACK:
                Lumberjack.run();
                break;
            case TANK:
                Tank.run();
                break;
            case SCOUT:
                Scout.run();
                break;
            case SOLDIER:
                Soldier.run();
                break;
            default:
                break;
        }
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

    static Direction randomDirection() {
        return new Direction((float) gen.nextDouble() * 2 * (float) Math.PI);
    }

    static void tryShakeNearby() throws GameActionException {
        if (!rc.canShake()) {
            // not sure why this would ever happen
            return;
        }

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

    static boolean isPathToRobotObstructed(RobotInfo other) throws GameActionException {
        return isPathToRobotObstructed(other.location, other.type.bodyRadius);
    }

    static boolean isPathToRobotObstructed(TreeInfo other) throws GameActionException {
        return isPathToRobotObstructed(other.location, other.radius);
    }

    static boolean isPathToRobotObstructed(MapLocation theirLoc, float theirRadius) throws GameActionException {
        // naive, just sample some points
        // TODO: be smarter
        MapLocation loc = rc.getLocation();
        Direction dir = loc.directionTo(theirLoc);
        float dist = loc.distanceTo(theirLoc) - type.bodyRadius - theirRadius;
        int numSamples = Math.max((int) (3 * dist), 2);
        for (int i = 0; i < numSamples; i++) {
            float curDist = type.bodyRadius + dist * i / (numSamples - 1);
            if (i == 0) {
                curDist = Math.nextUp(curDist);
            } else if (i == numSamples - 1) {
                curDist = Math.nextDown(curDist);
            }
            MapLocation sampleLoc = loc.add(dir, curDist);

            if (rc.canSenseLocation(sampleLoc) && rc.isLocationOccupied(sampleLoc)) {
                return true;
            }
        }
        return false;
    }

    static void updateNearby() {
        enemiesInSight = rc.senseNearbyRobots(type.sensorRadius, them);
        alliesInSignt = rc.senseNearbyRobots(type.sensorRadius, us);
    }

    static boolean tryDodgeBullets() {
        // TODO
        return false;
    }


    static float getExchangeRate() {
        return (float)7.5 + (float)0.004166667 * rc.getRoundNum();
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
        } else if (bullets >= 100f + rc.getVictoryPointCost()) {
            // maintain at least 100
            float excess = bullets - 100f;
            float roundedBullets = ((int) (excess / rc.getVictoryPointCost())) * rc.getVictoryPointCost();
            rc.donate(roundedBullets);
        } else if (bullets > rc.getVictoryPointCost()
                && Gardener.moreEfficientToNotBuildTree()) {
            // if we're so close that we don't need new trees, stop saving any money
            float roundedBullets = ((int) (bullets / rc.getVictoryPointCost())) * rc.getVictoryPointCost();
            rc.donate(roundedBullets);
        }
    }

}
