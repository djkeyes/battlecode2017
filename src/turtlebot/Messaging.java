package turtlebot;

import battlecode.common.GameActionException;

public strictfp class Messaging extends RobotPlayer {

    static final int TURNS_BETWEEN_COUNTS = 20;
    static final int LAST_COUNT_UPDATE_CHANNEL = 0;
    static final int ARCHON_COUNT_CHANNEL = 1;
    static final int GARDENER_COUNT_CHANNEL = 2;
    static final int MAXED_GARDENER_COUNT_CHANNEL = 10;
    static final int TOTAL_TREE_INCOME_CHANNEL = 11;
    static int archonCount = 0;
    static int gardenerCount = 0;
    static int maxedGardenerCount = 0;
    static float totalTreeIncome = 0;

    static void tryGetUnitCounts() throws GameActionException {
        // Periodically, all counts are reset to track robot deaths.
        // Don't fetch new information on those rounds.
        if (!shouldSendHeartbeat()) {
            getUnitCounts();
        }
    }

    static void getUnitCounts() throws GameActionException {
        archonCount = rc.readBroadcast(ARCHON_COUNT_CHANNEL);
        gardenerCount = rc.readBroadcast(GARDENER_COUNT_CHANNEL);
        maxedGardenerCount = rc.readBroadcast(MAXED_GARDENER_COUNT_CHANNEL);
        int encodedTreeIncome = rc.readBroadcast(TOTAL_TREE_INCOME_CHANNEL);
        totalTreeIncome = Float.intBitsToFloat(encodedTreeIncome);
    }

    static boolean shouldSendHeartbeat() {
        // use 1 as the remainder, since the game starts on round 1
        return rc.getRoundNum() % TURNS_BETWEEN_COUNTS == 1;
    }

    static void sendHeartbeatSignal(int numArchons, int numGardeners, int numMaxedGardeners, float treeIncome) throws GameActionException {
        // precondition: shouldSendHeartbeat() must be true

        int lastCountUpdate = rc.readBroadcast(LAST_COUNT_UPDATE_CHANNEL);
        if (rc.getRoundNum() != lastCountUpdate) {
            // if we're the first to execute, reset all counts
            rc.broadcast(LAST_COUNT_UPDATE_CHANNEL, rc.getRoundNum());
            rc.broadcast(ARCHON_COUNT_CHANNEL, numArchons);
            rc.broadcast(GARDENER_COUNT_CHANNEL, numGardeners);
            rc.broadcast(MAXED_GARDENER_COUNT_CHANNEL, numMaxedGardeners);
            int encodedIncome = Float.floatToIntBits(treeIncome);
            rc.broadcast(TOTAL_TREE_INCOME_CHANNEL, encodedIncome);
        } else {
            // otherwise send only as necessary
            if (numArchons > 0) {
                numArchons += rc.readBroadcast(ARCHON_COUNT_CHANNEL);
                rc.broadcast(ARCHON_COUNT_CHANNEL, numArchons);
            }
            if (numGardeners > 0) {
                numGardeners += rc.readBroadcast(GARDENER_COUNT_CHANNEL);
                rc.broadcast(GARDENER_COUNT_CHANNEL, numGardeners);
            }
            if (numMaxedGardeners > 0) {
                numMaxedGardeners += rc.readBroadcast(MAXED_GARDENER_COUNT_CHANNEL);
                rc.broadcast(MAXED_GARDENER_COUNT_CHANNEL, numMaxedGardeners);
            }
            if (treeIncome > 0) {
                int encodedTreeIncome = rc.readBroadcast(TOTAL_TREE_INCOME_CHANNEL);
                treeIncome += Float.intBitsToFloat(encodedTreeIncome);
                int encodedIncome = Float.floatToIntBits(treeIncome);
                rc.broadcast(TOTAL_TREE_INCOME_CHANNEL, encodedIncome);
            }
        }
    }

    static void reportUnitBuilt(int numArchons, int numGardeners, int numMaxedGardeners, float treeIncome) throws
            GameActionException {
        // precondition: getUnitCounts() must be called before this and shouldSendHeartbeat() must be false
        // (if shouldSendHeartbeat() is true, call sendHeartbeatSignal() instead)
        // this always increments counts, so only call it once between resets. This can be used directly for builders
        // to report robots still in-production.

        if (numArchons > 0) {
            rc.broadcast(ARCHON_COUNT_CHANNEL, archonCount + numArchons);
        }
        if (numGardeners > 0) {
            rc.broadcast(GARDENER_COUNT_CHANNEL, gardenerCount + numGardeners);
        }
        if (numMaxedGardeners > 0) {
            rc.broadcast(MAXED_GARDENER_COUNT_CHANNEL, maxedGardenerCount + numMaxedGardeners);
        }
        if (treeIncome > 0) {
            int encodedIncome = Float.floatToIntBits(totalTreeIncome + treeIncome);
            rc.broadcast(TOTAL_TREE_INCOME_CHANNEL, encodedIncome);
        }
    }

}
