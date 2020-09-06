package spin.core;

import spin.core.lifecycle.LifecycleComponentConfig;
import spin.core.lifecycle.LifecycleManager;
import spin.core.type.Result;
import spin.core.lifecycle.PanicOnlyMonitor;
import spin.core.util.Logger;
import spin.core.util.ThreadLocalPrintStream;

import java.util.concurrent.FutureTask;

public final class LongLivedEntryPoint {
    private static final Logger LOGGER = Logger.forClass(LongLivedEntryPoint.class);
    private static final int INTER_COMPONENT_QUEUE_CAPACITY = 262_144;

    public static void main(String[] args) {
        String enableLoggerProperty = System.getProperty("enable_logger");
        String writeToDbProperty = System.getProperty("write_to_db");
        String dbConfigPath = System.getProperty("db_config_path");
        String numThreadsProperty = System.getProperty("num_threads");

        if (enableLoggerProperty == null) {
            throw new NullPointerException("Must provider an enable_logger property value.");
        }
        if (writeToDbProperty == null) {
            throw new NullPointerException("Must provider a write_to_db property value.");
        }
        if (dbConfigPath == null) {
            throw new NullPointerException("Must provider a db_config_path property value.");
        }
        if (numThreadsProperty == null) {
            throw new NullPointerException("Must provider a num_threads property value.");
        }

        if (!Boolean.parseBoolean(enableLoggerProperty)) {
            Logger.globalDisable();
        }
        boolean writeToDb = Boolean.parseBoolean(writeToDbProperty);
        int numThreads = Integer.parseInt(numThreadsProperty);
        LOGGER.log("enable_logger property: " + enableLoggerProperty);
        LOGGER.log("write_to_db property: " + writeToDbProperty);
        LOGGER.log("db_config_path property: " + dbConfigPath);
        LOGGER.log("num_threads property: " + numThreadsProperty);

        overrideOutputStreams();

        logArguments(args);

        if (args.length != 0) {
            System.err.println("Incorrect usage: Spin takes zero arguments.");
            System.exit(1);
        }

        LifecycleComponentConfig config = LifecycleComponentConfig.Builder.newBuilder()
                .setPathOfDatabaseConfigFile(dbConfigPath)
                .setWhetherToOutputResultsToDatabase(writeToDb)
                .setNumberOfTestExecutors(numThreads)
                .setCapacityOfInterComponentQueues(INTER_COMPONENT_QUEUE_CAPACITY)
                .build();

        LifecycleManager lifecycleManager = LifecycleManager.newManager(config);
        PanicOnlyMonitor panicOnlyMonitor = lifecycleManager.getPanicOnlyShutdownMonitor();
        FutureTask<Result<Void>> lifecycleManagerTask = new FutureTask<>(lifecycleManager);
        Thread lifecycleManagerThread = new Thread(lifecycleManagerTask);
        lifecycleManagerThread.start();

        try {
            while (lifecycleManager.isAlive()) {
                try {
                    lifecycleManagerThread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            Result<Void> result = lifecycleManagerTask.get();
            if (result.isSuccess()) {
                LOGGER.log("Spin shutdown successfully.");
            } else {
                LOGGER.log("Spin encountered an unexpected error and shutdown: " + result.getError());
            }

        } catch (Throwable e) {
            panicOnlyMonitor.panic(e);
        }
    }

    private static void overrideOutputStreams() {
        System.setOut(ThreadLocalPrintStream.withInitialStream(System.out));
        System.setErr(ThreadLocalPrintStream.withInitialStream(System.err));
    }

    private static void logArguments(String[] args) {
        LOGGER.log("ARGS ------------------------------------------------");
        for (String a : args) {
            LOGGER.log(a);
        }
        LOGGER.log("ARGS ------------------------------------------------\n");
    }
}
