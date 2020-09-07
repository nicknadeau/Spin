package spin.core.exception;

import spin.core.execution.type.ExecutionTask;

/**
 * Throw by a {@link ExecutionTask} to indicate something went wrong while executing the task.
 */
public final class ExecutionTaskException extends Exception {

    public ExecutionTaskException(String message) {
        super(message);
    }
}
