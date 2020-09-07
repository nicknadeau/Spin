package spin.core.lifecycle;

import spin.core.runner.TestRunner;
import spin.core.server.Server;
import spin.core.server.handler.RequestHandler;
import spin.core.server.request.parse.JsonClientRequestParser;
import spin.core.execution.TestExecutor;
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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

public final class LifecycleComponentManager {
    private static final Logger LOGGER = Logger.forClass(LifecycleComponentManager.class);
    private enum State { PRE_INIT, INIT, STARTED, STOPPED }
    private final ShutdownMonitor shutdownMonitor;
    private final LifecycleComponentConfig config;
    private State state = State.PRE_INIT;
    private CloseableBlockingQueue<TestResult> testResultQueue;
    private Server server;
    private TestSuiteRunner testSuiteRunner;
    private ResultOutputter resultOutputter;
    private TestExecutor testExecutor;
    private Thread serverThread;
    private Thread suiteRunnerThread;
    private Thread outputterThread;

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

        CyclicBarrier barrier = new CyclicBarrier(3);
        PanicOnlyMonitor panicMonitor = PanicOnlyMonitor.wrapForPanicsOnly(this.shutdownMonitor);
        ShutdownOnlyMonitor shutdownOnlyMonitor = ShutdownOnlyMonitor.wrapForGracefulShutdownsOnly(this.shutdownMonitor);
        this.testResultQueue = CloseableBlockingQueue.withCapacity(this.config.interComponentQueueCapacity);

        this.testExecutor = new TestExecutor(this.config.numExecutorThreads);
        this.resultOutputter = (this.config.doOutputToDatabase)
                ? ResultOutputter.outputterToConsoleAndDb(barrier, panicMonitor, this.testResultQueue, databaseConnectionProvider.getConnection())
                : ResultOutputter.outputter(barrier, panicMonitor, this.testResultQueue);
        this.testSuiteRunner = (this.config.doOutputToDatabase)
                ? TestSuiteRunner.withDatabaseWriter(barrier, panicMonitor, this.testExecutor, this.testResultQueue, databaseConnectionProvider.getConnection())
                : TestSuiteRunner.withOutgoingQueue(barrier, panicMonitor, this.testExecutor, this.testResultQueue);

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

        this.serverThread.start();
        this.outputterThread.start();
        this.suiteRunnerThread.start();

        ProgramInfoWriter.publish(this.server.getPort());

        this.state = State.STARTED;
        LOGGER.log("All life-cycled components started.");
    }

    void shutdownAllComponents() {
        if (this.state == State.STARTED) {
            LOGGER.log("Shutting down all life-cycled components...");
            this.testResultQueue.close();
            LOGGER.log("All queues closed.\nShutting down server...");
            this.server.shutdown();
            LOGGER.log("Server shut down.\nShutting down test executors...");
            this.testExecutor.shutdown();
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
        this.testExecutor.waitUntilShutdown(10, TimeUnit.SECONDS);
        this.suiteRunnerThread.join();
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
