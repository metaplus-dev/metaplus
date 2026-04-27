package dev.metaplus.backend.lib.sample;

import dev.metaplus.core.model.Idea;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.sjf4j.JsonObject;

import java.time.Instant;

@Getter @Setter
public class Sample extends JsonObject {

    private String id;
    @NotNull
    private Idea idea;
    @NotEmpty
    private JsonObject data;
    private Instant createdAt;
    private boolean locked;

}
