package combinedstrategies;

import battlecode.common.GameActionException;

public interface SingleArgBooleanMethod<T> {
    boolean invoke(T arg) throws GameActionException;
}
