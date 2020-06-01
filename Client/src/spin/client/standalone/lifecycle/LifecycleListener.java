package spin.client.standalone.lifecycle;

/**
 * A listener that listens to events in a component's life-cycle and reacts accordingly.
 */
public interface LifecycleListener {

    /**
     * Notifies the listener that this component is complete whatever it is doing.
     */
    public void notifyDone();
}
