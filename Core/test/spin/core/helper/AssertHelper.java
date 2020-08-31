package spin.core.helper;

public final class AssertHelper {

    public static void assertThrows(Class<? extends Throwable> expected, Action action) {
        try {
            action.act();
            throw new AssertException("No exception thrown.");
        } catch (AssertException e) {
            // This is throw by us and is an actual exception we want to propagate.
            throw e;
        } catch (Throwable t) {
            if (!expected.equals(t.getClass())) {
                throw new AssertException("Actual exception [" + t.getClass() + "] != expected exception [" + expected + "]");
            }
        }
    }

    @FunctionalInterface
    public static interface Action {
        public void act() throws Throwable;
    }

    public static final class AssertException extends RuntimeException {
        public AssertException(String message) {
            super(message);
        }
    }
}
