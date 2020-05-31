package spin.client.standalone.execution;

import spin.client.standalone.runner.TestSuiteDetails;

import java.lang.reflect.Method;

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
    public final Class<?> testClass;
    public final Method testMethod;
    public final boolean successful;
    public final long durationNanos;
    public final TestSuiteDetails testSuiteDetails;

    TestResult(Class<?> testClass, Method testMethod, boolean successful, long durationNanos, TestSuiteDetails testSuiteDetails) {
        this.testClass = testClass;
        this.testMethod = testMethod;
        this.successful = successful;
        this.durationNanos = durationNanos;
        this.testSuiteDetails = testSuiteDetails;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " { class: " + this.testClass.getName() + ", method: " + this.testMethod.getName() + ", successful: " + this.successful + " }";
    }
}
