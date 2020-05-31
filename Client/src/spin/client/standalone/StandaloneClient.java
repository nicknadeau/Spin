package spin.client.standalone;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class StandaloneClient {
    private static final Logger LOGGER = Logger.forClass(StandaloneClient.class);
    private static final int EXECUTOR_THREAD_CAPACITY = 100;

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
                Logger.disable();
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

            // Grab all of the dependencies given to us and inject them into a new ClassLoader which we will use to load the test classes.
            int numDependencies = args.length - 2 - numTestClasses;
            LOGGER.log("Number of given dependencies: " + numDependencies);

            URL[] dependencyUrls = new URL[numDependencies + 1];
            dependencyUrls[0] = new File(testsBaseDir).toURI().toURL();
            for (int i = 1; i < 1 + numDependencies; i++) {
                dependencyUrls[i] = new File(args[2 + numDependencies + i - 1]).toURI().toURL();
            }

            URLClassLoader classLoader = new URLClassLoader(dependencyUrls);

            // Grab all of the submitted test classes. We want only the binary names of these classes so we can load them.
            String[] classes = new String[numTestClasses];
            for (int i = 2; i < 2 + numTestClasses; i++) {
                String classPathWithBaseDirStripped = args[i].substring(testsBaseDir.length());
                String classNameWithSuffixStripped = classPathWithBaseDirStripped.substring(0, classPathWithBaseDirStripped.length() - ".class".length());
                String classNameBinaryformat = classNameWithSuffixStripped.replaceAll("/", ".");

                LOGGER.log("Binary name of submitted test class: " + classNameBinaryformat);
                classes[i - 2] = classNameBinaryformat;
            }

            // Load all of the submitted test classes.
            Map<Class<?>, Integer> testsCount = new HashMap<>();
            Class<?>[] testClasses = new Class[classes.length];
            for (int i = 0; i < classes.length; i++) {
                testClasses[i] = classLoader.loadClass(classes[i]);
                testsCount.put(testClasses[i], 0);
            }

            // Split out each of the test methods declared in the given test classes.
            List<TestExecutor.TestMethod> testMethods = new ArrayList<>();
            for (Class<?> testClass : testClasses) {
                for (Method method : testClass.getDeclaredMethods()) {
                    if (method.getAnnotation(org.junit.Test.class) != null) {
                        testMethods.add(new TestExecutor.TestMethod(testClass, method));
                        int currCount = testsCount.get(testClass);
                        testsCount.put(testClass, currCount + 1);
                    }
                }
            }
            logTestMethods(testMethods);

            // Create the executor threads and the outputter thread and start them all.
            List<BlockingQueue<TestExecutor.TestResult>> resultQueues = createTestResultQueues(numThreads);
            List<TestExecutor> executors = createExecutors(resultQueues);
            List<Thread> executorThreads = createExecutorThreads(executors);
            ResultOutputter resultOutputter = ResultOutputter.outputter(resultQueues, testsCount);
            Thread outputterThread = new Thread(resultOutputter, "ResultOutputter");
            startExecutorThreads(executorThreads);
            outputterThread.start();

            // Execute each of the declared test methods.
            int index = 0;
            while (index < testMethods.size()) {
                for (TestExecutor executor : executors) {
                    if (executor.loadTest(testMethods.get(index), 30, TimeUnit.SECONDS)) {
                        index++;
                    }
                    if (index >= testMethods.size()) {
                        break;
                    }
                }
            }

            LOGGER.log("Finished submitting all tests to executors.");

            // Wait until the outputter finishes and then it is safe to shut down the executors.
            outputterThread.join();
            shutdownExecutors(executorThreads, executors);

        } catch (Throwable t) {
            System.err.println("Unexpected Error!");
            t.printStackTrace();
        } finally {
            LOGGER.log("Exiting.");
        }
    }

    private static List<BlockingQueue<TestExecutor.TestResult>> createTestResultQueues(int numQueues) {
        List<BlockingQueue<TestExecutor.TestResult>> queues = new ArrayList<>();
        for (int i = 0; i < numQueues; i++) {
            queues.add(new LinkedBlockingQueue<>());
        }
        return queues;
    }

    private static List<TestExecutor> createExecutors(List<BlockingQueue<TestExecutor.TestResult>> resultsQueues) {
        List<TestExecutor> executors = new ArrayList<>();
        for (BlockingQueue<TestExecutor.TestResult> resultsQueue : resultsQueues) {
            executors.add(TestExecutor.withCapacity(EXECUTOR_THREAD_CAPACITY, resultsQueue));
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

    private static void logTestMethods(Collection<TestExecutor.TestMethod> testMethods) {
        for (TestExecutor.TestMethod testMethod : testMethods) {
            LOGGER.log("Declared test: " + testMethod);
        }
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
