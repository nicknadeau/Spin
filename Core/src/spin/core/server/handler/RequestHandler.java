package spin.core.server.handler;

import spin.core.lifecycle.ShutdownOnlyMonitor;
import spin.core.runner.TestRunner;
import spin.core.server.request.ClientRequest;
import spin.core.server.request.RequestType;
import spin.core.server.request.RunSuiteClientRequest;
import spin.core.server.response.ErrorResponse;
import spin.core.server.response.RunSuiteResponse;
import spin.core.server.response.ServerResponse;
import spin.core.server.response.ShutdownResponse;
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
    private final ShutdownOnlyMonitor shutdownMonitor;

    private RequestHandler(TestRunner testRunner, ShutdownOnlyMonitor shutdownMonitor) {
        ObjectChecker.assertNonNull(testRunner, shutdownMonitor);
        this.testRunner = testRunner;
        this.shutdownMonitor = shutdownMonitor;
    }

    public static RequestHandler newHandler(TestRunner testRunner, ShutdownOnlyMonitor shutdownMonitor) {
        return new RequestHandler(testRunner, shutdownMonitor);
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
                writeResponseToClient(ErrorResponse.newResponse(addResult.getError()), sessionContext);
            } else if (!runSuiteRequest.isBlocking()) {
                writeResponseToClient(RunSuiteResponse.newResponse(addResult.getData()), sessionContext);
            }

        } else if (clientRequest.getType() == RequestType.SHUTDOWN) {
            LOGGER.log("Received shutdown request from client");
            writeResponseToClient(ShutdownResponse.newResponse(), sessionContext);
            this.shutdownMonitor.requestGracefulShutdown();
        } else {
            writeResponseToClient(ErrorResponse.newResponse("unknown request type: " + clientRequest.getType()), sessionContext);
        }
    }

    private static void writeResponseToClient(ServerResponse response, RequestSessionContext sessionContext) throws ClosedChannelException {
        sessionContext.clientSession.putServerResponse(response.toJsonString() + "\n");
        sessionContext.clientSession.terminateSession();
        sessionContext.socketChannel.register(sessionContext.selector, SelectionKey.OP_WRITE, sessionContext.clientSession);
        sessionContext.selector.wakeup();
    }
}
