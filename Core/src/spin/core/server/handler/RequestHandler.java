package spin.core.server.handler;

import spin.core.runner.TestRunner;
import spin.core.server.request.ClientRequest;
import spin.core.server.request.RequestType;
import spin.core.server.request.RunSuiteClientRequest;
import spin.core.server.response.RunSuiteResponse;
import spin.core.server.session.RequestSessionContext;
import spin.core.type.Result;
import spin.core.util.Logger;
import spin.core.util.ObjectChecker;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.TimeUnit;

/**
 * A class that handles incoming client requests.
 */
public final class RequestHandler {
    private static final Logger LOGGER = Logger.forClass(RequestHandler.class);
    private final TestRunner testRunner;

    private RequestHandler(TestRunner testRunner) {
        ObjectChecker.assertNonNull(testRunner);
        this.testRunner = testRunner;
    }

    public static RequestHandler withRunner(TestRunner testRunner) {
        return new RequestHandler(testRunner);
    }

    /**
     * Handles the specified client request using the given session context.
     *
     * This method will execute whatever is necessary to fulfill the request and will also ensure the server responds
     * back to the client when its response is ready.
     *
     * @param clientRequest The request to handle.
     * @param sessionContext The session context.
     */
    public void handleRequest(ClientRequest clientRequest, RequestSessionContext sessionContext) throws InterruptedException, ClosedChannelException {
        ObjectChecker.assertNonNull(clientRequest);

        if (clientRequest.getType() == RequestType.RUN_SUITE) {
            LOGGER.log("Submitting " + RequestType.RUN_SUITE + " request");
            RunSuiteClientRequest runSuiteRequest = (RunSuiteClientRequest) clientRequest;
            runSuiteRequest.bindContext(sessionContext);

            Result<Integer> addResult = this.testRunner.addRequest(runSuiteRequest, 5, TimeUnit.MINUTES);
            if (!addResult.isSuccess()) {
                writeResponseToClient(RunSuiteResponse.failed(addResult.getError()), sessionContext);
            } else if (!runSuiteRequest.isBlocking()) {
                writeResponseToClient(RunSuiteResponse.successful(addResult.getData()), sessionContext);
            }

        } else {
            writeResponseToClient(RunSuiteResponse.failed("unknown request type: " + clientRequest.getType()), sessionContext);
        }
    }

    private static void writeResponseToClient(RunSuiteResponse response, RequestSessionContext sessionContext) throws ClosedChannelException {
        sessionContext.clientSession.putServerResponse(response.toJsonString() + "\n");
        sessionContext.clientSession.terminateSession();
        sessionContext.socketChannel.register(sessionContext.selector, SelectionKey.OP_WRITE, sessionContext.clientSession);
        sessionContext.selector.wakeup();
    }
}
