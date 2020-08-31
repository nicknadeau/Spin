package spin.core.lifecycle;

import spin.core.server.request.RunSuiteClientRequest;
import spin.core.singleuse.util.CloseableBlockingQueue;

public final class CommunicationHolder {
    private static CommunicationHolder singleton = null;
    private final CloseableBlockingQueue<RunSuiteClientRequest> runSuiteRequestSubmissionQueue;

    private CommunicationHolder(CloseableBlockingQueue<RunSuiteClientRequest> runSuiteRequestQueue) {
        if (runSuiteRequestQueue == null) {
            throw new NullPointerException("runSuiteRequestQueue must be non-null.");
        }
        this.runSuiteRequestSubmissionQueue = runSuiteRequestQueue;
    }

    public static void initialize(CloseableBlockingQueue<RunSuiteClientRequest> runSuiteRequestQueue) {
        if (singleton != null) {
            throw new IllegalStateException("Cannot initialize: holder is already initialized.");
        }
        singleton = new CommunicationHolder(runSuiteRequestQueue);
    }

    public static CommunicationHolder singleton() {
        if (singleton == null) {
            throw new IllegalStateException("Cannot get singleton: holder is not initialized.");
        }
        return singleton;
    }

    /**
     * Returns the queue that is used to submit {@link RunSuiteClientRequest}s into the system.
     *
     * @return the run-suite request submission queue.
     */
    public CloseableBlockingQueue<RunSuiteClientRequest> getRunSuiteRequestSubmissionQueue() {
        return this.runSuiteRequestSubmissionQueue;
    }
}
