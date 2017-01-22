package combinedstrategies;

import battlecode.common.*;

public class Lumberjack extends RobotPlayer implements RobotHandler {

    private int turnsAlive = 0;
    private TreeInfo[] neutralTreesInVision = null;
    private TreeInfo[] neutralTreesInInteractionRadius = null;


    @Override
    public void init() throws GameActionException {
    }

    @Override
    public void onLoop() throws GameActionException {
        boolean attacked = tryMeleeAttackEnemy();
        neutralTreesInVision = rc.senseNearbyTrees(type.sensorRadius, Team.NEUTRAL);
        neutralTreesInInteractionRadius = rc.senseNearbyTrees(type.bodyRadius + GameConstants.INTERACTION_DIST_FROM_EDGE, Team.NEUTRAL);

        if (!tryMoveTowardEnemy(6000)) {
            if (!attacked && neutralTreesInInteractionRadius.length > 0) {
                // if we've found a neutral tree, don't bother moving at all.
                chopBest();
                attacked = true;
            } else if (neutralTreesInVision.length > 0) {
                tryMoveTowardNeutralTrees(8000);
                neutralTreesInVision = rc.senseNearbyTrees(type.sensorRadius, Team.NEUTRAL);
                // try again
                if (!attacked && neutralTreesInInteractionRadius.length > 0) {
                    chopBest();
                    attacked = true;
                }
            } else {
                // TODO: find a good heuristic for clearing at home vs attacking
                if (needToMoveAwayFromPack() && turnsAlive < 500 && rc.getRoundNum() < 2500) {
                    moveAwayFromPack();
                } else {
                    tryMoveTowardEnemyArchons(8000);
                }
            }
        }

        if (!attacked) {
            tryMeleeAttackEnemy();
        }

        turnsAlive++;
    }

    @Override
    public void reportUnitCount() throws GameActionException {
        if (Messaging.shouldSendHeartbeat()) {
            Messaging.sendHeartbeatSignal(0, 0, 1, 0, 0, 0, 0, 0f,0);
        }
    }


    private boolean tryMoveTowardNeutralTrees(int maxBytecodes) throws GameActionException {
        // precondition: neutralTreesInVision.length > 0
        // just find the closest
        MapLocation closest = null;
        float minDist = Float.MAX_VALUE;
        MapLocation myLoc = rc.getLocation();
        for (TreeInfo tree : neutralTreesInVision) {
            float dist = myLoc.distanceTo(tree.getLocation());
            if (dist < minDist) {
                minDist = dist;
                closest = tree.getLocation();
            }
        }
        return tryMoveTo(closest, maxBytecodes);
    }

    private void chopBest() throws GameActionException {
        // precondition: neutralTreesInInteractionRadius.length > 0

        // pick a tree
        // TODO: which one to choose? min health? max area covered? has contained robot?
        // for now, use 1. has contained, 2. min health

        TreeInfo bestTree = null;
        boolean hasContainedRobot = false;
        float minHealth = Float.MAX_VALUE;
        for (TreeInfo tree : neutralTreesInInteractionRadius) {
            if (tree.containedRobot != null) {
                if (tree.health < minHealth || !hasContainedRobot) {
                    hasContainedRobot = true;
                    minHealth = tree.health;
                    bestTree = tree;
                }
            } else if (!hasContainedRobot) {
                if (tree.health < minHealth) {
                    minHealth = tree.health;
                    bestTree = tree;
                }
            }
        }

        rc.chop(bestTree.getID());
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
            if (enemyTrees.length == 0 || (totalEnemies - totalAllies) * RobotType.LUMBERJACK.attackPower >
                    GameConstants.LUMBERJACK_CHOP_DAMAGE || weakestEnemyTree == null) {
                rc.strike();
            } else {
                // chop weakest tree
                rc.chop(weakestEnemyTree.getID());
            }
            return true;
        }

        // defensive play: if the enemy type is a scout, and there's a gardener nearby, use strike
        // even if there's an allied tree nearby, the gardener should be able to heal it fast enough.
        // note: the presence of a gardener sort of implies that we're on the defensive, so this should rarely
        // activate when being offensive.
        boolean nearEnemyScout = false;
        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == RobotType.SCOUT) {
                nearEnemyScout = true;
                break;
            }
        }
        if (nearEnemyScout) {
            boolean nearAllyGardener = false;
            // this requires updateNearby() as a prerequisite
            for (RobotInfo ally : alliesInSignt) {
                if (ally.getType() == RobotType.GARDENER) {
                    nearAllyGardener = true;
                    break;
                }
            }
            if (nearAllyGardener) {
                rc.strike();
                return true;
            }
        }

        // to risky to do AOE, but any enemy trees nearby?
        if (weakestEnemyTree != null) {
            rc.chop(weakestEnemyTree.getID());
            return true;
        }

        return false;
    }

    static TreeInfo getWeakestTree(TreeInfo[] trees) {
        double minHealth = Double.POSITIVE_INFINITY;
        TreeInfo bestTree = null;
        for (TreeInfo tree : trees) {
            float dist = tree.getLocation().distanceTo(rc.getLocation());
            if (dist > RobotType.LUMBERJACK.bodyRadius + tree.getRadius() + GameConstants.INTERACTION_DIST_FROM_EDGE) {
                continue;
            }
            if (tree.health < minHealth) {
                minHealth = tree.health;
                bestTree = tree;
            }
        }
        return bestTree;
    }

}
