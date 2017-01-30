package combinedstrategies;

import battlecode.common.*;

public class Soldier extends RobotPlayer implements RobotHandler {

    @Override
    public void init() throws GameActionException {

    }

    @Override
    public void onLoop() throws GameActionException {
        boolean attackedAndMoved = tryPentashotScoutRush(5000);
        if (!attackedAndMoved) {
            if (!tryMoveTowardEnemy(6500)) {
                if (!tryDodgeBullets(7000)) {
                    tryMoveTowardEnemyArchons(7500);
                }
            }
            tryAttackEnemy(8000);
        }
    }

    @Override
    public void reportUnitCount() throws GameActionException {
        if (Messaging.shouldSendHeartbeat()) {
            Messaging.sendHeartbeatSignal(0, 0, 1, 0, 0, 0, 0, 0f);
        }
    }


    private boolean tryPentashotScoutRush(int maxBytecodes) throws GameActionException {
        // if the only neighboring units are scouts, we're probably being rushed.
        // jump toward them and try to pentashot
        if (enemiesInSight.length == 0) {
            return false;
        }

        if(rc.getRoundNum() > 300){
            return false;
        }

        RobotInfo closestScout = null;
        float minDist = Float.MAX_VALUE;
        for (RobotInfo enemy : enemiesInSight) {
            if (enemy.type == RobotType.SCOUT) {
                float dist = rc.getLocation().distanceTo(enemy.getLocation());
                if (dist < minDist) {
                    minDist = dist;
                    closestScout = enemy;
                }
            }
        }
        if (closestScout == null) {
            return false;
        }

        if (minDist - RobotType.SCOUT.bodyRadius > type.strideRadius) {
            // get as close as possible, then shoot
            tryMoveTo(closestScout.location, maxBytecodes);
            double dist = rc.getLocation().distanceTo(closestScout.location);
            double projectedAngle = StrictMath.asin(RobotType.SCOUT.bodyRadius / dist);
            projectedAngle = StrictMath.toDegrees(projectedAngle);
            Direction dir = rc.getLocation().directionTo(closestScout.getLocation());
            if (projectedAngle >= GameConstants.PENTAD_SPREAD_DEGREES * 2
                    && rc.canFirePentadShot()) {
                rc.firePentadShot(dir);
            } else if (projectedAngle >= GameConstants.TRIAD_SPREAD_DEGREES
                    && rc.canFireTriadShot()) {
                rc.fireTriadShot(dir);
            } else if (rc.canFireSingleShot()) {
                rc.fireSingleShot(dir);
            }
            return true;
        }

        // walk right up to him, then shoot
        // get as close as possible, then shoot
        // first try to go in a stright line. otherwise fall back to more complex pathing.
        Direction dir = rc.getLocation().directionTo(closestScout.getLocation());
        MapLocation directMovement = rc.getLocation().add(dir, minDist - RobotType.SCOUT.bodyRadius - type.bodyRadius
                - 0.001f);
        if(rc.canMove(directMovement)){
            rc.move(directMovement);
        } else {
            tryMoveTo(closestScout.location, maxBytecodes);
        }
        double dist = rc.getLocation().distanceTo(closestScout.location);
        double projectedAngle = StrictMath.asin(RobotType.SCOUT.bodyRadius / dist);
        projectedAngle = StrictMath.toDegrees(projectedAngle);
        if (projectedAngle >= GameConstants.PENTAD_SPREAD_DEGREES * 2
                && rc.canFirePentadShot()) {
            rc.firePentadShot(dir);
        } else if (projectedAngle >= GameConstants.TRIAD_SPREAD_DEGREES
                && rc.canFireTriadShot()) {
            rc.fireTriadShot(dir);
        } else if (rc.canFireSingleShot()) {
            rc.fireSingleShot(dir);
        }
        return true;

    }

}
