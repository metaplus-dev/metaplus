package dev.metaplus.backend.server;

import dev.metaplus.backend.lib.BackendException;

public class BackendServerException extends BackendException {

    public BackendServerException(String message) {
        super(message);
    }

    public BackendServerException(Throwable cause) {
        super(cause);
    }

    public BackendServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
