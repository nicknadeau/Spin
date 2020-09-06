package spin.core.lifecycle;

import spin.core.type.Result;
import spin.core.util.Logger;

import java.util.concurrent.Callable;

public final class LifecycleManager implements Callable<Result<Void>> {
    private static final Logger LOGGER = Logger.forClass(LifecycleManager.class);
    private final LifecycleComponentManager componentManager;
    private volatile boolean isAlive = true;

    private LifecycleManager(LifecycleComponentConfig config) {
        this.componentManager = LifecycleComponentManager.newManager(config);
    }

    public static LifecycleManager newManager(LifecycleComponentConfig config) {
        return new LifecycleManager(config);
    }

    public PanicOnlyMonitor getPanicOnlyShutdownMonitor() {
        return this.componentManager.getPanicOnlyShutdownMonitor();
    }

    @Override
    public Result<Void> call() {
        Result<Void> result;
        try {
            this.componentManager.initializeAllComponents();
            this.componentManager.startAllComponents();

            ListenOnlyMonitor listenOnlyMonitor = this.componentManager.getListenOnlyShutdownMonitor();
            listenOnlyMonitor.waitUntilCauseForShutdown();

            // If there was a panic then display this to the user.
            if (!listenOnlyMonitor.isRequestToShutdownGracefully()) {
                listenOnlyMonitor.getCauseOfPanic().printStackTrace();
            }

            this.componentManager.shutdownAllComponents();
            this.componentManager.waitForAllComponentsToShutdown();

            result = Result.successful(null);

        } catch (Throwable e) {
            e.printStackTrace();
            this.componentManager.shutdownAllComponents();
            result = Result.error("Unexpected error: " + e.getMessage());
        } finally {
            this.isAlive = false;
            LOGGER.log("Exiting.");
        }

        return result;
    }

    public boolean isAlive() {
        return this.isAlive;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " { " + (this.isAlive ? "[running]" : "[shutdown]") + " }";
    }
}
