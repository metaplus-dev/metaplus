package dev.metaplus.backend.es;

public enum BulkItemMethod {
    INDEX("index"),
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete");

    private final String method;
    BulkItemMethod(String method) {
        this.method = method;
    }

    @Override
    public String toString() {
        return method;
    }
}