package turtlebot;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;

public strictfp class Archon extends RobotPlayer {

    // TODO(daniel): how do different values affect the outcome? I suspect not by much.
    static final float MIN_GARDENER_EFFICIENCY_TO_BUILD = 0.5f;

    // turns for a gardener to pay for himself after building i trees
    static final int[] TURNS_TO_BREAK_EVEN = {Integer.MAX_VALUE, 231, 186, 175, 171, 171, 173, 176, 179, 183, 183};

    static void run() throws GameActionException {
        // the first turn is a heartbeat turn, so unit counts aren't updated.
        // force a manual update (even though some information may only be partially correct)
        if (rc.getRoundNum() == 1) {
            Messaging.getUnitCounts();
        }

        while (true) {
            updateNearby();
            Messaging.tryGetUnitCounts();

            boolean builtGardener = tryBuildGardener();

            if (!tryDodgeBullets()) {
                // maybe we should try to move away from large groups?
                // or at least try to move 1 unit away from gardeners?
                tryMove(randomDirection(), 20, 10);
            }

            tryShakeNearby();

            donateExcessVictoryPoints();

            if (Messaging.shouldSendHeartbeat()) {
                Messaging.sendHeartbeatSignal(1, builtGardener ? 1 : 0, 0, 0);
            } else if (builtGardener) {
                Messaging.reportUnitBuilt(0, 1, 0, 0f);
            }

            Clock.yield();
        }
    }

    static boolean tryBuildGardener() throws GameActionException {
        if (!rc.isBuildReady()) {
            return false;
        }

        // trees are cheaper than gardeners, so we could just ignore this, but sometimes we'll end up with 2
        // gardeners at the beginning
        if (!maxedOutOnTrees()) {
            return false;
        }

        if (moreEfficientDoNothing()) {
            return false;
        }

        // TODO: check adjacent units to find an unobstructed direction
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

    static boolean moreEfficientDoNothing() {
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
        float estTreesPerGardener = estNumTrees / Messaging.gardenerCount;
        int floor = (int) estTreesPerGardener;
        int ceil = (int) StrictMath.ceil(estTreesPerGardener);

        float estTurnsToBreakEven;
        // not sure why this would ever happen
        if (floor <= 0) {
            estTurnsToBreakEven = TURNS_TO_BREAK_EVEN[0];
        } else if (ceil >= TURNS_TO_BREAK_EVEN.length) {
            estTurnsToBreakEven = TURNS_TO_BREAK_EVEN[TURNS_TO_BREAK_EVEN.length - 1];
        } else {
            // lerp
            float alpha = estTreesPerGardener - floor;
            estTurnsToBreakEven = TURNS_TO_BREAK_EVEN[floor] * (1f - alpha) + TURNS_TO_BREAK_EVEN[ceil] * alpha;
        }

        int vpLeft = GameConstants.VICTORY_POINTS_TO_WIN - rc.getTeamVictoryPoints();
        float defaultIncome = StrictMath.max(0f, GameConstants.ARCHON_BULLET_INCOME
                - GameConstants.BULLET_INCOME_UNIT_PENALTY * StrictMath.max(100f, rc.getTeamBullets()));
        float bulletsPerTurn = Messaging.totalTreeIncome + defaultIncome;
        float turnsToWin = (vpLeft*GameConstants.BULLET_EXCHANGE_RATE - rc.getTeamBullets())/bulletsPerTurn;

        return estTurnsToBreakEven > turnsToWin;
    }
}
