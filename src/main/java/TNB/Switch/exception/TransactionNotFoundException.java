package TNB.Switch.exception;

public class TransactionNotFoundException extends SwitchException {
    public TransactionNotFoundException(String message) {
        super(message, "TRANSACTION_NOT_FOUND");
    }
}