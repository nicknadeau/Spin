package spin.client.standalone;

public final class Logger {
    private final String className;
    private boolean enabled = true;

    private Logger(String className) {
        if (className == null) {
            throw new NullPointerException("className must be non-null.");
        }
        this.className = className;
    }

    public static Logger forClass(Class<?> logClass) {
        return new Logger(logClass.getName());
    }

    public void disable() {
        this.enabled = false;
    }

    public void enable() {
        this.enabled =true;
    }

    public void log(String message) {
        if (this.enabled) {
            System.out.println(this.className + ": " + message);
        }
    }
}
