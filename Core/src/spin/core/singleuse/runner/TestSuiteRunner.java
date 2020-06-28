package spin.core.singleuse.runner;

import spin.core.singleuse.execution.TestInfo;
import spin.core.singleuse.lifecycle.LifecycleListener;
import spin.core.singleuse.lifecycle.ShutdownMonitor;
import spin.core.singleuse.util.CloseableBlockingQueue;
import spin.core.singleuse.util.Logger;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * A class that is responsible for receiving a {@link TestSuite} object and for loading all of its classes, constructing
 * whatever additional information is required, and handing these tests off to any of the outgoing queues it has so that
 * they may be picked up with all the required context by some downstream consumer.
 */
public final class TestSuiteRunner implements Runnable {
    private static final Logger LOGGER = Logger.forClass(TestSuiteRunner.class);
    private final Object monitor = new Object();
    private final ShutdownMonitor shutdownMonitor;
    private final LifecycleListener lifecycleListener;
    private final List<CloseableBlockingQueue<TestInfo>> outgoingTestQueues;
    private final CloseableBlockingQueue<TestSuite> incomingSuiteQueue;
    private final Connection dbConnection;
    private volatile boolean isAlive = true;

    private TestSuiteRunner(LifecycleListener listener, ShutdownMonitor shutdownMonitor, CloseableBlockingQueue<TestSuite> incomingSuiteQueue, List<CloseableBlockingQueue<TestInfo>> outgoingTestQueues, Connection dbConnection) {
        if (listener == null) {
            throw new NullPointerException("listener must be non-null.");
        }
        if (shutdownMonitor == null) {
            throw new NullPointerException("shutdownMonitor must be non-null.");
        }
        if (incomingSuiteQueue == null) {
            throw new NullPointerException("incomingSuiteQueue must be non-null.");
        }
        if (outgoingTestQueues == null) {
            throw new NullPointerException("outgoingTestQueues must be non-null.");
        }
        this.lifecycleListener = listener;
        this.shutdownMonitor = shutdownMonitor;
        this.incomingSuiteQueue = incomingSuiteQueue;
        this.outgoingTestQueues = outgoingTestQueues;
        this.dbConnection = dbConnection;
    }

    /**
     * Constructs a new suite runner that will put all of the tests it receives into the given queues. It will attempt
     * to add tests to these queues fairly so that they each receive a roughly equal load.
     *
     * @param listener The life-cycle listener.
     * @param shutdownMonitor The shutdown monitor.
     * @param incomingSuiteQueue The queue in which test suites are loaded into.
     * @param outgoingTestQueues The queues to load the tests into.
     * @return the suite runner.
     */
    public static TestSuiteRunner withOutgoingQueue(LifecycleListener listener, ShutdownMonitor shutdownMonitor, CloseableBlockingQueue<TestSuite> incomingSuiteQueue, List<CloseableBlockingQueue<TestInfo>> outgoingTestQueues) {
        return new TestSuiteRunner(listener, shutdownMonitor, incomingSuiteQueue, outgoingTestQueues, null);
    }

    /**
     * Constructs a new suite runner that will put all of the tests it receives into the given queues. It will attempt
     * to add tests to these queues fairly so that they each receive a roughly equal load.
     *
     * This test suite will writer all of the tests, test classes and suites it receives into a database using the given
     * database writer.
     *
     * @param listener The life-cycle listener.
     * @param shutdownMonitor The shutdown monitor.
     * @param incomingSuiteQueue The queue in which test suites are loaded into.
     * @param outgoingTestQueues The queues to load the tests into.
     * @param dbConnection The database connection.
     * @return the suite runner.
     */
    public static TestSuiteRunner withDatabaseWriter(LifecycleListener listener, ShutdownMonitor shutdownMonitor, CloseableBlockingQueue<TestSuite> incomingSuiteQueue, List<CloseableBlockingQueue<TestInfo>> outgoingTestQueues, Connection dbConnection) {
        if (dbConnection == null) {
            throw new NullPointerException("dbConnection must be non-null.");
        }
        return new TestSuiteRunner(listener, shutdownMonitor, incomingSuiteQueue, outgoingTestQueues, dbConnection);
    }

    @Override
    public void run() {
        try {

            while (this.isAlive) {
                int suiteDbId = 0;

                try {
                    LOGGER.log("Attempting to fetch next test suite to load...");
                    TestSuite testSuite = this.incomingSuiteQueue.poll(5, TimeUnit.MINUTES);
                    if (testSuite != null) {
                        LOGGER.log("Got next test suite to load.");

                        // Grab all of the submitted test classes. We want only the binary names of these classes so we can load them.
                        String[] classes = new String[testSuite.testClassPaths.length];
                        for (int i = 0; i < testSuite.testClassPaths.length; i++) {
                            String classPathWithBaseDirStripped = testSuite.testClassPaths[i].substring(testSuite.testBaseDirPath.length());
                            String classNameWithSuffixStripped = classPathWithBaseDirStripped.substring(0, classPathWithBaseDirStripped.length() - ".class".length());
                            String classNameBinaryformat = classNameWithSuffixStripped.replaceAll("/", ".");

                            LOGGER.log("Binary name of submitted test class: " + classNameBinaryformat);
                            classes[i] = classNameBinaryformat;
                        }

                        // Load all of the submitted test classes.
                        LOGGER.log("Loading all test classes as Class objects.");
                        Map<Class<?>, List<TestInfo>> classToTestInfoMap = new HashMap<>();
                        Class<?>[] testClasses = new Class[classes.length];
                        for (int i = 0; i < classes.length; i++) {
                            testClasses[i] = testSuite.classLoader.loadClass(classes[i]);
                        }

                        TestSuiteDetails testSuiteDetails = new TestSuiteDetails();

                        // Split out each of the test methods declared in the given test classes.
                        List<TestInfo> allTestInfos = new ArrayList<>();
                        int classDbId = 0;
                        for (Class<?> testClass : testClasses) {

                            List<TestInfo> testInfos = new ArrayList<>();
                            for (Method method : testClass.getDeclaredMethods()) {
                                if (method.getAnnotation(org.junit.Test.class) != null) {
                                    TestInfo testInfo = new TestInfo(testClass, method, testSuiteDetails);
                                    testInfos.add(testInfo);
                                    allTestInfos.add(testInfo);

                                    if (this.dbConnection != null) {
                                        testInfo.setTestClassDatabaseId(classDbId);
                                        testInfo.setTestSuiteDatabaseId(suiteDbId);
                                    }
                                }
                            }
                            classToTestInfoMap.put(testClass, testInfos);

                            if (this.dbConnection != null) {
                                classDbId++;
                            }
                        }

                        for (Map.Entry<Class<?>, List<TestInfo>> classToInfoEntry : classToTestInfoMap.entrySet()) {
                            testSuiteDetails.setNumTestsPerClass(classToInfoEntry.getKey(), classToInfoEntry.getValue().size());
                        }

                        logTestMethods(classToTestInfoMap);

                        writeToDatabase(classToTestInfoMap, allTestInfos, suiteDbId);

                        // Execute each of the declared test methods.
                        if (allTestInfos.isEmpty()) {
                            // If we had zero tests to submit then our downstream consumers will never receive anything
                            // and wait forever. In this case, we notify the lifecycle listener that we are done.
                            LOGGER.log("Notifying listener suite is done due to it having zero tests.");
                            this.lifecycleListener.notifyDone();
                        } else {
                            int index = 0;
                            while (index < allTestInfos.size()) {
                                if (!this.isAlive) {
                                    break;
                                }

                                for (CloseableBlockingQueue<TestInfo> outgoingTestQueue : this.outgoingTestQueues) {
                                    if (!this.isAlive) {
                                        break;
                                    }

                                    LOGGER.log("Attempting to submit test #" + (index + 1) + " of " + allTestInfos.size());
                                    if (outgoingTestQueue.add(allTestInfos.get(index), 15, TimeUnit.SECONDS)) {
                                        LOGGER.log("Submitted test #" + (index + 1));
                                        index++;
                                    }

                                    if (index >= allTestInfos.size()) {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } catch (ClassNotFoundException | InterruptedException e) {
                    LOGGER.log("Unexpected error.");
                    e.printStackTrace();
                }

                suiteDbId++;
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

    private void writeToDatabase(Map<Class<?>, List<TestInfo>> classToTestInfoMap, List<TestInfo> allTestInfos, int suiteDbId) throws SQLException {
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

    @Override
    public String toString() {
        return this.getClass().getName() + (this.isAlive ? " { [running] }" : " { [shutdown] }");
    }
}
