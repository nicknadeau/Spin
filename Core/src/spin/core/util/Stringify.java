package spin.core.util;

public final class Stringify {

    public static String threadToStringPrefix(Object object, boolean isShutdown) {
        return object.getClass().getSimpleName() + " { " + (isShutdown ? "[shutdown]" : "[running]");
    }
}
