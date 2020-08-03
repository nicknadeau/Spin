package spin.core.server;

import spin.core.server.session.*;
import spin.core.server.type.Result;
import spin.core.server.type.RunSuiteRequest;
import spin.core.server.type.RunSuiteResponse;
import spin.core.singleuse.lifecycle.LifecycleListener;
import spin.core.singleuse.lifecycle.ShutdownMonitor;
import spin.core.singleuse.util.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;

public final class Server implements Runnable {
    private static final Logger LOGGER = Logger.forClass(Server.class);
    private static final int PORT_LOWER_BOUNDARY = 49152;
    private static final int PORT_UPPER_BOUNDARY = 50151;
    private static final int MAX_BIND_ATTEMPTS = 50;
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

    public static Server forHostAndRandomizedPort(CyclicBarrier barrier, String host, LifecycleListener lifecycleListener, ShutdownMonitor monitor) throws IOException {
        if (barrier == null) {
            throw new NullPointerException("barrier must be non-null.");
        }
        if (host == null) {
            throw new NullPointerException("host must be non-null.");
        }
        if (lifecycleListener == null) {
            throw new NullPointerException("lifecycleListener must be non-null.");
        }
        if (monitor == null) {
            throw new NullPointerException("monitor must be non-null.");
        }

        ServerSocketChannel socketChannel = ServerSocketChannel.open();
        socketChannel.configureBlocking(false);
        int port = findPortToBindTo(socketChannel, host);
        return new Server(barrier, host, port, socketChannel, lifecycleListener, monitor);
    }

    private static int findPortToBindTo(ServerSocketChannel socketChannel, String host) throws IOException {
        Random random = new Random();
        int numBindAttempts = 0;
        int boundPort = -1;

        while (boundPort == -1 && numBindAttempts < MAX_BIND_ATTEMPTS) {
            int port = random.nextInt(PORT_UPPER_BOUNDARY - PORT_LOWER_BOUNDARY + 1) + PORT_LOWER_BOUNDARY;

            try {
                LOGGER.log("Attempting to connect to port " + port);
                socketChannel.bind(new InetSocketAddress(host, port));
                boundPort = port;
            } catch (AlreadyBoundException | SocketException e) {
                // Do nothing, we are trying to find a port we can bind to and have a max num of attempts.
            }

            numBindAttempts++;
        }

        if (numBindAttempts == MAX_BIND_ATTEMPTS) {
            throw new IOException("Failed to find a port to bind to. Reached max number of attempts: " + MAX_BIND_ATTEMPTS);
        }

        return boundPort;
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
                if (this.selector.select() > 0) {
                    Set<SelectionKey> selectedKeys = this.selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectedKeys.iterator();

                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        iterator.remove();

                        ClientSession clientSession = (ClientSession) key.attachment();

                        if (key.isAcceptable()) {
                            SocketChannel sc = this.serverSocketChannel.accept();
                            sc.configureBlocking(false);

                            ClientSession newSession = new ClientSession();
                            sc.register(this.selector, SelectionKey.OP_READ, newSession);
                            System.out.println("Accepted connection to client #" + newSession.id);
                        }

                        if (key.isReadable()) {
                            if (!clientSession.isRequestRead()) {
                                SocketChannel sc = (SocketChannel) key.channel();
                                if (clientSession.readRequest(sc)) {
                                    System.out.println("Client request from client #" + clientSession.id + ": " + clientSession.getRequest());

                                    Result<RequestHolder> parseResult = RequestParser.parseRequest(clientSession.getRequest());
                                    if (parseResult.isSuccess()) {
                                        System.out.println("Request from client #" + clientSession.id + " successfully parsed.");
                                        RequestHolder requestHolder = parseResult.getData();
                                        switch (requestHolder.getRequestType()) {
                                            case RUN_SUITE:
                                                RunSuiteRequest runSuiteRequest = requestHolder.asRunSuiteRequest();
                                                RequestSessionContext context = RequestSessionContext.socketContext(this.selector, sc, clientSession);
                                                runSuiteRequest.bindContext(context);
                                                RequestHandler.handleRequest(runSuiteRequest);
                                                break;
                                            default:
                                                RunSuiteResponse response = RunSuiteResponse.failed("Unsupported request type: " + requestHolder.getRequestType());
                                                clientSession.setResponse(response.toJsonString());
                                                sc.register(this.selector, SelectionKey.OP_WRITE, clientSession);
                                                this.selector.wakeup();
                                        }
                                    } else {
                                        RunSuiteResponse response = RunSuiteResponse.failed(parseResult.getError());
                                        clientSession.setResponse(response.toJsonString());
                                        sc.register(this.selector, SelectionKey.OP_WRITE, clientSession);
                                        this.selector.wakeup();
                                    }
                                }
                            }
                        }

                        if (key.isWritable()) {
                            SocketChannel sc = (SocketChannel) key.channel();

                            if (clientSession.writeResponse(sc)) {
                                clientSession.setSessionComplete();
                            }
                        }

                        if (clientSession != null && clientSession.isSessionComplete()) {
                            key.channel().close();
                            System.out.println("Connection closed for client #" + clientSession.id);

                            // Since this is still a single-use server and not a long-lived one, once we close the connection
                            // we are done and should shut down.
                            this.lifecycleListener.notifyDone();
                        }
                    }
                }
            }
        } catch (Throwable e) {
            this.shutdownMonitor.panic(e);
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
        return this.getClass().getSimpleName() + " { " + (this.isAlive ? "[running]" : "[shutdown]")
                + " host: " + this.host
                + ", port: " + this.port + " }";
    }
}
