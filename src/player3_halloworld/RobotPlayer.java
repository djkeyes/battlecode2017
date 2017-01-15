package player3_halloworld;


import battlecode.common.*;

import java.util.Random;

public strictfp class RobotPlayer {
    static RobotController rc;
    static RobotType type;

    static Team us;
    static Team them;

    static Random gen;

    static int TREEOPPONENTIDCHANNEL = 0;
    static int TREEOPPONENTCOUNTCHANNEL = 1;
    static int OPPONEARCHONXCHANNEL = 3;
    static int OPPONEARCHONYCHANNEL = 4;
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

    static void init(RobotController rc) { // Initialization
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
        return new Direction((float) gen.nextFloat() * 2 * (float) StrictMath.PI);
    }

    static void tryShakeNearby() throws GameActionException {
        if (!rc.canShake()) {
            // not sure why this would ever happen
            return;
        }

        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(type.bodyRadius + type.strideRadius, Team.NEUTRAL);
        int maxBullets = 0;
        TreeInfo bestTree = null;
        for (TreeInfo tree : nearbyTrees){
            int curBullets = tree.getContainedBullets();
            if(curBullets > maxBullets){
                bestTree = tree;
                maxBullets= curBullets;
            }
        }
        if (bestTree != null) {
            // pick randomly
            rc.shake(bestTree.getID());
        }
    }

    static void tryTrivialWin() throws GameActionException {
        float bullets = rc.getTeamBullets();
        //int leftToWin = GameConstants.VICTORY_POINTS_TO_WIN - rc.getTeamVictoryPoints();
        // FIXME this condition isn't currently checked by the server, don't bother
        //if(bullets > leftToWin*GameConstants.BULLET_EXCHANGE_RATE){
        //    int bulletsToTrade = leftToWin*GameConstants.BULLET_EXCHANGE_RATE;
        //    rc.donate(bulletsToTrade);
        //} else
        if (bullets>10000.0) {
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
        	rc.broadcast(3, (int)bestTarget.location.x);
        	rc.broadcast(4, (int)bestTarget.location.y); // Broadcast this enemy location
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
    static boolean tryMoveTowardEnemy() throws GameActionException {
        // since we're melee, just move toward an enemy
        // preferably one that can't outrun us

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, them);

        if (enemies.length == 0) {
            return false;
        }

        RobotInfo closestSlowEnemy = null;
        RobotInfo closestFastEnemy = null;
        float slowEnemyDist = Float.MAX_VALUE;
        float fastEnemyDist = Float.MAX_VALUE;
        for (RobotInfo enemy : enemies) {
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

        Direction dir = rc.getLocation().directionTo(target.getLocation());

        return tryMove(dir, 45, 10);
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

    static Direction dirToArchons = null;
    static boolean tryMoveTowardEnemyArchons() throws GameActionException {
        MapLocation[] archonLocs = rc.getInitialArchonLocations(them);
        // target each one in order
        
        int readx = rc.readBroadcast(OPPONEARCHONXCHANNEL);
        if (readx ==0){
        	int idx = (rc.getRoundNum()/300)%archonLocs.length;
        	dirToArchons = rc.getLocation().directionTo(archonLocs[idx]);
        	return tryMove(dirToArchons, 45, 20);
        }
        else {
        	dirToArchons = rc.getLocation().directionTo(new MapLocation((float) (readx),(float)(rc.readBroadcast(OPPONEARCHONYCHANNEL))));
        	return tryMove(dirToArchons, 45, 20);
        }
    }

}