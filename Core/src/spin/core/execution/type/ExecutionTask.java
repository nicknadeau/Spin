package spin.core.execution.type;

import spin.core.exception.ExecutionTaskException;

/**
 * A task to execute.
 */
@FunctionalInterface
public interface ExecutionTask {

    /**
     * Executes the task.
     *
     * @throws ExecutionTaskException If something goes wrong executing the task.
     */
    public void execute() throws ExecutionTaskException;
}
