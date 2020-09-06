package spin.core.server;

import org.apache.camel.test.AvailablePortFinder;
import spin.core.runner.TestRunner;
import spin.core.server.handler.RequestHandler;
import spin.core.server.request.*;
import spin.core.server.request.parse.ClientRequestParser;
import spin.core.server.response.RunSuiteResponse;
import spin.core.server.session.*;
import spin.core.lifecycle.NotifyOnlyMonitor;
import spin.core.util.Logger;
import spin.core.type.Result;
import spin.core.util.ObjectChecker;
import spin.core.util.Stringify;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;

public final class Server implements Runnable {
    private static final Logger LOGGER = Logger.forClass(Server.class);
    private static final int BUFFER_CAPACITIES = 16_384;
    private final CyclicBarrier barrier;
    private final ServerContext context;
    private final NotifyOnlyMonitor shutdownMonitor;
    private final ClientRequestParser clientRequestParser;
    private final RequestHandler requestHandler;
    private volatile boolean isAlive = true;

    private Server(CyclicBarrier barrier, ServerContext context, NotifyOnlyMonitor shutdownMonitor, ClientRequestParser clientRequestParser, TestRunner testRunner) {
        this.barrier = barrier;
        this.context = context;
        this.shutdownMonitor = shutdownMonitor;
        this.clientRequestParser = clientRequestParser;
        this.requestHandler = RequestHandler.withRunner(testRunner);
    }

    public int getPort() {
        return this.context.port;
    }

    @Override
    public void run() {
        try {
            LOGGER.log("Waiting for other threads to hit barrier.");
            this.barrier.await();
            LOGGER.log(Thread.currentThread().getName() + " thread started on host " + this.context.host + " and port " + this.context.port);
            this.context.socketChannel.register(this.context.selector, SelectionKey.OP_ACCEPT);

            while (this.isAlive) {
                // Wait for a socket operation to become available & iterate over all available operations.
                if (this.context.selector.select() > 0) {
                    Set<SelectionKey> selectedKeys = this.context.selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectedKeys.iterator();

                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        iterator.remove();

                        if (key.isAcceptable()) {
                            acceptNewConnection();
                        }
                        if (key.isReadable()) {
                            readFromConnection(key);
                        }
                        if (key.isWritable()) {
                            writeToConnection(key);
                        }

                        endConnectionIfComplete(key);
                    }
                }
            }
        } catch (Throwable t) {
            this.shutdownMonitor.panic(t);
        } finally {
            LOGGER.log("Exiting.");
            this.isAlive = false;
        }
    }

    public void shutdown() {
        this.isAlive = false;
        this.context.selector.wakeup();
    }

    @Override
    public String toString() {
        return Stringify.threadToStringPrefix(this, !this.isAlive) + " host: " + this.context.host + ", port: " + this.context.port + " }";
    }

    /**
     * Accepts the new incoming connection attempt, binding a new empty {@link ClientSession} object against it.
     *
     * We will register the selector to listen for READ operations going forward since the client always initiates the
     * exchange.
     *
     * ASSUMPTION: The socket has an incoming connection to accept.
     */
    private void acceptNewConnection() throws IOException {
        // Create a new client session object that will hold any session data we need to persist.
        ClientSession newSession = ClientSession.withCapacities(BUFFER_CAPACITIES, BUFFER_CAPACITIES);

        SocketChannel channel = this.context.socketChannel.accept();
        channel.configureBlocking(false);
        channel.register(this.context.selector, SelectionKey.OP_READ, newSession);
        System.out.println("Accepted connection to client #" + newSession.id);
    }

    /**
     * Attempts to read the incoming request from the selected connection.
     *
     * If the request is successfully parsed then it may be fulfilled asynchronously, in which case when it comes time
     * to produce a response to the client, the selector will be registered for a WRITE operation.
     *
     * Otherwise, if unsuccessfully parsed the selector will be registered for a WRITE immediately and the associated
     * {@link ClientSession} object will hold onto the error to be communicated back to the client when the WRITE
     * operation is available.
     *
     * ASSUMPTION: The provided key has data to read.
     *
     * @param key The selected key with incoming data to read.
     */
    private void readFromConnection(SelectionKey key) throws IOException, InterruptedException {
        ClientSession clientSession = (ClientSession) key.attachment();

        // We read the request from the socket into the session object and then attempt to extract it back out. Note
        // that we may have read an incomplete request and thus when we attempt to get it we get a null request back.
        // In this case, we have to wait for the next READ operation on the socket to attempt to read the remainder.
        SocketChannel channel = (SocketChannel) key.channel();
        clientSession.writeRequestFromSocket(channel);
        String request = clientSession.getNextClientRequest();

        if (request != null) {
            System.out.println("Client request from client #" + clientSession.id + ": " + request);
            Result<ClientRequest> parseResult = this.clientRequestParser.parseClientRequest(request);

            if (parseResult.isSuccess()) {
                System.out.println("Request from client #" + clientSession.id + " successfully parsed.");
                RequestSessionContext context = RequestSessionContext.socketContext(this.context.selector, channel, clientSession);
                this.requestHandler.handleRequest(parseResult.getData(), context);
            } else {
                // Failed to parse the request. We respond with an error to the client and terminate the session.
                clientSession.putServerResponse(RunSuiteResponse.failed(parseResult.getError()).toJsonString());
                clientSession.terminateSession();
                channel.register(this.context.selector, SelectionKey.OP_WRITE, clientSession);
                this.context.selector.wakeup();
            }
        }
    }

    /**
     * Attempts to write to the selected connection.
     *
     * The associated {@link ClientSession} holds any data that the program wishes to publish back to the client so this
     * method can remain fairly agnostic to the entire exchange. This method simply attempts to write whatever data is
     * ready to be written to the client.
     *
     * If the write succeeds (all bytes are written) then the session is marked as complete and will be terminated.
     * Otherwise, if the write did not completely fully then the {@link ClientSession} object takes care of maintaining
     * what remains to be written and the next time the connection is available to WRITE to we re-attempt to publish
     * the rest.
     */
    private void writeToConnection(SelectionKey key) throws IOException {
        ClientSession clientSession = (ClientSession) key.attachment();
        SocketChannel channel = (SocketChannel) key.channel();
        clientSession.writeResponseToSocket(channel);
    }

    /**
     * Checks the selected connection for whether or not the session is complete and if so closes the connection.
     *
     * In addition to closing the connection, this method will also notify the system to shutdown since we are still in
     * a single-use mode.
     */
    private void endConnectionIfComplete(SelectionKey key) throws IOException {
        ClientSession clientSession = (ClientSession) key.attachment();
        if (clientSession != null && clientSession.isSessionTerminated()) {
            // Since this is still a single-use server and not a long-lived one, once we close the connection
            // we are done and should shut down.
            key.channel().close();
            System.out.println("Connection closed for client #" + clientSession.id);
            this.shutdownMonitor.requestGracefulShutdown();
        }
    }

    //<---------------------------------------------------------------------------------------------------------------->

    public static final class Builder {
        private CyclicBarrier barrier;
        private String host;
        private NotifyOnlyMonitor monitor;
        private ClientRequestParser requestParser;
        private TestRunner testRunner;

        private Builder() {}

        public static Builder newBuilder() {
            return new Builder();
        }

        public Builder forHost(String host) {
            this.host = host;
            return this;
        }

        public Builder withBarrier(CyclicBarrier barrier) {
            this.barrier = barrier;
            return this;
        }

        public Builder withShutdownMonitor(NotifyOnlyMonitor shutdownMonitor) {
            this.monitor = shutdownMonitor;
            return this;
        }

        public Builder usingClientRequestParser(ClientRequestParser parser) {
            this.requestParser = parser;
            return this;
        }

        public Builder withTestRunner(TestRunner testRunner) {
            this.testRunner = testRunner;
            return this;
        }

        public Server build() throws IOException {
            ObjectChecker.assertNonNull(this.barrier, this.host, this.monitor, this.requestParser, this.testRunner);
            ServerSocketChannel socketChannel = ServerSocketChannel.open();
            socketChannel.configureBlocking(false);
            int port = AvailablePortFinder.getNextAvailable();
            socketChannel.bind(new InetSocketAddress(this.host, port));
            ServerContext context = new ServerContext(socketChannel, Selector.open(), this.host, port);
            return new Server(this.barrier, context, this.monitor, this.requestParser, this.testRunner);
        }
    }
}
