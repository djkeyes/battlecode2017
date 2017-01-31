package combinedstrategies;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;

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
    static final int STRATEGY_CHANNEL = 12;

    // cues for deciding initial build order
    static final int NEARBY_TREE_HEALTH_CHANNEL = 13;
    static final int HAS_GIFTS_CHANNEL = 14;
    static final int INITIAL_BUILD_ORDER_CHANNEL = 15;

    // channels for map geometry
    static final int MAP_Y_LOWERLIMIT_CHANNEL = 20;
    static final int MAP_Y_UPPERLIMIT_CHANNEL = 21;
    static final int MAP_X_LOWERLIMIT_CHANNEL = 22;
    static final int MAP_X_UPPERLIMIT_CHANNEL = 23;

    static final int GARDENER_POSITIONS_SIZE_CHANNEL = 100;
    static final int GARDENER_POSITIONS_STACK_START_CHANNEL = 101;


    // Enemy positions. For now we will reserve 200 channels for this, which is 100 positions (x, y).
    // The detected positions will be stored sequentially and only positions which are not yet registered
    // will be stored.
    static final int ENEMY_POSITIONS_START_CHANNEL = 1000;
    static final int NUM_ENEMY_POSITIONS_CHANNELS = 200;
    // Specifies the "index" of last loaded channel.
    static int LAST_LOCATION_CHANNEL = -1;
    // Minimal distance squared to consider adding a new position.
    static final float MIN_POSITIONS_UPDATE_DISTANCE_SQ = 49;

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
    static int itemBuiltCount = 0; // Including units built and tree planted

    static int lowerLimitX = 0;
    static int lowerLimitY = 0;
    static int upperLimitX = 0;
    static int upperLimitY = 0;
    // possible strategies
    // TODO: any more we could implement? maybe a super-aggressive/low-econ rush? or maybe use tanks somehow?
    static final int MACRO_ARMY_STRATEGY = 0;
    static final int VP_WIN_STRATEGY = 1;
    static final int RUSH_STRATEGY = 2;
    static int currentStrategy;

    static final int MAX_GARDENER_POSITIONS_TO_COUNT = 50;
    static float gardenerDistX, gardenerDistY;

    static void tryGetUnitCounts() throws GameActionException {
        // Periodically, all counts are reset to track robot deaths.
        // Don't fetch new information on those rounds.
        // TODO: we could "double buffer" this information if we want to always have the latest info
        if (!shouldSendHeartbeat()) {
            getUnitCounts();
        }
    }

    static void tryGetMapSize() throws GameActionException { // Read broadcast about map size
        if (lowerLimitX == 0) {
            readMapLowerX();
        }
        if (lowerLimitY == 0) {
            readMapLowerY();
        }
        if (upperLimitX == 0) {
            readMapUpperX();
        }
        if (upperLimitY == 0) {
            readMapUpperY();
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
        totalTreeIncome = rc.readBroadcastFloat(TOTAL_TREE_INCOME_CHANNEL);
        itemBuiltCount = gardenerCount + lumberjackCount + scoutCount + soldierCount + tankCount + rc.getTreeCount();
//        System.out.printf("[A: %d, G: %d, L: %d, Sct: %d, Sdr: %d, T: %d, MaxedG: %d, Inc: %.4f, Tr: %d]\n",
//                archonCount, gardenerCount, lumberjackCount, scoutCount, soldierCount, tankCount, maxedGardenerCount,
//                totalTreeIncome,treeCount);
    }

    static boolean shouldSendHeartbeat() {
        // use 1 as the remainder, since the game starts on round 1
        return rc.getRoundNum() % TURNS_BETWEEN_COUNTS == 1;
    }

    static void sendHeartbeatSignal(int numArchons, int numGardeners, int numLumberjacks, int numScouts,
                                    int numSoldiers, int numTanks, int numMaxedGardeners, float treeIncome) throws GameActionException {
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
            rc.broadcastFloat(TOTAL_TREE_INCOME_CHANNEL, treeIncome);
            resetStationaryGardeneryPositions();
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
                treeIncome += rc.readBroadcastFloat(TOTAL_TREE_INCOME_CHANNEL);
                rc.broadcastFloat(TOTAL_TREE_INCOME_CHANNEL, treeIncome);
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

    static void reportMapLowerX(int lowerx) throws GameActionException {
        lowerLimitX = lowerx;
        rc.broadcast(MAP_X_LOWERLIMIT_CHANNEL, lowerx);
    }
    static void reportMapLowerY(int lowery) throws GameActionException {
        lowerLimitY = lowery;
        rc.broadcast(MAP_Y_LOWERLIMIT_CHANNEL, lowery);
    }
    static void reportMapUpperX(int upperx) throws GameActionException {
        upperLimitX = upperx;
        rc.broadcast(MAP_X_UPPERLIMIT_CHANNEL, upperx);
    }
    static void reportMapUpperY(int uppery) throws GameActionException {
        upperLimitY = uppery;
        rc.broadcast(MAP_Y_UPPERLIMIT_CHANNEL, uppery);
    }
    static void readMapLowerX() throws GameActionException {
        lowerLimitX = rc.readBroadcast(MAP_X_LOWERLIMIT_CHANNEL);
    }
    static void readMapLowerY() throws GameActionException {
        lowerLimitY = rc.readBroadcast(MAP_Y_LOWERLIMIT_CHANNEL);
    }
    static void readMapUpperX() throws GameActionException {
        upperLimitX = rc.readBroadcast(MAP_X_UPPERLIMIT_CHANNEL);
    }
    static void readMapUpperY() throws GameActionException {
        upperLimitY = rc.readBroadcast(MAP_Y_UPPERLIMIT_CHANNEL);
    }

    static void readEnemyPositions(int[] xpos, int[] ypos, int num_pos) throws GameActionException {
        if (num_pos == LAST_LOCATION_CHANNEL)
            return;
        // Assuming there is a limit to the number of robots detected.
        for (int i = num_pos + 1; i <= LAST_LOCATION_CHANNEL; ++i) {
            xpos[i] = (int)(rc.readBroadcast(2 * i + ENEMY_POSITIONS_START_CHANNEL) * 10);
            ypos[i] = (int)(rc.readBroadcast(2 * i + ENEMY_POSITIONS_START_CHANNEL + 1) * 10);
        }
        num_pos = LAST_LOCATION_CHANNEL;
    }
    static void writeEnemyPosition(float x, float y, int[] xpos, int[] ypos,
                                   int num_pos) throws GameActionException {
        // Make sure the local positions are up-to-date with the global broadcasted positions.
        readEnemyPositions(xpos, ypos, num_pos);
        if (num_pos == 100)
            return;
        for (int i = 0; i < num_pos; ++i) {
            if ((xpos[i] - 10 * x) * (xpos[i] - 10 * x) + (ypos[i] - 10 * y) * (ypos[i] - 10 * y) < 100 *
                    MIN_POSITIONS_UPDATE_DISTANCE_SQ) {
                return;
            }
        }
        ++num_pos;
        ++LAST_LOCATION_CHANNEL;
        xpos[num_pos] = (int)(x * 10);
        ypos[num_pos] = (int)(y * 10);
        rc.broadcast(ENEMY_POSITIONS_START_CHANNEL + num_pos * 2, xpos[num_pos]);
        rc.broadcast(ENEMY_POSITIONS_START_CHANNEL + 1 + num_pos * 2, ypos[num_pos]);
    }

    static void setNearbyTreeHealth(float amount) throws GameActionException {
        rc.broadcastFloat(NEARBY_TREE_HEALTH_CHANNEL, amount);
    }
    static float getNearbyTreeHealth() throws GameActionException {
        return rc.readBroadcastFloat(NEARBY_TREE_HEALTH_CHANNEL);
    }
    static void setHasGift(boolean hasGift) throws GameActionException {
        rc.broadcastBoolean(HAS_GIFTS_CHANNEL, hasGift);
    }
    static boolean getHasGift() throws GameActionException {
        return rc.readBroadcastBoolean(HAS_GIFTS_CHANNEL);
    }
    static void broadcastInitialBuildOrder(int buildOrder) throws GameActionException {
        rc.broadcast(INITIAL_BUILD_ORDER_CHANNEL, buildOrder);
    }
    static int readInitialBuildOrder() throws GameActionException {
        return rc.readBroadcast(INITIAL_BUILD_ORDER_CHANNEL);
    }

    public static void resetStationaryGardeneryPositions() throws GameActionException {
        rc.broadcast(GARDENER_POSITIONS_SIZE_CHANNEL, 0);
    }
    public static void setStationaryGardeneryPosition(MapLocation location) throws GameActionException {
        int size = rc.readBroadcast(GARDENER_POSITIONS_SIZE_CHANNEL);
        if(size >= MAX_GARDENER_POSITIONS_TO_COUNT){
            return;
        }
        int idx = 2*size;
        rc.broadcastFloat(GARDENER_POSITIONS_STACK_START_CHANNEL+idx, location.x);
        rc.broadcastFloat(GARDENER_POSITIONS_STACK_START_CHANNEL+idx+1, location.y);
        rc.broadcast(GARDENER_POSITIONS_SIZE_CHANNEL, size+1);
    }
    public static boolean anyGardenersInThreshold() throws GameActionException {
        int size = rc.readBroadcast(GARDENER_POSITIONS_SIZE_CHANNEL);
        float thresholdDistSq = RobotPlayer.thresholdDist * RobotPlayer.thresholdDist;
        for(int i=0; i < size; i++){
            int idx = 2*i;
            float x = rc.readBroadcastFloat(GARDENER_POSITIONS_STACK_START_CHANNEL+idx);
            float y = rc.readBroadcastFloat(GARDENER_POSITIONS_STACK_START_CHANNEL+idx+1);
            float dx = rc.getLocation().x - x;
            float dy = rc.getLocation().y - y;
            float distSq = dx * dx + dy * dy;

            if (distSq <= thresholdDistSq) {
                return true;
            }
        }
        return false;
    }

    public static void computeSquareDistanceToGardeners() throws GameActionException {
        gardenerDistX = 0f;
        gardenerDistY = 0f;
        int size = rc.readBroadcast(GARDENER_POSITIONS_SIZE_CHANNEL);
        float thresholdDistSq = RobotPlayer.thresholdDist * RobotPlayer.thresholdDist;
        for (int i = 0; i < size; i++) {
            int idx = 2 * i;
            float x = rc.readBroadcastFloat(GARDENER_POSITIONS_STACK_START_CHANNEL + idx);
            float y = rc.readBroadcastFloat(GARDENER_POSITIONS_STACK_START_CHANNEL + idx + 1);
            float dx = rc.getLocation().x - x;
            float dy = rc.getLocation().y - y;
            float distSq = dx * dx + dy * dy;

            if (distSq > thresholdDistSq) {
                continue;
            }
            double dist = StrictMath.sqrt(distSq);

            gardenerDistX += (float) (dx / dist) / distSq;
            gardenerDistY += (float) (dy / dist) / distSq;
        }
    }

}
