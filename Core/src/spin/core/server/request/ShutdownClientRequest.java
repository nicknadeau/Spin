package spin.core.server.request;

public final class ShutdownClientRequest implements ClientRequest {

    @Override
    public RequestType getType() {
        return RequestType.SHUTDOWN;
    }
}
