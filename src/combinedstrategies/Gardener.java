package combinedstrategies;

import battlecode.common.*;

strictfp class Gardener extends RobotPlayer implements RobotHandler {

    private UnitConstuctionQueue inConstruction = new UnitConstuctionQueue(3);

    private boolean shouldBuildTree;
    private RobotType typeToBuild;
    private boolean builtUnit;

    static int earliestRushTurn;

    // TODO: experiment with other build strategies:
    // -align trees on a grid
    // -circle builds, but aligned on a grid

    static final int NUM_TREES_PER_GARDENER = 8;
    static final float TREE_OFFSET_EPSILON = 0.0001f;

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
        if (!isStationary && turnsAlive <= 10 && rc.getRoundNum() > 5 && needToMoveAwayFromPack()) {
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

        donateExcessVictoryPoints();

        turnsAlive++;
    }

    @Override
    public void reportUnitCount() throws GameActionException {
        inConstruction.dequeueUntil(rc.getRoundNum());
        if (Messaging.shouldSendHeartbeat()) {
            examineAdjacentTrees();
            Messaging.sendHeartbeatSignal(0, 1, inConstruction.numLumberjacks,
                    inConstruction.numScouts, inConstruction.numSoldiers, inConstruction.numTanks, isMaxed,
                    adjacentTreeIncome);
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
        // if we build a tree, we could also send an update immediately. however, trees take a long time to grow, so
        // the information might not be very actionable.
    }

    private MapLocation computeTreePosition(int treeIdx) {
        return plantingPositions[treeIdx].add(plantingDirs[treeIdx], type.bodyRadius
                + GameConstants.GENERAL_SPAWN_OFFSET + GameConstants.BULLET_TREE_RADIUS);
    }

    private void initializeTreePattern() throws GameActionException {
        if (treesInitialized) {
            return;
        }
        treesInitialized = true;

        centerPosition = rc.getLocation();

        // create a set of tree positions
        double r = GameConstants.BULLET_TREE_RADIUS;
        double theta = (NUM_TREES_PER_GARDENER - 2f) * Math.PI / 2f / NUM_TREES_PER_GARDENER;
        // dist to center of tree
        float d = (float) (r / StrictMath.cos(theta));
        // due to finite precision of floating point numbers, we offset the tree by a small epsilon
        float plantingDist = d - GameConstants.BULLET_TREE_RADIUS
                - GameConstants.GENERAL_SPAWN_OFFSET - type.bodyRadius + TREE_OFFSET_EPSILON;
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
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(type.bodyRadius + type.strideRadius, us);
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
            if (rc.onTheMap(buildLoc, typeToBuild.bodyRadius)
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
            if (needDefences || !canAddTree) {
                shouldBuildTree = false;
                if (numUnitsBuilt < 2 || numUnitsBuilt % 2 == 1) {
                    typeToBuild = RobotType.LUMBERJACK;
                } else {
                    typeToBuild = RobotType.SOLDIER;
                }
            } else if (canAddTree) {
                shouldBuildTree = true;
                typeToBuild = null;
            } /*else {
                shouldBuildTree = false;
                typeToBuild = null;
            }*/
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
