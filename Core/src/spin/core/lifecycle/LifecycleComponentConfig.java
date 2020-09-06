package spin.core.lifecycle;

public final class LifecycleComponentConfig {
    public final String databaseConfigPath;
    public final boolean doOutputToDatabase;
    public final int numExecutorThreads;
    public final int interComponentQueueCapacity;

    private LifecycleComponentConfig(String dbConfigPath, boolean dbWrite, int numExecutors, int queueCap) {
        if (dbConfigPath == null) {
            throw new NullPointerException("dbConfigPath must be non-null.");
        }
        if (numExecutors < 1) {
            throw new IllegalArgumentException("numExecutors must be strictly positive but is: " + numExecutors);
        }
        if (queueCap < 1) {
            throw new IllegalArgumentException("queueCap must be strictly positive but is: " + queueCap);
        }
        this.databaseConfigPath = dbConfigPath;
        this.doOutputToDatabase = dbWrite;
        this.numExecutorThreads = numExecutors;
        this.interComponentQueueCapacity = queueCap;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " { num executors: " + this.numExecutorThreads
                + ", internal capacity: " + this.interComponentQueueCapacity
                + ", db config: " + this.databaseConfigPath
                + ", " + (this.doOutputToDatabase ? "[write to db]" : "[no db write]") + " }";
    }

    public static class Builder {
        private String databaseConfigPath;
        private Boolean doOutputToDatabase;
        private Integer numExecutorThreads;
        private Integer interComponentQueueCapacity;

        public static Builder newBuilder() {
            return new Builder();
        }

        public Builder setPathOfDatabaseConfigFile(String path) {
            if (this.databaseConfigPath != null) {
                throw new IllegalStateException("database config path is already set.");
            }
            this.databaseConfigPath = path;
            return this;
        }

        public Builder setWhetherToOutputResultsToDatabase(boolean doOutput) {
            if (this.doOutputToDatabase != null) {
                throw new IllegalStateException("output to database decision is already set.");
            }
            this.doOutputToDatabase = doOutput;
            return this;
        }

        public Builder setNumberOfTestExecutors(int num) {
            if (this.numExecutorThreads != null) {
                throw new IllegalStateException("num test executors is already set.");
            }
            this.numExecutorThreads = num;
            return this;
        }

        public Builder setCapacityOfInterComponentQueues(int capacity) {
            if (this.interComponentQueueCapacity != null) {
                throw new IllegalStateException("inter-component queue capacity is already set.");
            }
            this.interComponentQueueCapacity = capacity;
            return this;
        }

        //TODO: how does a null Boolean get unboxed? Does it throw or default to false?

        public LifecycleComponentConfig build() {
            return new LifecycleComponentConfig(this.databaseConfigPath, this.doOutputToDatabase, this.numExecutorThreads, this.interComponentQueueCapacity);
        }
    }
}
