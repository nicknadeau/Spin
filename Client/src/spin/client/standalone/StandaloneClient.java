package spin.client.standalone;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class StandaloneClient {
    private static final Logger LOGGER = Logger.forClass(StandaloneClient.class);
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.####");
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
                LOGGER.disable();
            }

            int numThreads = Integer.parseInt(System.getProperty("num_threads"));

            List<TestExecutor> executors = createExecutors(numThreads);
            List<Thread> executorThreads = createExecutorThreads(executors);
            startExecutorThreads(executorThreads);

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
            Class<?>[] testClasses = new Class[classes.length];
            for (int i = 0; i < classes.length; i++) {
                testClasses[i] = classLoader.loadClass(classes[i]);
            }

            // Split out each of the test methods declared in the given test classes.
            List<TestExecutor.TestMethod> testMethods = new ArrayList<>();
            for (Class<?> testClass : testClasses) {
                for (Method method : testClass.getDeclaredMethods()) {
                    if (method.getAnnotation(org.junit.Test.class) != null) {
                        testMethods.add(new TestExecutor.TestMethod(testClass, method));
                    }
                }
            }
            logTestMethods(testMethods);

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

            List<TestExecutor.TestResult> results = drainResults(executors, testMethods.size());
            shutdownExecutors(executors);

            outputTestResults(produceTestClassStatsMap(results));

        } catch (Throwable t) {
            System.err.println("Unexpected Error!");
            t.printStackTrace();
        }
    }

    private static List<TestExecutor> createExecutors(int num) {
        List<TestExecutor> executors = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            executors.add(TestExecutor.withCapacity(EXECUTOR_THREAD_CAPACITY));
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

    private static List<TestExecutor.TestResult> drainResults(List<TestExecutor> executors, int numToDrain) {
        List<TestExecutor.TestResult> results = new ArrayList<>();

        int numDrained = 0;
        while (numDrained < numToDrain) {
            for (TestExecutor executor : executors) {
                TestExecutor.TestResult result = executor.getNextResult();
                if (result != null) {
                    results.add(result);
                    numDrained++;
                }
                if (numDrained >= numToDrain) {
                    break;
                }
            }
        }

        return results;
    }

    private static void shutdownExecutors(List<TestExecutor> executors) {
        for (TestExecutor executor : executors) {
            executor.shutdown();
        }
    }

    private static Map<Class<?>, TestClassStats> produceTestClassStatsMap(List<TestExecutor.TestResult> results) {
        Map<Class<?>, TestClassStats> map = new HashMap<>();

        for (TestExecutor.TestResult result : results) {
            if (!map.containsKey(result.testClass)) {
                map.put(result.testClass, new TestClassStats());
            }

            TestClassStats testClassStats = map.get(result.testClass);
            testClassStats.testTimes.put(result.testMethod, result.durationNanos);
            if (result.successful) {
                testClassStats.successfulTestMethods.add(result.testMethod);
            } else {
                testClassStats.failedTestMethods.add(result.testMethod);
            }
        }

        return map;
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

    private static void outputTestResults(Map<Class<?>, TestClassStats> testClassStatsMap) {
        System.out.println("\n===============================================================");

        int suiteNumSuccesses = 0;
        int suiteNumFails = 0;
        long suiteTimeTotal = 0;

        for (Map.Entry<Class<?>, TestClassStats> entry : testClassStatsMap.entrySet()) {
            TestClassStats testClassStats = entry.getValue();
            int numSuccesses = testClassStats.successfulTestMethods.size();
            int numFails = testClassStats.failedTestMethods.size();
            long classTimeTotal = testClassStats.getTotalTestTime();

            System.out.println(entry.getKey().getName() + ": tests [" + (numSuccesses + numFails) + "] successes: " + numSuccesses + ", failures: " + numFails + ", seconds: " + nanosToSecondsString(classTimeTotal));

            for (Method success : testClassStats.successfulTestMethods) {
                System.out.println(success.getName() + " [successful], seconds: " + nanosToSecondsString(testClassStats.testTimes.get(success)));
            }
            for (Method failed : testClassStats.failedTestMethods) {
                System.out.println(failed.getName() + " [failed], seconds: " + nanosToSecondsString(testClassStats.testTimes.get(failed)));
            }

            suiteNumSuccesses += numSuccesses;
            suiteNumFails += numFails;
            suiteTimeTotal += classTimeTotal;

            System.out.println();
        }

        System.out.println("Total tests [" + (suiteNumFails + suiteNumSuccesses) + "] successes: " + suiteNumSuccesses + ", failures: " + suiteNumFails + ", seconds: " + nanosToSecondsString(suiteTimeTotal));
        System.out.println("===============================================================");
    }

    private static String nanosToSecondsString(long nanos) {
        return DECIMAL_FORMAT.format((double) nanos / 1_000_000_000L);
    }

    private static class TestClassStats {
        private final List<Method> successfulTestMethods = new ArrayList<>();
        private final List<Method> failedTestMethods = new ArrayList<>();
        private final Map<Method, Long> testTimes = new HashMap<>();

        private long getTotalTestTime() {
            long sum = 0;
            for (long time : this.testTimes.values()) {
                sum += time;
            }
            return sum;
        }
    }
}
