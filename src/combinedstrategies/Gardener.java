package combinedstrategies;

import battlecode.common.*;

strictfp class Gardener extends RobotPlayer implements RobotHandler {

    private UnitConstuctionQueue inConstruction = new UnitConstuctionQueue(3);
    private boolean shouldBuildTree;
    private RobotType typeToBuild;
    private boolean builtUnit;

    static int earliestRushTurn;

    /**
     * One problem with trees is that units tend to get stuck. There are many computationally-cheap ways to address
     * this; here are a few:
     * -Apply a "force" to move away, until there's space for 2 gardener radii, 2 trees, and for 1 unit to
     * pass through. ie gardeners centers are R = 2*b + 2*d + t apart, where b=body radius of small units, d=distance
     * to tree centers, t=tree radius. Note: if because NUM_TREES_PER_GARDENER > 6, then R > 7, so we need to
     * broadcast gardener positions (TODO). If NUM_TREES_PER_GARDENER = 6, then R is approximately 7, so we can just
     * check the vision range. Another problem is that robots get stuck in local minima; possible solutions are to
     * build anyway (maybe the map is too cramped to move anywhere else), or to build new gardeners (maybe the
     * previous gardener had bad luck) or to record and move away from "stuck" positions (TODO) (but that still has
     * local minima)
     * -only allow gardeners to build on grid coordinates. A good grid choice is a triangular grid with distance R.
     * We still need to store gardener positions, but don't need to compute a magic force, since gardeners can
     * pathfind to empty positions.
     * -allow gardeners to move, but only allow trees on a hexagonal grid. Now tree positions need to be stored, so
     * gardeners don't forget about far-away trees. Compared to clustering strategies, this might be slightly less
     * space efficient, but it's more defendable against scout rushes.
     */
//    private final int BROADCASTED_CLUSTER_STRATEGY = 0;
//    private final int CLUSTER_ON_GRID_STRATEGY = 1;
//    private final int GRID_ALIGNED_CLUSTER_STRATEGY = 2;
//    // TODO: read this as a parameter, and test which one works best
//    private int PLANTING_STRATEGY = BROADCASTED_CLUSTER_STRATEGY;

    // unfortunately with the latest patch, n>7 trees is too far to water without moving
    // TODO: make gardeners move more freely within their cluster
    static final int NUM_TREES_PER_GARDENER = 6;
    static final float TREE_OFFSET_EPSILON;
    static {
        // dk: GameConstants.GENERAL_SPAWN_OFFSET=0.01f is unnecessarily large. instead, gardeners position
        // themselves so that trees are only TREE_OFFSET_EPSILON<<0.01 from the intended position
        if(NUM_TREES_PER_GARDENER == 6) {
            // to pack trees tightly with n=6, we actually have to move backwards slightly,
            // so we are constrained by TREE_OFFSET_EPSILON*2 > GENERAL_SPAWN_OFFSET
            TREE_OFFSET_EPSILON = 0.00505f;
        } else {
            TREE_OFFSET_EPSILON = 0.0001f;
        }
    }

    private MapLocation centerPosition;
    private boolean treesInitialized = false;
    private MapLocation[] plantingPositions = new MapLocation[NUM_TREES_PER_GARDENER];
    private Direction[] plantingDirs = new Direction[NUM_TREES_PER_GARDENER];
    private int[] treeIds = new int[NUM_TREES_PER_GARDENER];
    private int[] treeConstructionTurn = new int[NUM_TREES_PER_GARDENER];
    private int nextTreeIdx;

    private int turnsAlive;

    private boolean isStationary = false;

    private int isMaxed = 0;
    private float adjacentTreeIncome = 0f;
    private int adjacentTreeNum = 0;
    private int numUnitsBuilt = 0;

    @Override
    public void init() throws GameActionException {
        earliestRushTurn = computeEarliestRush();
        turnsAlive = 0;
    }

    @Override
    public void onLoop() throws GameActionException {
        builtUnit = false;

        // TODO: unless we're the very first gardener, maybe we should move away from nearby units first.
        // or at least from nearby gardeners
        // or maybe we should have a messaging flag. if the map is very small/crowded, don't bother spreading.
        if (!isStationary && turnsAlive <= 200 && rc.getRoundNum() > 5 && needToMoveAwayFromPack()) {
            moveAwayFromPack();
        } else {
            if (!isStationary) {
                isStationary = true;
                initializeTreePattern();
            }
            // attempt to build a tree or unit
            if (rc.getBuildCooldownTurns() == 0) {
                determineWhatToBuild();
                if (shouldBuildTree) {
                    buildTree();
                } else if (typeToBuild != null) {
                    builtUnit = tryBuildRobot();
                }
            } else {
                tryReturnToCenter();
            }
        }

        // maybe if we haven't built any trees at all, we should dodge bullets?

        tryWateringNearby();

        turnsAlive++;
    }

    @Override
    public void reportUnitCount() throws GameActionException {
        inConstruction.dequeueUntil(rc.getRoundNum());
        if (Messaging.shouldSendHeartbeat()) {
            examineAdjacentTrees();
            Messaging.sendHeartbeatSignal(0, 1, inConstruction.numLumberjacks,
                    inConstruction.numScouts, inConstruction.numSoldiers, inConstruction.numTanks, isMaxed,
                    adjacentTreeIncome,adjacentTreeNum);
        } else if (builtUnit) {
            switch (typeToBuild) {
                case SCOUT:
                    Messaging.reportBuiltScout();
                    break;
                case SOLDIER:
                    Messaging.reportBuiltSoldier();
                    break;
                case TANK:
                    Messaging.reportBuiltTank();
                    break;
                case LUMBERJACK:
                    Messaging.reportBuiltLumberjack();
                    break;
            }
        }

    }

    private MapLocation computeTreePosition(int treeIdx) {
        return plantingPositions[treeIdx].add(plantingDirs[treeIdx], type.bodyRadius
                + GameConstants.GENERAL_SPAWN_OFFSET + GameConstants.BULLET_TREE_RADIUS);
    }

    static float computeTreePlantingDist(int numTrees){
        double r = GameConstants.BULLET_TREE_RADIUS;
        double theta = (numTrees - 2f) * Math.PI / 2f / numTrees;
        return (float) (r / StrictMath.cos(theta)) + TREE_OFFSET_EPSILON;
    }

    private void initializeTreePattern() throws GameActionException {
        if (treesInitialized) {
            return;
        }
        treesInitialized = true;

        centerPosition = rc.getLocation();

        // create a set of tree positions
        // dist to center of tree
        float d = computeTreePlantingDist(NUM_TREES_PER_GARDENER);
        // due to finite precision of floating point numbers, we offset the tree by a small epsilon
        float plantingDist = d - GameConstants.BULLET_TREE_RADIUS
                - GameConstants.GENERAL_SPAWN_OFFSET - type.bodyRadius;
        float centerRad = (float) (gen.nextDouble() * 2.0 * StrictMath.PI);
        for (int i = 0; i < NUM_TREES_PER_GARDENER; i++) {
            plantingDirs[i] = new Direction((float) (centerRad + 2.0 * Math.PI * i / NUM_TREES_PER_GARDENER));
            plantingPositions[i] = centerPosition.add(plantingDirs[i], plantingDist);
            treeIds[i] = -1;
        }
    }

    private void buildTree() throws GameActionException {
        rc.move(plantingPositions[nextTreeIdx]);
        rc.plantTree(plantingDirs[nextTreeIdx]);
        TreeInfo newTree = rc.senseTreeAtLocation(computeTreePosition(nextTreeIdx));
        treeIds[nextTreeIdx] = newTree.getID();
        treeConstructionTurn[nextTreeIdx] = rc.getRoundNum();
    }


    private void tryWateringNearby() throws GameActionException {
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(type.bodyRadius + GameConstants.INTERACTION_DIST_FROM_EDGE, us);
        // find lowest health tree
        float lowestHealth = Float.MAX_VALUE;
        TreeInfo bestTree = null;
        for (TreeInfo tree : nearbyTrees) {
            float health = tree.getHealth();
            if (health < lowestHealth) {
                lowestHealth = health;
                bestTree = tree;
            }
        }

        if (bestTree != null) {
            rc.water(bestTree.getID());
        }
    }

    private void examineAdjacentTrees() throws GameActionException {
        if (!isStationary) {
            adjacentTreeIncome = 0.0f;
            isMaxed = 0;
            return;
        }
        int numTrees = 0;
        int numTreesPossible = 0;
        float totalTreeHealth = 0.0f;
        for (int i = 0; i < NUM_TREES_PER_GARDENER; i++) {
            if (treeIds[i] != -1) {
                // check if tree is still alive
                if (rc.canSenseTree(treeIds[i])) {
                    TreeInfo tree = rc.senseTree(treeIds[i]);
                    // if it was built recently, give an amortized estimate
                    int completionTurn = treeConstructionTurn[i] + 80;
                    if (completionTurn > rc.getRoundNum()) {
                        if (completionTurn < rc.getRoundNum() + Messaging.TURNS_BETWEEN_COUNTS) {
                            float amortizedHealth = GameConstants.BULLET_TREE_MAX_HEALTH
                                    * (float) (completionTurn - rc.getRoundNum())
                                    / Messaging.TURNS_BETWEEN_COUNTS;
                            totalTreeHealth += amortizedHealth;
                        }
                    } else {
                        totalTreeHealth += tree.health;
                    }
                    numTrees++;
                    numTreesPossible++;
                } else {
                    treeIds[i] = -1;
                }
            }

            if (treeIds[i] == -1) {
                // check if it's even possible to build here
                MapLocation treePosition = computeTreePosition(i);
                if (rc.canMove(plantingPositions[i])
                        && rc.onTheMap(treePosition, GameConstants.BULLET_TREE_RADIUS)
                        && !rc.isCircleOccupied(treePosition, GameConstants.BULLET_TREE_RADIUS)) {
                    numTreesPossible++;
                }
            }

        }
        adjacentTreeIncome = GameConstants.BULLET_TREE_BULLET_PRODUCTION_RATE * totalTreeHealth;
        adjacentTreeNum = numTrees;
        // 2-tree leeway
        if (numTrees >= numTreesPossible - 2) {
            isMaxed = 1;
        } else {
            isMaxed = 0;
        }
    }

    private void tryReturnToCenter() throws GameActionException {
        if (rc.getLocation() != centerPosition && rc.canMove(centerPosition)) {
            rc.move(centerPosition);
        }
    }

    private boolean tryBuildRobot() throws GameActionException {
        // TODO check locations in a more structured way
        // for example, check nearby units and detect free space. alternatively, since trees are in a pattern, try
        // building in holes in the pattern.

        if (rc.getTeamBullets() < typeToBuild.bulletCost) {
            return false;
        }

        // first check planting locations
        for (int i = 0; i < NUM_TREES_PER_GARDENER; i++) {
            MapLocation buildLoc = computeTreePosition(i);
            if (rc.canMove(plantingPositions[i])
                    && rc.onTheMap(buildLoc, typeToBuild.bodyRadius)
                    && !rc.isCircleOccupied(buildLoc, typeToBuild.bodyRadius)) {
                rc.move(plantingPositions[i]);
                rc.buildRobot(typeToBuild, plantingDirs[i]);
                inConstruction.enqueue(typeToBuild, rc.getRoundNum() + 20);
                numUnitsBuilt++;
                return true;
            }
        }

        // then just try adjacent positions. maybe someone else is in the way.
        int numRetries = 20;
        for (int i = 0; i < numRetries; i++) {
            Direction dir = randomDirection();
            if (rc.canBuildRobot(typeToBuild, dir)) {
                rc.buildRobot(typeToBuild, dir);
                inConstruction.enqueue(typeToBuild, rc.getRoundNum() + 20);
                numUnitsBuilt++;
                return true;
            }
        }
        return false;
    }

    private void determineWhatToBuild() throws GameActionException {
        if (BuildOrder.shouldFollowInitialBuildOrder()) {
            typeToBuild = BuildOrder.nextToBuild();
            // null indicates tree
            shouldBuildTree = (typeToBuild == null) && canAddNewTree();
        } else {
            boolean needDefences = needDefences();
            boolean canAddTree = canAddNewTree();
            if (needDefences || (!canAddTree && Messaging.currentStrategy == Messaging.MACRO_ARMY_STRATEGY)) {
                shouldBuildTree = false;
                if (numUnitsBuilt < 2 || numUnitsBuilt % 2 == 1) {
                    typeToBuild = RobotType.LUMBERJACK;
                } else {
                    typeToBuild = RobotType.SOLDIER;
                }
            } else if (canAddTree) {
                shouldBuildTree = true;
                typeToBuild = null;
            } else {
                shouldBuildTree = false;
                typeToBuild = null;
            }
        }
    }

    static boolean needDefences() {
        // for this, only check nearby units

        // this should already be plenty of defenders. hopefully they're close enough.
        // TODO(daniel): maybe this should be a ratio, like gardeners-per-lumberjack
        if (Messaging.gardenerCount <= 4
                && Messaging.lumberjackCount + Messaging.soldierCount + Messaging.tankCount >= 3 * Messaging
                .gardenerCount) {
            return false;
        }

        int alliedFighters = 0;
        for (RobotInfo ally : alliesInSignt) {
            if (ally.type.canAttack()) {
                alliedFighters++;
            }
        }
        int enemyFighters = 0;
        for (RobotInfo enemy : enemiesInSight) {
            if (enemy.type.canAttack()) {
                enemyFighters++;
            }
        }

        // could experiment with tolerances here
        if (alliedFighters < enemyFighters) {
            return true;
        }

        if (alliedFighters > 0) {
            return false;
        }

        // exactly 0 nearby. check if we could have been rushed
        // TODO: even if this is true, we might be low on money. check our income rate and compute when to start saving
        // building anything increases cooldown by 10
        return rc.getRoundNum() + 10 > earliestRushTurn;
    }

    private boolean canAddNewTree() throws GameActionException {
        if (rc.getTeamBullets() < GameConstants.BULLET_TREE_COST) {
            return false;
        }

        if (moreEfficientToNotBuildTree()) {
            return false;
        }

        // check if there's space for another tree
        // for now, use a static tree pattern. in the future, it might help to dynamically fill gaps
        int numSlots = 0;
        for (int i = 0; i < NUM_TREES_PER_GARDENER; i++) {
            if (treeIds[i] == -1) {
                if (!rc.canMove(plantingPositions[i])) {
                    continue;
                }
                MapLocation treePosition = computeTreePosition(i);
                if (rc.onTheMap(treePosition, GameConstants.BULLET_TREE_RADIUS)
                        && !rc.isCircleOccupied(treePosition, GameConstants.BULLET_TREE_RADIUS)) {
                    nextTreeIdx = i;
                    numSlots++;
                    // leave one slot open
                    if (numSlots >= 2) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean moreEfficientToNotBuildTree() {
        if (Messaging.totalTreeIncome <= 0.0001f) {
            return false;
        }

        // if we're close to winning, it might be more efficient to not build anything, since trees take a while to
        // mature.
        // While it's difficult to estimate the payoff of gardeners and military, trees are easy with optimistic
        // assumptions about tree health).
        // ...for some reason, turns to grow and bullet tree max income aren't game constants...
        float turnsForTreeToBreakEven = 80f + GameConstants.BULLET_TREE_COST / 1f;

        float turnsToWin = estimateTurnsToWin();

        return turnsForTreeToBreakEven > turnsToWin;
    }
}
