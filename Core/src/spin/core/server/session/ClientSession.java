package spin.core.server.session;

import spin.core.type.CircularByteBuffer;
import spin.core.util.ObjectChecker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A class that holds session-related information for a server-client session. In particular, this class holds onto all
 * of the client requests and server responses to be written or read from the sockets. This object also carries the
 * notion of the session being terminated, which is triggered by the server, and is used to determine when a socket
 * connection should be closed.
 *
 * Each client session has a unique integer id associated with it. These ids are guaranteed to be unique within the
 * same JVM context unless all possible integer values are exhausted.
 */
public final class ClientSession {
    private static int ids = 0;
    private final CircularByteBuffer clientRequestBuffer;
    private final CircularByteBuffer serverResponseBuffer;
    private boolean isSessionTerminated = false;
    public final int id = ids++;

    private ClientSession(int requestBufferCapacity, int responseBufferCapacity) {
        this.clientRequestBuffer = CircularByteBuffer.withCapacity(requestBufferCapacity);
        this.serverResponseBuffer = CircularByteBuffer.withCapacity(responseBufferCapacity);
    }

    public static ClientSession withCapacities(int requestBufferCapacity, int responseBufferCapacity) {
        return new ClientSession(requestBufferCapacity, responseBufferCapacity);
    }

    /**
     * Writes however many bytes were able to be read from the specified socket into this session object so that they
     * can be gotten at a later time as a complete request.
     *
     * @param socketChannel The socket to read the bytes from.
     */
    public void writeRequestFromSocket(SocketChannel socketChannel) throws IOException {
        ObjectChecker.assertNonNull(socketChannel);

        //TODO: byte buffer position?
        byte[] bytes = new byte[this.clientRequestBuffer.availableSpace()];
        int numBytesRead = socketChannel.read(ByteBuffer.wrap(bytes));
        if (numBytesRead > 0) {
            this.clientRequestBuffer.writeBytes(Arrays.copyOf(bytes, numBytesRead));
        }
    }

    /**
     * Returns the next client request in this session object if one exists or {@code null} otherwise.
     *
     * @return the next client object or null if none.
     */
    public String getNextClientRequest() {
        byte[] bytes = this.clientRequestBuffer.readBytesUpToIfPresent((byte) '\n');
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Saves the specified server response to this session object so that it can be written to a socket at a later time.
     *
     * @param response The response.
     */
    public void putServerResponse(String response) {
        ObjectChecker.assertNonNull(response);

        this.serverResponseBuffer.writeBytes(response.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Attempts to write the server response to the specified socket. This method may fail to write the complete
     * response to the socket. If so, the underlying buffer holding the response will be non-empty and the session,
     * even if marked to be terminated by the server, will not return true when the {@code isSessionTerminated()}
     * method is invoked. Thus, this invariant can be used to determine when to stop invoking this method and when the
     * full response has been written.
     *
     * This holds true if multiple responses are present.
     *
     * @param channel The socket to write to.
     */
    public void writeResponseToSocket(SocketChannel channel) throws IOException {
        byte[] bytes = this.serverResponseBuffer.readBytesUpToIfPresent((byte) '\n');
        if (bytes == null) {
            throw new IllegalStateException("Cannot write response: buffer contains no response.");
        }

        //TODO: byte buffer position?
        int numBytesWritten = channel.write(ByteBuffer.wrap(bytes));
        if (numBytesWritten < bytes.length) {
            this.serverResponseBuffer.rollbackReadBy(bytes.length - numBytesWritten);
        }
    }

    /**
     * Signals that the session is over.
     */
    public void terminateSession() {
        this.isSessionTerminated = true;
    }

    /**
     * Returns {@code true} if and only if the session is over.
     *
     * @return whether or not the session is over.
     */
    public boolean isSessionTerminated() {
        return this.isSessionTerminated && this.serverResponseBuffer.isEmpty();
    }
}
