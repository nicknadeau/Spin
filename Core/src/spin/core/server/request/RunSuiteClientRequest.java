package spin.core.server.request;

import spin.core.server.session.RequestSessionContext;
import spin.core.util.ObjectChecker;

/**
 * A client request to run a test suite.
 */
public final class RunSuiteClientRequest implements ClientRequest {
    private final String baseDirectory;
    private final String matcher;
    private final String[] dependencies;
    private RequestSessionContext sessionContext = null;

    private RunSuiteClientRequest(String baseDirectory, String matcher, String[] dependencies) {
        this.baseDirectory = baseDirectory;
        this.matcher = matcher;
        this.dependencies = dependencies;
    }

    public static RunSuiteClientRequest from(String baseDirectory, String matcher, String[] dependencies) {
        ObjectChecker.assertNonNull(baseDirectory, matcher, dependencies);
        return new RunSuiteClientRequest(baseDirectory, matcher, dependencies);
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
    public RequestType getType() {
        return RequestType.RUN_SUITE;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " { base dir: " + this.baseDirectory
                + ", matcher: " + this.matcher
                + ", num dependencies: " + this.dependencies.length
                + ", " + (this.sessionContext == null ? "no context bound" : "context is bound") + " }";
    }
}
