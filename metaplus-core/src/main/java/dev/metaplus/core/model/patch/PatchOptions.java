package dev.metaplus.core.model.patch;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PatchOptions {

    private RefreshMode refresh;
    private String routeKey;
    private Boolean readFresh;
    private ExecutionMode executionMode;

    public enum RefreshMode {
        DEFAULT,
        IMMEDIATE,
        WAIT_UNTIL
    }

    public enum ExecutionMode {
        SYNC,
        ASYNC
    }

}
