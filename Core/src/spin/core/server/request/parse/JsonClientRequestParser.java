package spin.core.server.request.parse;

import com.google.gson.*;
import spin.core.exception.ParseException;
import spin.core.server.request.ClientRequest;
import spin.core.server.request.RequestType;
import spin.core.server.request.RunSuiteClientRequest;
import spin.core.server.request.ShutdownClientRequest;
import spin.core.type.Result;
import spin.core.util.ObjectChecker;

/**
 * A class that is used to parse incoming client requests under the assumption that those requests are JSON requests.
 */
public final class JsonClientRequestParser implements ClientRequestParser {
    private static final String REQUEST_TYPE_KEY = "request_type";
    private static final String BODY_KEY = "body";
    private static final String BASE_DIR_KEY = "base_dir";
    private static final String MATCHER_KEY = "matcher";
    private static final String DEPENDENCIES_KEY = "dependencies";
    private static final String BLOCKING_KEY = "is_blocking";
    private static final String DEFAULT_MATCHER = ".*\\.class";

    @Override
    public Result<ClientRequest> parseClientRequest(String request) {
        ObjectChecker.assertNonNull(request);

        try {
            JsonElement parsedRequest = JsonParser.parseString(request);
            if (!parsedRequest.isJsonObject()) {
                return Result.error(createParseFailureMessage("request is not a JSON object"));
            }

            JsonObject jsonRequest = parsedRequest.getAsJsonObject();

            RequestType requestType = RequestType.fromString(parseAsString(jsonRequest, REQUEST_TYPE_KEY));
            if (requestType == null) {
                return Result.error(createParseFailureMessage("unknown " + REQUEST_TYPE_KEY));
            }

            if (requestType == RequestType.RUN_SUITE) {
                return parseRunSuiteRequest(parseAsJsonObject(jsonRequest, BODY_KEY));
            } else if (requestType == RequestType.SHUTDOWN) {
                return Result.successful(new ShutdownClientRequest());
            } else {
                return Result.error(createParseFailureMessage("unsupported " + REQUEST_TYPE_KEY + ": " + requestType));
            }
        } catch (JsonSyntaxException e) {
            return Result.error(createParseFailureMessage("malformed JSON: " + e.getMessage()));
        } catch (ParseException e) {
            return Result.error(createParseFailureMessage(e.getMessage()));
        } catch (Exception e) {
            return Result.error(createParseFailureMessage("unexpected error: " + e.getMessage()));
        }
    }

    private Result<ClientRequest> parseRunSuiteRequest(JsonObject requestBody) throws ParseException {
        String baseDir = parseAsString(requestBody, BASE_DIR_KEY);

        String matcher = DEFAULT_MATCHER;
        if (requestBody.has(MATCHER_KEY)) {
            matcher = parseAsString(requestBody, MATCHER_KEY);
        }

        String[] dependencies = new String[]{ baseDir };
        if (requestBody.has(DEPENDENCIES_KEY)) {
            JsonArray dependenciesAsJson = parseAsJsonArray(requestBody, DEPENDENCIES_KEY);

            dependencies = new String[dependenciesAsJson.size() + 1];
            for (int i = 0; i < dependenciesAsJson.size(); i++) {
                if (!dependenciesAsJson.get(i).isJsonPrimitive()) {
                    return Result.error(createParseFailureMessage("expected dependency to be a String"));
                }
                dependencies[i] = dependenciesAsJson.get(i).getAsString();
            }
            dependencies[dependenciesAsJson.size()] = baseDir;
        }

        boolean isBlocking = true;
        if (requestBody.has(BLOCKING_KEY)) {
            isBlocking = parseAsBoolean(requestBody, BLOCKING_KEY);
        }

        return isBlocking
                ? Result.successful(RunSuiteClientRequest.blocking(baseDir, matcher, dependencies))
                : Result.successful(RunSuiteClientRequest.nonBlocking(baseDir, matcher, dependencies));
    }

    private static String createParseFailureMessage(String cause) {
        return "Failed to parse request: " + cause;
    }

    private static boolean parseAsBoolean(JsonObject json, String attribute) throws ParseException {
        JsonElement element = getElementFromAttribute(json, attribute);
        if (!element.isJsonPrimitive()) {
            throw new ParseException("expected " + attribute + " to be a Boolean");
        }
        return element.getAsBoolean();
    }

    private static String parseAsString(JsonObject json, String attribute) throws ParseException {
        JsonElement element = getElementFromAttribute(json, attribute);
        if (!element.isJsonPrimitive()) {
            throw new ParseException("expected " + attribute + " to be a String");
        }
        return element.getAsString();
    }

    private static JsonObject parseAsJsonObject(JsonObject json, String attribute) throws ParseException {
        JsonElement element = getElementFromAttribute(json, attribute);
        if (!element.isJsonObject()) {
            throw new ParseException("expected " + attribute + " to be a JSON Object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray parseAsJsonArray(JsonObject json, String attribute) throws ParseException {
        JsonElement element = getElementFromAttribute(json, attribute);
        if (!element.isJsonArray()) {
            throw new ParseException("expected " + attribute + " to be a JSON Array");
        }
        return element.getAsJsonArray();
    }

    private static JsonElement getElementFromAttribute(JsonObject json, String attribute) throws ParseException {
        if (!json.has(attribute)) {
            throw new ParseException("missing " + attribute);
        }
        return json.get(attribute);
    }
}
