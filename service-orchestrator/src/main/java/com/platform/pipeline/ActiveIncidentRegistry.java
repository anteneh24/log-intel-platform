package com.platform.pipeline;

import com.platform.persistence.repo.IncidentRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class ActiveIncidentRegistry {

  private static final String ACTIVE_PREFIX = "incident:active:";
  private static final String SAMPLE_PREFIX = "incident:samples:";
  private static final Duration ACTIVE_TTL = Duration.ofHours(48);

  private final StringRedisTemplate redis;
  private final IncidentRepository incidentRepository;

  public ActiveIncidentRegistry(StringRedisTemplate redis, IncidentRepository incidentRepository) {
    this.redis = redis;
    this.incidentRepository = incidentRepository;
  }

  public void register(String fingerprint, UUID incidentId) {
    redis.opsForValue().set(ACTIVE_PREFIX + fingerprint, incidentId.toString(), ACTIVE_TTL);
  }

  @Transactional
  public void recordBurstSample(String fingerprint) {
    Optional<UUID> incidentId = resolveActiveIncident(fingerprint);
    redis.opsForValue().increment(SAMPLE_PREFIX + fingerprint);
//    incidentId.ifPresent(
//        id -> incidentRepository.touchLastSeen(id, Instant.now()));
  }

  public long sampleCount(String fingerprint) {
    String val = redis.opsForValue().get(SAMPLE_PREFIX + fingerprint);
    if (val == null) {
      return 0L;
    }
    try {
      return Long.parseLong(val);
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  private Optional<UUID> resolveActiveIncident(String fingerprint) {
    String raw = redis.opsForValue().get(ACTIVE_PREFIX + fingerprint);
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(UUID.fromString(raw));
  }
}
