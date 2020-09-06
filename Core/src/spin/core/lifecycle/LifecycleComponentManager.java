package spin.core.lifecycle;

import spin.core.runner.TestRunner;
import spin.core.server.Server;
import spin.core.server.handler.RequestHandler;
import spin.core.server.request.parse.JsonClientRequestParser;
import spin.core.execution.TestExecutor;
import spin.core.execution.TestInfo;
import spin.core.execution.TestResult;
import spin.core.output.DatabaseConnectionProvider;
import spin.core.output.ResultOutputter;
import spin.core.runner.TestSuiteRunner;
import spin.core.util.CloseableBlockingQueue;
import spin.core.util.Logger;
import spin.core.util.ObjectChecker;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;

public final class LifecycleComponentManager {
    private static final Logger LOGGER = Logger.forClass(LifecycleComponentManager.class);
    private enum State { PRE_INIT, INIT, STARTED, STOPPED }
    private final ShutdownMonitor shutdownMonitor;
    private final LifecycleComponentConfig config;
    private State state = State.PRE_INIT;
    private List<CloseableBlockingQueue<TestInfo>> testInfoQueues;
    private List<CloseableBlockingQueue<TestResult>> testResultQueues;
    private Server server;
    private TestSuiteRunner testSuiteRunner;
    private ResultOutputter resultOutputter;
    private List<TestExecutor> testExecutors;
    private Thread serverThread;
    private Thread suiteRunnerThread;
    private Thread outputterThread;
    private List<Thread> executorThreads;

    private LifecycleComponentManager(LifecycleComponentConfig config) {
        ObjectChecker.assertNonNull(config);
        this.shutdownMonitor = new ShutdownMonitor();
        this.config = config;
    }

    public static LifecycleComponentManager newManager(LifecycleComponentConfig config) {
        return new LifecycleComponentManager(config);
    }

    PanicOnlyMonitor getPanicOnlyShutdownMonitor() {
        return PanicOnlyMonitor.wrapForPanicsOnly(this.shutdownMonitor);
    }

    ListenOnlyMonitor getListenOnlyShutdownMonitor() {
        return ListenOnlyMonitor.wrapForListeningOnly(this.shutdownMonitor);
    }

    void initializeAllComponents() throws IOException, SQLException {
        ObjectChecker.assertNonNull(this.config);
        if (this.state != State.PRE_INIT) {
            throw new IllegalStateException("Cannot initialize components: components are already initialized.");
        }

        LOGGER.log("Initializing all life-cycled components...");
        DatabaseConnectionProvider databaseConnectionProvider = (this.config.doOutputToDatabase) ? new DatabaseConnectionProvider() : null;
        if (databaseConnectionProvider != null) {
            databaseConnectionProvider.initialize(new File(this.config.databaseConfigPath));

            try (Connection connection = databaseConnectionProvider.getConnection()) {
                clearDatabase(connection);
                createTables(connection);
            }
        }

        CyclicBarrier barrier = new CyclicBarrier(this.config.numExecutorThreads + 3);
        PanicOnlyMonitor panicMonitor = PanicOnlyMonitor.wrapForPanicsOnly(this.shutdownMonitor);
        ShutdownOnlyMonitor shutdownOnlyMonitor = ShutdownOnlyMonitor.wrapForGracefulShutdownsOnly(this.shutdownMonitor);

        this.testInfoQueues = createTestQueues();
        this.testResultQueues = createTestResultQueues();
        this.testExecutors = createExecutors(this.testInfoQueues, this.testResultQueues, barrier, panicMonitor);
        this.resultOutputter = (this.config.doOutputToDatabase)
                ? ResultOutputter.outputterToConsoleAndDb(barrier, panicMonitor, this.testResultQueues, databaseConnectionProvider.getConnection())
                : ResultOutputter.outputter(barrier, panicMonitor, this.testResultQueues);
        this.testSuiteRunner = (this.config.doOutputToDatabase)
                ? TestSuiteRunner.withDatabaseWriter(barrier, panicMonitor, this.testInfoQueues, databaseConnectionProvider.getConnection())
                : TestSuiteRunner.withOutgoingQueue(barrier, panicMonitor, this.testInfoQueues);

        TestRunner testRunner = TestRunner.wrap(this.testSuiteRunner);

        this.server = Server.Builder.newBuilder()
                .forHost("127.0.0.1")
                .withBarrier(barrier)
                .withShutdownMonitor(panicMonitor)
                .withRequestHandler(RequestHandler.newHandler(testRunner, shutdownOnlyMonitor))
                .usingClientRequestParser(new JsonClientRequestParser())
                .build();

        this.state = State.INIT;

        LOGGER.log("All life-cycled components initialized.");
    }

    void startAllComponents() throws IOException {
        if (this.state != State.INIT) {
            throw new IllegalStateException("Cannot start components when in state: " + this.state);
        }

        LOGGER.log("Starting all life-cycled components...");
        this.serverThread = new Thread(this.server, "Server");
        this.outputterThread = new Thread(this.resultOutputter, "ResultOutputter");
        this.suiteRunnerThread = new Thread(this.testSuiteRunner, "TestSuiteRunner");
        this.executorThreads = createExecutorThreads(this.testExecutors);

        this.serverThread.start();
        this.outputterThread.start();
        this.suiteRunnerThread.start();
        for (Thread executorThread : this.executorThreads) {
            executorThread.start();
        }

        ProgramInfoWriter.publish(this.server.getPort());

        this.state = State.STARTED;
        LOGGER.log("All life-cycled components started.");
    }

    void shutdownAllComponents() {
        if (this.state == State.STARTED) {
            LOGGER.log("Shutting down all life-cycled components...");
            closeQueues();
            LOGGER.log("All queues closed.\nShutting down server...");
            this.server.shutdown();
            LOGGER.log("Server shut down.\nShutting down test executors...");
            shutdownExecutors();
            LOGGER.log("All test executors shut down.\nShutting down suite runner...");
            this.testSuiteRunner.shutdown();
            LOGGER.log("Suite runner shut down.");
            if (this.resultOutputter.isAlive()) {
                LOGGER.log("Shutting down result outputter...");
                this.resultOutputter.shutdown();
                this.outputterThread.interrupt();
                LOGGER.log("Result outputter shut down.");
            }

            this.state = State.STOPPED;
        }
    }

    void waitForAllComponentsToShutdown() throws InterruptedException {
        if (this.state != State.STOPPED) {
            throw new IllegalStateException("Cannot wait for all components to shut down: components have not been stopped.");
        }

        this.serverThread.join();
        this.outputterThread.join();
        waitForAllExecutorsToShutdown();
        this.suiteRunnerThread.join();
    }

    private void waitForAllExecutorsToShutdown() throws InterruptedException {
        for (Thread executor : this.executorThreads) {
            executor.join();
        }
    }

    private void closeQueues() {
        for (CloseableBlockingQueue<TestResult> resultQueue : this.testResultQueues) {
            resultQueue.close();
        }
        for (CloseableBlockingQueue<TestInfo> testQueue : this.testInfoQueues) {
            testQueue.close();
        }
    }

    private void shutdownExecutors() {
        for (TestExecutor executor : this.testExecutors) {
            executor.shutdown();
        }
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

    private List<CloseableBlockingQueue<TestInfo>> createTestQueues() {
        List<CloseableBlockingQueue<TestInfo>> queues = new ArrayList<>();
        for (int i = 0; i < this.config.numExecutorThreads; i++) {
            queues.add(CloseableBlockingQueue.withCapacity(this.config.interComponentQueueCapacity));
        }
        return queues;
    }

    private List<CloseableBlockingQueue<TestResult>> createTestResultQueues() {
        List<CloseableBlockingQueue<TestResult>> queues = new ArrayList<>();
        for (int i = 0; i < this.config.numExecutorThreads; i++) {
            queues.add(CloseableBlockingQueue.withCapacity(this.config.interComponentQueueCapacity));
        }
        return queues;
    }

    private List<TestExecutor> createExecutors(List<CloseableBlockingQueue<TestInfo>> testsQueues, List<CloseableBlockingQueue<TestResult>> resultsQueues, CyclicBarrier barrier, PanicOnlyMonitor shutdownMonitor) {
        if (testsQueues.size() != resultsQueues.size()) {
            throw new IllegalArgumentException("num tests queues (" + testsQueues.size() + ") != num results queues (" + resultsQueues.size() + ").");
        }
        if (testsQueues.size() != this.config.numExecutorThreads) {
            throw new IllegalArgumentException("num tests queues (" + testsQueues.size() + ") != num executor threads (" + this.config.numExecutorThreads + ").");
        }

        List<TestExecutor> executors = new ArrayList<>();
        for (int i = 0; i < this.config.numExecutorThreads; i++) {
            executors.add(TestExecutor.withQueues(barrier, shutdownMonitor, testsQueues.get(i), resultsQueues.get(i), this.config.doOutputToDatabase));
        }
        return executors;
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
