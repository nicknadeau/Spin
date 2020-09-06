package spin.core.server.handler;

import spin.core.exception.UnreachableException;
import spin.core.lifecycle.CommunicationHolder;
import spin.core.server.request.ClientRequest;
import spin.core.server.request.RequestType;
import spin.core.server.request.RunSuiteClientRequest;
import spin.core.server.session.RequestSessionContext;
import spin.core.util.Logger;
import spin.core.util.ObjectChecker;

/**
 * A class that handles incoming client requests.
 */
public final class RequestHandler {
    private static final Logger LOGGER = Logger.forClass(RequestHandler.class);

    /**
     * Handles the specified client request using the given session context.
     *
     * This method will execute whatever is necessary to fulfill the request and will also ensure the server responds
     * back to the client when its response is ready.
     *
     * @param clientRequest The request to handle.
     * @param sessionContext The session context.
     */
    public static void handleRequest(ClientRequest clientRequest, RequestSessionContext sessionContext) throws InterruptedException {
        ObjectChecker.assertNonNull(clientRequest);

        if (clientRequest.getType() == RequestType.RUN_SUITE) {
            LOGGER.log("Submitting " + RequestType.RUN_SUITE + " request");
            RunSuiteClientRequest runSuiteRequest = (RunSuiteClientRequest) clientRequest;
            runSuiteRequest.bindContext(sessionContext);

            //TODO: would prefer a more elegant solution to getting the request into the system.
            CommunicationHolder.singleton().getRunSuiteRequestSubmissionQueue().add(runSuiteRequest);
        } else {
            throw new UnreachableException("unknown request type: " + clientRequest.getType());
        }
    }
}
