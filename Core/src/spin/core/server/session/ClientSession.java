package spin.core.server.session;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class ClientSession {
    private static final int REQUEST_BUFFER_SIZE = 5;
    private static final int MAX_REQUEST_SIZE = 65_536;
    private static int ids = 0;
    public final int id = ids++;
    private ByteBuffer requestBuffer = ByteBuffer.allocate(REQUEST_BUFFER_SIZE);
    private StringBuilder requestBuilder = new StringBuilder();
    private int numRequestBytesReadTotal = 0;
    private String request = null;
    private ByteBuffer response = null;
    private int numResponseBytesToWrite = -1;
    private boolean isComplete = false;

    public boolean readRequest(SocketChannel socketChannel) throws IOException {
        if (socketChannel == null) {
            throw new NullPointerException("socketChannel must be non-null.");
        }
        if (this.request != null) {
            throw new IllegalStateException("Cannot read: request has already been fully read.");
        }

        int numBytesRead = socketChannel.read(this.requestBuffer);
        if (numBytesRead > 0) {

            int endingIndex = numBytesRead;
            boolean foundNewline = false;
            for (int i = 0; i < numBytesRead; i++) {
                if (this.requestBuffer.array()[i] == '\n') {
                    endingIndex = i;
                    foundNewline = true;
                    break;
                }
            }

            this.numRequestBytesReadTotal += endingIndex;
            if (this.numRequestBytesReadTotal > MAX_REQUEST_SIZE) {
                throw new IllegalArgumentException("Cannot read: request has exceeded capacity.");
            }

            this.requestBuilder.append(new String(Arrays.copyOf(this.requestBuffer.array(), endingIndex), StandardCharsets.UTF_8));
            if (foundNewline) {
                this.request = this.requestBuilder.toString();
                this.requestBuffer = null;
                this.requestBuilder = null;
                return true;
            } else {
                this.requestBuffer.compact();
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean isRequestRead() {
        return this.request != null;
    }

    public String getRequest() {
        return this.request;
    }

    public void setResponse(String response) {
        if (this.response != null) {
            throw new IllegalStateException("Cannot set response: response has already been set.");
        }

        this.response = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
        this.numResponseBytesToWrite = this.response.array().length;
    }

    public boolean writeResponse(SocketChannel socketChannel) throws IOException {
        if (this.response == null) {
            throw new IllegalStateException("Cannot write response: no response has been set.");
        }

        int numBytesWritten = socketChannel.write(this.response);
        this.numResponseBytesToWrite -= numBytesWritten;

        if (this.numResponseBytesToWrite > 0) {
            this.response.compact();
            return false;
        } else {
            this.response = null;
            return true;
        }
    }

    public void setSessionComplete() {
        this.isComplete = true;
    }

    public boolean isSessionComplete() {
        return this.isComplete;
    }
}
