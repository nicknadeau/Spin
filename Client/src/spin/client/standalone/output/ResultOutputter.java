package spin.client.standalone.output;

import spin.client.standalone.execution.TestResult;
import spin.client.standalone.util.CloseableBlockingQueue;
import spin.client.standalone.util.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The class that is responsible for outputting the test results to the console.
 */
public final class ResultOutputter implements Runnable {
    private static final Logger LOGGER = Logger.forClass(ResultOutputter.class);
    private final List<CloseableBlockingQueue<TestResult>> incomingResultQueues;
    private volatile boolean isAlive = true;

    private ResultOutputter(List<CloseableBlockingQueue<TestResult>> incomingResultQueues) {
        if (incomingResultQueues == null) {
            throw new NullPointerException("incomingResultQueues must be non-null.");
        }
        this.incomingResultQueues = incomingResultQueues;
    }

    /**
     * Creates a new result outputter that expects to witness the specified number of tests per each class as given by
     * the mapping and which expects to find all of the test results on the list of queues given to it.
     *
     * @param incomingResultQueues The queues that test results may be coming in on asynchronously.
     * @return the new outputter.
     */
    public static ResultOutputter outputter(List<CloseableBlockingQueue<TestResult>> incomingResultQueues) {
        return new ResultOutputter(incomingResultQueues);
    }

    @Override
    public void run() {
        try {
            System.out.println("\n===============================================================");

            while (this.isAlive) {
                for (CloseableBlockingQueue<TestResult> incomingResultQueue : this.incomingResultQueues) {
                    if (!this.isAlive) {
                        break;
                    }

                    LOGGER.log("Checking for new results...");

                    TestResult result = incomingResultQueue.tryPoll();

                    if (result != null) {
                        LOGGER.log("New result obtained.");

                        // Report the test as successful or failed.
                        if (result.successful) {
                            System.out.println(result.testClass.getName() + "::" + result.testMethod.getName() + " [successful], seconds: " + nanosToSecondsString(result.durationNanos));
                            result.testSuiteDetails.incrementNumSuccessfulTestsInClass(result.testClass, result.durationNanos);
                        } else {
                            System.out.println(result.testClass.getName() + "::" + result.testMethod.getName() + " [failure], seconds: " + nanosToSecondsString(result.durationNanos));
                            result.testSuiteDetails.incrementNumFailedTestsInClass(result.testClass, result.durationNanos);
                        }

                        // If all tests in class are complete then report the class as finished.
                        if (result.testSuiteDetails.isClassComplete(result.testClass)) {
                            System.out.println(result.testClass.getName() + ": tests [" + result.testSuiteDetails.getNumTestsInClass(result.testClass) + "] successes: " + result.testSuiteDetails.getTotalNumSuccessfulTestsInClass(result.testClass) + ", failures: " + result.testSuiteDetails.getTotalNumFailedTestsInClass(result.testClass) + ", seconds: " + nanosToSecondsString(result.testSuiteDetails.getTotalDurationForClass(result.testClass)) + "\n");
                            LOGGER.log("Witnessed all tests in class: " + result.testClass.getName());
                        }

                        // If all test classes are complete then report the suite as finished and exit.
                        if (result.testSuiteDetails.isSuiteComplete()) {
                            System.out.println("Total tests [" + result.testSuiteDetails.getTotalNumTests() + "] successes: " + result.testSuiteDetails.getTotalNumSuccessfulTests() + ", failures: " + result.testSuiteDetails.getTotalNumFailedTests() + ", seconds: " + nanosToSecondsString(result.testSuiteDetails.getTotalSuiteDuration()));
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

    @Override
    public String toString() {
        return this.getClass().getName() + (this.isAlive ? " { [running] }" : " { [shutdown] }");
    }
}
