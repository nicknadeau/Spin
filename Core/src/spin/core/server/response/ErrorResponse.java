package spin.core.server.response;

import com.google.gson.JsonObject;
import spin.core.util.ObjectChecker;

public final class ErrorResponse implements ServerResponse {
    private final String error;

    private ErrorResponse(String error) {
        ObjectChecker.assertNonNull(error);
        this.error = error;
    }

    public static ErrorResponse newResponse(String error) {
        return new ErrorResponse(error);
    }

    @Override
    public String toJsonString() {
        JsonObject response = new JsonObject();
        response.addProperty("is_success", false);
        response.addProperty("error", this.error);
        return response.toString();
    }
}
