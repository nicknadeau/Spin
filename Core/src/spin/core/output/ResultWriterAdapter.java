package spin.core.output;

/**
 * An adapter that gets attached to a {@link TestSuiteResultWriter} and is triggered by it to write test, class and
 * suite results as they come in to whatever underlying output channel this adapter's implementation is for.
 *
 * In general, an adapter should be prepared to receive test, class and suite write requests in any arbitrary order
 * since the system executes tests in parallel. If the adapter must enforce some sort of serialization of the order of
 * writing outputs then it must do so in its implementation and not rely on being called in a serial manner.
 *
 * Adapter implementations should attempt to be reasonably memory-efficient so that even very large test suites can be
 * run without issue.
 */
public interface ResultWriterAdapter {

    /**
     * Invoked when a new test has completed its execution and its results are thus ready to be written.
     *
     * @param testDescriptor The test's human-readable descriptor.
     * @param status The status of the test.
     * @param durationNanos The total duration of the test's execution.
     * @param stdout The stdout contents written during the test's execution.
     * @param stderr The stderr contents written during the test's execution.
     */
    public void writeTest(String testDescriptor, ExecutionStatus status, long durationNanos, byte[] stdout, byte[] stderr);

    /**
     * Invoked when a test class has completed its execution and its results are thus ready to be written.
     *
     * A test class is considered complete only once all of its tests have completed.
     *
     * @param classDescriptor The class's human-readable descriptor.
     * @param status The status of the entire test class's execution.
     * @param durationNanos The total cumulative duration of all the test durations in the test class.
     * @param additionalDetails Additional details relating to the execution of the test class.
     */
    public void writeClass(String classDescriptor, ExecutionStatus status, long durationNanos, AdditionalResultDetails additionalDetails);

    /**
     * Invoked when a test suite has completed its execution and its results are thus ready to be written.
     *
     * A test suite is considered complete only once all of its test classes have completed.
     *
     * @param suiteDescriptor The suite's human-readable descriptor.
     * @param status The status of the entire test suite's execution.
     * @param durationNanos The total cumulative duration of all the test class durations in the suite.
     * @param additionalDetails Additional details relating to the execution of the test suite.
     */
    public void writeSuite(String suiteDescriptor, ExecutionStatus status, long durationNanos, AdditionalResultDetails additionalDetails);
}
