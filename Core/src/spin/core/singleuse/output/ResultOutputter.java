package spin.core.singleuse.output;

import spin.core.singleuse.execution.TestResult;
import spin.core.singleuse.lifecycle.LifecycleListener;
import spin.core.singleuse.lifecycle.ShutdownMonitor;
import spin.core.singleuse.util.CloseableBlockingQueue;
import spin.core.singleuse.util.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The class that is responsible for outputting the test results to the console.
 *
 * This class has a finite amount of work to do and when it is complete it will notify the {@link LifecycleListener}.
 */
public final class ResultOutputter implements Runnable {
    private static final Logger LOGGER = Logger.forClass(ResultOutputter.class);
    private final ShutdownMonitor shutdownMonitor;
    private final LifecycleListener lifecycleListener;
    private final List<CloseableBlockingQueue<TestResult>> incomingResultQueues;
    private final Connection dbConnection;
    private volatile boolean isAlive = true;

    private ResultOutputter(ShutdownMonitor shutdownMonitor, LifecycleListener lifecycleListener, List<CloseableBlockingQueue<TestResult>> incomingResultQueues, Connection dbConnection) {
        if (shutdownMonitor == null) {
            throw new NullPointerException("shutdownMonitor must be non-null.");
        }
        if (lifecycleListener == null) {
            throw new NullPointerException("lifecycleListener must be non-null.");
        }
        if (incomingResultQueues == null) {
            throw new NullPointerException("incomingResultQueues must be non-null.");
        }
        this.shutdownMonitor = shutdownMonitor;
        this.lifecycleListener = lifecycleListener;
        this.incomingResultQueues = incomingResultQueues;
        this.dbConnection = dbConnection;
    }

    /**
     * Creates a new result outputter that expects to witness the specified number of tests per each class as given by
     * the mapping and which expects to find all of the test results on the list of queues given to it.
     *
     * @param shutdownMonitor The shutdown monitor.
     * @param lifecycleListener The lifecycle listener.
     * @param incomingResultQueues The queues that test results may be coming in on asynchronously.
     * @return the new outputter.
     */
    public static ResultOutputter outputter(ShutdownMonitor shutdownMonitor, LifecycleListener lifecycleListener, List<CloseableBlockingQueue<TestResult>> incomingResultQueues) {
        return new ResultOutputter(shutdownMonitor, lifecycleListener, incomingResultQueues, null);
    }

    /**
     * Creates a new result outputter that expects to witness the specified number of tests per each class as given by
     * the mapping and which expects to find all of the test results on the list of queues given to it.
     *
     * As each entry comes in it will be written to a database using the database writer.
     *
     * @param shutdownMonitor The shutdown monitor.
     * @param lifecycleListener The lifecycle listener.
     * @param incomingResultQueues The queues that test results may be coming in on asynchronously.
     * @param dbConnection The database connection.
     * @return the new outputter.
     */
    public static ResultOutputter outputterToConsoleAndDb(ShutdownMonitor shutdownMonitor, LifecycleListener lifecycleListener, List<CloseableBlockingQueue<TestResult>> incomingResultQueues, Connection dbConnection) {
        if (dbConnection == null) {
            throw new NullPointerException("dbConnection must be non-null.");
        }
        return new ResultOutputter(shutdownMonitor, lifecycleListener, incomingResultQueues, dbConnection);
    }

    @Override
    public void run() {
        try {
            while (this.isAlive) {
                for (CloseableBlockingQueue<TestResult> incomingResultQueue : this.incomingResultQueues) {
                    if (!this.isAlive) {
                        break;
                    }

                    LOGGER.log("Checking for new results...");

                    TestResult result = incomingResultQueue.poll(5, TimeUnit.MINUTES);

                    if (result != null) {
                        System.out.println("\n===============================================================");
                        LOGGER.log("New result obtained.");

                        // Report the test as successful or failed.
                        System.out.println("\nTEST RESULT:");
                        if (result.successful) {
                            System.out.println("\tTest: " + result.testMethod.getName() + ", Class: " + result.testClass.getName());
                            System.out.println("\tSUCCESS, duration: " + nanosToSecondsString(result.durationNanos));
                            result.testSuiteDetails.incrementNumSuccessfulTestsInClass(result.testClass, result.durationNanos);
                        } else {
                            System.out.println("\tTest: " + result.testMethod.getName() + ", Class: " + result.testClass.getName());
                            System.out.println("\tFAILED, duration: " + nanosToSecondsString(result.durationNanos));
                            result.testSuiteDetails.incrementNumFailedTestsInClass(result.testClass, result.durationNanos);
                        }

                        // Display the test's output.
                        if (!result.stdout.isEmpty()) {
                            System.out.println("\t---- stdout ----");
                            System.out.print(result.stdout);
                            System.out.println("\t----------------");
                        }
                        if (!result.stderr.isEmpty()) {
                            System.err.println("\t---- stderr ----");
                            System.err.print(result.stderr);
                            System.err.println("\t----------------");
                        }
                        writeTestResultToDatabase(result);

                        // If all tests in class are complete then report the class as finished.
                        if (result.testSuiteDetails.isClassComplete(result.testClass)) {
                            System.out.println("\nCLASS RESULT:");
                            System.out.println("\tClass: " + result.testClass.getName());
                            System.out.println("\tTests: " + result.testSuiteDetails.getNumTestsInClass(result.testClass) + ", Successes: " + result.testSuiteDetails.getTotalNumSuccessfulTestsInClass(result.testClass) + ", failures: " + result.testSuiteDetails.getTotalNumFailedTestsInClass(result.testClass));
                            System.out.println("\tDuration: " + nanosToSecondsString(result.testSuiteDetails.getTotalDurationForClass(result.testClass)));
                            writeClassResultToDatabase(result);
                            LOGGER.log("Witnessed all tests in class: " + result.testClass.getName());
                        }

                        // If all test classes are complete then report the suite as finished and exit.
                        if (result.testSuiteDetails.isSuiteComplete()) {
                            System.out.println("\nSUITE RESULT:");
                            System.out.println("\tTests: " + result.testSuiteDetails.getTotalNumTests() + ", successes: " + result.testSuiteDetails.getTotalNumSuccessfulTests() + ", failures: " + result.testSuiteDetails.getTotalNumFailedTests());
                            System.out.println("\tDuration: " + nanosToSecondsString(result.testSuiteDetails.getTotalSuiteDuration()));
                            writeSuiteResultToDatabase(result);
                            LOGGER.log("Witnessed all tests in suite.");
                            this.isAlive = false;
                            break;
                        }
                    }
                }
            }

        } catch (Throwable t) {
            this.shutdownMonitor.panic(t);
        } finally {
            System.out.println("===============================================================");
            if (this.dbConnection != null) {
                try {
                    this.dbConnection.close();
                } catch (SQLException e) {
                    LOGGER.log("Unexpected error closing database connection.");
                    e.printStackTrace();
                }
            }
            this.lifecycleListener.notifyDone();
            LOGGER.log("Exiting.");
        }
    }

    @Override
    public String toString() {
        return this.getClass().getName() + (this.isAlive ? " { [running] }" : " { [shutdown] }");
    }

    private void writeTestResultToDatabase(TestResult testResult) throws SQLException {
        if (this.dbConnection != null) {
            Statement statement = this.dbConnection.createStatement();
            statement.execute("INSERT INTO test(name, is_success, stdout, stderr, duration, class) VALUES('"
                    + testResult.testMethod.getName() + "', '"
                    + (testResult.successful ? 1 : 0) + "', '"
                    + testResult.stdout + "', '"
                    + testResult.stderr + "', "
                    + testResult.durationNanos + ", "
                    + testResult.testClassDbId + ")");
        }
    }

    private void writeClassResultToDatabase(TestResult testResult) throws SQLException {
        if (this.dbConnection != null) {
            Statement statement = this.dbConnection.createStatement();
            statement.execute("UPDATE test_class SET name = '" + testResult.testClass.getName() + "',"
                    + " num_tests = " + testResult.testSuiteDetails.getNumTestsInClass(testResult.testClass) + ", "
                    + " num_success = " + testResult.testSuiteDetails.getTotalNumSuccessfulTestsInClass(testResult.testClass) + ", "
                    + " num_failures = " + testResult.testSuiteDetails.getTotalNumFailedTestsInClass(testResult.testClass) + ", "
                    + " duration = " + testResult.testSuiteDetails.getTotalDurationForClass(testResult.testClass)
                    + " WHERE id = " + testResult.testClassDbId);
        }
    }

    private void writeSuiteResultToDatabase(TestResult testResult) throws SQLException {
        if (this.dbConnection != null) {
            Statement statement = this.dbConnection.createStatement();
            statement.execute("UPDATE test_suite SET num_tests = " + testResult.testSuiteDetails.getTotalNumTests() + ", "
                    + " num_success = " + testResult.testSuiteDetails.getTotalNumSuccessfulTests() + ", "
                    + " num_failures = " + testResult.testSuiteDetails.getTotalNumFailedTests() + ", "
                    + " duration = " + testResult.testSuiteDetails.getTotalSuiteDuration()
                    + " WHERE id = " + testResult.testSuiteDbId);
        }
    }

    private static String nanosToSecondsString(long nanos) {
        return BigDecimal.valueOf(nanos).setScale(4, RoundingMode.HALF_DOWN)
                .divide(BigDecimal.valueOf(1_000_000_000L), RoundingMode.HALF_DOWN).setScale(4, RoundingMode.HALF_DOWN)
                .toPlainString();
    }
}
