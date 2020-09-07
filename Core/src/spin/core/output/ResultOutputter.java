package spin.core.output;

import spin.core.server.response.ErrorResponse;
import spin.core.server.response.ServerResponse;
import spin.core.server.session.RequestSessionContext;
import spin.core.server.response.RunSuiteResponse;
import spin.core.execution.TestResult;
import spin.core.lifecycle.PanicOnlyMonitor;
import spin.core.util.CloseableBlockingQueue;
import spin.core.util.Logger;
import spin.core.util.ObjectChecker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

/**
 * The class that is responsible for outputting the test results to the console.
 */
public final class ResultOutputter implements Runnable {
    private static final Logger LOGGER = Logger.forClass(ResultOutputter.class);
    private final CyclicBarrier barrier;
    private final PanicOnlyMonitor shutdownMonitor;
    private final CloseableBlockingQueue<TestResult> resultQueue;
    private final Connection dbConnection;
    private volatile boolean isAlive = true;

    private ResultOutputter(CyclicBarrier barrier, PanicOnlyMonitor shutdownMonitor, CloseableBlockingQueue<TestResult> resultQueue, Connection dbConnection) {
        ObjectChecker.assertNonNull(barrier, shutdownMonitor, resultQueue);
        this.barrier = barrier;
        this.shutdownMonitor = shutdownMonitor;
        this.resultQueue = resultQueue;
        this.dbConnection = dbConnection;
    }

    /**
     * Creates a new result outputter that expects to witness the specified number of tests per each class as given by
     * the mapping and which expects to find all of the test results on the queue given to it.
     *
     * @param barrier The barrier to wait on before running.
     * @param shutdownMonitor The shutdown monitor.
     * @param resultQueue The queue that test results may be coming in on asynchronously.
     * @return the new outputter.
     */
    public static ResultOutputter outputter(CyclicBarrier barrier, PanicOnlyMonitor shutdownMonitor, CloseableBlockingQueue<TestResult> resultQueue) {
        return new ResultOutputter(barrier, shutdownMonitor, resultQueue, null);
    }

    /**
     * Creates a new result outputter that expects to witness the specified number of tests per each class as given by
     * the mapping and which expects to find all of the test results on the queue given to it.
     *
     * As each entry comes in it will be written to a database using the database writer.
     *
     * @param barrier The barrier to wait on before running.
     * @param shutdownMonitor The shutdown monitor.
     * @param resultQueue The queue that test results may be coming in on asynchronously.
     * @param dbConnection The database connection.
     * @return the new outputter.
     */
    public static ResultOutputter outputterToConsoleAndDb(CyclicBarrier barrier, PanicOnlyMonitor shutdownMonitor, CloseableBlockingQueue<TestResult> resultQueue, Connection dbConnection) {
        ObjectChecker.assertNonNull(dbConnection);
        return new ResultOutputter(barrier, shutdownMonitor, resultQueue, dbConnection);
    }

    @Override
    public void run() {
        TestResult result = null;

        try {
            LOGGER.log("Waiting on the other threads to hit the barrier.");
            this.barrier.await();
            LOGGER.log(Thread.currentThread().getName() + " thread started.");

            System.out.println("\n===============================================================");
            while (this.isAlive) {
                if (!this.isAlive) {
                    break;
                }

                LOGGER.log("Checking for new results...");

                result = this.resultQueue.poll(5, TimeUnit.MINUTES);

                if (result != null) {
                    LOGGER.log("New result obtained.");

                    // If we were given an empty suite then we can do all our work right here now.
                    if (result.isEmptySuite) {
                        if (!result.emptyClasses.isEmpty()) {
                            writeEmptyClassResultToDatabase(result.emptyClasses.iterator().next().getName(), result.testSuiteDbId);
                        }
                        writeEmptySuiteResultToDatabase(result.testSuiteDbId);
                        break;
                    }

                    // Report the test as successful or failed.
                    System.out.println("\nTEST RESULT:");
                    System.out.println("\tTest: " + result.testMethod.getName() + ", Class: " + result.testClass.getName());
                    System.out.println("\t" + ExecutionStatus.SUCCESS + ", duration: " + nanosToSecondsString(result.durationNanos));

                    if (result.status == ExecutionStatus.SUCCESS) {
                        result.testSuiteDetails.incrementNumSuccessfulTestsInClass(result.testClass, result.durationNanos);
                    } else {
                        result.testSuiteDetails.incrementNumFailedTestsInClass(result.testClass, result.durationNanos);
                    }

                    // Display the test's output.
                    if (result.testStdout.length > 0) {
                        System.out.println("\t---- stdout ----");
                        System.out.print(new String(result.testStdout, StandardCharsets.UTF_8));
                        System.out.println("\t----------------");
                    }
                    if (result.testStderr.length > 0) {
                        System.err.println("\t---- stderr ----");
                        System.err.print(new String(result.testStderr, StandardCharsets.UTF_8));
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

                        sendResponse(result.sessionContext, RunSuiteResponse.newResponse(result.testSuiteDbId));
                        LOGGER.log("Witnessed all tests in suite.");
                        this.isAlive = false;
                        break;
                    }
                }
            }

        } catch (Throwable t) {
            if (result != null) {
                try {
                    sendResponse(result.sessionContext, ErrorResponse.newResponse("Unexpected error: " + t.getMessage()));
                } catch (ClosedChannelException e) {
                    // Nothing to do. We are already in a panic. Ignore this.
                }
            }
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
            LOGGER.log("Exiting.");
        }
    }

    /**
     * Returns true iff this outputter is still alive.
     *
     * @return whether or not this outputter is alive.
     */
    public boolean isAlive() {
        return this.isAlive;
    }

    /**
     * Shuts down this outputter. Note that the outputter should be interrupted after invoking this method for immediate
     * effectiveness.
     */
    public void shutdown() {
        this.isAlive = false;
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
                    + (testResult.status == ExecutionStatus.SUCCESS ? 1 : 0) + "', '"
                    + new String(testResult.testStdout, StandardCharsets.UTF_8) + "', '"
                    + new String(testResult.testStderr, StandardCharsets.UTF_8) + "', "
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

    private void writeEmptyClassResultToDatabase(String testClassName, int suiteDbId) throws SQLException {
        if (this.dbConnection != null) {
            Statement statement = this.dbConnection.createStatement();
            statement.execute("INSERT INTO test_class(id, name, num_tests, num_success, num_failures, duration, suite)"
                    + " VALUES(0, '" + testClassName + "', 0, 0, 0, 0, " + suiteDbId + ")");
        }
    }

    private void writeEmptySuiteResultToDatabase(int suiteDbId) throws SQLException {
        if (this.dbConnection != null) {
            Statement statement = this.dbConnection.createStatement();
            statement.execute("UPDATE test_suite SET num_tests = 0, "
                    + " num_success = 0, "
                    + " num_failures = 0, "
                    + " duration = 0"
                    + " WHERE id = " + suiteDbId);
        }
    }

    private static void sendResponse(RequestSessionContext sessionContext, ServerResponse response) throws ClosedChannelException {
        sessionContext.clientSession.putServerResponse(response.toJsonString() + "\n");
        sessionContext.clientSession.terminateSession();
        sessionContext.socketChannel.register(sessionContext.selector, SelectionKey.OP_WRITE, sessionContext.clientSession);
        sessionContext.selector.wakeup();
    }

    private static String nanosToSecondsString(long nanos) {
        return BigDecimal.valueOf(nanos).setScale(4, RoundingMode.HALF_DOWN)
                .divide(BigDecimal.valueOf(1_000_000_000L), RoundingMode.HALF_DOWN).setScale(4, RoundingMode.HALF_DOWN)
                .toPlainString();
    }
}
