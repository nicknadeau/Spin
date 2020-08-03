package spin.core.server.session;

import spin.core.server.type.Request;
import spin.core.server.type.RequestType;
import spin.core.server.type.RunSuiteRequest;

public final class RequestHolder {
    private final Request request;
    private final RequestType requestType;

    private RequestHolder(RequestType requestType, Request request) {
        this.requestType = requestType;
        this.request = request;
    }

    public static RequestHolder forRunSuiteRequest(RunSuiteRequest request) {
        if (request == null) {
            throw new NullPointerException("request must be non-null.");
        }
        return new RequestHolder(RequestType.RUN_SUITE, request);
    }

    public RequestType getRequestType() {
        return this.requestType;
    }

    public RunSuiteRequest asRunSuiteRequest() {
        if (this.requestType != RequestType.RUN_SUITE) {
            throw new IllegalArgumentException("Cannot get as a " + RequestType.RUN_SUITE + " request when type is " + this.requestType);
        }
        return (RunSuiteRequest) this.request;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " { request type: " + this.requestType + ", request: " + this.request + " }";
    }
}
