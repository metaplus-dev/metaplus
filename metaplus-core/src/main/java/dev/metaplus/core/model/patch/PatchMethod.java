package dev.metaplus.core.model.patch;


/**
 * PatchMethod is an enum
 *
 */
public enum PatchMethod {
    META_CREATE("/meta/create"),
    META_UPDATE("/meta/update"),
    META_UPSERT("/meta/upsert"),
    META_REFACTOR("/meta/refactor"),
    META_DELETE("/meta/delete"),
    META_UPDATE_BY_QUERY("/meta/updateByQuery"),
    META_REFACTOR_BY_QUERY("/meta/refactorByQuery"),
    META_DELETE_BY_QUERY("/meta/deleteByQuery"),
    PLUS_UPDATE("/plus/update"),
    PLUS_SCRIPT("/plus/script"),
    PLUS_UPDATE_BY_QUERY("/plus/updateByQuery");

    private final String method;
    PatchMethod(String method) {
        this.method = method;
    }
    public String methodName() {
        return method;
    }
    @Override
    public String toString() {
        return methodName();
    }


    /**
     * convert string to PatchMethod
     *
     * @param method    method string
     * @return          PatchMethod or null
     */
    public static PatchMethod of(String method) {
        for (PatchMethod pm : PatchMethod.values()) {
            if (pm.methodName().equals(method)) {
                return pm;
            }
        }
        return null;
    }
}