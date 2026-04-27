package dev.metaplus.core.model;


import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.sjf4j.JsonObject;

/**
 *
 * FQMN = <domain>:<system>:<instance>:<entity>
 *
 * Example:
 *  data.table:mysql:main:sales.orders
 */
@Getter @Setter
public class Idea extends JsonObject {
    @NotEmpty
    private String fqmn;
    @NotEmpty
    private String domain;
    @NotEmpty
    private String system;
    @NotEmpty
    private String instance;
    @NotEmpty
    private String entity;

    public static Idea of(@NonNull String fqmn) {
        String[] ss = fqmn.split(":");
        if (ss.length != 4) throw new IllegalArgumentException("Invalid fqmn: " + fqmn);
        Idea idea = new Idea();
        idea.setFqmn(fqmn);
        idea.setDomain(ss[0]);
        idea.setSystem(ss[1]);
        idea.setInstance(ss[2]);
        idea.setEntity(ss[3]);
        return idea;
    }

}
