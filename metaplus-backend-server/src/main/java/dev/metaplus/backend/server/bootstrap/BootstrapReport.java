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

    public void markCreatedDomainIndex() {
        createdDomainIndex = true;
    }

    public void addCreatedBuiltInDomain(String domainName) {
        createdBuiltInDomains.add(domainName);
    }

    public void addSkippedBuiltInDomain(String domainName) {
        skippedBuiltInDomains.add(domainName);
    }

}
