package spin.core.lifecycle;

import spin.core.server.type.Result;
import spin.core.singleuse.lifecycle.LifecycleListener;
import spin.core.singleuse.lifecycle.ShutdownListener;
import spin.core.singleuse.lifecycle.ShutdownMonitor;
import spin.core.singleuse.util.Logger;

import java.util.concurrent.Callable;

public final class LifecycleManager implements Callable<Result<Void>>, ShutdownListener, LifecycleListener {
    private static final Logger LOGGER = Logger.forClass(LifecycleManager.class);
    private final Object monitor = new Object();
    private final ShutdownMonitor shutdownMonitor;
    private final LifecycleComponentConfig config;
    private volatile boolean isAlive = true;

    private LifecycleManager(LifecycleComponentConfig config) {
        this.shutdownMonitor = new ShutdownMonitor(this);
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
            lifecycleComponentManager.initializeAllComponents(this.shutdownMonitor, this, this.config);
            lifecycleComponentManager.startAllComponents();

            // Wait until either a panic signal arrives or a shutdown request.
            synchronized (this.monitor) {
                while ((this.isAlive) && (!this.shutdownMonitor.isPanic())) {
                    this.monitor.wait();
                }
            }

            // If there was a panic then display this to the user.
            if (this.shutdownMonitor.isPanic()) {
                this.shutdownMonitor.getPanicCause().printStackTrace();
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

    public void panic(Throwable error) {
        this.shutdownMonitor.panic(error);
    }

    @Override
    public void notifyPanic() {
        synchronized (this.monitor) {
            this.monitor.notifyAll();
        }
    }

    //TODO: note with the long-lived Spin we will no longer shut down this way.
    @Override
    public void notifyDone() {
        this.isAlive = false;
        synchronized (this.monitor) {
            this.monitor.notifyAll();
        }
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " { " + (this.isAlive ? "[running]" : "[shutdown]") + ", config: " + this.config + " }";
    }
}
