package spin.core.output;

import spin.core.util.ObjectChecker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A class that is responsible for writing the execution results of a single test suite.
 *
 * Each write must be associated with exactly one suite and it MUST be closed after all suite results have been written
 * in order to flush all of the information through the write to whatever output channel or channels it is hooked into.
 *
 * A writer has zero or more adapters attached to it. Each adapter is actually responsible for publishing the results
 * to whatever output channel that adapter implementation is tied to. If multiple adapters are present then results
 * may be written to multiple output channels. If zero adapters are present then the writer is absolutely silent and
 * publishes nothing.
 *
 * Since a writer is responsible for publishing details relating to test classes and suites as a whole, and not just
 * test methods, and since a test suite may be too large to hold in memory at any one time and thus its size and scope
 * is always unknown, this writer is always able to publish test method information immediately upon receiving them.
 * However, it is unable to publish class or suite information until it has been closed, in which case the class and
 * suite information is then derived based off everything this writer has witnessed, and only then are the test class
 * and suite results published.
 *
 * A test suite result writer is NOT thread-safe and should either only be used by a single thread or have access to it
 * properly synchronized.
 */
public final class TestSuiteResultWriter implements AutoCloseable {
    private final List<ResultWriterAdapter> adapters = new ArrayList<>();
    private final Map<String, ExecutionDetails> testClassDetails = new HashMap<>();
    private final ExecutionDetails testSuiteDetails = new ExecutionDetails();
    private final String testSuiteDescriptor;
    private boolean isClosed = false;

    private TestSuiteResultWriter(String testSuiteDescriptor, List<ResultWriterAdapter> adapters) {
        this.testSuiteDescriptor = testSuiteDescriptor;
        this.adapters.addAll(adapters);
    }

    /**
     * Writes the specified test execution result to each of the underlying adapters.
     *
     * @param result The result to write.
     */
    public void writeExecutionResult(TestExecutionResult result) {
        throwIfClosed();
        ObjectChecker.assertNonNull(result);

        for (ResultWriterAdapter adapter : this.adapters) {
            adapter.writeTest(result.getTestDescriptor(), result.executionStatus, result.executionDurationNanos, result.testStdout, result.testStderr);
        }

        updateTestClassDetails(result);
        updateExecutionDetails(this.testSuiteDetails, result);
    }

    /**
     * Closes this writer, flushing whatever contents have not been written yet to each of the adapters to cause them to
     * write if the write is not already closed.
     */
    @Override
    public void close() {
        if (!this.isClosed) {
            // Publish the class & suite details for each class.

            for (Map.Entry<String, ExecutionDetails> classDetailEntry : this.testClassDetails.entrySet()) {
                for (ResultWriterAdapter adapter : this.adapters) {
                    ExecutionDetails details = classDetailEntry.getValue();
                    AdditionalResultDetails additionalDetails = AdditionalResultDetails.Builder.newBuilder()
                            .addStatusCounts(details.statusCounts)
                            .build();

                    adapter.writeClass(classDetailEntry.getKey(), details.status, details.durationNanos, additionalDetails);
                }
            }

            for (ResultWriterAdapter adapter : this.adapters) {
                AdditionalResultDetails additionalDetails = AdditionalResultDetails.Builder.newBuilder()
                        .addStatusCounts(this.testSuiteDetails.statusCounts)
                        .build();
                adapter.writeSuite(this.testSuiteDescriptor, this.testSuiteDetails.status, this.testSuiteDetails.durationNanos, additionalDetails);
            }

            this.isClosed = true;
        }
    }

    private void updateTestClassDetails(TestExecutionResult result) {
        String testClassDescriptor = result.testClass.getName();

        ExecutionDetails details = this.testClassDetails.get(testClassDescriptor);
        if (details == null) {
            details = new ExecutionDetails();
            this.testClassDetails.put(testClassDescriptor, details);
        }

        updateExecutionDetails(details, result);
    }

    private static void updateExecutionDetails(ExecutionDetails details, TestExecutionResult result) {
        // Status is determined by test status. Once suite is considered FAILED it is always failed.
        if ((details.status == null) || (details.status == ExecutionStatus.SUCCESS)) {
            details.status = result.executionStatus;
        }

        details.durationNanos += result.executionDurationNanos;
        details.statusCounts.merge(result.executionStatus, 1, Integer::sum);
    }

    private void throwIfClosed() {
        if (this.isClosed) {
            throw new IllegalStateException("writer is closed.");
        }
    }

    private static final class ExecutionDetails {
        private ExecutionStatus status = null;
        private long durationNanos = 0;
        private final Map<ExecutionStatus, Integer> statusCounts = new HashMap<>();
    }

    private static final class Builder {
        private String testSuiteDescriptor;
        private final List<ResultWriterAdapter> adapters = new ArrayList<>();

        public static Builder newBuilder() {
            return new Builder();
        }

        public Builder testSuiteDescriptor(String descriptor) {
            this.testSuiteDescriptor = descriptor;
            return this;
        }

        public Builder attachAdapter(ResultWriterAdapter adapter) {
            ObjectChecker.assertNonNull(adapter);
            this.adapters.add(adapter);
            return this;
        }

        public TestSuiteResultWriter build() {
            return new TestSuiteResultWriter(this.testSuiteDescriptor, this.adapters);
        }
    }
}
