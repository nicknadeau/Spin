package spin.client.standalone.runner;

import spin.client.standalone.execution.TestInfo;
import spin.client.standalone.util.Logger;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A class that is responsible for receiving a {@link TestSuite} object and for loading all of its classes, constructing
 * whatever additional information is required, and handing these tests off to any of the outgoing queues it has so that
 * they may be picked up with all the required context by some downstream consumer.
 */
public final class TestSuiteRunner implements Runnable {
    private static final Logger LOGGER = Logger.forClass(TestSuiteRunner.class);
    private final Object monitor = new Object();
    private final List<BlockingQueue<TestInfo>> outgoingTestQueues;
    private TestSuite suite = null;
    private volatile boolean isAlive = true;

    private TestSuiteRunner(List<BlockingQueue<TestInfo>> outgoingTestQueues) {
        if (outgoingTestQueues == null) {
            throw new NullPointerException("outgoingTestQueues must be non-null.");
        }
        this.outgoingTestQueues = outgoingTestQueues;
    }

    /**
     * Constructs a new suite runner that will put all of the tests it receives into the given queues. It will attempt
     * to add tests to these queues fairly so that they each receive a roughly equal load.
     *
     * @param outgoingTestQueues The queues to load the tests into.
     * @return the suite runner.
     */
    public static TestSuiteRunner withOutgoingQueue(List<BlockingQueue<TestInfo>> outgoingTestQueues) {
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
                        List<TestInfo> testInfos = new ArrayList<>();
                        for (Class<?> testClass : testClasses) {
                            for (Method method : testClass.getDeclaredMethods()) {
                                if (method.getAnnotation(org.junit.Test.class) != null) {
                                    testInfos.add(new TestInfo(testClass, method, testSuiteDetails));
                                    int currCount = testsCount.get(testClass);
                                    testsCount.put(testClass, currCount + 1);
                                }
                            }
                        }
                        logTestMethods(testInfos);

                        // Execute each of the declared test methods.
                        int index = 0;
                        while (index < testInfos.size()) {
                            for (BlockingQueue<TestInfo> outgoingTestQueue : this.outgoingTestQueues) {
                                outgoingTestQueue.put(testInfos.get(index));
                                index++;

                                if (index >= testInfos.size()) {
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

    /**
     * Attempts to load the given suite into this runner. If this runner is already occupied with a suite then this
     * method will block until space frees and the suite could be loaded or until the runner is shutdown or the timeout
     * elapses, whichever happens first.
     *
     * Returns true iff the suite was successfully loaded into the runner.
     *
     * @param suite The suite to load.
     * @param timeout The timeout duration.
     * @param unit The timeout duration units.
     * @return whether or not the suite was loaded.
     */
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

    /**
     * Shuts down this runner.
     */
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

    private static void logTestMethods(Collection<TestInfo> testInfos) {
        for (TestInfo testInfo : testInfos) {
            LOGGER.log("Declared test: " + testInfo);
        }
    }

    @Override
    public String toString() {
        return this.getClass().getName() + (this.isAlive ? " { [running] }" : " { [shutdown] }");
    }
}
