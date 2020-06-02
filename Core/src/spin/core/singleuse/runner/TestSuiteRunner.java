package spin.core.singleuse.runner;

import spin.core.singleuse.execution.TestInfo;
import spin.core.singleuse.lifecycle.ShutdownMonitor;
import spin.core.singleuse.util.CloseableBlockingQueue;
import spin.core.singleuse.util.Logger;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * A class that is responsible for receiving a {@link TestSuite} object and for loading all of its classes, constructing
 * whatever additional information is required, and handing these tests off to any of the outgoing queues it has so that
 * they may be picked up with all the required context by some downstream consumer.
 */
public final class TestSuiteRunner implements Runnable {
    private static final Logger LOGGER = Logger.forClass(TestSuiteRunner.class);
    private final Object monitor = new Object();
    private final ShutdownMonitor shutdownMonitor;
    private final List<CloseableBlockingQueue<TestInfo>> outgoingTestQueues;
    private final CloseableBlockingQueue<TestSuite> incomingSuiteQueue;
    private volatile boolean isAlive = true;

    private TestSuiteRunner(ShutdownMonitor shutdownMonitor, CloseableBlockingQueue<TestSuite> incomingSuiteQueue, List<CloseableBlockingQueue<TestInfo>> outgoingTestQueues) {
        if (shutdownMonitor == null) {
            throw new NullPointerException("shutdownMonitor must be non-null.");
        }
        if (incomingSuiteQueue == null) {
            throw new NullPointerException("incomingSuiteQueue must be non-null.");
        }
        if (outgoingTestQueues == null) {
            throw new NullPointerException("outgoingTestQueues must be non-null.");
        }
        this.shutdownMonitor = shutdownMonitor;
        this.incomingSuiteQueue = incomingSuiteQueue;
        this.outgoingTestQueues = outgoingTestQueues;
    }

    /**
     * Constructs a new suite runner that will put all of the tests it receives into the given queues. It will attempt
     * to add tests to these queues fairly so that they each receive a roughly equal load.
     *
     * @param shutdownMonitor The shutdown monitor.
     * @param incomingSuiteQueue The queue in which test suites are loaded into.
     * @param outgoingTestQueues The queues to load the tests into.
     * @return the suite runner.
     */
    public static TestSuiteRunner withOutgoingQueue(ShutdownMonitor shutdownMonitor, CloseableBlockingQueue<TestSuite> incomingSuiteQueue, List<CloseableBlockingQueue<TestInfo>> outgoingTestQueues) {
        return new TestSuiteRunner(shutdownMonitor, incomingSuiteQueue, outgoingTestQueues);
    }

    @Override
    public void run() {
        try {

            while (this.isAlive) {

                try {
                    LOGGER.log("Attempting to fetch next test suite to load...");
                    TestSuite testSuite = this.incomingSuiteQueue.poll(5, TimeUnit.MINUTES);
                    if (testSuite != null) {
                        LOGGER.log("Got next test suite to load.");

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
                        LOGGER.log("Loading all test classes as Class objects.");
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
                            if (!this.isAlive) {
                                break;
                            }

                            for (CloseableBlockingQueue<TestInfo> outgoingTestQueue : this.outgoingTestQueues) {
                                if (!this.isAlive) {
                                    break;
                                }

                                LOGGER.log("Attempting to submit test #" + (index + 1) + " of " + testInfos.size());
                                if (outgoingTestQueue.add(testInfos.get(index), 15, TimeUnit.SECONDS)) {
                                    LOGGER.log("Submitted test #" + (index + 1));
                                    index++;
                                }

                                if (index >= testInfos.size()) {
                                    break;
                                }
                            }
                        }
                    }
                } catch (ClassNotFoundException | InterruptedException e) {
                    LOGGER.log("Unexpected error.");
                    e.printStackTrace();
                }
            }

        } catch (Throwable t) {
            this.shutdownMonitor.panic(t);
        } finally {
            this.isAlive = false;
            LOGGER.log("Exiting.");
        }
    }

    /**
     * Shuts down this runner.
     */
    public void shutdown() {
        this.isAlive = false;
        synchronized (this.monitor) {
            this.monitor.notifyAll();
        }
    }

    private static void logTestMethods(Collection<TestInfo> testInfos) {
        for (TestInfo testInfo : testInfos) {
            LOGGER.log("Test: " + testInfo);
        }
    }

    @Override
    public String toString() {
        return this.getClass().getName() + (this.isAlive ? " { [running] }" : " { [shutdown] }");
    }
}
