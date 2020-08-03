package spin.core.server.type;

public enum ResponseEvent {
    ALL_RESULTS_PUBLISHED("all_results_published")
    ;

    public final String asString;
    ResponseEvent(String string) {
        this.asString = string;
    }

    public static ResponseEvent from(String event) {
        for (ResponseEvent responseEvent : ResponseEvent.values()) {
            if (responseEvent.asString.equalsIgnoreCase(event)) {
                return responseEvent;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " { " + this.asString + " }";
    }
}
