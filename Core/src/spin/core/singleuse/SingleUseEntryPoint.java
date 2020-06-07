package spin.core.singleuse;

import spin.core.singleuse.lifecycle.LifecycleManager;
import spin.core.singleuse.lifecycle.ShutdownMonitor;
import spin.core.singleuse.runner.TestSuite;
import spin.core.singleuse.util.Logger;
import spin.core.singleuse.util.ThreadLocalPrintStream;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * A single-use entry point to the system that runs a test suite and then exits.
 */
public final class SingleUseEntryPoint {
    private static final Logger LOGGER = Logger.forClass(SingleUseEntryPoint.class);

    /**
     * We expect to be given N test classes and M dependencies.
     *
     * These N test classes should be given to us as canonical paths to each of those classes.
     * These N test classes are all expected to be compiled classes (ie. they are .class files).
     * These N test classes are all expected to belong to the same base test directory, call it TEST-BASE, so that every
     * one of the given canonical paths can be thought of as: TEST-BASE + (class package name slash-style) + (.class extension).
     *
     * These M dependencies are expected to be given to us as canonical paths to each dependency file or directory.
     * If a dependency is a file then it must be a .jar file.
     * If a dependency is a directory then it must contain .class files in their appropriate package structure.
     *
     * All of these parameters must be passed into the args of this main method as follows:
     *
     * args at index 0: TEST-BASE
     * args at index 1: N
     * args at indices [2..2+N]: each of the N test classes
     * args at indices [2+N+1..2+N+M]: each of the M dependencies
     */
    public static void main(String[] args) {
        ShutdownMonitor shutdownMonitor = null;

        try {
            if (args == null) {
                System.err.println("Null arguments given.");
                System.err.println(usage());
                System.exit(1);
            }

            String enableLoggerProperty = System.getProperty("enable_logger");
            String writeToDbProperty = System.getProperty("write_to_db");
            String dbConfigPath = System.getProperty("db_config_path");
            String numThreadsProperty = System.getProperty("num_threads");

            if (enableLoggerProperty == null) {
                throw new NullPointerException("Must provider an enable_logger property value.");
            }
            if (writeToDbProperty == null) {
                throw new NullPointerException("Must provider a write_to_db property value.");
            }
            if (dbConfigPath == null) {
                throw new NullPointerException("Must provider a db_config_path property value.");
            }
            if (numThreadsProperty == null) {
                throw new NullPointerException("Must provider a num_threads property value.");
            }

            if (!Boolean.parseBoolean(enableLoggerProperty)) {
                Logger.globalDisable();
            }
            boolean writeToDb = Boolean.parseBoolean(writeToDbProperty);
            int numThreads = Integer.parseInt(numThreadsProperty);
            LOGGER.log("enable_logger property: " + enableLoggerProperty);
            LOGGER.log("write_to_db property: " + writeToDbProperty);
            LOGGER.log("db_config_path property: " + dbConfigPath);
            LOGGER.log("num_threads property: " + numThreadsProperty);

            overrideOutputStreams();

            logArguments(args);

            if (args.length < 2) {
                System.err.println(usage());
                System.exit(1);
            }

            // Get the TEST-BASE directory and the number of test classes submitted to us.
            String testsBaseDir = args[0] + "/";
            int numTestClasses = Integer.parseInt(args[1], 10);
            LOGGER.log("Given base test directory: " + testsBaseDir);
            LOGGER.log("Given number of test classes: " + numTestClasses);

            if (args.length < 2 + numTestClasses) {
                System.err.println("Number of test classes is " + numTestClasses + ". Args length is too short: " + args.length);
                System.err.println(usage());
                System.exit(1);
            }

            // Create the lifecycle manager. This class will start up all the components of the system and manage them.
            LifecycleManager lifecycleManager = LifecycleManager.withNumExecutors(numThreads, writeToDb, dbConfigPath);
            shutdownMonitor = lifecycleManager.getShutdownMonitor();
            Thread lifecycleManagerThread = new Thread(lifecycleManager, "LifecycleManager");
            lifecycleManagerThread.start();

            // Grab all of the dependencies given to us and inject them into a new ClassLoader which we will use to load the test classes.
            int numDependencies = args.length - 2 - numTestClasses;
            LOGGER.log("Number of given dependencies: " + numDependencies);

            URL[] dependencyUrls = new URL[numDependencies + 1];
            dependencyUrls[0] = new File(testsBaseDir).toURI().toURL();
            for (int i = 1; i < 1 + numDependencies; i++) {
                dependencyUrls[i] = new File(args[2 + numDependencies + i - 1]).toURI().toURL();
            }
            URLClassLoader classLoader = new URLClassLoader(dependencyUrls);
            String[] testClassPaths = Arrays.copyOfRange(args, 2, 2 + numTestClasses);

            LOGGER.log("Loading test suite...");
            TestSuite testSuite = new TestSuite(testsBaseDir, testClassPaths, classLoader);
            if (!lifecycleManager.getTestSuiteQueue().add(testSuite)) {
                throw new IllegalArgumentException("failed to add test suite to queue.");
            }
            LOGGER.log("Test suite loaded.");

            // Done, now wait for the lifecycle manager to shutdown, signalling the program is done running.
            lifecycleManagerThread.join();

        } catch (Throwable t) {
            if (shutdownMonitor == null) {
                System.err.println("Unexpected Error!");
                t.printStackTrace();
            } else {
                shutdownMonitor.panic(t);
            }
        } finally {
            LOGGER.log("Exiting.");
        }
    }

    private static void overrideOutputStreams() {
        System.setOut(ThreadLocalPrintStream.withInitialStream(System.out));
        System.setErr(ThreadLocalPrintStream.withInitialStream(System.err));
    }

    private static void logArguments(String[] args) {
        LOGGER.log("ARGS ------------------------------------------------");
        for (String a : args) {
            LOGGER.log(a);
        }
        LOGGER.log("ARGS ------------------------------------------------\n");
    }

    private static String usage() {
        return SingleUseEntryPoint.class.getName()
                + " <test base> <num tests> [[test class]...] [[dependency]...]"
                + "\n\ttest base: a canonical path to the directory containing the test classes."
                + "\n\tnum tests: the total number of test classes to be run."
                + "\n\ttest class: a canonical path to the .class test class file to be run."
                + "\n\tdependency: a canonical path to the .jar dependency or a directory of .class file dependencies.";
    }
}
