package combinedstrategies;

import battlecode.common.GameActionException;

interface RobotHandler {
    void init() throws GameActionException;
    void onLoop() throws GameActionException;
    void reportUnitCount() throws GameActionException;
    
}
