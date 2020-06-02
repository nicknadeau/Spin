package spin.core.singleuse.lifecycle;

/**
 * A shared monitor that is used by threads being life-cycled by someone else so that they can alert of unexpected errors
 * that crash them.
 *
 * The {@link ShutdownListener} tied to this monitor will be notified of any events.
 *
 * This class is thread-safe.
 */
public final class ShutdownMonitor {
    private final ShutdownListener listener;
    private Throwable panic = null;

    /**
     * Constructs a new shutdown monitor with the specified listener.
     *
     * @param listener The listener to notify.
     */
    ShutdownMonitor(ShutdownListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener must be non-null.");
        }
        this.listener = listener;
    }

    /**
     * Indicates an unexpected error has occurred somewhere in the system that cannot be recovered from.
     *
     * Notifies the {@link ShutdownListener}.
     *
     * @param error The error.
     */
    public synchronized void panic(Throwable error) {
        if (error == null) {
            throw new NullPointerException("error must be non-null.");
        }
        if (this.panic == null) {
            this.panic = error;
            this.listener.notifyPanic();
        }
    }

    /**
     * Returns the cause of the panic. This method will always return null if {@code isPanic()} is false and otherwise
     * will return the non-null cause of the panic.
     *
     * @return the cause of the panic.
     */
    public synchronized Throwable getPanicCause() {
        return this.panic;
    }

    /**
     * Returns true iff there was a panic.
     *
     * @return whether or not a panic has occurred.
     */
    public synchronized boolean isPanic() {
        return this.panic != null;
    }
}
