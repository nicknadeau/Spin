package spin.core.singleuse.execution;

import spin.core.singleuse.runner.TestSuiteDetails;

import java.lang.reflect.Method;

/**
 * A class that holds basic information about a test. The test method itself, the class the test is declared in as well
 * as the suite details for the test suite that this test is apart of.
 */
public final class TestInfo {
    public final Class<?> testClass;
    public final Method method;
    public final TestSuiteDetails testSuiteDetails;

    public TestInfo(Class<?> testClass, Method method, TestSuiteDetails testSuiteDetails) {
        this.testClass = testClass;
        this.method = method;
        this.testSuiteDetails = testSuiteDetails;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " { class: " + this.testClass.getName() + ", method: " + this.method.getName() + " }";
    }
}
