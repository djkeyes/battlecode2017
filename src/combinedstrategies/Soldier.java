package combinedstrategies;

import battlecode.common.GameActionException;

public class Soldier extends RobotPlayer implements RobotHandler {

    @Override
    public void init() throws GameActionException {

    }

    @Override
    public void onLoop() throws GameActionException {
        if (!tryDodgeBullets(7000)) {
            if(!tryMoveTowardEnemy(7500)) {
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
