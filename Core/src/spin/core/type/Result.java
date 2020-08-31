package spin.core.type;

public final class Result<D> {
    private final boolean success;
    private final D data;
    private final String error;

    private Result(boolean success, D data, String error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static <D> Result<D> successful(D data) {
        return new Result<>(true, data, null);
    }

    public static <D> Result<D> error(String error) {
        return new Result<>(false, null, error);
    }

    public boolean isSuccess() {
        return this.success;
    }

    public D getData() {
        return this.data;
    }

    public String getError() {
        return this.error;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " { " + (this.success ? "success: " + this.data : "error: " + this.error) + " }";
    }
}
