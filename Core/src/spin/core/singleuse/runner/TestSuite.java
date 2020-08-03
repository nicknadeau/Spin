package spin.core.singleuse.runner;

import spin.core.server.session.RequestSessionContext;

import java.util.List;

/**
 * A description of the test suite. The list of all paths to each test class file in the suite, and a classloader used
 * to load these classes with.
 */
public final class TestSuite {
    final List<String> testClassPaths;
    final ClassLoader classLoader;
    final RequestSessionContext sessionContext;
    final int suiteId;

    public TestSuite(List<String> testClassPaths, ClassLoader classLoader, RequestSessionContext context, int suiteId) {
        this.testClassPaths = testClassPaths;
        this.classLoader = classLoader;
        this.sessionContext = context;
        this.suiteId = suiteId;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " { suite id: " + this.suiteId + ", contains " + this.testClassPaths.size() + " class(es) }";
    }
}
