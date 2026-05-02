package dev.metaplus.backend.server.bootstrap;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class BootstrapReport {

    private boolean createdDomainIndex;
    private final List<String> createdBuiltInDomains = new ArrayList<>();
    private final List<String> skippedBuiltInDomains = new ArrayList<>();
    @Setter
    private int loadedDomainCount;

    /**
     * Mark that the domain index was created during bootstrap.
     */
    public void markCreatedDomainIndex() {
        createdDomainIndex = true;
    }

    /**
     * Record one created built-in domain.
     */
    public void addCreatedBuiltInDomain(String domainName) {
        createdBuiltInDomains.add(domainName);
    }

    /**
     * Record one skipped built-in domain.
     */
    public void addSkippedBuiltInDomain(String domainName) {
        skippedBuiltInDomains.add(domainName);
    }

}
