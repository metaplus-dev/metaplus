package dev.metaplus.backend.server.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapRunner implements ApplicationRunner {

    private final BootstrapService bootstrapService;

    @Value("${metaplus.bootstrap.mode:verify}")
    private String bootstrapMode;

    /**
     * Run bootstrap according to the configured startup mode.
     */
    @Override
    public void run(ApplicationArguments args) {
        String mode = bootstrapMode == null ? "off" : bootstrapMode.trim().toLowerCase(Locale.ROOT);
        if ("off".equals(mode)) {
            return;
        }

        BootstrapReport report;
        switch (mode) {
            case "bootstrap" -> report = bootstrapService.bootstrapBuiltInsAndLoadDomainRegistry();
            case "verify" -> report = bootstrapService.verifyBuiltInsAndLoadDomainRegistry();
            default -> throw new IllegalArgumentException("Unsupported metaplus.bootstrap.mode: " + bootstrapMode);
        }

        log.info("Metaplus bootstrap mode={}, createdDomainIndex={}, createdBuiltInDomains={}, skippedBuiltInDomains={}, loadedDomainCount={}",
                mode,
                report.isCreatedDomainIndex(),
                report.getCreatedBuiltInDomains(),
                report.getSkippedBuiltInDomains(),
                report.getLoadedDomainCount());
    }
}
