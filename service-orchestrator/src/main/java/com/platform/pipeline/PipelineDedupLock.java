package com.platform.pipeline;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public class PipelineDedupLock {

    static final String KEY_PREFIX = "pipeline:lock:";
    static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redis;

    public PipelineDedupLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * @return pipeline id when lock acquired; empty when another worker owns the fingerprint window
     */
    public Optional<UUID> tryAcquire(String fingerPrint){
        UUID pipelineId = UUID.randomUUID();
        Boolean acquired = redis.opsForValue().setIfAbsent(KEY_PREFIX+fingerPrint,pipelineId.toString(),LOCK_TTL);
        if (Boolean.TRUE.equals(acquired)){
            return Optional.of(pipelineId);
        }
        return Optional.empty();
    }
}
