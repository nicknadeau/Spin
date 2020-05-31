package spin.client.standalone;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The class that is responsible for outputting the test results to the console.
 */
public final class ResultOutputter implements Runnable {
    private static final Logger LOGGER = Logger.forClass(ResultOutputter.class);
    private final List<BlockingQueue<TestExecutor.TestResult>> incomingResultQueues;
    private final Map<Class<?>, Integer> numTestsPerClass;
    private final Map<Class<?>, TestClassStats> testClassStats = new HashMap<>();
    private final TestSuiteStats suiteStats = new TestSuiteStats();
    private int numClassesFinished = 0;
    private volatile boolean isAlive = true;

    private ResultOutputter(List<BlockingQueue<TestExecutor.TestResult>> incomingResultQueues, Map<Class<?>, Integer> numTestsPerClass) {
        if (incomingResultQueues == null) {
            throw new NullPointerException("incomingResultQueues must be non-null.");
        }
        if (numTestsPerClass == null) {
            throw new NullPointerException("numTestsPerClass must be non-null.");
        }
        this.incomingResultQueues = incomingResultQueues;
        this.numTestsPerClass = numTestsPerClass;
    }

    /**
     * Creates a new result outputter that expects to witness the specified number of tests per each class as given by
     * the mapping and which expects to find all of the test results on the list of queues given to it.
     *
     * This outputter has a finite job to do and once it has witnessed the expected number of tests in the test suite it
     * will exit.
     *
     * @param incomingResultQueues The queues that test results may be coming in on asynchronously.
     * @param numTestsPerClass The number of tests for each test class.
     * @return the new outputter.
     */
    public static ResultOutputter outputter(List<BlockingQueue<TestExecutor.TestResult>> incomingResultQueues, Map<Class<?>, Integer> numTestsPerClass) {
        return new ResultOutputter(incomingResultQueues, numTestsPerClass);
    }

    @Override
    public void run() {
        try {
            System.out.println("\n===============================================================");

            while (this.isAlive) {
                for (BlockingQueue<TestExecutor.TestResult> incomingResultQueue : this.incomingResultQueues) {
                    if (!this.isAlive) {
                        break;
                    }

                    LOGGER.log("Checking for new results...");

                    TestExecutor.TestResult result = null;
                    try {
                        result = incomingResultQueue.poll(3, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    if (result != null) {
                        LOGGER.log("New result obtained.");

                        // If we don't have a stats entry for this class then create one.
                        if (!this.testClassStats.containsKey(result.testClass)) {
                            this.testClassStats.put(result.testClass, new TestClassStats());
                        }
                        TestClassStats stats = this.testClassStats.get(result.testClass);

                        // Report the test as successful or failed.
                        if (result.successful) {
                            System.out.println(result.testClass.getName() + "::" + result.testMethod.getName() + " [successful], seconds: " + nanosToSecondsString(result.durationNanos));
                            stats.numSuccesses++;
                            this.suiteStats.totalNumSuccessfulTests++;
                        } else {
                            System.out.println(result.testClass.getName() + "::" + result.testMethod.getName() + " [failure], seconds: " + nanosToSecondsString(result.durationNanos));
                            stats.numFailures++;
                            this.suiteStats.totalNumFailedTests++;
                        }
                        stats.totalDurationNanos += result.durationNanos;
                        this.suiteStats.totalSuiteDuration += result.durationNanos;

                        // If all tests in class are complete then report the class as finished.
                        int numTestsInClass = this.numTestsPerClass.get(result.testClass);
                        if (numTestsInClass == stats.numSuccesses + stats.numFailures) {
                            System.out.println(result.testClass.getName() + ": tests [" + numTestsInClass + "] successes: " + stats.numSuccesses + ", failures: " + stats.numFailures + ", seconds: " + nanosToSecondsString(stats.totalDurationNanos) + "\n");
                            this.numClassesFinished++;
                            LOGGER.log("Witnessed all tests in class: " + result.testClass.getName());
                        }

                        // If all test classes are complete then report the suite as finished and exit.
                        if (this.numTestsPerClass.keySet().size() == this.numClassesFinished) {
                            System.out.println("Total tests [" + (this.suiteStats.totalNumSuccessfulTests + this.suiteStats.totalNumFailedTests) + "] successes: " + this.suiteStats.totalNumSuccessfulTests + ", failures: " + this.suiteStats.totalNumFailedTests + ", seconds: " + nanosToSecondsString(this.suiteStats.totalSuiteDuration));
                            LOGGER.log("Witnessed all tests in suite.");
                            this.isAlive = false;
                            break;
                        }
                    }
                }
            }

        } finally {
            System.out.println("===============================================================");
            LOGGER.log("Exiting.");
        }
    }

    private static String nanosToSecondsString(long nanos) {
        return BigDecimal.valueOf(nanos).setScale(4, RoundingMode.HALF_DOWN)
                .divide(BigDecimal.valueOf(1_000_000_000L), RoundingMode.HALF_DOWN).setScale(4, RoundingMode.HALF_DOWN)
                .toPlainString();
    }

    private static class TestClassStats {
        private int numSuccesses = 0;
        private int numFailures = 0;
        private long totalDurationNanos = 0;
    }

    private static class TestSuiteStats {
        private int totalNumSuccessfulTests = 0;
        private int totalNumFailedTests = 0;
        private long totalSuiteDuration = 0;
    }
}
