package spin.core.singleuse.util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * A print stream that delegates to an underlying {@link ThreadLocal} print stream, so that each thread has its own
 * print stream.
 *
 * The default value of the {@link ThreadLocal} is a shared print stream. This is so that we can ensure all threads
 * originally print to a valid stream, and that stream will be the regular {@link System#in} and {@link System#out}
 * streams respectively. This keeps things simple.
 *
 * When a thread wants to capture output privately on their own stream they can call into this method to set their own
 * private stream to write to. This is used when we execute a test, for example, on its own thread, so that we can capture
 * its output cleanly.
 *
 * This class also provides a method to restore the initial stream so that threads can return back to their initial
 * values to write to.
 */
public final class ThreadLocalPrintStream extends PrintStream {
    private final ThreadLocal<PrintStream> stream;
    private final PrintStream initialStream;

    private ThreadLocalPrintStream(PrintStream initialStream) {
        super(new ByteArrayOutputStream());

        if (initialStream == null) {
            throw new NullPointerException("Cannot create thread local print stream with null initial stream!");
        }

        this.stream = ThreadLocal.withInitial(() -> { return initialStream; });
        this.initialStream = initialStream;
    }

    /**
     * Constructs a new thread local print stream. Every thread will inherit the specified stream as their initial
     * print stream that they will output to. It is the responsibility of every thread to manually override this shared
     * stream using {@code setStream()}.
     */
    public static ThreadLocalPrintStream withInitialStream(PrintStream stream) {
        return new ThreadLocalPrintStream(stream);
    }

    /**
     * Sets the stream for this specific thread.
     *
     * @param stream The new stream for this thread.
     */
    public void setStream(PrintStream stream) {
        this.stream.set(stream);
    }

    /**
     * Restores this stream with the initial stream. After invoking this method the existing stream will be overwritten.
     */
    public void restoreInitialStream() {
        this.stream.set(this.initialStream);
    }

    @Override
    public void write(byte[] buf, int off, int len) {
        this.stream.get().write(buf, off, len);
    }

    @Override
    public void flush() {
        this.stream.get().flush();
    }

    @Override
    public void close() {
        this.stream.get().close();
    }
}
