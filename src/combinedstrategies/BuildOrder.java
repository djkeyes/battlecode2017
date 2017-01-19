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
	
	static RobotType[] ECONOMIC_INITIAL_BUILD_ORDER = {
			RobotType.GARDENER,null,RobotType.SOLDIER,null,RobotType.SCOUT, null, RobotType.LUMBERJACK
	};
	
	static RobotType[] InitialBuildOrder = null; 
    static boolean shouldFollowInitialBuildOrder(){
        return rc.getRoundNum() < 100;
    }

    /**
     * @return the next robot type to build, or null if a tree should be built
     */
    static RobotType nextToBuild() {
        // currently: Ga -> Sc -> Sc -> So -> tree -> LJ for rush-friendly map
    	// economic 
    	InitialBuildOrder = ECONOMIC_INITIAL_BUILD_ORDER;
    	if (Messaging.itemBuiltCount < InitialBuildOrder.length){
    		return InitialBuildOrder[Messaging.itemBuiltCount];
    	}
    	else {
    		return null;
    		}    
    	}

}
