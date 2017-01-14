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
    static int nextTreeIdx;

    static void run() throws GameActionException {
        initializeTreePattern();

        while (true) {
            updateNearby();
            Messaging.getUnitCounts();

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

            reportAdjacentTrees();
            Messaging.tryReportUnitCount();

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
                MapLocation treePosition = plantingPositions[i].add(plantingDirs[i], type.bodyRadius
                        + GameConstants.GENERAL_SPAWN_OFFSET + GameConstants.BULLET_TREE_RADIUS);
                if (rc.onTheMap(treePosition, GameConstants.BULLET_TREE_RADIUS)
                        && !rc.isCircleOccupied(treePosition, GameConstants.BULLET_TREE_RADIUS)) {
                    nextTreeIdx = i;
                    return true;
                }
            }
        }
        return false;
    }

    static void buildTree() throws GameActionException {
        rc.move(plantingPositions[nextTreeIdx]);
        rc.plantTree(plantingDirs[nextTreeIdx]);
        TreeInfo newTree = rc.senseTreeAtLocation(plantingPositions[nextTreeIdx].add(plantingDirs[nextTreeIdx]));
        treeIds[nextTreeIdx] = newTree.getID();
    }

    static void buildRobot() {
        // TODO
    }

    static void tryReturnToCenter() throws GameActionException {
        if (rc.getLocation() != centerPosition) {
            rc.move(centerPosition);
        }
    }

    static void reportAdjacentTrees() {
        // TODO
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
