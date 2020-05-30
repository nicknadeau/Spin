package spin.client.standalone;

public final class Logger {
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

    public void log(String message) {
        System.out.println(this.className + ": " + message);
    }
}
