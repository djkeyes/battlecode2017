package noop;

import battlecode.common.Clock;
import battlecode.common.RobotController;

public strictfp class RobotPlayer {

    @SuppressWarnings("unused")
    public static void run(RobotController rc) {
        while(true) Clock.yield();
    }

}
