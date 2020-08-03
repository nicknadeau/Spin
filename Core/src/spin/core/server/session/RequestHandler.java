package spin.core.server.session;

import spin.core.lifecycle.CommunicationHolder;
import spin.core.server.type.ResponseEvent;
import spin.core.server.type.RunSuiteRequest;
import spin.core.singleuse.runner.TestSuite;
import spin.core.singleuse.util.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class RequestHandler {
    private static final Logger LOGGER = Logger.forClass(RequestHandler.class);

    //TODO: id should not be decided out here, it should be done internally in a protected & consistent manner and handed back to the handler.
    private static int suiteIds = 0;

    public static void handleRequest(RunSuiteRequest runSuiteRequest) {
        if (runSuiteRequest == null) {
            throw new NullPointerException("runSuiteRequest must be non-null.");
        }
        if (!runSuiteRequest.isContextBound()) {
            throw new IllegalArgumentException("cannot handle request: no session context bound to request.");
        }

        if (runSuiteRequest.getResponseEvent() == ResponseEvent.ALL_RESULTS_PUBLISHED) {
            processRunSuiteRequest(runSuiteRequest);
        } else {
            //TODO
            throw new UnsupportedOperationException("unimplemented: immediate is the only supported option right now.");
        }
    }

    private static void processRunSuiteRequest(RunSuiteRequest runSuiteRequest) {
        //TODO: we shouldn't be doing all this work in the server, it should be out-sourced to the runner.

        try {
            File baseDir = new File(runSuiteRequest.getBaseDirectory());
            if (!baseDir.exists()) {
                throw new IllegalStateException("Tests base dir does not exist.");
            }
            if (!baseDir.isDirectory()) {
                throw new IllegalStateException("Tests base dir is not a directory.");
            }
            List<String> classNames = new ArrayList<>();
            fetchAllFullyQualifiedTestClassNames(Pattern.compile(runSuiteRequest.getMatcher()), baseDir.getCanonicalPath().length(), baseDir, classNames);

            LOGGER.log("Number of given dependencies: " + (runSuiteRequest.getDependencies().length - 1));

            URL[] dependencyUrls = new URL[runSuiteRequest.getDependencies().length];
            for (int i = 0; i < runSuiteRequest.getDependencies().length; i++) {
                dependencyUrls[i] = new File(runSuiteRequest.getDependencies()[i]).toURI().toURL();
            }
            URLClassLoader classLoader = new URLClassLoader(dependencyUrls);

            LOGGER.log("Loading test suite...");
            int suiteId = suiteIds++;
            TestSuite testSuite = new TestSuite(classNames, classLoader, runSuiteRequest.getSessionContext(), suiteId);
            if (!CommunicationHolder.singleton().getIncomingSuiteQueue().add(testSuite)) {
                throw new IllegalStateException("failed to add test suite to queue.");
            }
            LOGGER.log("Test suite loaded.");
        } catch (Throwable e) {
            //TODO: temporary solution.
            throw new RuntimeException(e);
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
}
