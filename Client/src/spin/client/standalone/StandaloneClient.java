package spin.client.standalone;

import spin.client.standalone.execution.TestExecutor;
import spin.client.standalone.execution.TestInfo;
import spin.client.standalone.execution.TestResult;
import spin.client.standalone.output.ResultOutputter;
import spin.client.standalone.runner.TestSuite;
import spin.client.standalone.runner.TestSuiteRunner;
import spin.client.standalone.util.CloseableBlockingQueue;
import spin.client.standalone.util.Logger;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * A single-use client that runs a test suite and then exits.
 */
public final class StandaloneClient {
    private static final Logger LOGGER = Logger.forClass(StandaloneClient.class);
    private static final int SYSTEM_CAPACITY = 100;

    /**
     * We expect to be given N test classes and M dependencies.
     *
     * These N test classes should be given to us as canonical paths to each of those classes.
     * These N test classes are all expected to be compiled classes (ie. they are .class files).
     * These N test classes are all expected to belong to the same base test directory, call it TEST-BASE, so that every
     * one of the given canonical paths can be thought of as: TEST-BASE + (class package name slash-style) + (.class extension).
     *
     * These M dependencies are expected to be given to us as canonical paths to each dependency file or directory.
     * If a dependency is a file then it must be a .jar file.
     * If a dependency is a directory then it must contain .class files in their appropriate package structure.
     *
     * All of these parameters must be passed into the args of this main method as follows:
     *
     * args at index 0: TEST-BASE
     * args at index 1: N
     * args at indices [2..2+N]: each of the N test classes
     * args at indices [2+N+1..2+N+M]: each of the M dependencies
     */
    public static void main(String[] args) {
        try {
            if (args == null) {
                System.err.println("Null arguments given.");
                System.err.println(usage());
                System.exit(1);
            }

            if (!Boolean.parseBoolean(System.getProperty("enable_logger"))) {
                Logger.globalDisable();
            }

            int numThreads = Integer.parseInt(System.getProperty("num_threads"));

            logArguments(args);

            if (args.length < 2) {
                System.err.println(usage());
                System.exit(1);
            }

            // Get the TEST-BASE directory and the number of test classes submitted to us.
            String testsBaseDir = args[0] + "/";
            int numTestClasses = Integer.parseInt(args[1], 10);
            LOGGER.log("Given base test directory: " + testsBaseDir);
            LOGGER.log("Given number of test classes: " + numTestClasses);

            if (args.length < 2 + numTestClasses) {
                System.err.println("Number of test classes is " + numTestClasses + ". Args length is too short: " + args.length);
                System.err.println(usage());
                System.exit(1);
            }

            // Create the executor threads and the suiteRunner, outputter threads and start them all.
            List<CloseableBlockingQueue<TestInfo>> testQueues = createTestQueues(numThreads);
            List<CloseableBlockingQueue<TestResult>> resultQueues = createTestResultQueues(numThreads);
            List<TestExecutor> executors = createExecutors(testQueues, resultQueues);
            List<Thread> executorThreads = createExecutorThreads(executors);
            ResultOutputter resultOutputter = ResultOutputter.outputter(resultQueues);
            Thread outputterThread = new Thread(resultOutputter, "ResultOutputter");
            TestSuiteRunner suiteRunner = TestSuiteRunner.withOutgoingQueue(testQueues);
            Thread suiteRunnerThread = new Thread(suiteRunner, "TestSuiteRunner");
            startExecutorThreads(executorThreads);
            outputterThread.start();
            suiteRunnerThread.start();

            // Grab all of the dependencies given to us and inject them into a new ClassLoader which we will use to load the test classes.
            int numDependencies = args.length - 2 - numTestClasses;
            LOGGER.log("Number of given dependencies: " + numDependencies);

            URL[] dependencyUrls = new URL[numDependencies + 1];
            dependencyUrls[0] = new File(testsBaseDir).toURI().toURL();
            for (int i = 1; i < 1 + numDependencies; i++) {
                dependencyUrls[i] = new File(args[2 + numDependencies + i - 1]).toURI().toURL();
            }
            URLClassLoader classLoader = new URLClassLoader(dependencyUrls);
            String[] testClassPaths = Arrays.copyOfRange(args, 2, 2 + numTestClasses);

            LOGGER.log("Loading test suite...");
            TestSuite testSuite = new TestSuite(testsBaseDir, testClassPaths, classLoader);
            if (!suiteRunner.loadSuite(testSuite, 30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Failed to load suite!");
            }
            LOGGER.log("Test suite loaded.");

            // Wait until the outputter finishes and then it is safe to shut down all the other threads.
            LOGGER.log("Shutting down...");
            outputterThread.join();
            closeQueues(testQueues, resultQueues);
            LOGGER.log("Outputter thread shut down. Shutting down executors...");
            shutdownExecutors(executorThreads, executors);
            LOGGER.log("All executor threads shut down. Shutting down suite runner...");
            suiteRunner.shutdown();
            suiteRunnerThread.join();
            LOGGER.log("Suite runner thread shut down.");

        } catch (Throwable t) {
            System.err.println("Unexpected Error!");
            t.printStackTrace();
        } finally {
            LOGGER.log("Exiting.");
        }
    }

    private static List<CloseableBlockingQueue<TestInfo>> createTestQueues(int numQueues) {
        List<CloseableBlockingQueue<TestInfo>> queues = new ArrayList<>();
        for (int i = 0; i < numQueues; i++) {
            queues.add(CloseableBlockingQueue.withCapacity(SYSTEM_CAPACITY));
        }
        return queues;
    }

    private static List<CloseableBlockingQueue<TestResult>> createTestResultQueues(int numQueues) {
        List<CloseableBlockingQueue<TestResult>> queues = new ArrayList<>();
        for (int i = 0; i < numQueues; i++) {
            queues.add(CloseableBlockingQueue.withCapacity(SYSTEM_CAPACITY));
        }
        return queues;
    }

    private static List<TestExecutor> createExecutors(List<CloseableBlockingQueue<TestInfo>> testsQueues, List<CloseableBlockingQueue<TestResult>> resultsQueues) {
        if (testsQueues.size() != resultsQueues.size()) {
            throw new IllegalArgumentException("must be same number of tests and results queues but found " + testsQueues.size() + " and " + resultsQueues.size());
        }

        List<TestExecutor> executors = new ArrayList<>();
        for (int i = 0; i < testsQueues.size(); i++) {
            executors.add(TestExecutor.withQueues(testsQueues.get(i), resultsQueues.get(i)));
        }
        return executors;
    }

    private static List<Thread> createExecutorThreads(List<TestExecutor> executors) {
        List<Thread> threads = new ArrayList<>();

        int index = 0;
        for (TestExecutor executor : executors) {
            threads.add(new Thread(executor, "TestExecutor-" + index));
            index++;
        }
        return threads;
    }

    private static void startExecutorThreads(List<Thread> executors) {
        for (Thread executor : executors) {
            executor.start();
        }
    }

    private static void closeQueues(List<CloseableBlockingQueue<TestInfo>> testQueues, List<CloseableBlockingQueue<TestResult>> resultQueues) {
        for (CloseableBlockingQueue<TestResult> resultQueue : resultQueues) {
            resultQueue.close();
        }
        for (CloseableBlockingQueue<TestInfo> testQueue : testQueues) {
            testQueue.close();
        }
    }

    private static void shutdownExecutors(List<Thread> executorThreads, List<TestExecutor> executors) throws InterruptedException {
        for (TestExecutor executor : executors) {
            executor.shutdown();
        }
        for (Thread executor : executorThreads) {
            executor.join();
        }
    }

    private static void logArguments(String[] args) {
        LOGGER.log("ARGS ------------------------------------------------");
        for (String a : args) {
            LOGGER.log(a);
        }
        LOGGER.log("ARGS ------------------------------------------------\n");
    }

    private static String usage() {
        return StandaloneClient.class.getName()
                + " <test base> <num tests> [[test class]...] [[dependency]...]"
                + "\n\ttest base: a canonical path to the directory containing the test classes."
                + "\n\tnum tests: the total number of test classes to be run."
                + "\n\ttest class: a canonical path to the .class test class file to be run."
                + "\n\tdependency: a canonical path to the .jar dependency or a directory of .class file dependencies.";
    }
}
