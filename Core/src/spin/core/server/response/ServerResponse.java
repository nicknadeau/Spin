package spin.core.server.response;

/**
 * A response from the server to be sent to the client.
 */
public interface ServerResponse {

    /**
     * Converts the response to a JSON-encoded string.
     *
     * @return the JSON resonse string.
     */
    public String toJsonString();
}
