package TNB.Switch.exception;

public class SmsParsingException extends SwitchException {
    public SmsParsingException(String message) {
        super(message, "SMS_PARSING_FAILED");
    }
}