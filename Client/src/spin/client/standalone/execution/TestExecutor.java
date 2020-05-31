package spin.client.standalone.execution;

import spin.client.standalone.util.Logger;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A class that executes tests.
 *
 * Tests can be loaded into this executor dynamically as {@link TestInfo} objects and this executor will place the
 * results into a queue that can be polled by a consumer.
 */
public final class TestExecutor implements Runnable {
    private static final Logger LOGGER = Logger.forClass(TestExecutor.class);
    private final Object monitor = new Object();
    private final BlockingQueue<TestInfo> tests;
    private final BlockingQueue<TestResult> results;
    private volatile boolean isAlive = true;

    private TestExecutor(BlockingQueue<TestInfo> tests, BlockingQueue<TestResult> results) {
        if (tests == null) {
            throw new NullPointerException("tests must be non-null.");
        }
        if (results == null) {
            throw new NullPointerException("results must be non-null.");
        }
        this.tests = tests;
        this.results = results;
    }

    /**
     * Creates a new executor.
     *
     * The executor will wait on new tests to arrive on the designed test queue it is given.
     * The executor will place each new result when it is finished running a test into the given queue.
     *
     * @param tests The queue in which all incoming tests to be executed by this executor are submitted.
     * @param results The queue that all results are placed in when done by this executor.
     * @return the new executor.
     */
    public static TestExecutor withQueues(BlockingQueue<TestInfo> tests, BlockingQueue<TestResult> results) {
        return new TestExecutor(tests, results);
    }

    @Override
    public void run() {
        try {
            while (this.isAlive) {
                LOGGER.log("[" + Thread.currentThread().getName() + "] Waiting for new test method to be loaded...");
                TestInfo testInfo = null;
                try {
                    testInfo = this.tests.poll(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                if (testInfo != null) {
                    LOGGER.log("[" + Thread.currentThread().getName() + "] Found new test method to run.");
                    TestResult result;

                    long startTime = System.nanoTime();
                    try {
                        Object instance = testInfo.testClass.getConstructor().newInstance();
                        testInfo.method.invoke(instance);
                        long endTime = System.nanoTime();
                        result = new TestResult(testInfo.testClass, testInfo.method, true, endTime - startTime, testInfo.testSuiteDetails);

                    } catch (Exception e) {
                        long endTime = System.nanoTime();
                        result = new TestResult(testInfo.testClass, testInfo.method, false, endTime - startTime, testInfo.testSuiteDetails);
                    }

                    synchronized (this.monitor) {
                        this.results.add(result);
                    }
                    LOGGER.log("[" + Thread.currentThread().getName() + "] Completed running test " + testInfo.method.getName() + " in class " + testInfo.testClass.getName());
                }
            }
        } finally {
            this.isAlive = false;
            LOGGER.log("[" + Thread.currentThread().getName() + "] Exiting.");
        }
    }

    /**
     * Shuts down this executor.
     */
    public void shutdown() {
        synchronized (this.monitor) {
            this.isAlive = false;
            this.monitor.notifyAll();
        }
    }

    @Override
    public String toString() {
        return this.getClass().getName() + (this.isAlive ? " { [running] }" : " { [shutdown] }");
    }
}
