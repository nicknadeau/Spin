package spin.core.output;

import spin.core.execution.type.ExecutionReport;
import spin.core.util.ObjectChecker;

import java.lang.reflect.Method;

/**
 * A result detailing the outcome of executing a test.
 *
 * {@link TestExecutionResult#testClass}: the test class in which the test method is defined.
 * {@link TestExecutionResult#testMethod}: the test method with the {@link org.junit.Test} annotation.
 * {@link TestExecutionResult#executionStatus}: the execution status of the executed test.
 * {@link TestExecutionResult#executionDurationNanos}: the duration in nanoseconds the test took to execute.
 * {@link TestExecutionResult#testStdout}: the contents written to stdout during the execution of the test.
 * {@link TestExecutionResult#testStderr}: the contents written to stderr during the execution of the test.
 */
public final class TestExecutionResult {
    public final Class<?> testClass;
    public final Method testMethod;
    public final ExecutionStatus executionStatus;
    public final long executionDurationNanos;
    public final byte[] testStdout;
    public final byte[] testStderr;

    private TestExecutionResult(Class<?> testClass, Method testMethod, ExecutionStatus executionStatus, long executionDurationNanos, byte[] testStdout, byte[] testStderr) {
        this.testClass = testClass;
        this.testMethod = testMethod;
        this.executionStatus = executionStatus;
        this.executionDurationNanos = executionDurationNanos;
        this.testStdout = testStdout;
        this.testStderr = testStderr;
    }

    /**
     * Returns a human-readable descriptor for the test.
     *
     * @return the test descriptor.
     */
    public String getTestDescriptor() {
        return this.testClass.getName() + ":" + this.testMethod.getName();
    }

    public static final class Builder {
        private Class<?> testClass;
        private Method testMethod;
        private ExecutionReport report;

        public static Builder newBuilder() {
            return new Builder();
        }

        public Builder testClass(Class<?> testClass) {
            this.testClass = testClass;
            return this;
        }

        public Builder testMethod(Method testMethod) {
            this.testMethod = testMethod;
            return this;
        }

        public Builder fromReport(ExecutionReport report) {
            this.report = report;
            return this;
        }

        public TestExecutionResult build() {
            ObjectChecker.assertNonNull(this.testClass, this.testMethod, this.report);
            return new TestExecutionResult(
                    this.testClass,
                    this.testMethod,
                    this.report.status,
                    this.report.executionDurationNanos,
                    this.report.testStdout,
                    this.report.testStderr);
        }
    }
}
