package dev.metaplus.core.model.metric;

import lombok.Getter;
import lombok.Setter;
import org.sjf4j.JsonObject;

import java.time.Instant;
import java.util.Map;

@Getter @Setter
public class Metric extends JsonObject {

    private String metricName;
    private String assetName;
    private String period;
    private Instant startedAt;
    private Map<String, String> dims;
    private double value;
    private double multiValue1;
    private double multiValue2;
    private double multiValue3;
    private double multiValue4;
    private double multiValue5;

}
