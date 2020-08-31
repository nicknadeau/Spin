package spin.core.server;

import spin.core.util.ObjectChecker;

import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

final class ServerContext {
    final ServerSocketChannel socketChannel;
    final Selector selector;
    final String host;
    final int port;

    ServerContext(ServerSocketChannel socketChannel, Selector selector, String host, int port) {
        ObjectChecker.assertNonNull(socketChannel, selector, host);
        this.socketChannel = socketChannel;
        this.selector = selector;
        this.host = host;
        this.port = port;
    }
}
