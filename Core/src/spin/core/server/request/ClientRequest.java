package spin.core.server.request;

/**
 * A client request.
 */
public interface ClientRequest {

    /**
     * Returns the type of request that this is.
     *
     * @return the type of request this request is.
     */
    public RequestType getType();
}
