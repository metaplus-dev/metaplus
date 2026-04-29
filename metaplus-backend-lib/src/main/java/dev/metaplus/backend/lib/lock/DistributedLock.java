package dev.metaplus.backend.lib.lock;

import dev.metaplus.backend.lib.BackendException;
import dev.metaplus.backend.lib.es.EsClient;
import dev.metaplus.backend.lib.es.EsResponse;
import dev.metaplus.core.util.DateUtil;
import dev.metaplus.core.util.EnvUtil;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.sjf4j.JsonObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;

@Slf4j
@Component
public class DistributedLock {

    private final EsClient esClient;

    private final String indexName;

    public DistributedLock(EsClient esClient,
                           @Value("${metaplus.backend.es.indices.lock:i_metaplus_lock}")
                           String indexName) {
        this.esClient = esClient;
        this.indexName = indexName;
    }

    public boolean lock(@NonNull String lockId, int lockTimeSeconds) {
        return lock(lockId, lockTimeSeconds, EnvUtil.getProcessName());
    }

    public boolean lock(@NonNull String lockId, int lockTimeSeconds, @NonNull String lockedBy) {
        if (lockTimeSeconds <= 0) {
            throw new IllegalArgumentException("lockTimeSeconds must be > 0.");
        }

        URI uri = UriComponentsBuilder.fromPath("/{index}/_doc/{lockId}")
                .build(_indexName(), lockId);
        EsResponse response = esClient.get(uri);
        if (response.isSuccess()) {
            Instant now = Instant.now();
            // check if released or expired
            Boolean isReleased = response.getBody().getBooleanByPath("$._source.isReleased");
            if (null == isReleased || !isReleased) {
                if (_isLockActive(response, lockId, now)) {
                    return false;
                }
            }

            // update lock doc
            long seqNo = response.getBody().getLong("_seq_no");
            long primaryTerm = response.getBody().getLong("_primary_term");
            URI uri2 = UriComponentsBuilder.fromPath("/{index}/_doc/{lockId}")
                    .queryParam("if_seq_no", seqNo)
                    .queryParam("if_primary_term", primaryTerm)
                    .build(_indexName(), lockId);
            EsResponse response2 = esClient.put(uri2, JsonObject.of(
                    "lockedBy", lockedBy,
                    "lockedAt", DateUtil.format(now),
                    "expiredAt", DateUtil.format(now.plusSeconds(lockTimeSeconds)),
                    "isReleased", false,
                    "releasedAt", null
            ));
            return response2.isSuccess();

        } else if (response.isNotFound()) {
            // create lock doc
            Instant now = Instant.now();
            URI uri2 = UriComponentsBuilder.fromPath("/{index}/_doc/{lockId}")
                    .queryParam("op_type", "create")
                    .build(_indexName(), lockId);
            EsResponse response2 = esClient.put(uri2, JsonObject.of(
                    "lockedBy", lockedBy,
                    "lockedAt", DateUtil.format(now),
                    "expiredAt", DateUtil.format(now.plusSeconds(lockTimeSeconds)),
                    "isReleased", false,
                    "releasedAt", null
            ));
            return response2.isSuccess();

        } else {
            throw _failureWithEsResponse("lock", _targetLock(lockId), response);
        }

    }


    public void release(@NonNull String lockId) {
        release(lockId, EnvUtil.getProcessName());
    }

    public void release(@NonNull String lockId, @NonNull String lockedBy) {
        URI uri = UriComponentsBuilder.fromPath("/{index}/_doc/{lockId}")
                .build(_indexName(), lockId);
        EsResponse response = esClient.get(uri);
        if (response.isSuccess()) {
            String owner = response.getBody().getStringByPath("$._source.lockedBy");
            if (lockedBy.equals(owner)) {
                Instant now = Instant.now();
                if (_isLockActive(response, lockId, now)) {
                    // update release doc
                    long seqNo = response.getBody().getLong("_seq_no");
                    long primaryTerm = response.getBody().getLong("_primary_term");
                    URI uri2 = UriComponentsBuilder.fromPath("/{index}/_update/{lockId}")
                            .queryParam("if_seq_no", seqNo)
                            .queryParam("if_primary_term", primaryTerm)
                            .build(_indexName(), lockId);
                    EsResponse response2 = esClient.post(uri2, JsonObject.of("doc", JsonObject.of(
                            "isReleased", true,
                            "releasedAt", DateUtil.format(now)
                    )));
                    if (response2.isSuccess()) {
                        log.info("Release lock '{}' ok.", lockId);
                    } else {
                        log.warn("Release lock '{}' failed. Update res={}", lockId, response2);
                    }
                } else {
                    // do nothing
                }
            } else {
                log.warn("Release lock '{}' fail. The release lockedBy '{}' does not match to the lock owner '{}'.",
                        lockId, lockedBy, owner);
            }
        } else {
            log.warn("Release lock '{}' fail. The doc was not found.", lockId);
        }
    }

    private boolean _isLockActive(@NonNull EsResponse response, @NonNull String lockId, @NonNull Instant now) {
        String expiredAt = response.getBody().getStringByPath("$._source.expiredAt");
        if (expiredAt == null) {
            throw _failureWithReason("lock", _targetLock(lockId), "missing expiredAt");
        }
        Instant expiredAtInstant;
        try {
            expiredAtInstant = DateUtil.parseCanonical(expiredAt);
        } catch (IllegalArgumentException e) {
            throw new BackendException(_buildReasonFailureMessage("lock", _targetLock(lockId),
                    "non-canonical expiredAt=" + expiredAt), e);
        }
        return !expiredAtInstant.isBefore(now);
    }

    private String _indexName() {
        return indexName;
    }

    private String _targetLock(String lockId) {
        return "lockId=" + lockId;
    }

    private BackendException _failureWithEsResponse(String operation, String target, EsResponse response) {
        return new BackendException("DistributedLock." + operation + " failed for " + target
                + ", status=" + response.getStatusCode() + ", body=" + response.getBody());
    }

    private BackendException _failureWithReason(String operation, String target, String reason) {
        return new BackendException(_buildReasonFailureMessage(operation, target, reason));
    }

    private String _buildReasonFailureMessage(String operation, String target, String reason) {
        return "DistributedLock." + operation + " failed for " + target + ": " + reason;
    }




}
