package spin.client.standalone;

public final class Logger {
    private static boolean enabled = true;
    private final String className;

    private Logger(String className) {
        if (className == null) {
            throw new NullPointerException("className must be non-null.");
        }
        this.className = className;
    }

    public static Logger forClass(Class<?> logClass) {
        return new Logger(logClass.getName());
    }

    public static void disable() {
        enabled = false;
    }

    public static void enable() {
        enabled =true;
    }

    public void log(String message) {
        if (enabled) {
            System.out.println(this.className + ": " + message);
        }
    }
}
