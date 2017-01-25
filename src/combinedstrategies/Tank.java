package combinedstrategies;

import battlecode.common.GameActionException;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;

public class Tank extends RobotPlayer implements RobotHandler {

    @Override
    public void init() throws GameActionException {

    }

    @Override
    public void onLoop() throws GameActionException {
        // Tank movement is hard, because you can accidentally destroy your own trees.
        // for now, don't move.
        tryAttackEnemy(9000);
    }

    @Override
    public void reportUnitCount() throws GameActionException {
        if (Messaging.shouldSendHeartbeat()) {
            Messaging.sendHeartbeatSignal(0, 0, 1, 0, 0, 0, 0, 0f,0);
        }
    }

}
