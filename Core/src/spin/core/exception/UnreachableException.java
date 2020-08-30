package spin.core.exception;

/**
 * Thrown to indicate that the system ended up in a code-path it expects should be unreachable.
 */
public final class UnreachableException extends RuntimeException {

    public UnreachableException(String message) {
        super(message);
    }
}
