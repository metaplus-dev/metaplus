package dev.metaplus.core.exception;

public class MetaplusException extends RuntimeException {
    public MetaplusException(String message) {
        super(message);
    }
    public MetaplusException(String message, Throwable cause) {
        super(message, cause);
    }
}
