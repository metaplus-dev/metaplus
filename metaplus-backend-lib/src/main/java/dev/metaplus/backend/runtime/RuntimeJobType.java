package dev.metaplus.backend.runtime;

public enum RuntimeJobType {
    SAMPLING("lastSamplingAt"),
    LLM_GEN("lastLlmGenAt");

    /** Backing field stored in the runtime sidecar document. */
    private final String completedAtFieldName;

    RuntimeJobType(String completedAtFieldName) {
        this.completedAtFieldName = completedAtFieldName;
    }

    public String completedAtFieldName() {
        return completedAtFieldName;
    }
}
