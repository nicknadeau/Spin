package spin.example.singleton;

import spin.example.Resource;

public final class ResourceSingleton {
    private static final Resource SINGLETON = new Resource();

    private ResourceSingleton() {}

    public static Resource singleton() {
        return SINGLETON;
    }
}
