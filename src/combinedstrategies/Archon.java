package combinedstrategies;

import battlecode.common.*;

strictfp class Archon extends RobotPlayer implements RobotHandler {

    // TODO(daniel): how do different values affect the outcome? I suspect not by much.
    static final float MIN_GARDENER_EFFICIENCY_TO_BUILD = 0.5f;

    private boolean builtGardener = false;

    static final int[] TURNS_TO_BREAK_EVEN = {Integer.MAX_VALUE, 231, 186, 175, 171, 171, 173, 176, 179, 183, 183};

    @Override
    public void init() throws GameActionException {
        // the first turn is a heartbeat turn, so unit counts aren't updated.
        // force a manual update (even though some information may only be partially correct)
        if (rc.getRoundNum() == 1) {
            Messaging.getUnitCounts();
        }
    }

    @Override
    public void onLoop() throws GameActionException {

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
            builtGardener = tryBuildGardener();
        }

        if (!tryDodgeBullets()) {
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

    boolean tryBuildGardener() throws GameActionException {
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
        // TODO: determine numRetries adaptively based on num bytecodes left
        int numRetries = 10;
        for (int i = 0; i < numRetries; i++) {
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