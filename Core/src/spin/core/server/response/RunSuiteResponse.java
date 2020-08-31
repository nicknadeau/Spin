package spin.core.server.response;

import com.google.gson.JsonObject;

public final class RunSuiteResponse implements ServerResponse {
    private final boolean success;
    private final int suiteId;
    private final String error;

    private RunSuiteResponse(boolean success, int suiteId, String error) {
        this.success = success;
        this.suiteId = suiteId;
        this.error = error;
    }

    public static RunSuiteResponse successful(int suiteId) {
        return new RunSuiteResponse(true, suiteId, null);
    }

    public static RunSuiteResponse failed(String error) {
        return new RunSuiteResponse(false, -1, error);
    }

    @Override
    public String toJsonString() {
        JsonObject response = new JsonObject();
        response.addProperty("is_success", this.success);

        if (this.success) {
            JsonObject responseValue = new JsonObject();
            responseValue.addProperty("suite_id", this.suiteId);
            response.add("response", responseValue);
        } else {
            response.addProperty("error", this.error);
        }

        return response.toString();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " { " + (this.success ? "successful, suite id: " + this.suiteId : "failed") + ", error: " + this.error + " }";
    }
}
