package spin.core.runner;

import spin.core.lifecycle.PanicOnlyMonitor;
import spin.core.server.session.RequestSessionContext;
import spin.core.server.request.RunSuiteClientRequest;
import spin.core.server.response.RunSuiteResponse;
import spin.core.execution.TestInfo;
import spin.core.type.Result;
import spin.core.util.CloseableBlockingQueue;
import spin.core.util.Logger;
import spin.core.util.ObjectChecker;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * A class that is responsible for receiving a {@link RunSuiteClientRequest} object and for loading all of the test suite
 * classes, constructing whatever additional information is required, and handing these tests off to any of the outgoing
 * queues it has so that they may be picked up with all the required context by some downstream consumer.
 */
public final class TestSuiteRunner implements Runnable {
    private static final Logger LOGGER = Logger.forClass(TestSuiteRunner.class);
    private final Object monitor = new Object();
    private final PanicOnlyMonitor shutdownMonitor;
    private final CyclicBarrier barrier;
    private final List<CloseableBlockingQueue<TestInfo>> outgoingTestQueues;
    private final Connection dbConnection;
    private volatile boolean isAlive = true;
    private RunRequest runRequest = null;

    private TestSuiteRunner(CyclicBarrier barrier, PanicOnlyMonitor shutdownMonitor, List<CloseableBlockingQueue<TestInfo>> outgoingTestQueues, Connection dbConnection) {
        ObjectChecker.assertNonNull(barrier, shutdownMonitor, outgoingTestQueues);
        this.barrier = barrier;
        this.shutdownMonitor = shutdownMonitor;
        this.outgoingTestQueues = outgoingTestQueues;
        this.dbConnection = dbConnection;
    }

    /**
     * Constructs a new suite runner that will put all of the tests it receives into the given outgoing queues.
     * It will attempt to add tests to these queues fairly so that they each receive a roughly equal load.
     *
     * @param barrier The barrier to wait on before running.
     * @param shutdownMonitor The shutdown monitor.
     * @param outgoingTestQueues The queues to load the tests into.
     * @return the suite runner.
     */
    public static TestSuiteRunner withOutgoingQueue(CyclicBarrier barrier, PanicOnlyMonitor shutdownMonitor, List<CloseableBlockingQueue<TestInfo>> outgoingTestQueues) {
        return new TestSuiteRunner(barrier, shutdownMonitor, outgoingTestQueues, null);
    }

    /**
     * Constructs a new suite runner that will put all of the tests it receives into the given queues. It will attempt
     * to add tests to these queues fairly so that they each receive a roughly equal load.
     *
     * This test suite will write all of the tests, test classes and suites it receives into a database using the given
     * database writer.
     *
     * @param barrier The barrier to wait on before running.
     * @param shutdownMonitor The shutdown monitor.
     * @param outgoingTestQueues The queues to load the tests into.
     * @param dbConnection The database connection.
     * @return the suite runner.
     */
    public static TestSuiteRunner withDatabaseWriter(CyclicBarrier barrier, PanicOnlyMonitor shutdownMonitor, List<CloseableBlockingQueue<TestInfo>> outgoingTestQueues, Connection dbConnection) {
        ObjectChecker.assertNonNull(dbConnection);
        return new TestSuiteRunner(barrier, shutdownMonitor, outgoingTestQueues, dbConnection);
    }

    @Override
    public void run() {
        try {
            LOGGER.log("Waiting for other threads to hit barrier.");
            this.barrier.await();
            LOGGER.log(Thread.currentThread().getName() + " thread started.");

            while (this.isAlive) {
                try {
                    LOGGER.log("Attempting to fetch next test suite request to load...");
                    RunRequest request = this.blockingGetNextRequest();
                    if (request != null) {
                        LOGGER.log("Got next test suite request to load.");
                        TestSuite testSuite = createTestSuiteFromRequest(request);
                        LOGGER.log("Loaded test suite.");

                        // Load all of the submitted test classes.
                        LOGGER.log("Loading all test classes as Class objects.");
                        Map<Class<?>, List<TestInfo>> classToTestInfoMap = new HashMap<>();
                        List<Class<?>> testClasses = new ArrayList<>();
                        for (String className : testSuite.testClassPaths) {
                            testClasses.add(testSuite.classLoader.loadClass(className));
                        }

                        TestSuiteDetails testSuiteDetails = new TestSuiteDetails();

                        // Split out each of the test methods declared in the given test classes.
                        List<TestInfo> allTestInfos = createTestInfos(testSuite, testClasses, testSuiteDetails, classToTestInfoMap);

                        for (Map.Entry<Class<?>, List<TestInfo>> classToInfoEntry : classToTestInfoMap.entrySet()) {
                            testSuiteDetails.setNumTestsPerClass(classToInfoEntry.getKey(), classToInfoEntry.getValue().size());
                        }

                        logTestMethods(classToTestInfoMap);

                        writeInitialValuesToDatabase(classToTestInfoMap, allTestInfos, testSuite.suiteId);

                        // Execute each of the declared test methods.
                        runTests(testSuite, allTestInfos, classToTestInfoMap);
                    }
                } catch (ClassNotFoundException | InterruptedException e) {
                    LOGGER.log("Unexpected error.");
                    e.printStackTrace();
                }
            }

        } catch (Throwable t) {
            this.shutdownMonitor.panic(t);
        } finally {
            this.isAlive = false;
            if (this.dbConnection != null) {
                try {
                    this.dbConnection.close();
                } catch (SQLException e) {
                    LOGGER.log("Encountered error closing database connection.");
                    e.printStackTrace();
                }
            }
            LOGGER.log("Exiting.");
        }
    }

    /**
     * Shuts down this runner.
     */
    public void shutdown() {
        this.isAlive = false;
        synchronized (this.monitor) {
            this.monitor.notifyAll();
        }
    }

    /**
     * Attempts to add the specified request to this runner.
     *
     * Returns a successful result that holds the suite id of the newly submitted request or else an error result if the
     * request could not be submitted.
     *
     * @param request The request to submit.
     * @param timeout The timeout duration.
     * @param unit The time units the duration is specified in.
     * @return the result of adding the request.
     */
    public Result<Integer> addRequest(RunSuiteClientRequest request, long timeout, TimeUnit unit) throws InterruptedException {
        ObjectChecker.assertNonNull(request, unit);
        ObjectChecker.assertPositive(timeout);

        long currentTime = System.nanoTime();
        long deadline = currentTime + unit.toNanos(timeout);

        synchronized (this.monitor) {
            while ((this.isAlive) && (currentTime < deadline) && (this.runRequest != null)) {
                this.monitor.wait(TimeUnit.NANOSECONDS.toMillis(deadline - currentTime));
                currentTime = System.nanoTime();
            }

            if ((this.isAlive) && (currentTime < deadline)) {
                this.runRequest = new RunRequest(request);
                this.monitor.notifyAll();
                return Result.successful(this.runRequest.id);
            } else if (!this.isAlive) {
                return Result.error("Unable to add request: runner is shutdown.");
            } else {
                return Result.error("Timed out waiting to add request.");
            }
        }
    }

    private RunRequest blockingGetNextRequest() throws InterruptedException {
        synchronized (this.monitor) {
            while ((this.isAlive) && (this.runRequest == null)) {
                this.monitor.wait();
            }
            RunRequest nextRequest = this.isAlive ? this.runRequest : null;
            this.runRequest = null;
            this.monitor.notifyAll();
            return nextRequest;
        }
    }

    private static TestSuite createTestSuiteFromRequest(RunRequest runRequest) throws IOException {
        File baseDir = new File(runRequest.request.getBaseDirectory());
        if (!baseDir.exists()) {
            throw new IllegalStateException("Tests base dir does not exist.");
        }
        if (!baseDir.isDirectory()) {
            throw new IllegalStateException("Tests base dir is not a directory.");
        }
        List<String> classNames = new ArrayList<>();
        fetchAllFullyQualifiedTestClassNames(Pattern.compile(runRequest.request.getMatcher()), baseDir.getCanonicalPath().length(), baseDir, classNames);

        LOGGER.log("Number of given dependencies: " + (runRequest.request.getDependencies().length - 1));

        URL[] dependencyUrls = new URL[runRequest.request.getDependencies().length];
        for (int i = 0; i < runRequest.request.getDependencies().length; i++) {
            dependencyUrls[i] = new File(runRequest.request.getDependencies()[i]).toURI().toURL();
        }
        URLClassLoader classLoader = new URLClassLoader(dependencyUrls);

        return new TestSuite(classNames, classLoader, runRequest.request.getSessionContext(), runRequest.id);
    }

    private void runTests(TestSuite testSuite, List<TestInfo> testInfos, Map<Class<?>, List<TestInfo>> classToTestInfoMap) throws SQLException, ClosedChannelException, InterruptedException {
        if (testInfos.isEmpty()) {
            // If we had zero tests to submit then our downstream consumers will never receive anything
            // and wait forever. In this case, we write the results to the database, respond to the client
            // and notify the lifecycle listener that we are done.
            if (!testSuite.testClassPaths.isEmpty()) {
                writeEmptyClassResultToDatabase(classToTestInfoMap.keySet().iterator().next().getName(), testSuite.suiteId);
            }
            writeSuiteResultToDatabase(testSuite.suiteId);
            sendResponse(testSuite.sessionContext, RunSuiteResponse.newResponse(testSuite.suiteId));
        } else {
            int index = 0;
            while (index < testInfos.size()) {
                if (!this.isAlive) {
                    break;
                }

                for (CloseableBlockingQueue<TestInfo> outgoingTestQueue : this.outgoingTestQueues) {
                    if (!this.isAlive) {
                        break;
                    }

                    LOGGER.log("Attempting to submit test #" + (index + 1) + " of " + testInfos.size());
                    if (outgoingTestQueue.add(testInfos.get(index), 15, TimeUnit.SECONDS)) {
                        LOGGER.log("Submitted test #" + (index + 1));
                        index++;
                    }

                    if (index >= testInfos.size()) {
                        break;
                    }
                }
            }
        }
    }

    private List<TestInfo> createTestInfos(TestSuite testSuite, List<Class<?>> testClasses, TestSuiteDetails testSuiteDetails, Map<Class<?>, List<TestInfo>> classToTestInfoMap) {
        List<TestInfo> allTestInfos = new ArrayList<>();
        int classDbId = 0;
        for (Class<?> testClass : testClasses) {
            List<TestInfo> testInfos = new ArrayList<>();
            for (Method method : testClass.getDeclaredMethods()) {
                if (method.getAnnotation(org.junit.Test.class) != null) {
                    TestInfo testInfo = new TestInfo(testClass, method, testSuiteDetails, testSuite.sessionContext);
                    testInfos.add(testInfo);
                    allTestInfos.add(testInfo);

                    if (this.dbConnection != null) {
                        testInfo.setTestClassDatabaseId(classDbId);
                        testInfo.setTestSuiteDatabaseId(testSuite.suiteId);
                    }
                }
            }
            classToTestInfoMap.put(testClass, testInfos);

            if (this.dbConnection != null) {
                classDbId++;
            }
        }
        return allTestInfos;
    }

    private static void fetchAllFullyQualifiedTestClassNames(Pattern testPattern, int baseDirLength, File currDir, List<String> classNames) throws IOException {
        for (File file : currDir.listFiles()) {
            if (file.isFile() && testPattern.matcher(file.getName()).matches()) {
                String fullPath = file.getCanonicalPath();
                String classPathWithBaseDirStripped = fullPath.substring(baseDirLength);
                String classNameWithSuffixStripped = classPathWithBaseDirStripped.substring(0, classPathWithBaseDirStripped.length() - ".class".length());
                String classNameBinaryformat = classNameWithSuffixStripped.replaceAll("/", ".");

                if (classNameBinaryformat.startsWith(".")) {
                    classNameBinaryformat = classNameBinaryformat.substring(1);
                }

                LOGGER.log("Binary name of submitted test class: " + classNameBinaryformat);
                classNames.add(classNameBinaryformat);
            } else if (file.isDirectory()) {
                fetchAllFullyQualifiedTestClassNames(testPattern, baseDirLength, file, classNames);
            }
        }
    }

    private void writeInitialValuesToDatabase(Map<Class<?>, List<TestInfo>> classToTestInfoMap, List<TestInfo> allTestInfos, int suiteDbId) throws SQLException {
        if (this.dbConnection != null) {
            Statement statement = this.dbConnection.createStatement();
            statement.execute("INSERT INTO test_suite(id, num_tests) VALUES(" + suiteDbId + ", " + allTestInfos.size() + ")");

            for (Map.Entry<Class<?>, List<TestInfo>> testCountEntry : classToTestInfoMap.entrySet()) {
                if (!testCountEntry.getValue().isEmpty()) {
                    // All these test infos are for the same class so they will all report the same class id, we can ask any of them for the value.
                    int id = testCountEntry.getValue().get(0).getTestClassDatabaseId();

                    statement = this.dbConnection.createStatement();
                    statement.execute("INSERT INTO test_class(id, name, num_tests, suite) VALUES(" + id + ", '"
                            + testCountEntry.getKey().getName() + "', "
                            + testCountEntry.getValue().size() + ", "
                            + suiteDbId + ")");
                }
            }
        }
    }

    private static void logTestMethods(Map<Class<?>, List<TestInfo>> classToTestInfoMap) {
        for (List<TestInfo> testInfos : classToTestInfoMap.values()) {
            for (TestInfo testInfo : testInfos) {
                LOGGER.log("Test: " + testInfo);
            }
        }
    }

    private void writeEmptyClassResultToDatabase(String testClassName, int suiteDbId) throws SQLException {
        if (this.dbConnection != null) {
            Statement statement = this.dbConnection.createStatement();
            statement.execute("INSERT INTO test_class(id, name, num_tests, num_success, num_failures, duration, suite)"
                    + " VALUES(0, '" + testClassName + "', 0, 0, 0, 0, " + suiteDbId + ")");
        }
    }

    private void writeSuiteResultToDatabase(int suiteDbId) throws SQLException {
        if (this.dbConnection != null) {
            Statement statement = this.dbConnection.createStatement();
            statement.execute("UPDATE test_suite SET num_tests = 0, "
                    + " num_success = 0, "
                    + " num_failures = 0, "
                    + " duration = 0"
                    + " WHERE id = " + suiteDbId);
        }
    }

    private static void sendResponse(RequestSessionContext sessionContext, RunSuiteResponse response) throws ClosedChannelException {
        sessionContext.clientSession.putServerResponse(response.toJsonString() + "\n");
        sessionContext.clientSession.terminateSession();
        sessionContext.socketChannel.register(sessionContext.selector, SelectionKey.OP_WRITE, sessionContext.clientSession);
        sessionContext.selector.wakeup();
    }

    @Override
    public String toString() {
        return this.getClass().getName() + (this.isAlive ? " { [running] }" : " { [shutdown] }");
    }

    private static final class RunRequest {
        private static int ids = 0;
        private final int id;
        private final RunSuiteClientRequest request;

        private RunRequest(RunSuiteClientRequest request) {
            this.id = ids++;
            this.request = request;
        }
    }
}
