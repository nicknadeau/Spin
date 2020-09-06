package spin.core.util;

public final class ObjectChecker {

    public static void assertNonNull(Object object) {
        if (object == null) {
            throw new NullPointerException("object must be non-null.");
        }
    }

    public static void assertNonNull(Object... objects) {
        for (int i = 0; i < objects.length; i++) {
            if (objects[i] == null) {
                throw new NullPointerException("object must be non-null: violated by object at index " + i);
            }
        }
    }

    public static void assertPositive(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("value must be strictly positive but was: " + value);
        }
    }
}
