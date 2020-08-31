package spin.core.server.request.parse;

import spin.core.server.request.ClientRequest;
import spin.core.type.Result;

/**
 * A class that parses incoming client requests.
 *
 * The expected structure of those requests is implementation-specific.
 */
public interface ClientRequestParser {

    /**
     * Returns the result of the attempt to parse the client request.
     *
     * @param request The request to parse.
     * @return the result of the parse attempt.
     */
    public Result<ClientRequest> parseClientRequest(String request);
}
