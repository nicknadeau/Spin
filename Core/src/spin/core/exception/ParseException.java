package spin.core.exception;

/**
 * Thrown when parsing data and finding that it is not structured as expected.
 */
public final class ParseException extends Exception {

    public ParseException(String message) {
        super(message);
    }
}
