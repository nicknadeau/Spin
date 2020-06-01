package spin.client.standalone.execution;

import spin.client.standalone.lifecycle.ShutdownMonitor;
import spin.client.standalone.util.CloseableBlockingQueue;
import spin.client.standalone.util.Logger;
import spin.client.standalone.util.ThreadLocalPrintStream;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * A class that executes tests.
 *
 * Tests can be loaded into this executor dynamically as {@link TestInfo} objects and this executor will place the
 * results into a queue that can be polled by a consumer.
 */
public final class TestExecutor implements Runnable {
    private static final Logger LOGGER = Logger.forClass(TestExecutor.class);
    private final Object monitor = new Object();
    private final ShutdownMonitor shutdownMonitor;
    private final CloseableBlockingQueue<TestInfo> tests;
    private final CloseableBlockingQueue<TestResult> results;
    private volatile boolean isAlive = true;

    private TestExecutor(ShutdownMonitor shutdownMonitor, CloseableBlockingQueue<TestInfo> tests, CloseableBlockingQueue<TestResult> results) {
        if (shutdownMonitor == null) {
            throw new NullPointerException("shutdownMonitor must be non-null.");
        }
        if (tests == null) {
            throw new NullPointerException("tests must be non-null.");
        }
        if (results == null) {
            throw new NullPointerException("results must be non-null.");
        }
        this.shutdownMonitor = shutdownMonitor;
        this.tests = tests;
        this.results = results;
    }

    /**
     * Creates a new executor.
     *
     * The executor will wait on new tests to arrive on the designed test queue it is given.
     * The executor will place each new result when it is finished running a test into the given queue.
     *
     * @param shutdownMonitor The shutdown monitor.
     * @param tests The queue in which all incoming tests to be executed by this executor are submitted.
     * @param results The queue that all results are placed in when done by this executor.
     * @return the new executor.
     */
    public static TestExecutor withQueues(ShutdownMonitor shutdownMonitor, CloseableBlockingQueue<TestInfo> tests, CloseableBlockingQueue<TestResult> results) {
        return new TestExecutor(shutdownMonitor, tests, results);
    }

    @Override
    public void run() {
        try {
            while (this.isAlive) {
                LOGGER.log("[" + Thread.currentThread().getName() + "] Waiting for new test method to be loaded...");
                TestInfo testInfo = null;
                try {
                    testInfo = this.tests.poll(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                if (testInfo != null) {
                    LOGGER.log("[" + Thread.currentThread().getName() + "] Found new test method to run.");
                    TestResult result;

                    // Capture the stdout & stderr of the test method on its own private stream so we can publish it later.
                    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
                    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
                    ((ThreadLocalPrintStream) System.out).setStream(new PrintStream(new BufferedOutputStream(stdout)));
                    ((ThreadLocalPrintStream) System.err).setStream(new PrintStream(new BufferedOutputStream(stderr)));

                    long startTime = System.nanoTime();
                    try {
                        Object instance = testInfo.testClass.getConstructor().newInstance();
                        testInfo.method.invoke(instance);
                        long endTime = System.nanoTime();

                        String capturedStdout = closeAndCaptureStream(true, stdout);
                        String capturedStderr = closeAndCaptureStream(false, stderr);

                        result = new TestResult(testInfo.testClass, testInfo.method, true, endTime - startTime, capturedStdout, capturedStderr, testInfo.testSuiteDetails);

                    } catch (Exception e) {
                        long endTime = System.nanoTime();

                        String capturedStdout = closeAndCaptureStream(true, stdout);
                        String capturedStderr = closeAndCaptureStream(false, stderr);

                        result = new TestResult(testInfo.testClass, testInfo.method, false, endTime - startTime, capturedStdout, capturedStderr, testInfo.testSuiteDetails);
                    }

                    synchronized (this.monitor) {
                        try {
                            if (!this.results.add(result)) {
                                throw new IllegalStateException("unable to submit result: queue is closed.");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    LOGGER.log("[" + Thread.currentThread().getName() + "] Completed running test " + testInfo.method.getName() + " in class " + testInfo.testClass.getName());
                }
            }
        } catch (Throwable t) {
            this.shutdownMonitor.panic(t);
        } finally {
            this.isAlive = false;
            LOGGER.log("[" + Thread.currentThread().getName() + "] Exiting.");
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

    @Override
    public String toString() {
        return this.getClass().getName() + (this.isAlive ? " { [running] }" : " { [shutdown] }");
    }

    /**
     * If isStdout is true then the stream captures stdout otherwise it captures stderr.
     *
     * This method will flush the stream, return its contents as a UTF-8 String, then close the stream and restore the
     * {@link ThreadLocalPrintStream} back to its initial value if and only if the given stream is non-null.
     *
     * If the given stream is null then nothing happens and this method simply returns an empty string.
     *
     * ASSUMPTION: the given stream is the stream that the {@link ThreadLocalPrintStream} injected into {@link System#err}/{@link System#out}
     * is redirecting to.
     */
    private static String closeAndCaptureStream(boolean isStdout, ByteArrayOutputStream stream) {
        String output = "";

        if (stream != null) {
            ThreadLocalPrintStream threadLocalOut = (ThreadLocalPrintStream) ((isStdout) ? System.out : System.err);
            threadLocalOut.flush();
            output = new String(stream.toByteArray(), StandardCharsets.UTF_8);
            threadLocalOut.close();
            threadLocalOut.restoreInitialStream();
        }

        return output;
    }
}
