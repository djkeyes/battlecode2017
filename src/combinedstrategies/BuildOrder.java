package combinedstrategies;

import battlecode.common.RobotType;

public class BuildOrder extends RobotPlayer{

    /**
     * The choice of the first few units is very important. If the round num is low enough, check nextToBuild instead
     * of building adaptively.
     * @return true if nextToBuild() to be called
     */
	static RobotType[] AGGRESIVE_INITIAL_BUILD_ORDER = {
			RobotType.GARDENER,RobotType.SCOUT,RobotType.SCOUT,RobotType.SOLDIER,null,RobotType.LUMBERJACK
	};

	static RobotType[] SOLDIER_FIRST_INITIAL_BUILD_ORDER = {
			RobotType.GARDENER,RobotType.SOLDIER,null,RobotType.SCOUT, null, RobotType.LUMBERJACK
	};

	static RobotType[] LUMBERJACK_FIRST_INITIAL_BUILD_ORDER = {
			RobotType.GARDENER,RobotType.LUMBERJACK,null,RobotType.SOLDIER, null, RobotType.SCOUT
	};

	static RobotType[] ECONOMIC_INITIAL_BUILD_ORDER = {
			RobotType.GARDENER,null,null,RobotType.SOLDIER,null,RobotType.SCOUT, null, RobotType.LUMBERJACK
	};
	
	static RobotType[] initialBuildOrder = null;
    static boolean shouldFollowInitialBuildOrder(){
        return rc.getRoundNum() < 100;
    }

    static final int SOLDIER_FIRST = 0;
    static final int LUMBERJACK_FIRST = 1;
    static final int ECONOMIC = 2;
    static void setInitialBuildOrder(int orderName){
    	switch(orderName){
			case SOLDIER_FIRST:
				initialBuildOrder = SOLDIER_FIRST_INITIAL_BUILD_ORDER;
				break;
			case LUMBERJACK_FIRST:
				initialBuildOrder = LUMBERJACK_FIRST_INITIAL_BUILD_ORDER;
				break;
			case ECONOMIC:
				initialBuildOrder = ECONOMIC_INITIAL_BUILD_ORDER;
				break;
		}

	}

    /**
     * @return the next robot type to build, or null if a tree should be built
     */
    static RobotType nextToBuild() {
        // currently: Ga -> Sc -> Sc -> So -> tree -> LJ for rush-friendly map
    	// economic 
    	if (Messaging.itemBuiltCount < initialBuildOrder.length){
    		return initialBuildOrder[Messaging.itemBuiltCount];
    	}
    	else {
    		return null;
    		}    
    	}

}
