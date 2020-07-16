package spin.core.singleuse;

import spin.core.singleuse.lifecycle.LifecycleManager;
import spin.core.singleuse.lifecycle.ShutdownMonitor;
import spin.core.singleuse.runner.TestSuite;
import spin.core.singleuse.util.Logger;
import spin.core.singleuse.util.ThreadLocalPrintStream;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.regex.Pattern;

/**
 * A single-use entry point to the system that runs a test suite and then exits.
 */
public final class SingleUseEntryPoint {
    private static final Logger LOGGER = Logger.forClass(SingleUseEntryPoint.class);

    /**
     * We expect to be given the following arguments:
     *
     * args[0] = TEST-BASE
     * args[1] = MATCHER
     * args[2..K] = each of the K - 1 dependencies
     *
     * The TEST-BASE argument is a path to a directory which contains all of the .class test files to be potentially
     * run by this program. These .class files are located in subdirectories corresponding to their package names.
     *
     * The MATCHER is a regular expression that is used to filter all of the .class files in the TEST-BASE directory
     * so that any such files matching the expression will be selected as test classes to be run by this program. Any
     * others will still be loaded into the class loader, but will not be run as tests. All of these selected test
     * classes constitute the "test suite" that is to be run.
     *
     * The K - 1 dependencies are each paths to either a directory or a file. If a directory, then it must be the base
     * directory of some compiled .class java class files. If a file, then it must be a .jar file. All of these will be
     * loaded into the class loader while executing this suite.
     *
     * @param args The program arguments.
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

            // Get the TEST-BASE directory and the MATCHER regular expression.
            String testsBaseDir = args[0];
            Pattern testToRun = Pattern.compile(args[1]);
            LOGGER.log("Given base test directory: " + testsBaseDir);
            LOGGER.log("Given the regular expression: " + args[1]);

            // Fetch all of the test classes to run.
            File baseDir = new File(testsBaseDir);
            if (!baseDir.exists()) {
                throw new IllegalArgumentException("Tests base dir does not exist.");
            }
            if (!baseDir.isDirectory()) {
                throw new IllegalArgumentException("Tests base dir is not a directory.");
            }
            List<String> classNames = new ArrayList<>();
            fetchAllFullyQualifiedTestClassNames(testToRun, baseDir.getCanonicalPath().length(), baseDir, classNames);

            // Create the lifecycle manager. This class will start up all the components of the system and manage them.
            LifecycleManager lifecycleManager = LifecycleManager.withNumExecutors(numThreads, writeToDb, dbConfigPath);
            shutdownMonitor = lifecycleManager.getShutdownMonitor();
            Thread lifecycleManagerThread = new Thread(lifecycleManager, "LifecycleManager");
            lifecycleManagerThread.start();

            // Grab all of the dependencies given to us and inject them into a new ClassLoader which we will use to load the test classes.
            int numDependencies = args.length - 2;
            LOGGER.log("Number of given dependencies: " + numDependencies);

            // Plus 1 dependency because we also add the base test dir as a dependency (there may be non-test helper
            // classes defined in the test directory after all).
            URL[] dependencyUrls = new URL[numDependencies + 1];
            for (int i = 0; i < numDependencies; i++) {
                dependencyUrls[i] = new File(args[2 + i]).toURI().toURL();
            }
            dependencyUrls[numDependencies] = new File(testsBaseDir).toURI().toURL();

            URLClassLoader classLoader = new URLClassLoader(dependencyUrls);

            LOGGER.log("Loading test suite...");
            TestSuite testSuite = new TestSuite(classNames, classLoader);
            if (!lifecycleManager.getTestSuiteQueue().add(testSuite)) {
                throw new IllegalArgumentException("failed to add test suite to queue.");
            }
            LOGGER.log("Test suite loaded.");

            // Done, now wait for the lifecycle manager to shutdown, signalling the program is done running.
            lifecycleManagerThread.join();
            //TODO: we should be exiting with a non-zero exit code if the manager detected a panic.

        } catch (Throwable t) {
            if (shutdownMonitor == null) {
                System.err.println("Unexpected Error!");
                t.printStackTrace();
            } else {
                shutdownMonitor.panic(t);
            }
            System.exit(1);
        } finally {
            LOGGER.log("Exiting.");
        }
    }

    private static void fetchAllFullyQualifiedTestClassNames(Pattern testPattern, int baseDirLength, File currDir, List<String> classNames) throws IOException {
        for (File file : currDir.listFiles()) {
            if (file.isFile() && testPattern.matcher(file.getName()).matches()) {
                String fullPath = file.getCanonicalPath();
                String classPathWithBaseDirStripped = fullPath.substring(baseDirLength);
                String classNameWithSuffixStripped = classPathWithBaseDirStripped.substring(0, classPathWithBaseDirStripped.length() - ".class".length());
                String classNameBinaryformat = classNameWithSuffixStripped.replaceAll("/", ".");

                if (classNameBinaryformat.startsWith(".")) {
                    classNameBinaryformat = classNameBinaryformat.substring(1);
                }

                LOGGER.log("Binary name of submitted test class: " + classNameBinaryformat);
                classNames.add(classNameBinaryformat);
            } else if (file.isDirectory()) {
                fetchAllFullyQualifiedTestClassNames(testPattern, baseDirLength, file, classNames);
            }
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
