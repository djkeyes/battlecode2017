package player3_halloworld;


import battlecode.common.*;

strictfp class Lumberjack extends RobotPlayer {

    static void run() throws GameActionException {
        while (true) {

            boolean moved;
            boolean attacked = false;

            moved = tryMoveTowardEnemy();
            if (!moved) {
                moved = tryMoveTowardEnemyArchons();
            }
            if (!moved) {
                attacked = tryChop(dirToArchons);
            }

            if (!attacked) {
                tryMeleeAttackEnemy();
            }

            tryShakeNearby();

            tryTrivialWin();

            Clock.yield();
        }
    }

    static boolean tryDodge() throws GameActionException {
        // TODO: actually take bullets into account
        if (rc.senseNearbyRobots(7.0f, them).length > 0) {
            // Move randomly
            tryMove(randomDirection(), 20, 10);
            return true;
        }
        return false;
    }


    static boolean tryChop(Direction dir) throws GameActionException {
        // try chopping
        MapLocation loc = rc.getLocation();
        // First, try intended direction
        TreeInfo[] blockingTrees = rc.senseNearbyTrees(loc.add(dir, type.strideRadius), type.bodyRadius, them);
        if (blockingTrees.length == 0) {
            blockingTrees = rc.senseNearbyTrees(loc.add(dir, type.strideRadius), type.bodyRadius, Team.NEUTRAL);
        }
        if (blockingTrees.length > 0) {
            // pick one within range
            TreeInfo reachableTree = null;
            for (TreeInfo tree : blockingTrees) {
                if (rc.canChop(tree.getID())) {
                    reachableTree = tree;
                    break;
                }
            }
            if (reachableTree == null) {
                return false;
            }
            float prevHealth = reachableTree.getHealth();
            rc.chop(reachableTree.getID());
            if (prevHealth < GameConstants.LUMBERJACK_CHOP_DAMAGE) {
                if (rc.canMove(dir)) {
                    rc.move(dir);
                }
            }
            return true;
        }

        // A move never happened, so return false.
        return false;
    }



    static boolean tryMeleeAttackEnemy() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(type.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, them);
        RobotInfo[] allies = rc.senseNearbyRobots(type.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, us);

        boolean containsArchon = false;
        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == RobotType.ARCHON) {
                containsArchon = true;
                break;
            }
        }

        if (enemies.length <= allies.length && !containsArchon) {
            return false;
        }

        if (!rc.canStrike()) {
            return false;
        }

        rc.strike();
        return true;
    }
}