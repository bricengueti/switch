package TNB.Switch.exception;

public class DeviceDisconnectedException extends SwitchException {
    public DeviceDisconnectedException(String message) {
        super(message, "DEVICE_DISCONNECTED");
    }
}