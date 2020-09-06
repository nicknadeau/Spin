package spin.core.runner;

import spin.core.server.request.RunSuiteClientRequest;
import spin.core.type.Result;
import spin.core.util.ObjectChecker;

import java.util.concurrent.TimeUnit;

/**
 * A wrapper over {@link TestSuiteRunner} that only exposes the ability to add a request to the runner.
 *
 * This class is primarily to be passed to classes that are only interested in the ability to add tests and nothing
 * else.
 */
public final class TestRunner {
    private final TestSuiteRunner testSuiteRunner;

    private TestRunner(TestSuiteRunner testSuiteRunner) {
        ObjectChecker.assertNonNull(testSuiteRunner);
        this.testSuiteRunner = testSuiteRunner;
    }

    public static TestRunner wrap(TestSuiteRunner testSuiteRunner) {
        return new TestRunner(testSuiteRunner);
    }

    /**
     * @see TestSuiteRunner --> {@link TestSuiteRunner#addRequest(RunSuiteClientRequest, long, TimeUnit)}.
     */
    public Result<Integer> addRequest(RunSuiteClientRequest request, long timeout, TimeUnit unit) throws InterruptedException {
        return this.testSuiteRunner.addRequest(request, timeout, unit);
    }
}
