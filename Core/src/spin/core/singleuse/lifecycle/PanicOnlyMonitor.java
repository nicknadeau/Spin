package spin.core.singleuse.lifecycle;

import spin.core.util.ObjectChecker;

/**
 * A monitor shared by any component in the system that each have a common class that is responsible for life-cycling
 * them, which allows them to communicate the fact that an unexpected error has caused them to crash and that their
 * crashing has likely compromised the integrity or normal functioning of the system.
 *
 * @see NotifyOnlyMonitor if the ability to notify of a graceful shutdown is also required.
 *
 * This class is thread-safe and is intended to be used by multiple classes.
 */
public final class PanicOnlyMonitor {
    private final ShutdownMonitor shutdownMonitor;

    private PanicOnlyMonitor(ShutdownMonitor shutdownMonitor) {
        ObjectChecker.assertNonNull(shutdownMonitor);
        this.shutdownMonitor = shutdownMonitor;
    }

    /**
     * Wraps the underlying {@link ShutdownMonitor} but only exposes the ability to issue a panic and nothing else.
     *
     * @param shutdownMonitor The shutdown monitor to wrap.
     * @return the new panic-only monitor.
     */
    public static PanicOnlyMonitor wrapForPanicsOnly(ShutdownMonitor shutdownMonitor) {
        return new PanicOnlyMonitor(shutdownMonitor);
    }

    /**
     * @see ShutdownMonitor
     */
    public void panic(Throwable error) {
        this.shutdownMonitor.panic(error);
    }
}
