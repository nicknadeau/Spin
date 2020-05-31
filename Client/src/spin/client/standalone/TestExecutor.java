package spin.client.standalone;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * A class that executes tests.
 *
 * Tests can be loaded into this executor dynamically as {@link TestMethod} objects and this executor will place the
 * results into a queue that can be polled by a consumer.
 */
public final class TestExecutor implements Runnable {
    private final Object monitor = new Object();
    private final Queue<TestMethod> tests = new LinkedList<>();
    private final Queue<TestResult> results = new LinkedList<>();
    private final int capacity;
    private volatile boolean isAlive = true;

    private TestExecutor(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive but was: " + capacity);
        }
        this.capacity = capacity;
    }

    /**
     * Creates a new executor that only allows the specified capacity amount of tests to be loaded into it at once as
     * backlog tests to run. Note that the executor can run more tests than this number, this number is a maximum that
     * can be in the executor waiting to be run at one time.
     *
     * @param capacity the capacity of this executor.
     * @return the new executor.
     */
    public static TestExecutor withCapacity(int capacity) {
        return new TestExecutor(capacity);
    }

    @Override
    public void run() {
        try {
            while (this.isAlive) {
                TestMethod testMethod = fetchNextTestMethod();

                if (testMethod != null) {
                    TestResult result;

                    long startTime = System.nanoTime();
                    try {
                        Object instance = testMethod.testClass.getConstructor().newInstance();
                        testMethod.method.invoke(instance);
                        long endTime = System.nanoTime();
                        result = new TestResult(testMethod.testClass, testMethod.method, true, endTime - startTime);

                    } catch (Exception e) {
                        long endTime = System.nanoTime();
                        result = new TestResult(testMethod.testClass, testMethod.method, false, endTime - startTime);
                    }

                    synchronized (this.monitor) {
                        this.results.add(result);
                    }
                }
            }
        } finally {
            this.isAlive = false;
        }
    }

    /**
     * Attempts to load the specified test method into this executor to be executed. This method blocks if the executor
     * has been filled to capacity and will unblock once the capacity has gone down and this test method could be loaded
     * or until this executor is no longer alive or the timeout period elapses, whichever happens first.
     *
     * Returns true iff the test method was loaded into this executor successfully.
     *
     * @param testMethod The method to load.
     * @param timeout The timeout duration.
     * @param unit The timeout duration units.
     * @return whether or not the test method was loaded.
     */
    public boolean loadTest(TestMethod testMethod, long timeout, TimeUnit unit) throws InterruptedException {
        if (testMethod == null) {
            throw new NullPointerException("testMethod must be non-null.");
        }
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must be non-negative.");
        }
        if (unit == null) {
            throw new NullPointerException("unit must be non-null.");
        }

        long currentTime = System.currentTimeMillis();
        long deadline = currentTime + unit.toMillis(timeout);

        synchronized (this.monitor) {
            while ((currentTime < deadline) && (this.isAlive) && (this.tests.size() >= this.capacity)) {
                this.monitor.wait(deadline - currentTime);
                currentTime = System.currentTimeMillis();
            }

            if (this.tests.size() < this.capacity) {
                this.tests.add(testMethod);
                this.monitor.notifyAll();
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Returns the next test result if a result was available or null if no result is available. This method does not
     * block but returns immediately.
     *
     * @return the next result or null if none.
     */
    public TestResult getNextResult() {
        synchronized (this.monitor) {
            return (this.results.isEmpty()) ? null : this.results.poll();
        }
    }

    /**
     * Shuts down this executor.
     */
    public void shutdown() {
        synchronized (this.monitor) {
            this.isAlive = false;
            this.monitor.notifyAll();
        }
    }

    /**
     * Returns true iff this executor is still alive and is not shutdown.
     *
     * @return whether or not this executor is alive.
     */
    public boolean isAlive() {
        return this.isAlive;
    }

    private TestMethod fetchNextTestMethod() {
        synchronized (this.monitor) {
            while ((this.isAlive) && (this.tests.isEmpty())) {
                try {
                    this.monitor.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            return (this.isAlive) ? this.tests.poll() : null;
        }
    }

    public static class TestMethod {
        public final Class<?> testClass;
        public final Method method;

        public TestMethod(Class<?> testClass, Method method) {
            this.testClass = testClass;
            this.method = method;
        }

        @Override
        public String toString() {
            return "TestMethod { class: " + this.testClass.getName() + ", method: " + this.method.getName() + " }";
        }
    }

    public static class TestResult {
        public final Class<?> testClass;
        public final Method testMethod;
        public final boolean successful;
        public final long durationNanos;

        private TestResult(Class<?> testClass, Method testMethod, boolean successful, long durationNanos) {
            this.testClass = testClass;
            this.testMethod = testMethod;
            this.successful = successful;
            this.durationNanos = durationNanos;
        }
    }
}
