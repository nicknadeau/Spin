package spin.client.standalone.runner;

import java.util.HashMap;
import java.util.Map;

/**
 * A class that holds the live details of a test suite. These details are updated as the program runs. This object is
 * held by multiple parties and for most of them constitutes the only reference point they have into the larger details
 * of the suite. These details accompany each test and this allows the tests to be broken up and passed around and for
 * the state of the suite to be updated and managed through this class.
 */
public final class TestSuiteDetails {
    private final Map<Class<?>, Integer> numTestsPerClass;
    private final Map<Class<?>, TestClassStats> testClassStats = new HashMap<>();
    private int totalNumSuccessfulTests = 0;
    private int totalNumFailedTests = 0;
    private long totalSuiteDuration = 0;
    private int numClassesFinished = 0;

    /**
     * Constructs a new suite details object that is for all of the listed classes in the given map and which expects
     * each of those classes to have the corresponding number of tests in it.
     *
     * This map must be a complete listing of all the classes in the suite.
     *
     * @param numTestsPerClass A mapping of test counts for each class.
     */
    TestSuiteDetails(Map<Class<?>, Integer> numTestsPerClass) {
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

    private static class TestClassStats {
        private int numSuccesses = 0;
        private int numFailures = 0;
        private long totalDurationNanos = 0;
    }
}
