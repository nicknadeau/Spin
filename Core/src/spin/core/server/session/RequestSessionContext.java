package spin.core.server.session;

import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

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
        if (selector == null) {
            throw new NullPointerException("selector must be non-null.");
        }
        if (socket == null) {
            throw new NullPointerException("socket must be non-null.");
        }
        if (session == null) {
            throw new NullPointerException("session must be non-null.");
        }

        return new RequestSessionContext(selector, socket, session);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " { socket context for client id: " + this.clientSession.id + " }";
    }
}
