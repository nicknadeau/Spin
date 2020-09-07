package spin.core.execution;

import spin.core.execution.type.ExecutionReport;
import spin.core.output.ExecutionStatus;
import spin.core.server.session.RequestSessionContext;
import spin.core.runner.TestSuiteDetails;
import spin.core.util.ObjectChecker;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * The result of running a test.
 *
 * A test result has the test method reference itself, the class in which the test is defined, whether or not the test
 * was successful as well as the duration in nanoseconds the test took to execute.
 *
 * This result also holds onto a global {@link TestSuiteDetails} object that holds live information about the test suite
 * this test belongs to so that the suite can be tracked throughout the system.
 */
public final class TestResult {
    public final boolean isEmptySuite;
    public final Collection<Class<?>> emptyClasses;
    public final Class<?> testClass;
    public final Method testMethod;
    public final ExecutionStatus status;
    public final long durationNanos;
    public final byte[] testStdout;
    public final byte[] testStderr;
    public final TestSuiteDetails testSuiteDetails;
    public final RequestSessionContext sessionContext;
    public final int testSuiteDbId;
    public final int testClassDbId;

    private TestResult(boolean isEmptySuite, Collection<Class<?>> emptyClasses, Class<?> testClass, Method testMethod, ExecutionStatus status, long durationNanos, byte[] stdout, byte[] stderr, TestSuiteDetails testSuiteDetails, RequestSessionContext sessionContext, int testSuiteDbId, int testClassDbId) {
        this.isEmptySuite = isEmptySuite;
        this.emptyClasses = emptyClasses;
        this.testClass = testClass;
        this.testMethod = testMethod;
        this.status = status;
        this.durationNanos = durationNanos;
        this.testStdout = stdout;
        this.testStderr = stderr;
        this.testSuiteDetails = testSuiteDetails;
        this.sessionContext = sessionContext;
        this.testSuiteDbId = testSuiteDbId;
        this.testClassDbId = testClassDbId;
    }

    public static TestResult forEmptySuite(Collection<Class<?>> emptyClasses, int testSuiteDbId) {
        return new TestResult(true, emptyClasses, null, null, ExecutionStatus.SUCCESS, 0, null, null, null, null, testSuiteDbId, -1);
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " { class: " + this.testClass.getName() + ", method: " + this.testMethod.getName() + ", status: " + this.status + " }";
    }

    public static final class Builder {
        private TestInfo testInfo;
        private ExecutionReport executionReport;

        public static Builder newBuilder() {
            return new Builder();
        }

        public Builder fromExecutionReport(ExecutionReport report) {
            this.executionReport = report;
            return this;
        }

        public Builder fromTestInfo(TestInfo testInfo) {
            this.testInfo = testInfo;
            return this;
        }

        public TestResult build() {
            ObjectChecker.assertNonNull(this.executionReport, this.testInfo);
            return new TestResult(
                    false,
                    null,
                    this.testInfo.testClass,
                    this.testInfo.method,
                    this.executionReport.status,
                    this.executionReport.executionDurationNanos,
                    this.executionReport.testStdout,
                    this.executionReport.testStderr,
                    this.testInfo.testSuiteDetails,
                    this.testInfo.sessionContext,
                    this.testInfo.getTestSuiteDatabaseId(),
                    this.testInfo.getTestClassDatabaseId()
            );
        }
    }
}
