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
                // are there working "debug_" methods this year?
                ex.printStackTrace();
                // end the current turn before trying to restart
                Clock.yield();
            }
        }
    }

    static void init(RobotController rc) {
        RobotPlayer.rc = rc;
        RobotPlayer.type = rc.getType();

        us = rc.getTeam();
        them = us.opponent();

        gen = new Random(rc.getID());
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
        return new Direction((float) Math.random() * 2 * (float) Math.PI);
    }

    static void tryShakeNearby() throws GameActionException {
        if (!rc.canShake()) {
            // not sure why this would ever happen
            return;
        }

        // FIXME currently, I believe the docs are wrong
        // they claim the tree must be within the stride radius, but I think it's actually the body radius which the
        // server uses as the threshold.
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(2.0f * type.bodyRadius, Team.NEUTRAL);
        if (nearbyTrees.length > 0) {
            // pick randomly
            TreeInfo tree = nearbyTrees[gen.nextInt(nearbyTrees.length)];
            rc.shake(tree.getID());
        }

    }

    static void tryTrivialWin() throws GameActionException {
        float bullets = rc.getTeamBullets();
        int leftToWin = GameConstants.VICTORY_POINTS_TO_WIN - rc.getTeamVictoryPoints();
        if (bullets > leftToWin * GameConstants.BULLET_EXCHANGE_RATE) {
            int bulletsToTrade = leftToWin * GameConstants.BULLET_EXCHANGE_RATE;
            rc.donate(bulletsToTrade);
        } else if (rc.getRoundNum() > rc.getRoundLimit() - 3) {
            int roundedBullets = (int) (bullets / GameConstants.BULLET_EXCHANGE_RATE) * GameConstants.BULLET_EXCHANGE_RATE;
            rc.donate(roundedBullets);
        }
    }

    static boolean tryAttackEnemy() throws GameActionException {
        RobotInfo[] robots = rc.senseNearbyRobots(-1, them);

        if (robots.length == 0) {
            return false;
        }

        if (!rc.canFireSingleShot()) {
            return false;
        }

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
        return false;
    }

    static boolean isPathToRobotObstructed(RobotInfo other) throws GameActionException {
        // naive, just sample some points
        // TODO: be smarter
        MapLocation loc = rc.getLocation();
        MapLocation otherLoc = other.getLocation();
        Direction dir = loc.directionTo(otherLoc);
        float dist = loc.distanceTo(otherLoc) - type.bodyRadius - other.getType().bodyRadius;
        int numSamples = Math.max((int) (3 * dist), 2);
        for (int i = 0; i < numSamples; i++) {
            float curDist = type.bodyRadius + dist * i / (numSamples - 1);
            if (i == 0) {
                curDist = Math.nextUp(curDist);
            } else if (i == numSamples - 1) {
                curDist = Math.nextDown(curDist);
            }
            MapLocation sampleLoc = loc.add(dir, curDist);

            if (rc.isLocationOccupied(sampleLoc)) {
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
        return tryMove(dirToArchons, 45, 20);
    }

}
