package spin.core.singleuse.lifecycle;

import spin.core.singleuse.execution.TestExecutor;
import spin.core.singleuse.execution.TestInfo;
import spin.core.singleuse.execution.TestResult;
import spin.core.singleuse.output.DatabaseConnectionProvider;
import spin.core.singleuse.output.ResultOutputter;
import spin.core.singleuse.runner.TestSuite;
import spin.core.singleuse.runner.TestSuiteRunner;
import spin.core.singleuse.util.CloseableBlockingQueue;
import spin.core.singleuse.util.Logger;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * A holder that has all of the components in the system being life-cycled. This class also provides methods to push
 * those components into various states. This can be viewed as a simple state machine that moves all the components it
 * is holding through the states one by one as it is told.
 */
public final class LifecycleObjectHolder {
    private static final Logger LOGGER = Logger.forClass(LifecycleObjectHolder.class);
    private enum State { INITIAL, OBJECTS_CREATED, OBJECTS_STARTED, OBJECTS_SHUTDOWN }

    private final int queueCapacities;
    private final int numExecutorThreads;
    private final ShutdownMonitor shutdownMonitor;
    private final CloseableBlockingQueue<TestSuite> testSuiteQueue;
    private final boolean writeToDb;
    private State state = State.INITIAL;
    private List<CloseableBlockingQueue<TestInfo>> testInfoQueues = null;
    private List<CloseableBlockingQueue<TestResult>> testResultQueues = null;
    private List<TestExecutor> testExecutors = null;
    private TestSuiteRunner testSuiteRunner = null;
    private List<Thread> executorThreads = null;
    private ResultOutputter resultOutputter = null;
    private Thread outputterThread = null;
    private Thread suiteRunnerThread = null;

    /**
     * Constructs a new life-cycle object holder.
     *
     * @param numThreads The number of executor threads to use.
     * @param queueCapacities The capacities on any communication queues.
     * @param writeToDb Whether or not to write results to database.
     * @param shutdownMonitor The shutdown monitor to be shared by all threads.
     * @param testSuiteQueue The test suite queue, the queue used to push test suites into this managed system from outside.
     */
    public LifecycleObjectHolder(int numThreads, int queueCapacities, boolean writeToDb, ShutdownMonitor shutdownMonitor, CloseableBlockingQueue<TestSuite> testSuiteQueue) {
        if (numThreads < 1) {
            throw new IllegalArgumentException("numThreads must be positive but was: " + numThreads);
        }
        if (queueCapacities < 1) {
            throw new IllegalArgumentException("queueCapacities must be positive but was: " + queueCapacities);
        }
        if (shutdownMonitor == null) {
            throw new NullPointerException("shutdownMonitor must be non-null.");
        }
        if (testSuiteQueue == null) {
            throw new NullPointerException("testSuiteQueue must be non-null.");
        }
        this.numExecutorThreads = numThreads;
        this.queueCapacities = queueCapacities;
        this.shutdownMonitor = shutdownMonitor;
        this.testSuiteQueue = testSuiteQueue;
        this.writeToDb = writeToDb;
    }

    /**
     * Creates all of the lifecycle objects.
     *
     * @param lifecycleListener The lifecycle listener to be notified when the last component has fulfilled its duty. It
     * is safe to shutdown every component when this is true.
     * @param dbConfigPath The path to the database configuration file.
     */
    public void constructAllLifecycleObjects(LifecycleListener lifecycleListener, String dbConfigPath) throws SQLException, IOException {
        if (this.state != State.INITIAL) {
            throw new IllegalStateException("cannot construct life-cycle objects because in state: " + this.state);
        }
        if (lifecycleListener == null) {
            throw new NullPointerException("lifecycleListener must be non-null.");
        }

        LOGGER.log("Creating all life-cycle objects...");
        DatabaseConnectionProvider databaseConnectionProvider = (this.writeToDb) ? new DatabaseConnectionProvider() : null;
        if (databaseConnectionProvider != null) {
            databaseConnectionProvider.initialize(new File(dbConfigPath));

            try (Connection connection = databaseConnectionProvider.getConnection()) {
                clearDatabase(connection);
                createTables(connection);
            }
        }

        this.testInfoQueues = createTestQueues(this.numExecutorThreads);
        this.testResultQueues = createTestResultQueues(this.numExecutorThreads);

        this.testExecutors = createExecutors(this.testInfoQueues, this.testResultQueues);
        this.resultOutputter = (this.writeToDb)
                ? ResultOutputter.outputterToConsoleAndDb(this.shutdownMonitor, lifecycleListener, this.testResultQueues, databaseConnectionProvider.getConnection())
                : ResultOutputter.outputter(this.shutdownMonitor, lifecycleListener, this.testResultQueues);
        this.testSuiteRunner = (this.writeToDb)
                ? TestSuiteRunner.withDatabaseWriter(lifecycleListener, this.shutdownMonitor, this.testSuiteQueue, this.testInfoQueues, databaseConnectionProvider.getConnection())
                : TestSuiteRunner.withOutgoingQueue(lifecycleListener, this.shutdownMonitor, this.testSuiteQueue, this.testInfoQueues);

        this.executorThreads = createExecutorThreads(this.testExecutors);
        this.outputterThread = new Thread(this.resultOutputter, "ResultOutputter");
        this.suiteRunnerThread = new Thread(this.testSuiteRunner, "TestSuiteRunner");
        LOGGER.log("All life-cycle objects created.");

        this.state = State.OBJECTS_CREATED;
    }

    /**
     * Starts any life-cycled objects that require being started.
     */
    public void startAllLifecycleObjects() {
        if (this.state != State.OBJECTS_CREATED) {
            throw new IllegalStateException("cannot start life-cycle objects because in state: " + this.state);
        }

        LOGGER.log("Starting all life-cycled threads...");
        startExecutorThreads(this.executorThreads);
        this.outputterThread.start();
        this.suiteRunnerThread.start();
        LOGGER.log("All life-cycled threads started.");

        this.state = State.OBJECTS_STARTED;
    }

    /**
     * Shuts down all of the life-cycled objects and closes any resources.
     */
    public void shutdownAllLifecycleObjects() throws IOException {
        if (this.state == State.OBJECTS_STARTED) {
            LOGGER.log("Shutting down...");
            closeQueues();
            LOGGER.log("All queues closed. Shutting down executors...");
            shutdownExecutors();
            LOGGER.log("All executor threads shut down. Shutting down suite runner...");
            this.testSuiteRunner.shutdown();
            LOGGER.log("Suite runner thread shut down.");
            if (this.resultOutputter.isAlive()) {
                LOGGER.log("Shutting down result outputter...");
                this.resultOutputter.shutdown();
                this.outputterThread.interrupt();
                LOGGER.log("Result outputter shut down.");
            }
        }
        this.state = State.OBJECTS_SHUTDOWN;
    }

    /**
     * Waits until all of the life-cycled objects have shutdown successfully.
     */
    public void waitForAllLifecycleObjectsToShutdown() throws InterruptedException {
        if (this.state != State.OBJECTS_SHUTDOWN) {
            throw new IllegalStateException("cannot wait for life-cycle objects to finish shutting down because in state: " + this.state);
        }

        this.outputterThread.join();
        waitForAllExecutorsToShutdown();
        this.suiteRunnerThread.join();
    }

    private List<CloseableBlockingQueue<TestInfo>> createTestQueues(int numQueues) {
        List<CloseableBlockingQueue<TestInfo>> queues = new ArrayList<>();
        for (int i = 0; i < numQueues; i++) {
            queues.add(CloseableBlockingQueue.withCapacity(this.queueCapacities));
        }
        return queues;
    }

    private List<CloseableBlockingQueue<TestResult>> createTestResultQueues(int numQueues) {
        List<CloseableBlockingQueue<TestResult>> queues = new ArrayList<>();
        for (int i = 0; i < numQueues; i++) {
            queues.add(CloseableBlockingQueue.withCapacity(this.queueCapacities));
        }
        return queues;
    }

    private List<TestExecutor> createExecutors(List<CloseableBlockingQueue<TestInfo>> testsQueues, List<CloseableBlockingQueue<TestResult>> resultsQueues) {
        if (testsQueues.size() != resultsQueues.size()) {
            throw new IllegalArgumentException("must be same number of tests and results queues but found " + testsQueues.size() + " and " + resultsQueues.size());
        }

        List<TestExecutor> executors = new ArrayList<>();
        for (int i = 0; i < testsQueues.size(); i++) {
            executors.add(TestExecutor.withQueues(this.shutdownMonitor, testsQueues.get(i), resultsQueues.get(i), this.writeToDb));
        }
        return executors;
    }

    private List<Thread> createExecutorThreads(List<TestExecutor> executors) {
        List<Thread> threads = new ArrayList<>();
        int index = 0;
        for (TestExecutor executor : executors) {
            threads.add(new Thread(executor, "TestExecutor-" + index));
            index++;
        }
        return threads;
    }

    private void startExecutorThreads(List<Thread> executors) {
        for (Thread executor : executors) {
            executor.start();
        }
    }

    private void closeQueues() {
        for (CloseableBlockingQueue<TestResult> resultQueue : this.testResultQueues) {
            resultQueue.close();
        }
        for (CloseableBlockingQueue<TestInfo> testQueue : this.testInfoQueues) {
            testQueue.close();
        }
        this.testSuiteQueue.close();
    }

    private void shutdownExecutors() {
        for (TestExecutor executor : this.testExecutors) {
            executor.shutdown();
        }
    }

    private void waitForAllExecutorsToShutdown() throws InterruptedException {
        for (Thread executor : this.executorThreads) {
            executor.join();
        }
    }

    private void clearDatabase(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        statement.execute("DROP TABLE IF EXISTS test_suite CASCADE");

        statement = connection.createStatement();
        statement.execute("DROP TABLE IF EXISTS test_class CASCADE");

        statement = connection.createStatement();
        statement.execute("DROP TABLE IF EXISTS test");
    }

    private void createTables(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        statement.execute("CREATE TABLE test_suite("
                + "id int PRIMARY KEY,"
                + " num_tests int NOT NULL,"
                + " num_success int,"
                + " num_failures int,"
                + " duration bigint);");

        statement = connection.createStatement();
        statement.execute("CREATE TABLE test_class("
                + "id int PRIMARY KEY,"
                + " name VARCHAR(100) NOT NULL,"
                + " num_tests int NOT NULL,"
                + " num_success int,"
                + " num_failures int,"
                + " duration bigint,"
                + " suite int REFERENCES test_suite(id));");

        statement = connection.createStatement();
        statement.execute("CREATE TABLE test("
                + "id SERIAL PRIMARY KEY,"
                + " name VARCHAR(100) NOT NULL,"
                + " is_success BIT NOT NULL,"
                + " stdout TEXT,"
                + " stderr TEXT,"
                + " duration bigint NOT NULL,"
                + " class int REFERENCES test_class(id));");
    }
}
