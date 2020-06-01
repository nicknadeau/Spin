package spin.client.standalone.lifecycle;

/**
 * A listener that listens to components in the event shutting down so that it can react accordingly.
 */
public interface ShutdownListener {

    /**
     * Notifies the listener that some component has panicked and encountered an unexpected error it cannot recover from.
     */
    public void notifyPanic();
}
