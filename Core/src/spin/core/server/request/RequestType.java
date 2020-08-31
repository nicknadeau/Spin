package spin.core.server.request;

public enum RequestType {
    RUN_SUITE("run_suite")
    ;

    public final String asString;
    RequestType(String string) {
        this.asString = string;
    }

    public static RequestType fromString(String request) {
        for (RequestType requestType : RequestType.values()) {
            if (requestType.asString.equalsIgnoreCase(request)) {
                return requestType;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " { " + this.asString + " }";
    }
}
