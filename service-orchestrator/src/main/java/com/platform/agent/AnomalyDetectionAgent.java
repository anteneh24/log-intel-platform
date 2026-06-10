package com.platform.agent;

import com.platform.core.model.AnomalyReport;
import com.platform.core.model.ParsedLog;
import com.platform.core.model.ParsedLogBatch;
import com.platform.detector.AnomalyDetectionContext;
import com.platform.detector.AnomalyDetector;
import com.platform.stats.BaselineStore;
import com.platform.stats.WindowedFingerprintCounter;
import com.platform.util.LogPipelineMapper;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/** Deterministic statistical gate — no LLM. */
@Component
public class AnomalyDetectionAgent {

  private static final int BASELINE_WINDOWS_24H = 24 * 60 / 5;

  private final WindowedFingerprintCounter counter;
  private final BaselineStore baselineStore;
  private final AnomalyDetector detector;

  public AnomalyDetectionAgent(
      WindowedFingerprintCounter counter,
      BaselineStore baselineStore,
      AnomalyDetector detector) {
    this.counter = counter;
    this.baselineStore = baselineStore;
    this.detector = detector;
  }

  public record Evaluation(AnomalyReport report, com.platform.detector.AnomalyReport stats) {}

  public Optional<Evaluation> evaluate(ParsedLog log, Instant evaluatedAt) {
    Instant ts = log.raw().occurredAt() == null ? evaluatedAt : log.raw().occurredAt();
    String service = log.raw().service();
    String fingerprint = log.fingerprint();

    baselineStore.ensureFirstSeen(service, fingerprint, ts);
    counter.increment(service, fingerprint, ts);
    if (log.raw().level() != null && "ERROR".equalsIgnoreCase(log.raw().level())) {
      counter.incrementServiceError(service, ts);
    }

    double currentFp = counter.getCount(service, fingerprint, ts);
    DescriptiveStatistics fpBaseline =
        counter.fingerprintBaseline(service, fingerprint, ts, BASELINE_WINDOWS_24H);
    double currentSvcErr = counter.getServiceErrorCount(service, ts);
    DescriptiveStatistics svcBaseline =
        counter.serviceErrorBaseline(service, ts, BASELINE_WINDOWS_24H);
    boolean cold = baselineStore.isColdStart(service, fingerprint, ts);

    AnomalyDetectionContext ctx =
        new AnomalyDetectionContext(
            service, fingerprint, currentFp, fpBaseline, currentSvcErr, svcBaseline, cold);

    Optional<com.platform.detector.AnomalyReport> hit = detector.detect(ctx);
    if (hit.isEmpty()) {
      return Optional.empty();
    }

    com.platform.detector.AnomalyReport stats = hit.get();
    ParsedLogBatch batch = LogPipelineMapper.toBatch(log);
    AnomalyReport report =
        new AnomalyReport(
            batch,
            true,
            stats.score(),
            stats.type() == com.platform.detector.AnomalyReport.Type.NEW_ERROR,
            stats.type() == com.platform.detector.AnomalyReport.Type.RATE_JUMP
                ? stats.score()
                : 0.0,
            stats.type().name() + " z=" + stats.score());
    return Optional.of(new Evaluation(report, stats));
  }
}
