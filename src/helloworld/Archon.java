package helloworld;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;

public strictfp class Archon extends RobotPlayer {

    static int gardenersBuilt = 0;

    static void run() throws GameActionException {
        while (true) {
            // Generate a random direction
            Direction dir = randomDirection();

            // Randomly attempt to build a gardener in this direction
            if (rc.canHireGardener(dir) && (gardenersBuilt < 10 || rc.getRobotCount() < 10)) {
                rc.hireGardener(dir);
                gardenersBuilt++;
            }

            // Move randomly
            tryMove(randomDirection(), 20, 10);

            tryShakeNearby();

            tryTrivialWin();

            Clock.yield();
        }
    }

}
