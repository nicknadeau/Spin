package spin.core.server.response;

import com.google.gson.JsonObject;

public final class RunSuiteResponse implements ServerResponse {
    private final int suiteId;

    private RunSuiteResponse(int suiteId) {
        this.suiteId = suiteId;
    }

    public static RunSuiteResponse newResponse(int suiteId) {
        return new RunSuiteResponse(suiteId);
    }

    @Override
    public String toJsonString() {
        JsonObject response = new JsonObject();
        response.addProperty("is_success", true);

        JsonObject responseValue = new JsonObject();
        responseValue.addProperty("suite_id", this.suiteId);
        response.add("response", responseValue);

        return response.toString();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " { suite id: " + this.suiteId + " }";
    }
}
