package turtlebot;

import battlecode.common.*;

strictfp class Lumberjack extends RobotPlayer {

    static void run() throws GameActionException {
        while (true) {
            boolean attacked = false;

            if (!tryMoveTowardEnemy()) {
                Direction dir = randomDirection();
                if(!tryMove(dir, 45, 20)) {
                    attacked = tryChop(dir);
                }
            }

            if (!attacked) {
                tryMeleeAttackEnemy();
            }

            tryShakeNearby();

            donateExcessVictoryPoints();

            Clock.yield();
        }
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

    static boolean tryMeleeAttackEnemy() throws GameActionException {
        if (!rc.canStrike()) {
            return false;
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(type.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, them);
        RobotInfo[] allies = rc.senseNearbyRobots(type.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, us);

        TreeInfo[] enemyTrees = rc.senseNearbyTrees(type.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, them);
        TreeInfo[] alliedTrees = rc.senseNearbyTrees(type.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, us);

        int totalEnemies = enemies.length + enemyTrees.length;
        int totalAllies = allies.length + alliedTrees.length;

        boolean nearArchon = false;
        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == RobotType.ARCHON) {
                nearArchon = true;
                break;
            }
        }

        TreeInfo weakestEnemyTree = getWeakestTree(enemyTrees);

        if (totalEnemies > totalAllies || nearArchon) {
            // maximize damage
            if (enemyTrees.length == 0 || (totalEnemies - totalAllies)*RobotType.LUMBERJACK.attackPower >
                    GameConstants.LUMBERJACK_CHOP_DAMAGE || weakestEnemyTree == null) {
                rc.strike();
            } else {
                // chop weakest tree
                rc.chop(weakestEnemyTree.getID());
            }
            return true;
        }

        // to risky to do AOE, but any enemy trees nearby?
        if(weakestEnemyTree != null){
            rc.chop(weakestEnemyTree.getID());
            return true;
        }
        TreeInfo[] neutralTrees = rc.senseNearbyTrees(type.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS,
                Team.NEUTRAL);
        // any neutral trees nearby?
        if(neutralTrees.length > 0){
            TreeInfo weakestNeutralTree = getWeakestTree(neutralTrees);
            if (weakestNeutralTree != null) {
                rc.chop(weakestNeutralTree.getID());
                return true;
            }
        }

        return false;
    }

    static TreeInfo getWeakestTree(TreeInfo[] trees){
        double minHealth = Double.POSITIVE_INFINITY;
        TreeInfo bestTree = null;
        for(TreeInfo tree : trees){
            float dist = tree.getLocation().distanceTo(rc.getLocation());
            if(dist > RobotType.LUMBERJACK.bodyRadius + tree.getRadius() + RobotType.LUMBERJACK.strideRadius){
                continue;
            }
            if(tree.health < minHealth){
                minHealth = tree.health;
                bestTree = tree;
            }
        }
        return bestTree;
    }
}
