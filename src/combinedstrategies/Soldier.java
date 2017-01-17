package combinedstrategies;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.RobotInfo;

public class Soldier extends RobotPlayer implements RobotHandler {

    @Override
    public void init() throws GameActionException {

    }

    @Override
    public void onLoop() throws GameActionException {
        if (!tryDodgeBullets()) {
            if(!tryMoveTowardEnemy(6000)) {
                tryMoveTowardEnemyArchons(8000);
            }
        }

        tryAttackEnemy();
    }

    @Override
    public void reportUnitCount() throws GameActionException {
        if (Messaging.shouldSendHeartbeat()) {
            Messaging.sendHeartbeatSignal(0, 0, 1, 0, 0, 0, 0, 0f);
        }
    }



}
