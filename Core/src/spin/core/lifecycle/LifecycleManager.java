package spin.core.lifecycle;

import spin.core.type.Result;
import spin.core.util.Logger;

import java.util.concurrent.Callable;

public final class LifecycleManager implements Callable<Result<Void>> {
    private static final Logger LOGGER = Logger.forClass(LifecycleManager.class);
    private final LifecycleComponentConfig config;
    private volatile boolean isAlive = true;

    private LifecycleManager(LifecycleComponentConfig config) {
        this.config = config;
    }

    public static LifecycleManager newManager(LifecycleComponentConfig config) {
        if (config == null) {
            throw new NullPointerException("config must be non-null.");
        }
        return new LifecycleManager(config);
    }

    @Override
    public Result<Void> call() {
        LifecycleComponentManager lifecycleComponentManager = LifecycleComponentManager.newManager();

        Result<Void> result;
        try {
            ListenOnlyMonitor listenOnlyMonitor = lifecycleComponentManager.initializeAllComponents(this.config);
            lifecycleComponentManager.startAllComponents();

            listenOnlyMonitor.waitUntilCauseForShutdown();

            // If there was a panic then display this to the user.
            if (!listenOnlyMonitor.isRequestToShutdownGracefully()) {
                listenOnlyMonitor.getCauseOfPanic().printStackTrace();
            }

            lifecycleComponentManager.shutdownAllComponents();
            lifecycleComponentManager.waitForAllComponentsToShutdown();

            result = Result.successful(null);

        } catch (Throwable e) {
            e.printStackTrace();
            lifecycleComponentManager.shutdownAllComponents();
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
        return this.getClass().getSimpleName() + " { " + (this.isAlive ? "[running]" : "[shutdown]") + ", config: " + this.config + " }";
    }
}
