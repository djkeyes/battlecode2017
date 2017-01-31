package combinedstrategies;

import battlecode.common.GameActionException;

public interface TwoArgBooleanMethod<S, T> {
    boolean invoke(S arg0, T arg1) throws GameActionException;
}
