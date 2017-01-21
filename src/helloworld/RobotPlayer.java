package helloworld;

import battlecode.common.*;

import java.util.Random;

public strictfp class RobotPlayer {

    static RobotController rc;
    static RobotType type;

    static Team us;
    static Team them;

    static Random gen;
    static Direction dirToArchons = null;

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

    static void debug_printStackTrace(Exception ex){
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
        if(us == Team.A){
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

    static Direction randomDirection() {
        return new Direction((float) gen.nextDouble() * 2 * (float) StrictMath.PI);
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

    static float getExchangeRate() throws GameActionException {
        return (float)7.5 + (float)0.004166667 * rc.getRoundNum();
    }

    static void tryTrivialWin() throws GameActionException {
        float bullets = rc.getTeamBullets();
        int leftToWin = GameConstants.VICTORY_POINTS_TO_WIN - rc.getTeamVictoryPoints();
        if (bullets > (int)(leftToWin * getExchangeRate())) {
            int bulletsToTrade = (int)(leftToWin * getExchangeRate());
            rc.donate(bulletsToTrade);
        } else if (rc.getRoundNum() > rc.getRoundLimit() - 3) {
            int roundedBullets = (int)((int) (bullets / getExchangeRate()) * getExchangeRate());
            rc.donate(roundedBullets);
        }
    }

    static boolean tryAttackEnemy() throws GameActionException {
        RobotInfo[] robots = rc.senseNearbyRobots(-1, them);

        if (!rc.canFireSingleShot()) {
            return false;
        }

        if (robots.length > 0) {

            // pick an enemy somehow
            double maxEnemyScore = Double.NEGATIVE_INFINITY;
            RobotInfo bestTarget = null;
            for (RobotInfo enemy : robots) {
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
                rc.fireSingleShot(rc.getLocation().directionTo(bestTarget.location));
                return true;
            }
        }

        // no enemies nearby--try killing trees
        TreeInfo[] trees = rc.senseNearbyTrees(-1, them);

        if (trees.length == 0) {
            return false;
        }

        // just pick the weakest tree
        double minHealth = Double.POSITIVE_INFINITY;
        TreeInfo bestTree = null;
        for (TreeInfo enemy : trees) {
            // we should also check if there is an unobstructed path to the enemy from here
            // unfortunately, that's complicated. maybe collect all nearby robots and sort by angle? that way we can
            // binary search these kinds of queries.
            if (isPathToRobotObstructed(enemy)) {
                continue;
            }
            double health = enemy.health;
            if (health < minHealth) {
                minHealth = health;
                bestTree = enemy;
            }
        }

        if (bestTree != null) {
            rc.fireSingleShot(rc.getLocation().directionTo(bestTree.location));
            return true;
        }
        return false;
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

    static double evaluateEnemy(RobotInfo enemy) {
        // things to consider:
        // -health
        // -value (dps, dps*hp, isArchon) of enemy robot
        // -distance / dodgeabililty
        // -clusters of enemies

        // for now, let's use -health*dist
        // health for the obvious reason of attacking the weakest
        // dist to estimate dodgeability, because the bullet density of a circle of bullets decreases linearly with the
        // radius
        // (minus sign, just to make it max instead of min)
        return -enemy.getHealth() * rc.getLocation().distanceTo(enemy.getLocation());
    }

    static boolean tryMoveTowardEnemyArchons() throws GameActionException {
        MapLocation[] archonLocs = rc.getInitialArchonLocations(them);
        // target each one in order
        int idx = (rc.getRoundNum() / 300) % archonLocs.length;
        dirToArchons = rc.getLocation().directionTo(archonLocs[idx]);
        if(dirToArchons == null){
            return false;
        }
        return tryMove(dirToArchons, 45, 20);
    }

}
