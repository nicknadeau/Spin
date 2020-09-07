package spin.core.execution.type;

import spin.core.util.ObjectChecker;

/**
 * A report detailing the outcome of executing an {@link ExecutionTask}.
 *
 * {@link ExecutionReport#isSuccessful}: whether or not the task executed successfully.
 * {@link ExecutionReport#executionDurationNanos}: the duration in nanoseconds the task took to execute.
 * {@link ExecutionReport#stdout}: the contents written to stdout during the execution of the task.
 * {@link ExecutionReport#stderr}: the contents written to stderr during the execution of the task.
 */
public final class ExecutionReport {
    public final boolean isSuccessful;
    public final long executionDurationNanos;
    public final String stdout;
    public final String stderr;

    private ExecutionReport(boolean isSuccessful, long executionDurationNanos, String stdout, String stderr) {
        this.isSuccessful = isSuccessful;
        this.executionDurationNanos = executionDurationNanos;
        this.stdout = stdout;
        this.stderr = stderr;
    }

    public static final class Builder {
        private Boolean isSuccessful;
        private Long executionDurationNanos;
        private String stdout;
        private String stderr;

        public static Builder newBuilder() {
            return new Builder();
        }

        public Builder isSuccessful(boolean isSuccessful) {
            this.isSuccessful = isSuccessful;
            return this;
        }

        public Builder executionDurationNanos(long duration) {
            this.executionDurationNanos = duration;
            return this;
        }

        public Builder executionStdout(String stdout) {
            this.stdout = stdout;
            return this;
        }

        public Builder executionStderr(String stderr) {
            this.stderr = stderr;
            return this;
        }

        public ExecutionReport build() {
            ObjectChecker.assertNonNull(this.isSuccessful, this.executionDurationNanos, this.stdout, this.stderr);
            return new ExecutionReport(this.isSuccessful, this.executionDurationNanos, this.stdout, this.stderr);
        }
    }
}
