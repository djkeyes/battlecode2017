package combinedstrategies;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.TreeInfo;

public class Tank extends RobotPlayer implements RobotHandler {

    private static TreeInfo[] alliedTreesInStride = null;

    @Override
    public void init() throws GameActionException {

    }

    @Override
    public void onLoop() throws GameActionException {
        senseNearbyAlliedTrees();
        if (!tryKiteLumberJacks(10000)) {
            if (!tryMoveWithinDistanceOfEnemy(11500, 5f)) {
                tryMoveTowardDistantEnemies(12500);
            }
        }
        tryAttackEnemy(13000);
    }

    private void senseNearbyAlliedTrees() {
        alliedTreesInStride = rc.senseNearbyTrees(type.bodyRadius + type.strideRadius + 0.0001f, us);
    }

    @Override
    public void reportUnitCount() throws GameActionException {
        if (Messaging.shouldSendHeartbeat()) {
            Messaging.sendHeartbeatSignal(0, 0, 0, 0, 0, 1, 0, 0f);
        }
    }

    public static boolean canMoveDir(Direction dir) {
        if (!rc.canMove(dir)) {
            return false;
        }

        MapLocation newLoc = rc.getLocation().add(dir);
        return !anyAlliedTreesInLoc(newLoc);
    }

    public static boolean canMoveLoc(MapLocation loc) {
        if (!rc.canMove(loc)) {
            return false;
        }

        return !anyAlliedTreesInLoc(loc);
    }

    public static boolean canMoveDirDist(Direction dir, Float mag) {
        if (!rc.canMove(dir, mag)) {
            return false;
        }

        MapLocation newLoc = rc.getLocation().add(dir, mag);
        return !anyAlliedTreesInLoc(newLoc);
    }

    private static boolean anyAlliedTreesInLoc(MapLocation loc) {
        for (TreeInfo alliedTree : alliedTreesInStride) {
            if (loc.distanceTo(alliedTree.location) <= type.bodyRadius + 0.001f) {
                return true;
            }
        }
        return false;
    }

}
