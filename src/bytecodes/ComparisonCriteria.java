package bytecodes;

/**
 * Defines criteria for finding two items "equal enough". Concrete subclasses
 * may demand exact equality, or, for example, equality within a given delta.
 */
public abstract class ComparisonCriteria {

	protected abstract void assertElementsEqual(Object expected, Object actual);
}
