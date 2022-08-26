package ua.tn.exception;

public class SessionOperationException extends RuntimeException {
    public SessionOperationException(String message) {
        super(message);
    }

    public SessionOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
