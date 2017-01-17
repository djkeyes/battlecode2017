package combinedstrategies;

import battlecode.common.GameActionException;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;

public class Tank extends RobotPlayer implements RobotHandler {

    @Override
    public void init() throws GameActionException {

    }

    @Override
    public void onLoop() throws GameActionException {
        // Tank movement is hard, because you can accidentally destroy your own trees.
        // for now, don't move.
        tryAttackEnemy();
    }

    @Override
    public void reportUnitCount() throws GameActionException {
        if (Messaging.shouldSendHeartbeat()) {
            Messaging.sendHeartbeatSignal(0, 0, 1, 0, 0, 0, 0, 0f);
        }
    }


    private boolean tryAttackEnemy() throws GameActionException {
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
            if (enemy.type != RobotType.GARDENER || isPathToRobotObstructed(enemy)) {
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

    private double evaluateEnemy(RobotInfo enemy) {
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


}
