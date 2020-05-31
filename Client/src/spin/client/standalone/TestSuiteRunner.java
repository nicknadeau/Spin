package spin.client.standalone;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public final class TestSuiteRunner implements Runnable {
    private static final Logger LOGGER = Logger.forClass(TestSuiteRunner.class);
    private final Object monitor = new Object();
    private final List<BlockingQueue<TestExecutor.TestMethod>> outgoingTestQueues;
    private TestSuite suite = null;
    private volatile boolean isAlive = true;

    private TestSuiteRunner(List<BlockingQueue<TestExecutor.TestMethod>> outgoingTestQueues) {
        if (outgoingTestQueues == null) {
            throw new NullPointerException("outgoingTestQueues must be non-null.");
        }
        this.outgoingTestQueues = outgoingTestQueues;
    }

    public static TestSuiteRunner withOutgoingQueue(List<BlockingQueue<TestExecutor.TestMethod>> outgoingTestQueues) {
        return new TestSuiteRunner(outgoingTestQueues);
    }

    @Override
    public void run() {
        try {

            while (this.isAlive) {

                try {
                    TestSuite testSuite = fetchNextSuite();
                    if (testSuite != null) {

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
                        Map<Class<?>, Integer> testsCount = new HashMap<>();
                        Class<?>[] testClasses = new Class[classes.length];
                        for (int i = 0; i < classes.length; i++) {
                            testClasses[i] = testSuite.classLoader.loadClass(classes[i]);
                            testsCount.put(testClasses[i], 0);
                        }

                        TestSuiteDetails testSuiteDetails = new TestSuiteDetails(testsCount);

                        // Split out each of the test methods declared in the given test classes.
                        List<TestExecutor.TestMethod> testMethods = new ArrayList<>();
                        for (Class<?> testClass : testClasses) {
                            for (Method method : testClass.getDeclaredMethods()) {
                                if (method.getAnnotation(org.junit.Test.class) != null) {
                                    testMethods.add(new TestExecutor.TestMethod(testClass, method, testSuiteDetails));
                                    int currCount = testsCount.get(testClass);
                                    testsCount.put(testClass, currCount + 1);
                                }
                            }
                        }
                        logTestMethods(testMethods);

                        // Execute each of the declared test methods.
                        int index = 0;
                        while (index < testMethods.size()) {
                            for (BlockingQueue<TestExecutor.TestMethod> outgoingTestQueue : this.outgoingTestQueues) {
                                outgoingTestQueue.put(testMethods.get(index));
                                index++;

                                if (index >= testMethods.size()) {
                                    break;
                                }
                            }
                        }
                    }
                } catch (ClassNotFoundException | InterruptedException e) {
                    LOGGER.log("Unexpected error.");
                    e.printStackTrace();
                } finally {
                    clearSuite();
                }

            }

        } finally {
            this.isAlive = false;
            LOGGER.log("Exiting.");
        }
    }

    public boolean loadSuite(TestSuite suite, long timeout, TimeUnit unit) throws InterruptedException {
        if (suite == null) {
            throw new NullPointerException("suite must be non-null.");
        }
        if (timeout < 1) {
            throw new IllegalArgumentException("timeout must be positive but was: " + timeout);
        }
        if (unit == null) {
            throw new NullPointerException("unit must be non-null.");
        }

        long currentTime = System.currentTimeMillis();
        long deadline = currentTime + unit.toMillis(timeout);

        synchronized (this.monitor) {
            while ((currentTime < deadline) && (this.isAlive) && (this.suite != null)) {
                this.monitor.wait(deadline - currentTime);
                currentTime = System.currentTimeMillis();
            }

            if ((this.isAlive) && (this.suite == null)) {
                this.suite = suite;
                this.monitor.notifyAll();
                return true;
            } else {
                return false;
            }
        }
    }

    public void shutdown() {
        this.isAlive = false;
    }

    private TestSuite fetchNextSuite() {
        synchronized (this.monitor) {
            while ((this.isAlive) && (this.suite == null)) {
                try {
                    this.monitor.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            return (this.isAlive) ? this.suite : null;
        }
    }

    private void clearSuite() {
        synchronized (this.monitor) {
            this.suite = null;
            this.monitor.notifyAll();
        }
    }

    private static void logTestMethods(Collection<TestExecutor.TestMethod> testMethods) {
        for (TestExecutor.TestMethod testMethod : testMethods) {
            LOGGER.log("Declared test: " + testMethod);
        }
    }

    public static class TestSuite {
        private final String testBaseDirPath;
        private final String[] testClassPaths;
        private final ClassLoader classLoader;

        public TestSuite(String testBaseDirPath, String[] testClassPaths, ClassLoader classLoader) {
            this.testBaseDirPath = testBaseDirPath;
            this.testClassPaths = testClassPaths;
            this.classLoader = classLoader;
        }
    }

    public static class TestSuiteDetails {
        private final Map<Class<?>, Integer> numTestsPerClass;
        private final Map<Class<?>, TestClassStats> testClassStats = new HashMap<>();
        private int totalNumSuccessfulTests = 0;
        private int totalNumFailedTests = 0;
        private long totalSuiteDuration = 0;
        private int numClassesFinished = 0;

        private TestSuiteDetails(Map<Class<?>, Integer> numTestsPerClass) {
            this.numTestsPerClass = numTestsPerClass;
        }

        public synchronized int getNumTestsInClass(Class<?> testClass) {
            if (testClass == null) {
                throw new NullPointerException("testClass must be non-null.");
            }
            if (!this.numTestsPerClass.containsKey(testClass)) {
                throw new IllegalArgumentException("no entry for class: " + testClass.getName());
            }
            return this.numTestsPerClass.get(testClass);
        }

        public synchronized void incrementNumSuccessfulTestsInClass(Class<?> testClass, long duration) {
            if (testClass == null) {
                throw new NullPointerException("testClass must be non-null.");
            }
            if (duration < 0) {
                throw new IllegalArgumentException("duration must be non-negative but was: " + duration);
            }
            if (!this.numTestsPerClass.containsKey(testClass)) {
                throw new IllegalArgumentException("no entry for specified test class: " + testClass);
            }
            if (!this.testClassStats.containsKey(testClass)) {
                this.testClassStats.put(testClass, new TestClassStats());
            }
            TestClassStats testClassStats = this.testClassStats.get(testClass);
            testClassStats.numSuccesses++;
            testClassStats.totalDurationNanos += duration;
            this.totalNumSuccessfulTests++;
            this.totalSuiteDuration += duration;

            int numTestsTotal = this.numTestsPerClass.get(testClass);
            this.numClassesFinished += (testClassStats.numSuccesses + testClassStats.numFailures == numTestsTotal) ? 1 : 0;
        }

        public synchronized int getTotalNumSuccessfulTestsInClass(Class<?> testClass) {
            if (testClass == null) {
                throw new NullPointerException("testClass must be non-null.");
            }
            if (!this.testClassStats.containsKey(testClass)) {
                throw new IllegalArgumentException("no entry for specified test class: " + testClass);
            }
            return this.testClassStats.get(testClass).numSuccesses;
        }

        public synchronized int getTotalNumSuccessfulTests() {
            return this.totalNumSuccessfulTests;
        }

        public synchronized void incrementNumFailedTestsInClass(Class<?> testClass, long duration) {
            if (testClass == null) {
                throw new NullPointerException("testClass must be non-null.");
            }
            if (duration < 0) {
                throw new IllegalArgumentException("duration must be non-negative but was: " + duration);
            }
            if (!this.numTestsPerClass.containsKey(testClass)) {
                throw new IllegalArgumentException("no entry for specified test class: " + testClass);
            }
            if (!this.testClassStats.containsKey(testClass)) {
                this.testClassStats.put(testClass, new TestClassStats());
            }
            TestClassStats testClassStats = this.testClassStats.get(testClass);
            testClassStats.numFailures++;
            testClassStats.totalDurationNanos += duration;
            this.totalNumFailedTests++;
            this.totalSuiteDuration += duration;

            int numTestsTotal = this.numTestsPerClass.get(testClass);
            this.numClassesFinished += (testClassStats.numSuccesses + testClassStats.numFailures == numTestsTotal) ? 1 : 0;
        }

        public synchronized int getTotalNumFailedTestsInClass(Class<?> testClass) {
            if (testClass == null) {
                throw new NullPointerException("testClass must be non-null.");
            }
            if (!this.testClassStats.containsKey(testClass)) {
                throw new IllegalArgumentException("no entry for specified test class: " + testClass);
            }
            return this.testClassStats.get(testClass).numFailures;
        }

        public synchronized int getTotalNumFailedTests() {
            return this.totalNumFailedTests;
        }

        public synchronized long getTotalDurationForClass(Class<?> testClass) {
            if (testClass == null) {
                throw new NullPointerException("testClass must be non-null.");
            }
            if (!this.testClassStats.containsKey(testClass)) {
                throw new IllegalArgumentException("no entry for specified test class: " + testClass);
            }
            return this.testClassStats.get(testClass).totalDurationNanos;
        }

        public synchronized long getTotalSuiteDuration() {
            return this.totalSuiteDuration;
        }

        public synchronized int getTotalNumTests() {
            return this.totalNumFailedTests + this.totalNumSuccessfulTests;
        }

        public synchronized boolean isSuiteComplete() {
            return this.numClassesFinished == this.numTestsPerClass.keySet().size();
        }

        public synchronized boolean isClassComplete(Class<?> testClass) {
            if (testClass == null) {
                throw new NullPointerException("testClass must be non-null.");
            }
            if (!this.testClassStats.containsKey(testClass)) {
                throw new IllegalArgumentException("no entry for given test class: " + testClass);
            }

            TestClassStats testClassStats = this.testClassStats.get(testClass);
            int numTestsTotal = this.numTestsPerClass.get(testClass);
            return testClassStats.numSuccesses + testClassStats.numFailures == numTestsTotal;
        }
    }

    private static class TestClassStats {
        private int numSuccesses = 0;
        private int numFailures = 0;
        private long totalDurationNanos = 0;
    }
}
