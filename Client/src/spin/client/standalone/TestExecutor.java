package spin.client.standalone;

import java.lang.reflect.Method;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A class that executes tests.
 *
 * Tests can be loaded into this executor dynamically as {@link TestMethod} objects and this executor will place the
 * results into a queue that can be polled by a consumer.
 */
public final class TestExecutor implements Runnable {
    private static final Logger LOGGER = Logger.forClass(TestExecutor.class);
    private final Object monitor = new Object();
    private final BlockingQueue<TestMethod> tests;
    private final BlockingQueue<TestResult> results;
    private volatile boolean isAlive = true;

    private TestExecutor(BlockingQueue<TestMethod> tests, BlockingQueue<TestResult> results) {
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
    public static TestExecutor withQueues(BlockingQueue<TestMethod> tests, BlockingQueue<TestResult> results) {
        return new TestExecutor(tests, results);
    }

    @Override
    public void run() {
        try {
            while (this.isAlive) {
                LOGGER.log("[" + Thread.currentThread().getName() + "] Waiting for new test method to be loaded...");
                TestMethod testMethod = null;
                try {
                    testMethod = this.tests.poll(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                if (testMethod != null) {
                    LOGGER.log("[" + Thread.currentThread().getName() + "] Found new test method to run.");
                    TestResult result;

                    long startTime = System.nanoTime();
                    try {
                        Object instance = testMethod.testClass.getConstructor().newInstance();
                        testMethod.method.invoke(instance);
                        long endTime = System.nanoTime();
                        result = new TestResult(testMethod.testClass, testMethod.method, true, endTime - startTime, testMethod.testSuiteDetails);

                    } catch (Exception e) {
                        long endTime = System.nanoTime();
                        result = new TestResult(testMethod.testClass, testMethod.method, false, endTime - startTime, testMethod.testSuiteDetails);
                    }

                    synchronized (this.monitor) {
                        this.results.add(result);
                    }
                    LOGGER.log("[" + Thread.currentThread().getName() + "] Completed running test " + testMethod.method.getName() + " in class " + testMethod.testClass.getName());
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

    /**
     * Returns true iff this executor is still alive and is not shutdown.
     *
     * @return whether or not this executor is alive.
     */
    public boolean isAlive() {
        return this.isAlive;
    }

    public static class TestMethod {
        public final Class<?> testClass;
        public final Method method;
        public final TestSuiteRunner.TestSuiteDetails testSuiteDetails;

        public TestMethod(Class<?> testClass, Method method, TestSuiteRunner.TestSuiteDetails testSuiteDetails) {
            this.testClass = testClass;
            this.method = method;
            this.testSuiteDetails = testSuiteDetails;
        }

        @Override
        public String toString() {
            return "TestMethod { class: " + this.testClass.getName() + ", method: " + this.method.getName() + " }";
        }
    }

    public static class TestResult {
        public final Class<?> testClass;
        public final Method testMethod;
        public final boolean successful;
        public final long durationNanos;
        public final TestSuiteRunner.TestSuiteDetails testSuiteDetails;

        private TestResult(Class<?> testClass, Method testMethod, boolean successful, long durationNanos, TestSuiteRunner.TestSuiteDetails testSuiteDetails) {
            this.testClass = testClass;
            this.testMethod = testMethod;
            this.successful = successful;
            this.durationNanos = durationNanos;
            this.testSuiteDetails = testSuiteDetails;
        }
    }
}
