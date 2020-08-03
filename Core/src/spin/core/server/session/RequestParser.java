package spin.core.server.session;

import com.google.gson.*;
import spin.core.server.type.RequestType;
import spin.core.server.type.ResponseEvent;
import spin.core.server.type.Result;
import spin.core.server.type.RunSuiteRequest;

public final class RequestParser {
    private static final String REQUEST_TYPE_KEY = "request_type";
    private static final String BODY_KEY = "body";
    private static final String BASE_DIR_KEY = "base_dir";
    private static final String MATCHER_KEY = "matcher";
    private static final String DEPENDENCIES_KEY = "dependencies";
    private static final String RESPOND_WHEN_KEY = "respond_when";
    private static final String DEFAULT_MATCHER = ".*\\.class";

    public static Result<RequestHolder> parseRequest(String request) {
        if (request == null) {
            throw new NullPointerException("request must be non-null.");
        }

        try {
            JsonElement parsedRequest = JsonParser.parseString(request);
            if (!parsedRequest.isJsonObject()) {
                return Result.error(createParseFailureMessage("request is not a JSON object"));
            }

            JsonObject jsonRequest = parsedRequest.getAsJsonObject();
            if (!jsonRequest.has(REQUEST_TYPE_KEY)) {
                return Result.error(createParseFailureMessage("missing " + REQUEST_TYPE_KEY));
            }

            JsonElement rawRequestType = jsonRequest.get(REQUEST_TYPE_KEY);
            if (!rawRequestType.isJsonPrimitive()) {
                return Result.error(createParseFailureMessage("expected " + REQUEST_TYPE_KEY + " to be a String"));
            }

            RequestType requestType = RequestType.fromString(rawRequestType.getAsString());
            if (requestType == null) {
                return Result.error(createParseFailureMessage("unknown " + REQUEST_TYPE_KEY + ": " + rawRequestType));
            }

            if (requestType == RequestType.RUN_SUITE) {
                if (!jsonRequest.has(BODY_KEY)) {
                    return Result.error(createParseFailureMessage("missing " + BODY_KEY));
                }

                JsonElement rawBody = jsonRequest.get(BODY_KEY);
                if (!rawBody.isJsonObject()) {
                    return Result.error(createParseFailureMessage("expected body to be a JSON object"));
                }

                return parseRunSuiteRequest(rawBody.getAsJsonObject());
            } else {
                return Result.error(createParseFailureMessage("unsupported " + REQUEST_TYPE_KEY + ": " + requestType));
            }
        } catch (JsonSyntaxException e) {
            return Result.error(createParseFailureMessage("malformed JSON: " + e.getMessage()));
        } catch (Exception e) {
            return Result.error(createParseFailureMessage("unexpected error: " + e.getMessage()));
        }
    }

    private static Result<RequestHolder> parseRunSuiteRequest(JsonObject requestBody) {
        if (!requestBody.has(BASE_DIR_KEY)) {
            return Result.error(createParseFailureMessage("missing " + BASE_DIR_KEY));
        }

        JsonElement rawBaseDir = requestBody.get(BASE_DIR_KEY);
        if (!rawBaseDir.isJsonPrimitive()) {
            return Result.error(createParseFailureMessage("expected " + BASE_DIR_KEY + " to be a String"));
        }
        String baseDir = rawBaseDir.getAsString();

        String matcher = DEFAULT_MATCHER;
        if (requestBody.has(MATCHER_KEY)) {
            JsonElement rawMatcher = requestBody.get(MATCHER_KEY);
            if (!rawMatcher.isJsonPrimitive()) {
                return Result.error(createParseFailureMessage("expected " + MATCHER_KEY + " to be a String"));
            }
            matcher = rawMatcher.getAsString();
        }

        String[] dependencies = new String[]{ baseDir };
        if (requestBody.has(DEPENDENCIES_KEY)) {
            JsonElement rawDependencies = requestBody.get(DEPENDENCIES_KEY);
            if (!rawDependencies.isJsonArray()) {
                return Result.error(createParseFailureMessage("expected " + DEPENDENCIES_KEY + " to be a JSON array"));
            }

            JsonArray dependenciesAsJson = rawDependencies.getAsJsonArray();
            dependencies = new String[dependenciesAsJson.size() + 1];

            int index = 0;
            for (JsonElement rawDependency : dependenciesAsJson) {
                if (!rawDependency.isJsonPrimitive()) {
                    return Result.error(createParseFailureMessage("expected dependency to be a String"));
                }

                dependencies[index] = rawDependency.getAsString();
                index++;
            }

            dependencies[index] = baseDir;
        }

        ResponseEvent responseEvent = ResponseEvent.ALL_RESULTS_PUBLISHED;
        if (requestBody.has(RESPOND_WHEN_KEY)) {
            JsonElement rawRespondWhen = requestBody.get(RESPOND_WHEN_KEY);
            if (!rawRespondWhen.isJsonPrimitive()) {
                return Result.error(createParseFailureMessage("expected " + RESPOND_WHEN_KEY + " to be a String"));
            }

            ResponseEvent parsedEvent = ResponseEvent.from(rawRespondWhen.getAsString());
            if (parsedEvent == null) {
                return Result.error(createParseFailureMessage("unsupported " + RESPOND_WHEN_KEY + ": " + rawRespondWhen.getAsString()));
            }

            responseEvent = parsedEvent;
        }

        RequestHolder holder = RequestHolder.forRunSuiteRequest(RunSuiteRequest.from(baseDir, matcher, dependencies, responseEvent));
        return Result.successful(holder);
    }

    private static String createParseFailureMessage(String cause) {
        return "Failed to parse request: " + cause;
    }
}
