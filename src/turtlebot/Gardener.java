package turtlebot;

import battlecode.common.*;

strictfp class Gardener extends RobotPlayer {


    // TODO: we could experiment with other values 7 <= n <= 10. Alternatively, each gardener could dynamically
    // this implementation doesn't hand n=6, because it packs trees more tightly than GameConstants
    // .GENERAL_SPAWN_OFFSET--but that could be added as a special case.
    static final int NUM_TREES_PER_GARDENER = 8;
    static final float TREE_OFFSET_EPSILON = 0.0001f;
    static boolean shouldBuildTree;
    static RobotType typeToBuild;
    static MapLocation centerPosition;
    static boolean treesInitialized = false;
    static MapLocation[] plantingPositions = new MapLocation[NUM_TREES_PER_GARDENER];
    static Direction[] plantingDirs = new Direction[NUM_TREES_PER_GARDENER];
    static int[] treeIds = new int[NUM_TREES_PER_GARDENER];
    static int[] treeConstructionTurn = new int[NUM_TREES_PER_GARDENER];
    static int nextTreeIdx;

    static int isMaxed = 0;
    static float adjacentTreeIncome = 0f;

    static void run() throws GameActionException {
        initializeTreePattern();

        while (true) {
            updateNearby();
            Messaging.tryGetUnitCounts();

            // TODO: unless we're the very first gardener, maybe we should move away from nearby units first.
            // or at least from nearby gardeners
            // or maybe we should have a messaging flag. if the map is very small/crowded, don't bother spreading.

            // attempt to build a tree or unit
            if (rc.getBuildCooldownTurns() == 0) {
                determineWhatToBuild();
                if (shouldBuildTree) {
                    buildTree();
                } else if (typeToBuild != null) {
                    buildRobot();
                }
            } else {
                tryReturnToCenter();
            }

            // maybe if we haven't built any trees at all, we should dodge bullets?

            tryShakeNearby();

            tryWateringNearby();

            donateExcessVictoryPoints();

            if (Messaging.shouldSendHeartbeat()) {
                examineAdjacentTrees();
                Messaging.sendHeartbeatSignal(0, 1, isMaxed, adjacentTreeIncome);
            }
            // if we build a tree, we could also send an update immediately. however, trees take a long time to grow, so
            // the information might not be very actionable.

            Clock.yield();
        }
    }

    static void initializeTreePattern() throws GameActionException {
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

    static void determineWhatToBuild() throws GameActionException {
        if (needDefences()) {
            shouldBuildTree = false;
            typeToBuild = RobotType.LUMBERJACK;
        } else if (canAddNewTree()) {
            shouldBuildTree = true;
            typeToBuild = null;
        } else {
            shouldBuildTree = false;
            typeToBuild = null;
        }
    }

    static boolean needDefences() {
        // TODO: determine a good rule for building a military
        // ideas:
        // - the enemy outnumbers us OR we have zero units, but scouts could reach us by the time our units are finished
        return false;
    }

    static boolean canAddNewTree() throws GameActionException {
        if (rc.getTeamBullets() < GameConstants.BULLET_TREE_COST) {
            return false;
        }
        // check if there's space for another tree
        // for now, use a static tree pattern. in the future, it might help to dynamically fill gaps
        for (int i = 0; i < NUM_TREES_PER_GARDENER; i++) {
            if (treeIds[i] == -1) {
                if (!rc.canMove(plantingPositions[i])) {
                    continue;
                }
                MapLocation treePosition = computeTreePosition(i);
                if (rc.onTheMap(treePosition, GameConstants.BULLET_TREE_RADIUS)
                        && !rc.isCircleOccupied(treePosition, GameConstants.BULLET_TREE_RADIUS)) {
                    nextTreeIdx = i;
                    return true;
                }
            }
        }
        return false;
    }

    static MapLocation computeTreePosition(int treeIdx) {
        return plantingPositions[treeIdx].add(plantingDirs[treeIdx], type.bodyRadius
                + GameConstants.GENERAL_SPAWN_OFFSET + GameConstants.BULLET_TREE_RADIUS);
    }

    static void buildTree() throws GameActionException {
        rc.move(plantingPositions[nextTreeIdx]);
        rc.plantTree(plantingDirs[nextTreeIdx]);
        TreeInfo newTree = rc.senseTreeAtLocation(computeTreePosition(nextTreeIdx));
        treeIds[nextTreeIdx] = newTree.getID();
        treeConstructionTurn[nextTreeIdx] = rc.getRoundNum();
    }

    static void buildRobot() {
        // TODO
    }

    static void tryReturnToCenter() throws GameActionException {
        if (rc.getLocation() != centerPosition && rc.canMove(centerPosition)) {
            rc.move(centerPosition);
        }
    }

    static void examineAdjacentTrees() throws GameActionException {
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
                if (rc.onTheMap(treePosition, GameConstants.BULLET_TREE_RADIUS)
                        && !rc.isCircleOccupied(treePosition, GameConstants.BULLET_TREE_RADIUS)) {
                    numTreesPossible++;
                }
            }

        }
        adjacentTreeIncome = GameConstants.BULLET_TREE_BULLET_PRODUCTION_RATE * totalTreeHealth;

        // 1-tree leeway
        if (numTrees >= numTreesPossible - 1) {
            isMaxed = 1;
        } else {
            isMaxed = 0;
        }
    }

    static void tryWateringNearby() throws GameActionException {
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
}
