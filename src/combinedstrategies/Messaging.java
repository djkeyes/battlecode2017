package combinedstrategies;

import battlecode.common.GameActionException;

public strictfp class Messaging extends RobotPlayer {

    // CHANNEL ASSIGNMENTS

    static final int LAST_COUNT_UPDATE_CHANNEL = 0;
    static final int ARCHON_COUNT_CHANNEL = 1;
    static final int GARDENER_COUNT_CHANNEL = 2;
    static final int LUMBERJACK_COUNT_CHANNEL = 3;
    static final int SCOUT_COUNT_CHANNEL = 4;
    static final int SOLDIER_COUNT_CHANNEL = 5;
    static final int TANK_COUNT_CHANNEL = 6;

    // useful variables to track when creating rooted gardeners
    static final int MAXED_GARDENER_COUNT_CHANNEL = 10;
    static final int TOTAL_TREE_INCOME_CHANNEL = 11;


    // overarching game strategy
    static final int STRATEGY_CHANNEL = 20;

    /********************************************************************/

    static final int TURNS_BETWEEN_COUNTS = 5;

    static int archonCount = 0;
    static int gardenerCount = 0;
    static int lumberjackCount = 0;
    static int scoutCount = 0;
    static int soldierCount = 0;
    static int tankCount = 0;

    static int maxedGardenerCount = 0;
    static float totalTreeIncome = 0;

    // possible strategies
    // TODO: any more we could implement? maybe a super-aggressive/low-econ rush? or maybe use tanks somehow?
    static final int MACRO_ARMY_STRATEGY = 0;
    static final int VP_WIN_STRATEGY = 1;
    static int currentStrategy;


    static void tryGetUnitCounts() throws GameActionException {
        // Periodically, all counts are reset to track robot deaths.
        // Don't fetch new information on those rounds.
        // TODO: we could "double buffer" this information if we want to always have the latest info
        if (!shouldSendHeartbeat()) {
            getUnitCounts();
        }
    }

    static void getUnitCounts() throws GameActionException {
        archonCount = rc.readBroadcast(ARCHON_COUNT_CHANNEL);
        gardenerCount = rc.readBroadcast(GARDENER_COUNT_CHANNEL);
        lumberjackCount = rc.readBroadcast(LUMBERJACK_COUNT_CHANNEL);
        scoutCount = rc.readBroadcast(SCOUT_COUNT_CHANNEL);
        soldierCount = rc.readBroadcast(SOLDIER_COUNT_CHANNEL);
        tankCount = rc.readBroadcast(TANK_COUNT_CHANNEL);
        maxedGardenerCount = rc.readBroadcast(MAXED_GARDENER_COUNT_CHANNEL);
        int encodedTreeIncome = rc.readBroadcast(TOTAL_TREE_INCOME_CHANNEL);
        totalTreeIncome = Float.intBitsToFloat(encodedTreeIncome);

//        System.out.printf("[A: %d, G: %d, L: %d, Sct: %d, Sdr: %d, T: %d, MaxedG: %d, Inc: %.4f]\n",
//                archonCount, gardenerCount, lumberjackCount, scoutCount, soldierCount, tankCount, maxedGardenerCount,
//                totalTreeIncome);
    }

    static boolean shouldSendHeartbeat() {
        // use 1 as the remainder, since the game starts on round 1
        return rc.getRoundNum() % TURNS_BETWEEN_COUNTS == 1;
    }

    static void sendHeartbeatSignal(int numArchons, int numGardeners, int numLumberjacks, int numScouts,
                                    int numSoldiers, int numTanks, int numMaxedGardeners, float treeIncome)
            throws GameActionException {
        // TODO: a common use case is to have exactly 1 non-zero argument. maybe we should separate this into n
        // separate methods?

        // precondition: shouldSendHeartbeat() must be true

        int lastCountUpdate = rc.readBroadcast(LAST_COUNT_UPDATE_CHANNEL);
        if (rc.getRoundNum() != lastCountUpdate) {
            // if we're the first to execute, reset all counts
            rc.broadcast(LAST_COUNT_UPDATE_CHANNEL, rc.getRoundNum());
            rc.broadcast(ARCHON_COUNT_CHANNEL, numArchons);
            rc.broadcast(GARDENER_COUNT_CHANNEL, numGardeners);
            rc.broadcast(LUMBERJACK_COUNT_CHANNEL, numLumberjacks);
            rc.broadcast(SCOUT_COUNT_CHANNEL, numScouts);
            rc.broadcast(SOLDIER_COUNT_CHANNEL, numSoldiers);
            rc.broadcast(TANK_COUNT_CHANNEL, numTanks);
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
            if (numLumberjacks > 0) {
                numLumberjacks += rc.readBroadcast(LUMBERJACK_COUNT_CHANNEL);
                rc.broadcast(LUMBERJACK_COUNT_CHANNEL, numLumberjacks);
            }
            if (numScouts > 0) {
                numScouts += rc.readBroadcast(SCOUT_COUNT_CHANNEL);
                rc.broadcast(SCOUT_COUNT_CHANNEL, numScouts);
            }
            if (numSoldiers > 0) {
                numSoldiers += rc.readBroadcast(SOLDIER_COUNT_CHANNEL);
                rc.broadcast(SOLDIER_COUNT_CHANNEL, numSoldiers);
            }
            if (numTanks > 0) {
                numTanks += rc.readBroadcast(TANK_COUNT_CHANNEL);
                rc.broadcast(TANK_COUNT_CHANNEL, numTanks);
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

    /**
     * Update unit counts for units built/hired/found in trees, between heartbeat rounds.
     * precondition: getUnitCounts() must be called before this and shouldSendHeartbeat() must be false
     *
     * @throws GameActionException
     */
    static void reportBuiltArchon() throws GameActionException {
        rc.broadcast(ARCHON_COUNT_CHANNEL, archonCount + 1);
    }

    /**
     * Update unit counts for units built/hired/found in trees, between heartbeat rounds.
     * precondition: getUnitCounts() must be called before this and shouldSendHeartbeat() must be false
     *
     * @throws GameActionException
     */
    static void reportBuiltGardener() throws GameActionException {
        rc.broadcast(GARDENER_COUNT_CHANNEL, gardenerCount + 1);
    }

    /**
     * Update unit counts for units built/hired/found in trees, between heartbeat rounds.
     * precondition: getUnitCounts() must be called before this and shouldSendHeartbeat() must be false
     *
     * @throws GameActionException
     */
    static void reportBuiltLumberjack() throws GameActionException {
        rc.broadcast(LUMBERJACK_COUNT_CHANNEL, lumberjackCount + 1);
    }

    /**
     * Update unit counts for units built/hired/found in trees, between heartbeat rounds.
     * precondition: getUnitCounts() must be called before this and shouldSendHeartbeat() must be false
     *
     * @throws GameActionException
     */
    static void reportBuiltScout() throws GameActionException {
        rc.broadcast(SCOUT_COUNT_CHANNEL, scoutCount + 1);
    }

    /**
     * Update unit counts for units built/hired/found in trees, between heartbeat rounds.
     * precondition: getUnitCounts() must be called before this and shouldSendHeartbeat() must be false
     *
     * @throws GameActionException
     */
    static void reportBuiltSoldier() throws GameActionException {
        rc.broadcast(SOLDIER_COUNT_CHANNEL, soldierCount + 1);
    }

    /**
     * Update unit counts for units built/hired/found in trees, between heartbeat rounds.
     * precondition: getUnitCounts() must be called before this and shouldSendHeartbeat() must be false
     *
     * @throws GameActionException
     */
    static void reportBuiltTank() throws GameActionException {
        rc.broadcast(TANK_COUNT_CHANNEL, tankCount + 1);
    }


    static void setStrategy(int strategy) throws GameActionException {
        currentStrategy = strategy;
        rc.broadcast(STRATEGY_CHANNEL, strategy);
    }

    static void readStrategy() throws GameActionException {
        currentStrategy = rc.readBroadcast(STRATEGY_CHANNEL);
    }

}
