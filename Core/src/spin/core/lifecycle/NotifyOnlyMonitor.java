package spin.core.lifecycle;

import spin.core.util.ObjectChecker;

/**
 * A monitor shared by any component in the system that each have a common class that is responsible for life-cycling
 * them, which allows them to communicate the fact that an unexpected error has caused them to crash and that their
 * crashing has likely compromised the integrity or normal functioning of the system.
 *
 * This monitor can also be used to issue a graceful shutdown request as well.
 *
 * @see PanicOnlyMonitor if the requirement to gracefully shutdown is not necesssary.
 *
 * This class is thread-safe and is intended to be used by multiple classes.
 */
public final class NotifyOnlyMonitor {
    private final ShutdownMonitor shutdownMonitor;

    private NotifyOnlyMonitor(ShutdownMonitor shutdownMonitor) {
        ObjectChecker.assertNonNull(shutdownMonitor);
        this.shutdownMonitor = shutdownMonitor;
    }

    /**
     * Wraps the underlying {@link ShutdownMonitor} but only exposes the ability to issue a panic or request a
     * graceful shutdown, nothing else.
     *
     * @param shutdownMonitor The shutdown monitor to wrap.
     * @return the new notify-only monitor.
     */
    public static NotifyOnlyMonitor wrapForNotificationsOnly(ShutdownMonitor shutdownMonitor) {
        return new NotifyOnlyMonitor(shutdownMonitor);
    }

    /**
     * @see ShutdownMonitor
     */
    public void panic(Throwable error) {
        this.shutdownMonitor.panic(error);
    }

    /**
     * @see ShutdownMonitor
     */
    public void requestGracefulShutdown() {
        this.shutdownMonitor.requestGracefulShutdown();
    }
}
