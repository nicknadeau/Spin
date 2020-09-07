package spin.core.output;

import spin.core.util.ObjectChecker;

import java.util.HashMap;
import java.util.Map;

public final class AdditionalResultDetails {
    private final Map<ExecutionStatus, Integer> statusCounts = new HashMap<>();
    private int countTotal = -1;

    private AdditionalResultDetails(Map<ExecutionStatus, Integer> statusCounts) {
        ObjectChecker.assertNonNull(statusCounts);
        this.statusCounts.putAll(statusCounts);
    }

    public int getStatusCount(ExecutionStatus status) {
        ObjectChecker.assertNonNull(status);
        Integer count = this.statusCounts.putIfAbsent(status, 0);
        return count == null ? 0 : count;
    }

    public int getTotalCount() {
        if (this.countTotal == -1) {
            this.countTotal = 0;
            this.statusCounts.values().forEach((count) -> this.countTotal += count);
        }
        return this.countTotal;
    }

    public static final class Builder {
        private final Map<ExecutionStatus, Integer> statusCounts = new HashMap<>();

        public static Builder newBuilder() {
            return new Builder();
        }

        public Builder addStatusCounts(Map<ExecutionStatus, Integer> statusCounts) {
            this.statusCounts.putAll(statusCounts);
            return this;
        }

        public AdditionalResultDetails build() {
            return new AdditionalResultDetails(this.statusCounts);
        }
    }
}
