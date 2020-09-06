package spin.core.lifecycle;

import spin.core.util.ObjectChecker;

/**
 * A monitor shared by any component in the system that each have a common class that is responsible for life-cycling
 * them, which allows them to communicate the fact that an unexpected error has caused them to crash and that their
 * crashing has likely compromised the integrity or normal functioning of the system.
 *
 * The monitor can also be used by anyone who wishes to shutdown all of the life-cycled components for an innocuous
 * reason (ie. no error has occurred, but someone has requested the system be shut down).
 *
 * @see NotifyOnlyMonitor
 * @see PanicOnlyMonitor
 * @see ListenOnlyMonitor
 *
 * This class is thread-safe and is intended to be used by multiple classes.
 */
public final class ShutdownMonitor {
    private final Object monitor = new Object();
    private Throwable panic = null;
    private boolean requestToShutdownGracefullyExists = false;

    /**
     * Alerts the life-cycling thread that an error has occurred somewhere in the system and the system integrity or
     * normal functionality is compromised as a result and everything should therefore be shutdown.
     *
     * If a panic already exists in this monitor then this one will be ignored since any subsequent panics will be
     * handled the same way.
     *
     * @param error the fatal error.
     */
    void panic(Throwable error) {
        ObjectChecker.assertNonNull(error);

        synchronized (this.monitor) {
            if (this.panic == null) {
                this.panic = error;
                this.monitor.notifyAll();
            }
        }
    }

    /**
     * Alerts the life-cycling thread that a request for the system to be shutdown gracefully has been made and the
     * system should be shut down as soon as possible.
     */
    void requestGracefulShutdown() {
        synchronized (this.monitor) {
            this.requestToShutdownGracefullyExists = true;
            this.monitor.notifyAll();
        }
    }

    /**
     * Blocks indefinitely until a shutdown cause has been registered with this monitor.
     */
    void waitUntilCauseForShutdown() throws InterruptedException {
        synchronized (this.monitor) {
            while ((!this.requestToShutdownGracefullyExists) && (this.panic == null)) {
                this.monitor.wait();
            }
        }
    }

    /**
     * Returns true if and only if a graceful shutdown request has been made.
     */
    boolean isRequestToShutdownGracefully() {
        synchronized (this.monitor) {
            return this.requestToShutdownGracefullyExists;
        }
    }

    /**
     * Returns the cause of the panic if a panic was registered with this monitor.
     */
    Throwable getCauseOfPanic() {
        synchronized (this.monitor) {
            return this.panic;
        }
    }
}
