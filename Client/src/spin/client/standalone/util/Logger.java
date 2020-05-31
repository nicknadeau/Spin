package spin.client.standalone.util;

/**
 * A simple logging utility that can be either enabled or disabled globally.
 */
public final class Logger {
    private static boolean enabled = true;
    private final String className;

    private Logger(String className) {
        if (className == null) {
            throw new NullPointerException("className must be non-null.");
        }
        this.className = className;
    }

    /**
     * Constructs and returns a new logging for the given class.
     *
     * @param logClass The logging class.
     * @return the new logger.
     */
    public static Logger forClass(Class<?> logClass) {
        return new Logger(logClass.getName());
    }

    /**
     * Globally disables all loggers.
     */
    public static void disable() {
        enabled = false;
    }

    /**
     * Globally enables all loggers.
     */
    public static void enable() {
        enabled =true;
    }

    /**
     * Logs the specified message to stdout if logging is enabled.
     *
     * @param message The message to log.
     */
    public void log(String message) {
        if (enabled) {
            System.out.println(this.className + ": " + message);
        }
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " { class: " + this.className + ", enabled: " + enabled + " }";
    }
}
