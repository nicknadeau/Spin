package spin.example;

public final class Resource {
    private int hits = 0;

    public synchronized void hit() {
        this.hits++;
    }

    public synchronized int getHits() {
        return this.hits;
    }
}
