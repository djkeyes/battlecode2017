package turtlebot;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;

public strictfp class Archon extends RobotPlayer {

    // TODO(daniel): how do different values affect the outcome? I suspect not by much.
    static final float MIN_GARDENER_EFFICIENCY_TO_BUILD = 0.5f;

    static void run() throws GameActionException {
        // the first turn is a heartbeat turn, so unit counts aren't updated.
        // force a manual update (even though some information may only be partially correct)
        if (rc.getRoundNum() == 1) {
            Messaging.getUnitCounts();
        }

        while (true) {
            updateNearby();
            Messaging.tryGetUnitCounts();

            boolean builtGardener = tryBuildGardener();

            if (!tryDodgeBullets()) {
                // maybe we should try to move away from large groups?
                // or at least try to move 1 unit away from gardeners?
                tryMove(randomDirection(), 20, 10);
            }

            tryShakeNearby();

            donateExcessVictoryPoints();

            if (Messaging.shouldSendHeartbeat()) {
                Messaging.sendHeartbeatSignal(1, builtGardener ? 1 : 0, 0, 0);
            } else if (builtGardener) {
                Messaging.reportUnitBuilt(0, 1, 0, 0f);
            }

            Clock.yield();
        }
    }

    static boolean tryBuildGardener() throws GameActionException {
        if (!rc.isBuildReady()) {
            return false;
        }

        // trees are cheaper than gardeners, so we could just ignore this, but sometimes we'll end up with 2
        // gardeners at the beginning
        if (!maxedOutOnTrees()) {
            return false;
        }

        // TODO: check adjacent units to find an unobstructed direction
        Direction dir = randomDirection();
        if (rc.canHireGardener(dir)) {
            rc.hireGardener(dir);
            return true;
        }
        return false;
    }

    static boolean maxedOutOnTrees() {
        return Messaging.maxedGardenerCount >= Messaging.gardenerCount * MIN_GARDENER_EFFICIENCY_TO_BUILD;
    }


}
