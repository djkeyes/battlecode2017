package combinedstrategies;

import battlecode.common.RobotType;
import battlecode.common.Team;

public class BuildOrder extends RobotPlayer{

    /**
     * The choice of the first few units is very important. If the round num is low enough, check nextToBuild instead
     * of building adaptively.
     * @return true if nextToBuild() to be called
     */
	static RobotType[] ScScSoTL_BUILD_ORDER = {
			RobotType.GARDENER,RobotType.SCOUT,RobotType.SCOUT,RobotType.SOLDIER,null,RobotType.LUMBERJACK
	};

	static RobotType[] SoTScTL_BUILD_ORDER = {
			RobotType.GARDENER,RobotType.SOLDIER,null,RobotType.SCOUT, null, RobotType.LUMBERJACK
	};

	static RobotType[] SoTSoTL_BUILD_ORDER = {
			RobotType.GARDENER,null,RobotType.SOLDIER,RobotType.SOLDIER, null, RobotType.LUMBERJACK
	};

	static RobotType[] SoTTSoL_BUILD_ORDER = {
			RobotType.GARDENER,null,null,RobotType.SOLDIER,RobotType.SOLDIER, RobotType.LUMBERJACK
	};

	static RobotType[] SoSoTTL_BUILD_ORDER = {
			RobotType.GARDENER,RobotType.SOLDIER,RobotType.SOLDIER, null, null, RobotType.LUMBERJACK
	};
	static RobotType[] SoSoSoTTL_BUILD_ORDER = {
			RobotType.GARDENER,RobotType.SOLDIER,RobotType.SOLDIER,RobotType.SOLDIER, null, null, RobotType.LUMBERJACK
	};

	static RobotType[] LTSoTSc_BUILD_ORDER = {
			RobotType.GARDENER,RobotType.LUMBERJACK,null,RobotType.SOLDIER, null, RobotType.SCOUT
	};

	static RobotType[] LSoTTSc_BUILD_ORDER = {
			RobotType.GARDENER,RobotType.LUMBERJACK,RobotType.SOLDIER, null, null, RobotType.SCOUT
	};

	static RobotType[] TTSoTScTL_BUILD_ORDER = {
			RobotType.GARDENER,null,null,RobotType.SOLDIER,null,RobotType.SCOUT, null, RobotType.LUMBERJACK
	};

	static RobotType[] TTTSoTScTL_BUILD_ORDER = {
			RobotType.GARDENER,null,null,null,RobotType.SOLDIER,null,RobotType.SCOUT, null, RobotType.LUMBERJACK
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
				initialBuildOrder = SoTScTL_BUILD_ORDER;
				break;
			case LUMBERJACK_FIRST:
				initialBuildOrder = LTSoTSc_BUILD_ORDER;
				break;
			case ECONOMIC:
				initialBuildOrder = TTSoTScTL_BUILD_ORDER;
				break;
		}


		String param;
		if (us == Team.A) {
			param = System.getProperty("bc.testing.team-a-param");
		} else {
			param = System.getProperty("bc.testing.team-b-param");
		}
		if (param != null) {
			switch(param){
				case "0":
					initialBuildOrder = ScScSoTL_BUILD_ORDER;
					break;
				case "1":
					initialBuildOrder = SoTScTL_BUILD_ORDER;
					break;
				case "2":
					initialBuildOrder = SoTSoTL_BUILD_ORDER;
					break;
				case "3":
					initialBuildOrder = SoTTSoL_BUILD_ORDER;
					break;
				case "4":
					initialBuildOrder = SoSoTTL_BUILD_ORDER;
					break;
				case "5":
					initialBuildOrder = SoSoSoTTL_BUILD_ORDER;
					break;
				case "6":
					initialBuildOrder = LTSoTSc_BUILD_ORDER;
					break;
				case "7":
					initialBuildOrder = LSoTTSc_BUILD_ORDER;
					break;
				case "8":
					initialBuildOrder = TTSoTScTL_BUILD_ORDER;
					break;
				case "9":
					initialBuildOrder = TTTSoTScTL_BUILD_ORDER;
					break;
			}
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
