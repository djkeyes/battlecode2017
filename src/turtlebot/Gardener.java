package turtlebot;

import battlecode.common.*;

strictfp class Gardener extends RobotPlayer {


    // Changing the values 7 <= n <= 10 doesn't seems to have a significant effect.
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

    static int earliestRushTurn;

    static int turnsAlive;
    static boolean isStationary = false;

    static void run() throws GameActionException {
        computeEarliestRush();
        turnsAlive = 0;

        while (true) {
            updateNearby();
            Messaging.tryGetUnitCounts();

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
                        tryBuildRobot();
                    }
                } else {
                    tryReturnToCenter();
                }
            }

            // maybe if we haven't built any trees at all, we should dodge bullets?

            tryShakeNearby();

            tryWateringNearby();

            donateExcessVictoryPoints();

            if (Messaging.shouldSendHeartbeat()) {
                examineAdjacentTrees();
                Messaging.sendHeartbeatSignal(0, 1, 0, isMaxed, adjacentTreeIncome);
            }
            // if we build a tree, we could also send an update immediately. however, trees take a long time to grow, so
            // the information might not be very actionable.

            turnsAlive++;
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

    static void computeEarliestRush() {
        // compare our position to all enemy archons.
        // it takes 1 turn to build a gardener, then one turn to build a scout, then 20 turns for the scout to finish
        // we can ignore the 20 though, because it also takes 20 minutes for us to build a unit
        int scoutCompletionTurn = 1 + 1;
        float minDist = Float.POSITIVE_INFINITY;
        MapLocation[] archonLocs = rc.getInitialArchonLocations(them);
        for (MapLocation loc : archonLocs) {
            float dist = rc.getLocation().distanceTo(loc);
            if (dist < minDist) {
                minDist = dist;
            }
        }

        // let's be pessimistic: the archon could build the gardener toward us, and the gardener could build the
        // scout toward us
        minDist -= RobotType.ARCHON.bodyRadius + 2 * RobotType.GARDENER.bodyRadius + RobotType.SCOUT.bodyRadius;
        // when do we want to have our defence?
        // when they see us? when we see them? when they're next to us?
        minDist -= RobotType.SCOUT.sensorRadius;

        int travelTurns = (int) StrictMath.ceil(minDist / RobotType.SCOUT.strideRadius);

        earliestRushTurn = scoutCompletionTurn + travelTurns;
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
        // for this, only check nearby units

        // this should already be plenty of defenders. hopefully they're close enough.
        // TODO(daniel): maybe this should be a ratio, like gardeners-per-lumberjack
        if(Messaging.lumberjackCount >= 6){
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

    static boolean canAddNewTree() throws GameActionException {
        if (rc.getTeamBullets() < GameConstants.BULLET_TREE_COST) {
            return false;
        }

        if (moreEfficientToNotBuildTree()) {
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

    static boolean tryBuildRobot() throws GameActionException {
        // TODO check locations in a more structured way
        // for example, check nearby units and detect free space. alternatively, since trees are in a pattern, try
        // building in holes in the pattern.

        if (rc.getTeamBullets() < typeToBuild.bulletCost) {
            return false;
        }

        int numRetries = 20;
        for (int i = 0; i < numRetries; i++) {
            Direction dir = randomDirection();
            if (rc.canBuildRobot(typeToBuild, dir)) {
                rc.buildRobot(typeToBuild, dir);
                return true;
            }
        }
        return false;
    }

    static void tryReturnToCenter() throws GameActionException {
        if (rc.getLocation() != centerPosition && rc.canMove(centerPosition)) {
            rc.move(centerPosition);
        }
    }

    static void examineAdjacentTrees() throws GameActionException {
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

        int vpLeft = GameConstants.VICTORY_POINTS_TO_WIN - rc.getTeamVictoryPoints();
        float defaultIncome = StrictMath.max(0f, GameConstants.ARCHON_BULLET_INCOME
                - GameConstants.BULLET_INCOME_UNIT_PENALTY * StrictMath.max(100f, rc.getTeamBullets()));
        float bulletsPerTurn = Messaging.totalTreeIncome + defaultIncome;
        float turnsToWin = (vpLeft * 10 - rc.getTeamBullets()) / bulletsPerTurn;

        return turnsForTreeToBreakEven > turnsToWin;
    }

    static boolean needToMoveAwayFromPack() {
        // check if any gardeners are less than 2*bodyRadius + 4*bulletTreeRadius away
        // in fact, we actually need more, but ignore that for now
        for (RobotInfo ally : alliesInSignt) {
            if (ally.type == RobotType.GARDENER || ally.type == RobotType.ARCHON) {
                return true;
            }

            // because these are in sorted order, we can terminate early
            if (ally.location.distanceTo(rc.getLocation())
                    > 2 * RobotType.GARDENER.bodyRadius + 4 * GameConstants.BULLET_TREE_RADIUS + 0.5f) {
                break;
            }
        }

        // TODO(daniel): check for map edge
        // I'm not sure if moving away from the edge will actually help though, since that indicates that things are
        // pretty crowded already.
        return false;
    }

    static void moveAwayFromPack() throws GameActionException {
        // apply a "force"
        float K = 50.0f;
        float fx = 0f;
        float fy = 0f;
        for (RobotInfo ally : alliesInSignt) {
            if (ally.type == RobotType.GARDENER || ally.type == RobotType.ARCHON) {
                float distSq = ally.location.distanceSquaredTo(rc.getLocation());
                Direction dir = ally.location.directionTo(rc.getLocation());
                fx += dir.getDeltaX(K) / distSq;
                fy += dir.getDeltaY(K) / distSq;
            }
        }

        float mag = (float) StrictMath.sqrt(fx * fx + fy * fy);
        Direction dir = new Direction(fx, fy);

        if (mag > type.strideRadius) {
            mag = type.strideRadius;
        }

        tryMove(dir, mag, 10, 10);
    }
}
