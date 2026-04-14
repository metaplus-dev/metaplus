package dev.metaplus.core.model.metric;

import lombok.Getter;
import lombok.Setter;
import org.sjf4j.JsonObject;

import java.time.Instant;
import java.util.Map;

@Getter @Setter
public class MetricRequest extends JsonObject {
    private String metricName;
    private String assetName;
    private String period;
    private Map<String, String> dims;
    private Instant startedAtStart;
    private Instant startedAtEnd;
    private int from;
    private int size;
}
