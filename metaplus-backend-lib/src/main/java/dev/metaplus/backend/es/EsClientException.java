package dev.metaplus.backend.es;


public class EsClientException extends RuntimeException {
    public EsClientException(String message) {
        super(message);
    }

    public EsClientException(Throwable cause) {
        super(cause);
    }

    public EsClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
