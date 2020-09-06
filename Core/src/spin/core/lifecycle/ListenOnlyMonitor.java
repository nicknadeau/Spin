package spin.core.lifecycle;

import spin.core.util.ObjectChecker;

/**
 * A monitor shared by any component in the system that each have a common class that is responsible for life-cycling
 * them which allows them to communicate back to it that it is time to shutdown and why.
 *
 * This is the listen-only side of the shared monitor and only the listen methods are exposed, no methods to issue
 * shutdown requests. This should be handed off to the life-cycle manager thread.
 *
 * This class is thread-safe and is intended to be used by multiple classes.
 */
public final class ListenOnlyMonitor {
    private final ShutdownMonitor shutdownMonitor;

    private ListenOnlyMonitor(ShutdownMonitor shutdownMonitor) {
        ObjectChecker.assertNonNull(shutdownMonitor);
        this.shutdownMonitor = shutdownMonitor;
    }

    /**
     * Wraps the underlying {@link ShutdownMonitor} but only exposes the ability to listen for a shutdown request and
     * nothing else.
     *
     * @param shutdownMonitor The shutdown monitor to wrap.
     * @return the new listen-only monitor.
     */
    public static ListenOnlyMonitor wrapForListeningOnly(ShutdownMonitor shutdownMonitor) {
        return new ListenOnlyMonitor(shutdownMonitor);
    }

    /**
     * @see ShutdownMonitor
     */
    public void waitUntilCauseForShutdown() throws InterruptedException {
        this.shutdownMonitor.waitUntilCauseForShutdown();
    }

    /**
     * @see ShutdownMonitor
     */
    public boolean isRequestToShutdownGracefully() {
        return this.shutdownMonitor.isRequestToShutdownGracefully();
    }

    /**
     * @see ShutdownMonitor
     */
    public Throwable getCauseOfPanic() {
        return this.shutdownMonitor.getCauseOfPanic();
    }
}
