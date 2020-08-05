package spin.core.server.session;

import spin.core.lifecycle.CommunicationHolder;
import spin.core.server.type.RequestType;
import spin.core.server.type.ResponseEvent;
import spin.core.server.type.RunSuiteRequest;
import spin.core.singleuse.util.Logger;

public final class RequestHandler {
    private static final Logger LOGGER = Logger.forClass(RequestHandler.class);

    public static void handleRequest(RunSuiteRequest runSuiteRequest) throws InterruptedException {
        if (runSuiteRequest == null) {
            throw new NullPointerException("runSuiteRequest must be non-null.");
        }
        if (!runSuiteRequest.isContextBound()) {
            throw new IllegalArgumentException("cannot handle request: no session context bound to request.");
        }

        if (runSuiteRequest.getResponseEvent() == ResponseEvent.ALL_RESULTS_PUBLISHED) {
            LOGGER.log("Submitting " + RequestType.RUN_SUITE + " request with response event " + ResponseEvent.ALL_RESULTS_PUBLISHED);
            CommunicationHolder.singleton().getRunSuiteRequestSubmissionQueue().add(runSuiteRequest);
        } else {
            //TODO
            throw new UnsupportedOperationException("unimplemented: immediate is the only supported option right now.");
        }
    }
}
