package spin.core.server.session;

import spin.core.util.ObjectChecker;

import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * A class that holds contextual data or objects related to the client-server session in which some client request has
 * been received by the server.
 */
public final class RequestSessionContext {
    public final Selector selector;
    public final SocketChannel socketChannel;
    public final ClientSession clientSession;

    private RequestSessionContext(Selector selector, SocketChannel socketChannel, ClientSession clientSession) {
        this.selector = selector;
        this.socketChannel = socketChannel;
        this.clientSession = clientSession;
    }

    public static RequestSessionContext socketContext(Selector selector, SocketChannel socket, ClientSession session) {
        ObjectChecker.assertNonNull(selector, socket, session);
        return new RequestSessionContext(selector, socket, session);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " { socket context for client id: " + this.clientSession.id + " }";
    }
}
