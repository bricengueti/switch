package TNB.Switch.exception;

public abstract class SwitchException extends RuntimeException {
    private final String errorCode;

    protected SwitchException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}