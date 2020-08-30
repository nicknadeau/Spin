package spin.core.util;

public final class ObjectChecker {

    public static void assertNonNull(Object... objects) {
        for (int i = 0; i < objects.length; i++) {
            if (objects[i] == null) {
                throw new NullPointerException("object must be non-null: violated by object at index " + i);
            }
        }
    }
}
