package spin.core.util;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

/**
 * A thread-safe blocking queue that can be closed.
 *
 * The purpose of this implementation is really to gain control over thread life-cycles more reliably so that we do not
 * have to use interrupts to get them out of blocking queues.
 */
public final class CloseableBlockingQueue<E> {
    private final Object monitor = new Object();
    private final int capacity;
    private final Queue<E> queue;
    private boolean isClosed = false;

    private CloseableBlockingQueue(int capacity, Queue<E> queue) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive but was: " + capacity);
        }
        if (queue == null) {
            throw new NullPointerException("queue must be non-null.");
        }
        this.capacity = capacity;
        this.queue = queue;
    }

    /**
     * Constructs a new closeable blocking queue with the specified capacity.
     *
     * @param capacity The maximum amount of elements allowed in the queue at once.
     * @return the new queue.
     */
    public static <E> CloseableBlockingQueue<E> withCapacity(int capacity) {
        return new CloseableBlockingQueue<>(capacity, new LinkedList<>());
    }

    /**
     * Attempts to add the element to the queue. If the queue is full then this method blocks until space it available
     * and it can be added or until the queue is closed or the timeout elapsed, whichever happens first.
     *
     * Returns true iff the element was successfully added to the queue.
     *
     * @param element The element to add.
     * @param timeout The timeout duration.
     * @param unit The timeout duration units.
     * @return whether or not the element was added.
     */
    public boolean add(E element, long timeout, TimeUnit unit) throws InterruptedException {
        if (element == null) {
            throw new NullPointerException("element must be non-null.");
        }
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must be non-negative but was: " + timeout);
        }
        if (unit == null) {
            throw new NullPointerException("unit must be non-null.");
        }

        long currentTime = System.currentTimeMillis();
        long deadline = currentTime + unit.toMillis(timeout);

        synchronized (this.monitor) {
            while ((currentTime < deadline) && (!this.isClosed) && (this.queue.size() == this.capacity)) {
                this.monitor.wait(deadline - currentTime);
                currentTime = System.currentTimeMillis();
            }

            if (this.queue.size() < this.capacity) {
                this.queue.add(element);
                this.monitor.notifyAll();
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Attempts to add the element to the queue. If the queue is full then this method blocks until space it available
     * and it can be added or until the queue is closed, whichever happens first.
     *
     * Returns true iff the element was successfully added to the queue.
     *
     * @param element The element to add.
     * @return whether or not the element was added.
     */
    public boolean add(E element) throws InterruptedException {
        if (element == null) {
            throw new NullPointerException("element must be non-null.");
        }

        synchronized (this.monitor) {
            while ((!this.isClosed) && (this.queue.size() == this.capacity)) {
                this.monitor.wait();
            }

            if (this.queue.size() < this.capacity) {
                this.queue.add(element);
                this.monitor.notifyAll();
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Attempts to poll the next element in the queue. If the queue is empty then this method blocks until a new element
     * is added or until the queue is closed or the timeout elapsed, whichever happens first.
     *
     * Returns the next element in the queue or null if the queue is empty and closed. Note that if the queue is closed
     * but still contains elements, this method will successfully poll another element. The queue can be drained after
     * being closed, but no new elements can be added again. Once this method returns null it will always return null.
     *
     * @param timeout The timeout duration.
     * @param unit The timeout duration units.
     * @return the next element or null if no elements and queue is closed.
     */
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must be non-negative but was: " + timeout);
        }
        if (unit == null) {
            throw new NullPointerException("unit must be non-null.");
        }

        long currentTime = System.currentTimeMillis();
        long deadline = currentTime + unit.toMillis(timeout);

        synchronized (this.monitor) {
            while ((currentTime < deadline) && (!this.isClosed) && (this.queue.isEmpty())) {
                this.monitor.wait(deadline - currentTime);
                currentTime = System.currentTimeMillis();
            }

            return (this.queue.isEmpty()) ? null : this.queue.poll();
        }
    }

    /**
     * Attempts to poll the next element in the queue without blocking. If the queue is empty then this method returns
     * null, otherwise this method polls the next element and returns it.
     *
     * @return the next element or null if the queue is empty.
     */
    public E tryPoll() {
        synchronized (this.monitor) {
            return (this.queue.isEmpty()) ? null : this.queue.poll();
        }
    }

    /**
     * Closes this queue. Once this queue is closed it cannot be reopened.
     */
    public void close() {
        synchronized (this.monitor) {
            this.isClosed = true;
            this.monitor.notifyAll();
        }
    }
}
