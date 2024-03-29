package bytecodes;

/**
 * Assertions, shamelessly copied from JUnit source
 */
public class Assert {
	private Assert() {
	}

	/**
	 * Asserts that a condition is true. If it isn't it throws an
	 * {@link AssertionError} with the given message.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param condition
	 *            condition to be checked
	 */
	static public void assertTrue(String message, boolean condition) {
		if (!condition)
			fail(message);
	}

	/**
	 * Asserts that a condition is true. If it isn't it throws an
	 * {@link AssertionError} without a message.
	 * 
	 * @param condition
	 *            condition to be checked
	 */
	static public void assertTrue(boolean condition) {
		assertTrue(null, condition);
	}

	/**
	 * Asserts that a condition is false. If it isn't it throws an
	 * {@link AssertionError} with the given message.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param condition
	 *            condition to be checked
	 */
	static public void assertFalse(String message, boolean condition) {
		assertTrue(message, !condition);
	}

	/**
	 * Asserts that a condition is false. If it isn't it throws an
	 * {@link AssertionError} without a message.
	 * 
	 * @param condition
	 *            condition to be checked
	 */
	static public void assertFalse(boolean condition) {
		assertFalse(null, condition);
	}

	/**
	 * Fails a test with the given message.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @see AssertionError
	 */
	static public void fail(String message) {
		throw new AssertionError(message == null ? "" : message);
	}

	/**
	 * Fails a test with no message.
	 */
	static public void fail() {
		fail(null);
	}

	/**
	 * Asserts that two objects are equal. If they are not, an
	 * {@link AssertionError} is thrown with the given message. If
	 * <code>expected</code> and <code>actual</code> are <code>null</code>,
	 * they are considered equal.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param expected
	 *            expected value
	 * @param actual
	 *            actual value
	 */
	static public void assertEquals(String message, Object expected,
			Object actual) {
		if (expected == null && actual == null)
			return;
		if (expected != null && isEquals(expected, actual))
			return;
		else if (expected instanceof String && actual instanceof String) {
			String cleanMessage= message == null ? "" : message;
			throw new ComparisonFailure(cleanMessage, (String) expected,
					(String) actual);
		} else
			failNotEquals(message, expected, actual);
	}

	private static boolean isEquals(Object expected, Object actual) {
		return expected.equals(actual);
	}

	/**
	 * Asserts that two objects are equal. If they are not, an
	 * {@link AssertionError} without a message is thrown. If
	 * <code>expected</code> and <code>actual</code> are <code>null</code>,
	 * they are considered equal.
	 * 
	 * @param expected
	 *            expected value
	 * @param actual
	 *            the value to check against <code>expected</code>
	 */
	static public void assertEquals(Object expected, Object actual) {
		assertEquals(null, expected, actual);
	}

	/**
	 * Asserts that two doubles or floats are equal to within a positive delta.
	 * If they are not, an {@link AssertionError} is thrown with the given
	 * message. If the expected value is infinity then the delta value is
	 * ignored. NaNs are considered equal:
	 * <code>assertEquals(Double.NaN, Double.NaN, *)</code> passes
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param expected
	 *            expected value
	 * @param actual
	 *            the value to check against <code>expected</code>
	 * @param delta
	 *            the maximum delta between <code>expected</code> and
	 *            <code>actual</code> for which both numbers are still
	 *            considered equal.
	 */
	static public void assertEquals(String message, double expected,
			double actual, double delta) {
		if (Double.compare(expected, actual) == 0)
			return;
		if (!(Math.abs(expected - actual) <= delta))
			failNotEquals(message, new Double(expected), new Double(actual));
	}

	/**
	 * Asserts that two longs are equal. If they are not, an
	 * {@link AssertionError} is thrown.
	 * 
	 * @param expected
	 *            expected long value.
	 * @param actual
	 *            actual long value
	 */
	static public void assertEquals(long expected, long actual) {
		assertEquals(null, expected, actual);
	}

	/**
	 * Asserts that two longs are equal. If they are not, an
	 * {@link AssertionError} is thrown with the given message.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param expected
	 *            long expected value.
	 * @param actual
	 *            long actual value
	 */
	static public void assertEquals(String message, long expected, long actual) {
		assertEquals(message, (Long) expected, (Long) actual);
	}

	/**
	 * @deprecated Use
	 *             <code>assertEquals(double expected, double actual, double epsilon)</code>
	 *             instead
	 */
	@Deprecated
	static public void assertEquals(double expected, double actual) {
		assertEquals(null, expected, actual);
	}

	/**
	 * @deprecated Use
	 *             <code>assertEquals(String message, double expected, double actual, double epsilon)</code>
	 *             instead
	 */
	@Deprecated
	static public void assertEquals(String message, double expected,
			double actual) {
		fail("Use assertEquals(expected, actual, delta) to compare floating-point numbers");
	}

	/**
	 * Asserts that two doubles or floats are equal to within a positive delta.
	 * If they are not, an {@link AssertionError} is thrown. If the expected
	 * value is infinity then the delta value is ignored.NaNs are considered
	 * equal: <code>assertEquals(Double.NaN, Double.NaN, *)</code> passes
	 * 
	 * @param expected
	 *            expected value
	 * @param actual
	 *            the value to check against <code>expected</code>
	 * @param delta
	 *            the maximum delta between <code>expected</code> and
	 *            <code>actual</code> for which both numbers are still
	 *            considered equal.
	 */
	static public void assertEquals(double expected, double actual, double delta) {
		assertEquals(null, expected, actual, delta);
	}

	/**
	 * Asserts that an object isn't null. If it is an {@link AssertionError} is
	 * thrown with the given message.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param object
	 *            Object to check or <code>null</code>
	 */
	static public void assertNotNull(String message, Object object) {
		assertTrue(message, object != null);
	}

	/**
	 * Asserts that an object isn't null. If it is an {@link AssertionError} is
	 * thrown.
	 * 
	 * @param object
	 *            Object to check or <code>null</code>
	 */
	static public void assertNotNull(Object object) {
		assertNotNull(null, object);
	}

	/**
	 * Asserts that an object is null. If it is not, an {@link AssertionError}
	 * is thrown with the given message.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param object
	 *            Object to check or <code>null</code>
	 */
	static public void assertNull(String message, Object object) {
		assertTrue(message, object == null);
	}

	/**
	 * Asserts that an object is null. If it isn't an {@link AssertionError} is
	 * thrown.
	 * 
	 * @param object
	 *            Object to check or <code>null</code>
	 */
	static public void assertNull(Object object) {
		assertNull(null, object);
	}

	/**
	 * Asserts that two objects refer to the same object. If they are not, an
	 * {@link AssertionError} is thrown with the given message.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param expected
	 *            the expected object
	 * @param actual
	 *            the object to compare to <code>expected</code>
	 */
	static public void assertSame(String message, Object expected, Object actual) {
		if (expected == actual)
			return;
		failNotSame(message, expected, actual);
	}

	/**
	 * Asserts that two objects refer to the same object. If they are not the
	 * same, an {@link AssertionError} without a message is thrown.
	 * 
	 * @param expected
	 *            the expected object
	 * @param actual
	 *            the object to compare to <code>expected</code>
	 */
	static public void assertSame(Object expected, Object actual) {
		assertSame(null, expected, actual);
	}

	/**
	 * Asserts that two objects do not refer to the same object. If they do
	 * refer to the same object, an {@link AssertionError} is thrown with the
	 * given message.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param unexpected
	 *            the object you don't expect
	 * @param actual
	 *            the object to compare to <code>unexpected</code>
	 */
	static public void assertNotSame(String message, Object unexpected,
			Object actual) {
		if (unexpected == actual)
			failSame(message);
	}

	/**
	 * Asserts that two objects do not refer to the same object. If they do
	 * refer to the same object, an {@link AssertionError} without a message is
	 * thrown.
	 * 
	 * @param unexpected
	 *            the object you don't expect
	 * @param actual
	 *            the object to compare to <code>unexpected</code>
	 */
	static public void assertNotSame(Object unexpected, Object actual) {
		assertNotSame(null, unexpected, actual);
	}

	static private void failSame(String message) {
		String formatted= "";
		if (message != null)
			formatted= message + " ";
		fail(formatted + "expected not same");
	}

	static private void failNotSame(String message, Object expected,
			Object actual) {
		String formatted= "";
		if (message != null)
			formatted= message + " ";
		fail(formatted + "expected same:<" + expected + "> was not:<" + actual
				+ ">");
	}

	static private void failNotEquals(String message, Object expected,
			Object actual) {
		fail(format(message, expected, actual));
	}

	static String format(String message, Object expected, Object actual) {
		String formatted= "";
		if (message != null && !message.equals(""))
			formatted= message + " ";
		String expectedString= String.valueOf(expected);
		String actualString= String.valueOf(actual);
		if (expectedString.equals(actualString))
			return formatted + "expected: "
					+ formatClassAndValue(expected, expectedString)
					+ " but was: " + formatClassAndValue(actual, actualString);
		else
			return formatted + "expected:<" + expectedString + "> but was:<"
					+ actualString + ">";
	}

	private static String formatClassAndValue(Object value, String valueString) {
		Class className= value == null ? null : value.getClass();
		return className + "<" + valueString + ">";
	}
}
