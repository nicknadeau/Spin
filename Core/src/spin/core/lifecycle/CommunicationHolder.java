package spin.core.lifecycle;

import spin.core.singleuse.runner.TestSuite;
import spin.core.singleuse.util.CloseableBlockingQueue;

public final class CommunicationHolder {
    private static CommunicationHolder singleton = null;
    private final CloseableBlockingQueue<TestSuite> incomingSuiteQueue;

    private CommunicationHolder(CloseableBlockingQueue<TestSuite> incomingSuiteQueue) {
        if (incomingSuiteQueue == null) {
            throw new NullPointerException("incomingSuiteQueue must be non-null.");
        }
        this.incomingSuiteQueue = incomingSuiteQueue;
    }

    public static void initialize(CloseableBlockingQueue<TestSuite> incomingSuiteQueue) {
        if (singleton != null) {
            throw new IllegalStateException("Cannot initialize: holder is already initialized.");
        }
        singleton = new CommunicationHolder(incomingSuiteQueue);
    }

    public static CommunicationHolder singleton() {
        if (singleton == null) {
            throw new IllegalStateException("Cannot get singleton: holder is not initialized.");
        }
        return singleton;
    }

    public CloseableBlockingQueue<TestSuite> getIncomingSuiteQueue() {
        return this.incomingSuiteQueue;
    }
}
