package spin.core.server.response;

import com.google.gson.JsonObject;

public final class ShutdownResponse implements ServerResponse {

    private ShutdownResponse() {}

    public static ShutdownResponse newResponse() {
        return new ShutdownResponse();
    }

    @Override
    public String toJsonString() {
        JsonObject response = new JsonObject();
        response.addProperty("is_success", true);
        return response.toString();
    }
}
