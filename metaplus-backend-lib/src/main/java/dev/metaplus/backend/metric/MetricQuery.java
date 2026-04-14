package dev.metaplus.backend.metric;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.sjf4j.JsonObject;

import java.time.Instant;
import java.util.Map;

@Getter @Setter
public class MetricQuery extends JsonObject {

    @NotBlank
    private String metricName;
    @NotBlank
    private String assetName;
    @NotBlank
    private String period;
    private Map<String, String> dims;
    @NotNull
    private Instant startedAtStart;
    private Instant startedAtEnd;
    private int from = 0;
    private int size = 1000;

}
