package bytecodes;

import battlecode.common.*;

import java.util.*;

import static bytecodes.Assert.assertEquals;

/**
 * Daniel:
 * It might be nice to have some kind of automated test / generator for this information. I'm not sure how to inject
 * the code profiler into junit tests or how to mock the various battlecode classes, so instead this is a
 * full-fledged player.
 *
 * You can run it with, for example:
 *
 * gradle run -PteamA=bytecodes -PteamB=noop -Pmaps=shrine
 */
public strictfp class RobotPlayer {

    public static void runTests() {
        testSimpleAssertion();
        timeNoop();
        timeDeclarePrimitive();
        timeAssignPrimitive();
        timeCreateString();

        timeSimpleForLoop();
        Clock.yield();

        timeCreateArray();
        Clock.yield();

        timeAllocateObject();
        timeAllocateCollections();
        timeSimpleLambda();
    }

    @SuppressWarnings("unused")
    public static void run(RobotController rc) {
        // TODO(daniel): it would be nice to test the cost of static initialization
        int bytecodesAtStart = Clock.getBytecodeNum();
        System.out.println("bytecodes at start of tests: " + bytecodesAtStart);

        try {
            runTests();

            System.out.println("All tests passed!");
        } catch (AssertionError e1) {
            System.out.println("Test(s) failed!");
            e1.printStackTrace();
        } catch (Exception e2) {
            System.out.println("Caught unknown exception!");
            e2.printStackTrace();
        }

        while (true) ;
    }

    public static void testSimpleAssertion() {
        assert (true);
    }

    public static void timeNoop() {
        int before, after;
        before = Clock.getBytecodeNum();
        after = Clock.getBytecodeNum();
        int expected = 1;
        int actual = after - before;
        assertEquals(expected, actual);
    }

    public static void timeDeclarePrimitive() {
        int before, after;
        before = Clock.getBytecodeNum();
        int x;
        after = Clock.getBytecodeNum();
        int expected = 1;
        int actual = after - before;
        assertEquals(expected, actual);
    }

    public static void timeAssignPrimitive() {
        int before, after;
        int x = 123;
        before = Clock.getBytecodeNum();
        x = 5;
        after = Clock.getBytecodeNum();
        int expected = 3;
        int actual = after - before;
        assertEquals(expected, actual);
    }

    public static void timeCreateString() {
        int before, after;
        String x = "init value";
        before = Clock.getBytecodeNum();
        x = "a";
        after = Clock.getBytecodeNum();
        int expected = 3;
        int actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        x = "ab";
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        x = "abc";
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);
    }

    public static void timeSimpleForLoop() {
        // general observations:
        // if possible, it's usually better to go through for-loops backwards

        int before, after;
        before = Clock.getBytecodeNum();
        for (int i = 0; i < 100; i++) ;
        after = Clock.getBytecodeNum();
        int expected = 506;
        int actual = after - before;
        assertEquals(expected, actual);

        // try a longer loop
        before = Clock.getBytecodeNum();
        for (int i = 0; i < 1000; i++) ;
        after = Clock.getBytecodeNum();
        expected = 5006;
        actual = after - before;
        assertEquals(expected, actual);

        // try a prefix increment
        before = Clock.getBytecodeNum();
        for (int i = 0; i < 100; ++i) ;
        after = Clock.getBytecodeNum();
        expected = 506;
        actual = after - before;
        assertEquals(expected, actual);

        // loop backwards
        before = Clock.getBytecodeNum();
        for (int i = 100; i >=0; i--) ;
        after = Clock.getBytecodeNum();
        expected = 409;
        actual = after - before;
        assertEquals(expected, actual);

        // loop backwards, change operand order
        before = Clock.getBytecodeNum();
        for (int i = 100; --i >=0;) ;
        after = Clock.getBytecodeNum();
        expected = 406;
        actual = after - before;
        assertEquals(expected, actual);
    }

    public static void timeCreateArray() {
        int before, after;
        before = Clock.getBytecodeNum();
        int[] tmp1;
        after = Clock.getBytecodeNum();
        int expected = 1;
        int actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        int[] tmp2 = null;
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);

        int[] arr;
        before = Clock.getBytecodeNum();
        arr = new int[10];
        after = Clock.getBytecodeNum();
        expected = 13;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        arr = new int[100];
        after = Clock.getBytecodeNum();
        expected = 103;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        arr = new int[1000];
        after = Clock.getBytecodeNum();
        expected = 1003;
        actual = after - before;
        assertEquals(expected, actual);
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        arr = new int[0];
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);
        assertEquals(expected, actual);

        long[] bigArr;
        before = Clock.getBytecodeNum();
        bigArr = new long[10];
        after = Clock.getBytecodeNum();
        expected = 13;
        actual = after - before;
        assertEquals(expected, actual);

        Object[] objArr;
        before = Clock.getBytecodeNum();
        objArr = new Object[10];
        after = Clock.getBytecodeNum();
        expected = 13;
        actual = after - before;
        assertEquals(expected, actual);

    }

    public static void timeAllocateObject() {
        int before, after;
        Object x;
        before = Clock.getBytecodeNum();
        x = new Object();
        after = Clock.getBytecodeNum();
        int expected = 4;
        int actual = after - before;
        assertEquals(expected, actual);
    }
    public static void timeAllocateCollections() {
        int before, after;
        Collection x;
        before = Clock.getBytecodeNum();
        x = new ArrayList();
        after = Clock.getBytecodeNum();
        int expected = 21;
        int actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        x = new LinkedList();
        after = Clock.getBytecodeNum();
        expected = 18;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        x = new HashSet();
        after = Clock.getBytecodeNum();
        expected = 25;
        actual = after - before;
        assertEquals(expected, actual);

        AbstractMap y;
        before = Clock.getBytecodeNum();
        y = new HashMap();
        after = Clock.getBytecodeNum();
        expected = 11;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        x = new PriorityQueue();
        after = Clock.getBytecodeNum();
        expected = 40;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        x = new TreeSet();
        after = Clock.getBytecodeNum();
        expected = 38;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        y = new TreeMap();
        after = Clock.getBytecodeNum();
        expected = 17;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        x = new LinkedHashSet();
        after = Clock.getBytecodeNum();
        expected = 92;
        actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        y = new LinkedHashMap();
        after = Clock.getBytecodeNum();
        expected = 16;
        actual = after - before;
        assertEquals(expected, actual);
    }

    private interface SimpleLambda {
        void doIt();
    }
    public static void timeSimpleLambda() {
        int before, after;
        SimpleLambda x;
        before = Clock.getBytecodeNum();
        x = () -> {};
        after = Clock.getBytecodeNum();
        int expected = 2;
        int actual = after - before;
        assertEquals(expected, actual);

        before = Clock.getBytecodeNum();
        x.doIt();
        after = Clock.getBytecodeNum();
        expected = 3;
        actual = after - before;
        assertEquals(expected, actual);
    }
}
