package dev.metaplus.core.model.patch;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.sjf4j.JsonObject;


/**
 *
 * {
 *     "_id": "",
 *     "_version": "",
 *     "created": 1,
 *     "updated": 1,
 *     "deleted": 1,
 *     "noops": 1,
 *     "not_found": 1
 * }
 *
 */
@Getter @Setter
public class Result extends JsonObject {

    private String innerId;
    private Long innerVersion;
    private int created;
    private int updated;
    private int deleted;
    private int noops;
    private int notFound;
    private int total;


    // support _update/_doc/_delete/_update_by_query
    public static Result fromEsBody(JsonObject esBody) {
        if (null == esBody) return null;
        Result result = new Result();
        result.setInnerId(esBody.getString("_id"));
        result.setInnerVersion(esBody.getLong("_version"));

        String ss = esBody.getString("result");
        boolean parsedSingleResult = false;
        if (null != ss) {
            if ("created".equals(ss)) {
                result.setCreated(1);
                parsedSingleResult = true;
            } else if ("updated".equals(ss)) {
                result.setUpdated(1);
                parsedSingleResult = true;
            } else if ("deleted".equals(ss)) {
                result.setDeleted(1);
                parsedSingleResult = true;
            } else if ("not_found".equals(ss)) {
                result.setNotFound(1);
                parsedSingleResult = true;
            } else if ("noop".equals(ss)) {
                result.setNoops(1);
                parsedSingleResult = true;
            }
        }
        if (!parsedSingleResult) {
            result.setCreated(esBody.getInt("created", 0));
            result.setUpdated(esBody.getInt("updated", 0));
            result.setDeleted(esBody.getInt("deleted", 0));
            result.setNotFound(esBody.getInt("not_found", 0));
            result.setNoops(esBody.getInt("noops", 0));
        }
        result.setTotal(result.getCreated() + result.getUpdated() + result.getDeleted() +
                result.getNotFound() + result.getNoops());
        return result;
    }


    public void plus(@NonNull Result result) {
        total += result.getTotal();
        created += result.getCreated();
        updated += result.getUpdated();
        deleted += result.getDeleted();
        noops += result.getNoops();
        notFound += result.getNotFound();
    }

}
