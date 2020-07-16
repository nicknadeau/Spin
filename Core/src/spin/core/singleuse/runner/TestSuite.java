package spin.core.singleuse.runner;

import java.util.List;

/**
 * A description of the test suite. The list of all paths to each test class file in the suite, and a classloader used
 * to load these classes with.
 */
public final class TestSuite {
    final List<String> testClassPaths;
    final ClassLoader classLoader;

    public TestSuite(List<String> testClassPaths, ClassLoader classLoader) {
        this.testClassPaths = testClassPaths;
        this.classLoader = classLoader;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " { contains " + this.testClassPaths.size() + " class(es) }";
    }
}
