package spin.core.execution;

import spin.core.server.session.RequestSessionContext;
import spin.core.runner.TestSuiteDetails;

import java.lang.reflect.Method;

/**
 * A class that holds basic information about a test. The test method itself, the class the test is declared in as well
 * as the suite details for the test suite that this test is apart of.
 */
public final class TestInfo {
    public final Class<?> testClass;
    public final Method method;
    public final TestSuiteDetails testSuiteDetails;
    public final RequestSessionContext sessionContext;
    private int testSuiteDatabaseId;
    private int testClassDatabaseId;

    public TestInfo(Class<?> testClass, Method method, TestSuiteDetails testSuiteDetails, RequestSessionContext sessionContext) {
        this.testClass = testClass;
        this.method = method;
        this.testSuiteDetails = testSuiteDetails;
        this.sessionContext = sessionContext;
    }

    public void setTestClassDatabaseId(int id) {
        this.testClassDatabaseId = id;
    }

    public int getTestClassDatabaseId() {
        return this.testClassDatabaseId;
    }

    public void setTestSuiteDatabaseId(int id) {
        this.testSuiteDatabaseId = id;
    }

    public int getTestSuiteDatabaseId() {
        return this.testSuiteDatabaseId;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " { class: " + this.testClass.getName() + ", method: " + this.method.getName() + " }";
    }
}
