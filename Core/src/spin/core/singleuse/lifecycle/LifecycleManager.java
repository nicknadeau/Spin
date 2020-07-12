package spin.core.singleuse.lifecycle;

import spin.core.singleuse.runner.TestSuite;
import spin.core.singleuse.util.CloseableBlockingQueue;
import spin.core.singleuse.util.Logger;

import java.io.IOException;

/**
 * The lifecycle manager. This class is responsible for starting up all of the internal components the system is comprised
 * of and ensuring they are all shutdown when the program is done running or that if there is an unexpected unrecoverable
 * error in any component that the whole system gets shutdown.
 */
public final class LifecycleManager implements Runnable, ShutdownListener, LifecycleListener {
    private static final Logger LOGGER = Logger.forClass(LifecycleManager.class);
    private static final int SYSTEM_CAPACITY = 100_000;
    private final Object monitor = new Object();
    private final CloseableBlockingQueue<TestSuite> testSuiteQueue = CloseableBlockingQueue.withCapacity(1);
    private final ShutdownMonitor shutdownMonitor;
    private final int numExecutorThreads;
    private final boolean writeToDb;
    private final String dbConfigFile;
    private volatile boolean isAlive = true;

    private LifecycleManager(int numThreads, boolean writeToDb, String dbConfigFile) {
        if (numThreads < 1) {
            throw new IllegalArgumentException("numThreads must be positive but was: " + numThreads);
        }
        this.numExecutorThreads = numThreads;
        this.writeToDb = writeToDb;
        this.dbConfigFile = dbConfigFile;
        this.shutdownMonitor = new ShutdownMonitor(this);
    }

    /**
     * Constructs a new lifecycle manager that will create the given number of test executor threads.
     *
     * @param numExecutorThreads The number of test executor threads.
     * @param writeToDb Whether or not to write the results to a database.
     * @param dbConfigFile The path to the database configuration file.
     * @return the new lifecycle manager.
     */
    public static LifecycleManager withNumExecutors(int numExecutorThreads, boolean writeToDb, String dbConfigFile) {
        return new LifecycleManager(numExecutorThreads, writeToDb, dbConfigFile);
    }

    @Override
    public void run() {
        LifecycleObjectHolder lifecycleObjectHolder = new LifecycleObjectHolder(this.numExecutorThreads, SYSTEM_CAPACITY, this.writeToDb, this.shutdownMonitor, this.testSuiteQueue);

        try {
            // Create all of the threads and start them.
            lifecycleObjectHolder.constructAllLifecycleObjects(this, this.dbConfigFile);
            lifecycleObjectHolder.startAllLifecycleObjects();

            // Wait until either a panic signal arrives or a shutdown request.
            synchronized (this.monitor) {
                while ((this.isAlive) && (!this.shutdownMonitor.isPanic())) {
                    this.monitor.wait();
                }
            }

            // If there was a panic then display this to the user.
            if (this.shutdownMonitor.isPanic()) {
                System.err.println("Encountered an unexpected error.");
                this.shutdownMonitor.getPanicCause().printStackTrace();
            }

            lifecycleObjectHolder.shutdownAllLifecycleObjects();
            lifecycleObjectHolder.waitForAllLifecycleObjectsToShutdown();

        } catch (Throwable t) {
            try {
                System.err.println("Encountered an unexpected error.");
                t.printStackTrace();
                lifecycleObjectHolder.shutdownAllLifecycleObjects();
            } catch (IOException e) {
                System.err.println("Encountered an unexpected shutdown error.");
                e.printStackTrace();
            }
        } finally {
            this.isAlive = false;
            LOGGER.log("Exiting.");
        }
    }

    /**
     * Invoked by a life-cycled object when it panics due to an unexpected error.
     */
    @Override
    public void notifyPanic() {
        synchronized (this.monitor) {
            this.monitor.notifyAll();
        }
    }

    /**
     * Invoked by the final component in the system when it is complete its job, signifying that the whole system is
     * done and can be shutdown.
     *
     * Alternatively, this may be invoked by the initial component in the system if it detects that there are zero tests
     * to run.
     */
    @Override
    public void notifyDone() {
        this.isAlive = false;
        synchronized (this.monitor) {
            this.monitor.notifyAll();
        }
    }

    /**
     * Returns the queue that can be used to submit test suites from the outside into this internally managed system.
     *
     * This is the only real point of contact this other side of the system has with all the internal components managed
     * by this manager.
     *
     * @return the test suite queue.
     */
    public CloseableBlockingQueue<TestSuite> getTestSuiteQueue() {
        return this.testSuiteQueue;
    }

    /**
     * Returns the shared shutdown monitor that all components in the system are attached to and which notifies this
     * life-cycle manager of any issues.
     *
     * @return the shutdown monitor.
     */
    public ShutdownMonitor getShutdownMonitor() {
        return this.shutdownMonitor;
    }
}
