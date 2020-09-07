package spin.core.execution.util;

import spin.core.exception.ExecutionTaskException;
import spin.core.execution.type.ExecutionReport;
import spin.core.execution.type.ExecutionTask;
import spin.core.output.ExecutionStatus;
import spin.core.util.ObjectChecker;
import spin.core.util.ThreadLocalPrintStream;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * A stateless class that is able to execute {@link ExecutionTask}s and return {@link ExecutionReport}s detailing the
 * outcome of that execution.
 */
public final class TaskExecutor {

    /**
     * Executes the specified task and returns a report detailing the execution outcome.
     *
     * @param task The task to execute.
     * @return the report.
     */
    public static ExecutionReport executeTask(ExecutionTask task) {
        ObjectChecker.assertNonNull(task);
        ByteArrayOutputStream stdout = hijackStream(System.out);
        ByteArrayOutputStream stderr = hijackStream(System.err);

        ExecutionStatus executionStatus = ExecutionStatus.SUCCESS;
        long endTime;

        long startTime = System.nanoTime();
        try {
            task.execute();
        } catch (ExecutionTaskException e) {
            executionStatus = ExecutionStatus.FAILURE;
        } finally {
            endTime = System.nanoTime();
        }

        byte[] capturedStdout = closeAndRestoreHijackedStream(System.out, stdout);
        byte[] capturedStderr = closeAndRestoreHijackedStream(System.err, stderr);

        return ExecutionReport.Builder.newBuilder()
                .executionStatus(executionStatus)
                .executionDurationNanos(endTime - startTime)
                .executionStdout(capturedStdout)
                .executionStderr(capturedStderr)
                .build();
    }

    /**
     * Hijacks the specified stream by replacing its underlying {@link PrintStream} with a stream backed by a
     * {@link ByteArrayOutputStream}, which is then returned to the caller.
     *
     * ASSUMPTION: the specified stream must be an instance of {@link ThreadLocalPrintStream} so we can successfully
     * hijack the stream in a thread-safe manner.
     *
     * @param stream The stream to hijack.
     * @return The underlying hijacker stream.
     */
    private static ByteArrayOutputStream hijackStream(PrintStream stream) {
        if (!(stream instanceof ThreadLocalPrintStream)) {
            throw new IllegalStateException("cannot hijack stream: not an instance of " + ThreadLocalPrintStream.class.getSimpleName());
        }

        ByteArrayOutputStream hijackerStream = new ByteArrayOutputStream();
        ((ThreadLocalPrintStream) stream).setStream(new PrintStream(new BufferedOutputStream(hijackerStream)));
        return hijackerStream;
    }

    /**
     * Flushes and closes the specified hijacked stream and then grabs its contents as an uninterpreted byte array and
     * returns it after also restoring the hijacked stream back to its initial state.
     *
     * ASSUMPTION: see {@link TaskExecutor#hijackStream(PrintStream)} regarding the {@link ThreadLocalPrintStream}
     * assumption, this method makes the same one.
     *
     * @param hijackedStream The hijacked stream.
     * @param hijacker The underlying hijacker stream.
     * @return the contents of the stream.
     */
    private static byte[] closeAndRestoreHijackedStream(PrintStream hijackedStream, ByteArrayOutputStream hijacker) {
        ThreadLocalPrintStream hijackedThreadLocal = (ThreadLocalPrintStream) hijackedStream;
        hijackedThreadLocal.flush();
        byte[] contents = hijacker.toByteArray();
        hijackedThreadLocal.close();
        hijackedThreadLocal.restoreInitialStream();
        return contents;
    }
}
