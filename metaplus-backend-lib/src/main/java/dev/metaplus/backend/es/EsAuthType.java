package dev.metaplus.backend.es;

import lombok.NonNull;

/**
 * Supported Elasticsearch authentication modes.
 */
public enum EsAuthType {
    NONE,
    BASIC,
    BEARER,
    API_KEY;

    public static EsAuthType of(@NonNull String value) {
        try {
            return EsAuthType.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unsupported Elasticsearch auth type '" + value + "'.", e);
        }
    }
}
