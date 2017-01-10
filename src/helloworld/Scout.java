package helloworld;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;

strictfp class Scout extends RobotPlayer {

    static void run() throws GameActionException {
        while (true) {

            if(!tryDodge()){
                tryMoveTowardEnemyArchons();
            }

            tryAttackEnemy();

            tryShakeNearby();

            tryTrivialWin();

            Clock.yield();
        }
    }

    static boolean tryDodge() throws GameActionException {
        // TODO: actually take bullets into account
        if(rc.senseNearbyRobots(7.0f, them).length > 0){
            // Move randomly
            tryMove(randomDirection(), 20, 10);
            return true;
        }
        return false;
    }

}
