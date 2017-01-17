package combinedstrategies;

import battlecode.common.RobotType;

import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * Manage a list of units that have recently been built by the current robot.
 * <p>
 * Archons can hire gardeners (although that's sort of moot, since gardeners take 1 turns to spawn), gardeners can
 * build robots, and lumberjacks can chop() trees to produce neutral units.
 * <p>
 * Due to cooldowns, this means gardeners can have at most 2 things in the queue, and lumberjacks can have
 * at most 20 (but probably fewer).
 */
public class UnitConstuctionQueue {
    public int size;
    public int numArchons = 0;
    public int numGardeners = 0;
    public int numScouts = 0;
    public int numSoldiers = 0;
    public int numTanks = 0;
    public int numLumberjacks = 0;
    private int head, tail, capacity;
    private int[] turnCompleted;
    private RobotType[] robotTypes;

    public UnitConstuctionQueue(int capacity) {
        size = head = tail = 0;
        this.capacity = capacity;

        turnCompleted = new int[capacity];
        robotTypes = new RobotType[capacity];
    }

    void enqueue(RobotType type, int completionTurn) {
        turnCompleted[head] = completionTurn;
        robotTypes[head] = type;
        head++;
        head %= capacity;
        size++;

        // increment the type just added
        switch (type) {
            case ARCHON:
                numArchons++;
                break;
            case GARDENER:
                numGardeners++;
                break;
            case SCOUT:
                numScouts++;
                break;
            case SOLDIER:
                numSoldiers++;
                break;
            case TANK:
                numTanks++;
                break;
            case LUMBERJACK:
                numLumberjacks++;
                break;
        }
    }

    void dequeueUntil(int curTurn) {
        while (size > 0 && curTurn > turnCompleted[tail]) {
            // decrement the type we're removing
            switch (robotTypes[tail]) {
                case ARCHON:
                    numArchons--;
                    break;
                case GARDENER:
                    numGardeners--;
                    break;
                case SCOUT:
                    numScouts--;
                    break;
                case SOLDIER:
                    numSoldiers--;
                    break;
                case TANK:
                    numTanks--;
                    break;
                case LUMBERJACK:
                    numLumberjacks--;
                    break;
            }

            tail++;
            tail %= capacity;
            size--;
        }
    }

}
