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
			RobotType.GARDENER,null,RobotType.SOLDIER,null,RobotType.SCOUT
	};
	
	static RobotType[] InitialBuildOrder = null; 
    static boolean shouldFollowInitialBuildOrder(){
        return rc.getRoundNum() < 50;
    }

    /**
     * @return the next robot type to build, or null if a tree should be built
     */
    static RobotType nextToBuild() {
        // currently: Ga -> Sc -> So -> trees
        // a better strategy might be:
        // Ga -> tree -> So -> tree -> Sc
        // or
        // Ga -> tree -> So -> Sc
        // since that is more economic, and gives the scout a later-execution bonus
        // TODO: rewrite this class to make testing easier
    	InitialBuildOrder = AGGRESIVE_INITIAL_BUILD_ORDER;
    	if (Messaging.unitBuiltCount < InitialBuildOrder.length){
    		return InitialBuildOrder[Messaging.unitBuiltCount];
    	}
    	else {
    		return null;
    		}    
    	}

}
