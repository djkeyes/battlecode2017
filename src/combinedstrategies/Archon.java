package combinedstrategies;

import battlecode.common.*;

strictfp class Archon extends RobotPlayer implements RobotHandler {

    // TODO(daniel): how do different values affect the outcome? I suspect not by much.
    static final float MIN_GARDENER_EFFICIENCY_TO_BUILD = 0.5f;

    private boolean builtGardener = false;

    private boolean amITheFirstArchon = false;
    private boolean amITheClosestArchon = false;
    private boolean anyTreesWithGifts;
    private float treeHealthNearby;

    static final int[] TURNS_TO_BREAK_EVEN = {Integer.MAX_VALUE, 231, 186, 175, 171, 171, 173, 176, 179, 183, 183};

    private int numArchonsPerTeam;
    private float minInterArchonDistance;

    @Override
    public void init() throws GameActionException {
        if (rc.getRoundNum() == 1) {
            if (Messaging.archonCount == 0) {
                amITheFirstArchon = true;
            }
            TreeInfo[] neutralTreesNearby = rc.senseNearbyTrees();

            anyTreesWithGifts = false;
            treeHealthNearby = 0f;

            for (TreeInfo tree : neutralTreesNearby) {
                if (tree.containedRobot != null) {
                    anyTreesWithGifts = true;
                }
                treeHealthNearby += tree.maxHealth;
            }

            minInterArchonDistance = Float.POSITIVE_INFINITY;
            MapLocation[] ourArchons = rc.getInitialArchonLocations(us);
            MapLocation[] theirArchons = rc.getInitialArchonLocations(them);
            MapLocation ourClosestLoc = null;
            for (MapLocation theirs : theirArchons) {
                for (MapLocation ours : ourArchons) {
                    float dist = theirs.distanceTo(ours);
                    if (dist < minInterArchonDistance) {
                        minInterArchonDistance = dist;
                        ourClosestLoc = ours;
                    }
                }
            }
            amITheClosestArchon = rc.getLocation().distanceTo(ourClosestLoc) < 0.1f;
            numArchonsPerTeam = ourArchons.length;

            if (anyTreesWithGifts) {
                Messaging.setHasGift(anyTreesWithGifts);
            }
            if (!amITheFirstArchon) {
                treeHealthNearby += Messaging.getNearbyTreeHealth();
            }
            Messaging.setNearbyTreeHealth(treeHealthNearby);

            // the first turn is a heartbeat turn, so unit counts aren't updated.
            // force a manual update (even though some information may only be partially correct)
            Messaging.getUnitCounts();
        }
    }

    @Override
    public void onLoop() throws GameActionException {

        if(rc.getRoundNum() <= 2){
            if(rc.getRoundNum() == 1){
                return;
            } else {
                int buildOrder;
                // TODO: determine these coefficients by doing a regression over win rates
                final float RUSH_DISTANCE_THRESHOLD = 37f;
                final float AVG_TREE_HEALTH_THRESHOLD = 4000f;
                if (amITheFirstArchon) {
                    // now that we have information, make a decision

                    if (Messaging.getHasGift()) {
                        // lumberjack first
                        buildOrder = BuildOrder.LUMBERJACK_FIRST;
                    } else if (minInterArchonDistance <= RUSH_DISTANCE_THRESHOLD) {
                        // soldier first
                        buildOrder = BuildOrder.SOLDIER_FIRST;
                    } else if (Messaging.getNearbyTreeHealth() / numArchonsPerTeam >= AVG_TREE_HEALTH_THRESHOLD) {
                        // lumberjack first
                        buildOrder = BuildOrder.LUMBERJACK_FIRST;
                    } else {
                        // double tree first
                        buildOrder = BuildOrder.ECONOMIC;
                    }
                    Messaging.broadcastInitialBuildOrder(buildOrder);
                } else {
                    buildOrder = Messaging.readInitialBuildOrder();
                }
                BuildOrder.setInitialBuildOrder(buildOrder);

                // on the first round of construction, different archons may have sensed different things.
                // If someone saw a tree with a gift, only build a lumberjack if you are that archon.
                // If it's a good idea to rush, only build a soldier if you're that archon.
                // If that's impossible (eg DigMeOut), don't do anything. Next turn another archon can pick up the slack.
                boolean shouldBuildFirst = false;
                if(buildOrder == BuildOrder.LUMBERJACK_FIRST && anyTreesWithGifts){
                    // there's gifts, and one is near us
                    shouldBuildFirst = true;
                } else if(buildOrder == BuildOrder.SOLDIER_FIRST && amITheClosestArchon){
                    // it's soldiers, and we're the closest
                    shouldBuildFirst = true;
                } else if (buildOrder == BuildOrder.ECONOMIC ||
                        (buildOrder == BuildOrder.LUMBERJACK_FIRST
                                && treeHealthNearby > AVG_TREE_HEALTH_THRESHOLD
                                && !Messaging.getHasGift())) {
                    // it's economic, or
                    // it's lumberjacks, and there's lots of trees near us (but no gifts)
                    shouldBuildFirst = true;
                }

                if (shouldBuildFirst) {
                    builtGardener = tryBuildGardener(10000);
                }
                // can't build? try moving and then building
                if (!builtGardener) {
                    tryMove(randomDirection(), 180, 50);
                    builtGardener = tryBuildGardener(18000);
                }
                return;
            }
        }


        // game plan:
        // -make gardeners if our build order decrees it
        // otherwise, we can move. here are some options:
        // -dodge bullets (always a good idea, can do others as a fallback)
        // -move toward other archons, for defence
        // -move away from gardeners, to reduce clutter
        // -stay still
        // staying still is the easiest to implement, so we'll do that for now

        if (Messaging.gardenerCount <= 15
                && (Messaging.gardenerCount < 4
                || Messaging.soldierCount + Messaging.lumberjackCount + Messaging.tankCount > 3 * Messaging.gardenerCount)) {
            builtGardener = tryBuildGardener(14000);
        }

        if (!tryDodgeBullets(16000)) {
            if (needToMoveAwayFromPack()) {
                // maybe we should always try to stay on the exterior of the convex hull of trees? otherwise we could
                // get stuck in a minimum.
                moveAwayFromPack();
            } else {
                tryMove(randomDirection(), 20, 10);
            }
        }


    }

    @Override
    public void reportUnitCount() throws GameActionException {
        if (Messaging.shouldSendHeartbeat()) {
            Messaging.sendHeartbeatSignal(1, builtGardener ? 1 : 0, 0, 0, 0, 0, 0, 0f);
        } else if (builtGardener) {
            Messaging.reportBuiltGardener();
            builtGardener = false;
        }
    }

    boolean tryBuildGardener(int maxBytecodes) throws GameActionException {
        if (!rc.isBuildReady()) {
            return false;
        }

        if (!BuildOrder.shouldFollowInitialBuildOrder()) {
            if (waitingForUndefendedGardener()) {
                return false;
            }
            if (moreEfficientToDoNothing()) {
                return false;
            }
            if (!maxedOutOnTrees()) {
                return false;
            }
        } else {
            if (BuildOrder.nextToBuild() != RobotType.GARDENER) {
                return false;
            }
        }

        // TODO: check adjacent units to find an unobstructed direction
        // we could also check for bullets, but we'd have to look 20 turns ahead.
        while(Clock.getBytecodeNum() < maxBytecodes){
            Direction dir = randomDirection();
            if (rc.canHireGardener(dir)) {
                rc.hireGardener(dir);
                return true;
            }
        }
        return false;
    }

    static boolean maxedOutOnTrees() {
        return Messaging.maxedGardenerCount >= Messaging.gardenerCount * MIN_GARDENER_EFFICIENCY_TO_BUILD;
    }

    static int lastTurnWaitingForDefence;
    static int turnsSpentWaitingForDefence;

    static boolean waitingForUndefendedGardener() {
        // if there's no military nearby, gardeners will try to build defenses
        // so if there's already a gardener and no military, don't build more gardeners
        // might need to set a messaging flag for this
        boolean isGardenerNearby = false;
        boolean isFighterNearby = false;
        for (RobotInfo ally : alliesInSignt) {
            if (ally.type == RobotType.GARDENER) {
                isGardenerNearby = true;
            } else if (ally.type.canAttack()) {
                isFighterNearby = true;
            }
        }

        boolean shouldWait = isGardenerNearby && !isFighterNearby;
        if (shouldWait && rc.getTeamBullets() >= 100f) {
            if (lastTurnWaitingForDefence != rc.getRoundNum() - 1) {
                turnsSpentWaitingForDefence = 0;
            }
            lastTurnWaitingForDefence = rc.getRoundNum();
            turnsSpentWaitingForDefence++;

            // the new gardener hasn't built anything. maybe he's stuck/obstructed/throwing exceptions?
            if (turnsSpentWaitingForDefence >= 10) {
                turnsSpentWaitingForDefence = 0;
                return false;
            }
        }
        return shouldWait;
    }

    // Gardeners get less efficient over time, due to crowding. Down-weight their future efficiency.
    // TODO: determine this value experimentally
    static final float FUTURE_GARDENER_EFFICIENCY = 0.8f;

    boolean moreEfficientToDoNothing() {
        // if we're close to winning, it might be more efficient to not build anything, since trees take a while to
        // mature.
        // Due to map layouts and enemy activity, it's difficult to compute exactly how much money per turn a
        // gardener will eventually yield, or how long he will take to finish all his trees.
        // However, in the optimal case (for both 4 and 5 trees), it takes 171 turns to pay for a gardener and his
        // trees.
        // instead, just estimate the #trees/gardener based on the reported income.
        if (Messaging.gardenerCount == 0 || Messaging.totalTreeIncome <= 0.0001f) {
            return false;
        }
        float estNumTrees = Messaging.totalTreeIncome / 1.0f;
        float estTreesPerGardener = estNumTrees / Messaging.gardenerCount * FUTURE_GARDENER_EFFICIENCY;
        int floor = (int) estTreesPerGardener;
        int ceil = (int) StrictMath.ceil(estTreesPerGardener);

        float estTurnsToBreakEven;
        if (floor <= 0) {
            estTurnsToBreakEven = TURNS_TO_BREAK_EVEN[1];
        } else if (ceil >= TURNS_TO_BREAK_EVEN.length) {
            estTurnsToBreakEven = TURNS_TO_BREAK_EVEN[TURNS_TO_BREAK_EVEN.length - 1];
        } else {
            // lerp
            float alpha = estTreesPerGardener - floor;
            estTurnsToBreakEven = TURNS_TO_BREAK_EVEN[floor] * (1f - alpha) + TURNS_TO_BREAK_EVEN[ceil] * alpha;
        }
        float turnsToWin = estimateTurnsToWin();

        return estTurnsToBreakEven > turnsToWin;
    }

}