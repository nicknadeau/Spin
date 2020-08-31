package spin.core.server;

import org.apache.camel.test.AvailablePortFinder;
import spin.core.exception.UnreachableException;
import spin.core.server.request.ClientRequest;
import spin.core.server.request.RequestType;
import spin.core.server.request.RunSuiteClientRequest;
import spin.core.server.session.*;
import spin.core.server.type.*;
import spin.core.singleuse.lifecycle.LifecycleListener;
import spin.core.singleuse.lifecycle.ShutdownMonitor;
import spin.core.singleuse.util.Logger;
import spin.core.util.ObjectChecker;
import spin.core.util.Stringify;

import java.io.IOException;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;

public final class Server implements Runnable {
    private static final Logger LOGGER = Logger.forClass(Server.class);
    private final CyclicBarrier barrier;
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final String host;
    private final int port;
    private final LifecycleListener lifecycleListener;
    private final ShutdownMonitor shutdownMonitor;
    private volatile boolean isAlive = true;

    private Server(CyclicBarrier barrier, String host, int port, ServerSocketChannel socketChannel, LifecycleListener lifecycleListener, ShutdownMonitor shutdownMonitor) throws IOException {
        this.barrier = barrier;
        this.selector = Selector.open();
        this.host = host;
        this.port = port;
        this.serverSocketChannel = socketChannel;
        this.lifecycleListener = lifecycleListener;
        this.shutdownMonitor = shutdownMonitor;
    }

    public int getPort() {
        return this.port;
    }

    @Override
    public void run() {
        try {
            LOGGER.log("Waiting for other threads to hit barrier.");
            this.barrier.await();
            LOGGER.log(Thread.currentThread().getName() + " thread started on host " + this.host + " and port " + this.port);
            this.serverSocketChannel.register(this.selector, SelectionKey.OP_ACCEPT);

            while (this.isAlive) {
                // Wait for a socket operation to become available & iterate over all available operations.
                if (this.selector.select() > 0) {
                    Set<SelectionKey> selectedKeys = this.selector.selectedKeys();
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
        this.selector.wakeup();
    }

    @Override
    public String toString() {
        return Stringify.threadToStringPrefix(this, !this.isAlive) + " host: " + this.host + ", port: " + this.port + " }";
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
        ClientSession newSession = new ClientSession();

        SocketChannel channel = this.serverSocketChannel.accept();
        channel.configureBlocking(false);
        channel.register(this.selector, SelectionKey.OP_READ, newSession);
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

        //TODO: refactor ClientSession --> in particular, 'isRequestRead' name is not helping.
        if (!clientSession.isRequestRead()) {
            SocketChannel channel = (SocketChannel) key.channel();
            if (clientSession.readRequest(channel)) {
                System.out.println("Client request from client #" + clientSession.id + ": " + clientSession.getRequest());

                Result<ClientRequest> parseResult = RequestParser.parseClientRequest(clientSession.getRequest());
                if (parseResult.isSuccess()) {
                    System.out.println("Request from client #" + clientSession.id + " successfully parsed.");
                    ClientRequest clientRequest = parseResult.getData();
                    if (clientRequest.getType() == RequestType.RUN_SUITE) {
                        RunSuiteClientRequest runSuiteRequest = (RunSuiteClientRequest) clientRequest;
                        RequestSessionContext context = RequestSessionContext.socketContext(this.selector, channel, clientSession);
                        runSuiteRequest.bindContext(context);
                        RequestHandler.handleRequest(runSuiteRequest);
                    } else {
                        throw new UnreachableException("unknown request type: " + clientRequest.getType());
                    }
                } else {
                    RunSuiteResponse response = RunSuiteResponse.failed(parseResult.getError());
                    clientSession.setResponse(response.toJsonString());
                    channel.register(this.selector, SelectionKey.OP_WRITE, clientSession);
                    this.selector.wakeup();
                }
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
        if (clientSession.writeResponse(channel)) {
            clientSession.setSessionComplete();
        }
    }

    /**
     * Checks the selected connection for whether or not the session is complete and if so closes the connection.
     *
     * In addition to closing the connection, this method will also notify the {@link LifecycleListener} to shutdown
     * the program since we are still in a single-use mode.
     */
    private void endConnectionIfComplete(SelectionKey key) throws IOException {
        ClientSession clientSession = (ClientSession) key.attachment();
        if (clientSession != null && clientSession.isSessionComplete()) {
            key.channel().close();
            System.out.println("Connection closed for client #" + clientSession.id);

            // Since this is still a single-use server and not a long-lived one, once we close the connection
            // we are done and should shut down.
            this.lifecycleListener.notifyDone();
        }
    }

    //<---------------------------------------------------------------------------------------------------------------->

    public static final class Builder {
        private CyclicBarrier barrier;
        private String host;
        private LifecycleListener listener;
        private ShutdownMonitor monitor;

        private Builder() {}

        public static Builder newBuilder() {
            return new Builder();
        }

        public Builder forHost(String host) {
            this.host = host;
            return this;
        }

        public Builder withBarrier(CyclicBarrier barrer) {
            this.barrier = barrer;
            return this;
        }

        public Builder withLifecycleListener(LifecycleListener listener) {
            this.listener = listener;
            return this;
        }

        public Builder withShutdownMonitor(ShutdownMonitor shutdownMonitor) {
            this.monitor = shutdownMonitor;
            return this;
        }

        public Server build() throws IOException {
            ObjectChecker.assertNonNull(this.barrier, this.host, this.listener, this.monitor);
            ServerSocketChannel socketChannel = ServerSocketChannel.open();
            socketChannel.configureBlocking(false);
            int port = AvailablePortFinder.getNextAvailable();
            return new Server(this.barrier, this.host, port, socketChannel, this.listener, this.monitor);
        }
    }
}
