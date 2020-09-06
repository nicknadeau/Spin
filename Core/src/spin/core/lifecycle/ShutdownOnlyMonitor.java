package spin.core.lifecycle;

import spin.core.util.ObjectChecker;

public final class ShutdownOnlyMonitor {
    private final ShutdownMonitor shutdownMonitor;

    private ShutdownOnlyMonitor(ShutdownMonitor shutdownMonitor) {
        ObjectChecker.assertNonNull(shutdownMonitor);
        this.shutdownMonitor = shutdownMonitor;
    }

    /**
     * Wraps the underlying {@link ShutdownMonitor} but only exposes the ability to request a graceful shutdown,
     * nothing else.
     *
     * @param shutdownMonitor The shutdown monitor to wrap.
     * @return the new shutdown-only monitor.
     */
    public static ShutdownOnlyMonitor wrapForGracefulShutdownsOnly(ShutdownMonitor shutdownMonitor) {
        return new ShutdownOnlyMonitor(shutdownMonitor);
    }

    /**
     * @see ShutdownMonitor --> {@link ShutdownMonitor#requestGracefulShutdown()}
     */
    public void requestGracefulShutdown() {
        this.shutdownMonitor.requestGracefulShutdown();
    }
}
