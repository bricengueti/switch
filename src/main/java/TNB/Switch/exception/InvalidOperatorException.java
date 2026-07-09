package TNB.Switch.exception;

public class InvalidOperatorException extends SwitchException {
    public InvalidOperatorException(String message) {
        super(message, "INVALID_OPERATOR");
    }
}
