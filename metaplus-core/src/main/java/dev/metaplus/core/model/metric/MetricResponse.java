package dev.metaplus.core.model.metric;

import lombok.Getter;
import lombok.Setter;
import org.sjf4j.JsonObject;

import java.util.List;

@Getter @Setter
public class MetricResponse extends JsonObject {
    private long total;
    private List<Metric> metrics;
}
