package spin.core.execution.type;

import spin.core.output.ExecutionStatus;
import spin.core.util.ObjectChecker;

/**
 * A report detailing the outcome of executing an {@link ExecutionTask}.
 *
 * {@link ExecutionReport#status}: the execution status of the executed task.
 * {@link ExecutionReport#executionDurationNanos}: the duration in nanoseconds the task took to execute.
 * {@link ExecutionReport#testStdout}: the contents written to stdout during the execution of the task.
 * {@link ExecutionReport#testStderr}: the contents written to stderr during the execution of the task.
 */
public final class ExecutionReport {
    public final ExecutionStatus status;
    public final long executionDurationNanos;
    public final byte[] testStdout;
    public final byte[] testStderr;

    private ExecutionReport(ExecutionStatus status, long executionDurationNanos, byte[] stdout, byte[] stderr) {
        this.status = status;
        this.executionDurationNanos = executionDurationNanos;
        this.testStdout = stdout;
        this.testStderr = stderr;
    }

    public static final class Builder {
        private ExecutionStatus status;
        private Long executionDurationNanos;
        private byte[] stdout;
        private byte[] stderr;

        public static Builder newBuilder() {
            return new Builder();
        }

        public Builder executionStatus(ExecutionStatus status) {
            this.status = status;
            return this;
        }

        public Builder executionDurationNanos(long duration) {
            this.executionDurationNanos = duration;
            return this;
        }

        public Builder executionStdout(byte[] stdout) {
            this.stdout = stdout;
            return this;
        }

        public Builder executionStderr(byte[] stderr) {
            this.stderr = stderr;
            return this;
        }

        public ExecutionReport build() {
            ObjectChecker.assertNonNull(this.status, this.executionDurationNanos, this.stdout, this.stderr);
            return new ExecutionReport(this.status, this.executionDurationNanos, this.stdout, this.stderr);
        }
    }
}
