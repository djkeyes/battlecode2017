package turtlebot;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;

public strictfp class Archon extends RobotPlayer {

    static void run() throws GameActionException {
        while (true) {
            updateNearby();
            Messaging.getUnitCounts();

            tryBuildGardener();

            if(!tryDodgeBullets()){
                // maybe we should try to move away from large groups?
                // or at least try to move 1 unit away from gardeners?
                tryMove(randomDirection(), 20, 10);
            }

            tryShakeNearby();

            donateExcessVictoryPoints();

            Messaging.tryReportUnitCount();

            Clock.yield();
        }
    }

    static void tryBuildGardener() throws GameActionException {
        if(!rc.isBuildReady()){
            return;
        }

        // trees are cheaper than gardeners, so we could just ignore this, but sometimes we'll end up with 2
        // gardeners at the beginning
        if(!maxedOutOnTrees()){
            return;
        }

        // TODO: check adjacent units to find an unobstructed direction
        Direction dir = randomDirection();
        if(rc.canHireGardener(dir)){
            rc.hireGardener(dir);
        }
    }

    static boolean maxedOutOnTrees(){
        // TODO: have each gardener report whether they are at tree max capacity
        return true;
    }


}
