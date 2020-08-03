package spin.core.server.type;

import spin.core.server.session.RequestSessionContext;

public final class RunSuiteRequest implements Request {
    private final String baseDirectory;
    private final String matcher;
    private final String[] dependencies;
    private final ResponseEvent responseEvent;
    private RequestSessionContext sessionContext = null;

    private RunSuiteRequest(String baseDirectory, String matcher, String[] dependencies, ResponseEvent responseEvent) {
        this.baseDirectory = baseDirectory;
        this.matcher = matcher;
        this.dependencies = dependencies;
        this.responseEvent = responseEvent;
    }

    public static RunSuiteRequest from(String baseDirectory, String matcher, String[] dependencies, ResponseEvent responseEvent) {
        if (baseDirectory == null) {
            throw new NullPointerException("baseDirectory must be non-null.");
        }
        if (matcher == null) {
            throw new NullPointerException("matcher must be non-null.");
        }
        if (dependencies == null) {
            throw new NullPointerException("dependencies must be non-null.");
        }
        if (responseEvent == null) {
            throw new NullPointerException("responseEvent must be non-null.");
        }
        return new RunSuiteRequest(baseDirectory, matcher, dependencies, responseEvent);
    }

    public String getBaseDirectory() {
        return this.baseDirectory;
    }

    public String getMatcher() {
        return this.matcher;
    }

    public String[] getDependencies() {
        return this.dependencies;
    }

    public ResponseEvent getResponseEvent() {
        return this.responseEvent;
    }

    public RequestSessionContext getSessionContext() {
        if (this.sessionContext == null) {
            throw new IllegalStateException("Cannot get session context: no context has been bound.");
        }
        return this.sessionContext;
    }

    public void bindContext(RequestSessionContext sessionContext) {
        if (this.sessionContext != null) {
            throw new IllegalArgumentException("Cannot bind session context: a context is already bound to this request.");
        }
        if (sessionContext == null) {
            throw new NullPointerException("sessionContext must be non-null.");
        }
        this.sessionContext = sessionContext;
    }

    public boolean isContextBound() {
        return this.sessionContext != null;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " { base dir: " + this.baseDirectory
                + ", matcher: " + this.matcher
                + ", num dependencies: " + this.dependencies.length
                + ", respond when: " + this.responseEvent.asString
                + ", " + (this.sessionContext == null ? "no context bound" : "context is bound") + " }";
    }
}
