package spin.core.singleuse.runner;

/**
 * A description of the test suite. The path of the base test directory which all the tests are located in, the array
 * of all paths to each test class file in the suite, and a classloader used to load these classes with.
 */
public final class TestSuite {
    final String testBaseDirPath;
    final String[] testClassPaths;
    final ClassLoader classLoader;

    public TestSuite(String testBaseDirPath, String[] testClassPaths, ClassLoader classLoader) {
        this.testBaseDirPath = testBaseDirPath;
        this.testClassPaths = testClassPaths;
        this.classLoader = classLoader;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " { using test base dir: " + this.testBaseDirPath + " }";
    }
}
