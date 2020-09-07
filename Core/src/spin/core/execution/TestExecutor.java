package spin.core.execution;

import spin.core.exception.ExecutionTaskException;
import spin.core.execution.type.ExecutionReport;
import spin.core.execution.type.ExecutionTask;
import spin.core.execution.util.TaskExecutor;
import spin.core.lifecycle.PanicOnlyMonitor;
import spin.core.util.CloseableBlockingQueue;
import spin.core.util.Logger;
import spin.core.util.ObjectChecker;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

/**
 * A class that executes tests.
 *
 * Tests can be loaded into this executor dynamically as {@link TestInfo} objects and this executor will place the
 * results into a queue that can be polled by a consumer.
 */
public final class TestExecutor implements Runnable {
    private static final Logger LOGGER = Logger.forClass(TestExecutor.class);
    private final CyclicBarrier barrier;
    private final PanicOnlyMonitor shutdownMonitor;
    private final CloseableBlockingQueue<TestInfo> tests;
    private final CloseableBlockingQueue<TestResult> results;
    private volatile boolean isAlive = false;

    private TestExecutor(CyclicBarrier barrier, PanicOnlyMonitor shutdownMonitor, CloseableBlockingQueue<TestInfo> tests, CloseableBlockingQueue<TestResult> results) {
        ObjectChecker.assertNonNull(barrier, shutdownMonitor, tests, results);
        this.barrier = barrier;
        this.shutdownMonitor = shutdownMonitor;
        this.tests = tests;
        this.results = results;
    }

    /**
     * Creates a new executor.
     *
     * The executor will wait on new tests to arrive on the designed test queue it is given.
     * The executor will place each new result when it is finished running a test into the given queue.
     *
     * @param barrier The barrier to wait on before running.
     * @param shutdownMonitor The shutdown monitor.
     * @param tests The queue in which all incoming tests to be executed by this executor are submitted.
     * @param results The queue that all results are placed in when done by this executor.
     * @return the new executor.
     */
    public static TestExecutor withQueues(CyclicBarrier barrier, PanicOnlyMonitor shutdownMonitor, CloseableBlockingQueue<TestInfo> tests, CloseableBlockingQueue<TestResult> results) {
        return new TestExecutor(barrier, shutdownMonitor, tests, results);
    }

    @Override
    public void run() {
        try {
            LOGGER.log("Waiting for other threads to hit barrier.");
            this.isAlive = true;
            this.barrier.await();
            LOGGER.log(Thread.currentThread().getName() + " thread started.");

            while (this.isAlive) {
                LOGGER.log("[" + Thread.currentThread().getName() + "] Waiting for new test method to be loaded...");
                TestInfo testInfo = this.tests.poll(5, TimeUnit.SECONDS);

                if (testInfo != null) {
                    LOGGER.log("[" + Thread.currentThread().getName() + "] Found new test method to run.");

                    ExecutionTask task = createRunTestTask(testInfo);
                    ExecutionReport executionReport = TaskExecutor.executeTask(task);

                    TestResult testResult = TestResult.Builder.newBuilder()
                            .fromTestInfo(testInfo)
                            .fromExecutionReport(executionReport)
                            .build();

                    if (!this.results.add(testResult)) {
                        throw new IllegalStateException("unable to submit result: queue is closed.");
                    }
                    LOGGER.log("[" + Thread.currentThread().getName() + "] Completed running test " + testInfo.method.getName() + " in class " + testInfo.testClass.getName());
                }
            }
        } catch (Throwable t) {
            this.shutdownMonitor.panic(t);
        } finally {
            this.isAlive = false;
            LOGGER.log("[" + Thread.currentThread().getName() + "] Exiting.");
        }
    }

    /**
     * Shuts down this executor.
     */
    public void shutdown() {
        this.isAlive = false;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + (this.isAlive ? " { [running] }" : " { [shutdown] }");
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
