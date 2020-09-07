package spin.core.execution;

import spin.core.exception.ExecutionTaskException;
import spin.core.execution.type.ExecutionReport;
import spin.core.execution.type.ExecutionTask;
import spin.core.execution.util.TaskExecutor;
import spin.core.util.Logger;
import spin.core.util.ObjectChecker;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.*;

/**
 * A class that executes tests asynchronously.
 */
public final class TestExecutor {
    private static final Logger LOGGER = Logger.forClass(TestExecutor.class);
    private final ExecutorService executorService;
    private volatile boolean isAlive = true;

    /**
     * Constructs a new executor that uses no more than the specified number of threads at any given time.
     *
     * @param maxNumThreads The max number of threads to use.
     */
    public TestExecutor(int maxNumThreads) {
        this.executorService = Executors.newFixedThreadPool(maxNumThreads);
    }

    /**
     * Runs the test and returns a future report detailing the outcome of the execution of the test.
     *
     * @param testInfo The test info.
     * @return a future execution report.
     */
    public Future<ExecutionReport> runTest(TestInfo testInfo) {
        throwIfNotAlive();
        ObjectChecker.assertNonNull(testInfo);

        LOGGER.log("Submitting test to execute: " + testInfo.testClass.getSimpleName() + ":" + testInfo.method.getName());
        Callable<ExecutionReport> callableTask = () -> TaskExecutor.executeTask(createRunTestTask(testInfo));
        return this.executorService.submit(callableTask);
    }

    /**
     * Shuts down this executor.
     */
    public void shutdown() {
        this.isAlive = false;
        this.executorService.shutdown();
    }

    public void waitUntilShutdown(long timeout, TimeUnit unit) throws InterruptedException {
        this.executorService.awaitTermination(timeout, unit);
    }

    @Override
    public String toString() {
        return this.getClass().getName() + (this.isAlive ? " { [running] }" : " { [shutdown] }");
    }

    private void throwIfNotAlive() {
        if (!this.isAlive) {
            throw new IllegalStateException(this.getClass().getSimpleName() + " is shutdown.");
        }
    }

    private static ExecutionTask createRunTestTask(TestInfo testInfo) {
        return () -> {
            try {
                Object instance = testInfo.testClass.getConstructor().newInstance();
                testInfo.method.invoke(instance);
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new ExecutionTaskException(e.getMessage());
            }
        };
    }
}
