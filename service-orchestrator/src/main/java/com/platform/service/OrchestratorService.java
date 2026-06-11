package com.platform.service;

import com.google.common.hash.Hashing;
import com.platform.agent.AnomalyDetectionAgent;
import com.platform.agent.RagRetrievalAgent;
import com.platform.core.agent.AgentContext;
import com.platform.core.model.AnomalyReport;
import com.platform.core.model.ParsedLog;
import com.platform.core.model.Ticket;
import com.platform.detector.AnomalyDetectionContext;
import com.platform.detector.AnomalyDetector;
import com.platform.model.RetrievedChunk;
import com.platform.orchestrator.IncidentPipelineInput;
import com.platform.pipeline.ActiveIncidentRegistry;
import com.platform.pipeline.PipelineDedupLock;
import com.platform.service.AgentExecutionRecorder;
import com.platform.service.TicketWriteService;
import com.platform.stats.BaselineStore;
import com.platform.stats.WindowedFingerprintCounter;
import com.platform.util.LogPipelineMapper;
import com.platform.util.RagChunkFormatter;
import com.platform.util.StubTicketGenerator;
import com.platform.persistence.entity.IncidentEntity;
import com.platform.persistence.entity.TicketEntity;
import com.platform.persistence.repo.TicketRepository;
import com.platform.queue.config.KafkaTopicsConfig;
import com.platform.queue.model.KafkaEnvelope;
import com.platform.queue.producer.EnvelopeProducer;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);
    private static final int BASELINE_WINDOWS_24H = 24 * 60 / 5; // 288 five-minute windows / day
    private static final String TRACE_ID_KEY = "traceId";
    private static final String TICKETS_SCHEMA = "tickets.new.v1";
    private static final String AGENT_NAME = "ORCHESTRATOR";

    private final WindowedFingerprintCounter counter;
    private final AnomalyDetectionAgent anomalyDetectionAgent;
    private final BaselineStore baselineStore;
    private final AnomalyDetector detector;
    private final TicketRepository ticketRepository;
    private final TicketWriteService ticketWriteService;
    private final AgentExecutionRecorder agentExecutionRecorder;
    private final EnvelopeProducer ticketProducer;
    private final MeterRegistry meterRegistry;
    private final PipelineDedupLock pipelineDedupLock;
    private final ActiveIncidentRegistry activeIncidentRegistry;
    private final RagRetrievalAgent ragRetrievalAgent;

    public OrchestratorService(
        WindowedFingerprintCounter counter,
        BaselineStore baselineStore,
        AnomalyDetector detector,
        TicketRepository ticketRepository,
        TicketWriteService ticketWriteService,
        AgentExecutionRecorder agentExecutionRecorder,
        EnvelopeProducer ticketProducer,
        MeterRegistry meterRegistry,
        PipelineDedupLock pipelineDedupLock,
        ActiveIncidentRegistry activeIncidentRegistry,
        AnomalyDetectionAgent anomalyDetectionAgent,
        RagRetrievalAgent ragRetrievalAgent) {
        this.counter = counter;
        this.baselineStore = baselineStore;
        this.detector = detector;
        this.ticketRepository = ticketRepository;
        this.ticketWriteService = ticketWriteService;
        this.agentExecutionRecorder = agentExecutionRecorder;
        this.ticketProducer = ticketProducer;
        this.meterRegistry = meterRegistry;
        this.pipelineDedupLock = pipelineDedupLock;
        this.activeIncidentRegistry = activeIncidentRegistry;
        this.anomalyDetectionAgent = anomalyDetectionAgent;
        this.ragRetrievalAgent = ragRetrievalAgent;
    }

    /**
     * Statistical anomaly path for one preprocessed log: Redis counters/baseline, optional stub
     * ticket (idempotent on {@code incident_id} hash), Kafka publish, metrics, and {@code
     * agent_executions} audit row (no LLM cost).
     */
    public void run(ParsedLog log) {
        long wallStart = System.currentTimeMillis();
        Instant startedAt = Instant.now();
        UUID executionId = UUID.randomUUID();
        UUID pipelineId = executionId;
        String traceId = traceIdFromMdc();

        Optional<UUID> lockOwner = pipelineDedupLock.tryAcquire(log.fingerprint());

        if (lockOwner.isEmpty()) {
            activeIncidentRegistry.recordBurstSample(log.fingerprint());
            recordStep(UUID.randomUUID(), null, "PipelineDedupLock", traceId, startedAt, "BURST_SAMPLE", wallStart);
            meterRegistry.counter("pipeline.lock_denied_total").increment();
            return;
        }
        UUID pipeLineId = lockOwner.get();
        try {

            Optional<AnomalyDetectionAgent.Evaluation> anomaly =
                anomalyDetectionAgent.evaluate(log, startedAt);
            if (anomaly.isEmpty()) {
                recordStep(pipelineId, pipelineId, "AnomalyDetectionAgent", traceId, startedAt, "DISCARDED", wallStart);
                meterRegistry.counter("pipeline.completed_total", "terminal_state", "DISCARDED").increment();
                return;
            }

            AnomalyDetectionAgent.Evaluation evaluation = anomaly.get();
            com.platform.core.model.AnomalyReport report = evaluation.report();
            String stackTrace = LogPipelineMapper.extractStackTrace(log);

            AgentContext ragCtx = AgentContext.withPipelineId(pipelineId);
            String ragContext = retrieveRagContext(report, ragCtx, startedAt, traceId, pipelineId);

            UUID incidentId =
                computeIncidentId(log.fingerprint(), evaluation.stats().type().name());
            pipelineId = incidentId;

            if (ticketRepository.existsByIncidentId(incidentId)) {
                recordStep(pipelineId, pipelineId, "OrchestratorService", traceId, startedAt, "DEDUPLICATED", wallStart);
                meterRegistry.counter("pipeline.completed_total", "terminal_state", "DEDUPLICATED").increment();
                return;
            }

            IncidentPipelineInput agentInput =
                new IncidentPipelineInput(
                    LogPipelineMapper.requirementFor(log),
                    stackTrace,
                    ragContext,
                    true,
                    log.raw().service());
            AgentContext pipelineCtx = AgentContext.withPipelineId(pipelineId);
            long agentStart = System.currentTimeMillis();

            Instant ts = log.raw().occurredAt() == null ? startedAt : log.raw().occurredAt();
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

            Optional<AnomalyReport> report = detector.detect(ctx);
            if (report.isEmpty()) {
                recordTerminal(executionId, pipelineId, traceId, startedAt, "DISCARDED", wallStart);
                return;
            }

            AnomalyReport anomaly = report.get();
            UUID incidentId = computeIncidentId(fingerprint, anomaly.type().name());
            pipelineId = incidentId;

            if (ticketRepository.existsByIncidentId(incidentId)) {
                recordTerminal(executionId, pipelineId, traceId, startedAt, "DISCARDED", wallStart);
                return;
            }

            Instant now = Instant.now();
            IncidentEntity incident =
                new IncidentEntity(
                    incidentId,
                    UUID.fromString("00000000-0000-0000-0000-000000000000"),
                    "Anomaly: " + anomaly.type() + " in " + log.raw().service(),
                    "Fingerprint " + fingerprint,
                    null,
                    "OPEN",
                    anomaly.score() > 5 ? "HIGH" : "MEDIUM",
                    service,
                    log.raw().occurredAt() == null ? now : log.raw().occurredAt(),
                    log.raw().occurredAt() == null ? now : log.raw().occurredAt(),
                    now);

            Ticket ticket = StubTicketGenerator.from(log, anomaly, incidentId);
            TicketEntity entity =
                new TicketEntity(
                    ticket.id(),
                    ticket.incidentId(),
                    ticket.title(),
                    ticket.description(),
                    ticket.severity(),
                    ticket.service(),
                    "[]",
                    ticket.fixSuggestion(),
                    ticket.externalId(),
                    ticket.status().name(),
                    ticket.createdAt());
            ticketWriteService.saveIncidentAndTicket(incident, entity, incidentId);

            KafkaEnvelope<Ticket> outgoing =
                new KafkaEnvelope<>(
                    TICKETS_SCHEMA,
                    UUID.randomUUID(),
                    log.raw().occurredAt() == null ? now : log.raw().occurredAt(),
                    now,
                    traceId.isEmpty() ? null : traceId,
                    null,
                    ticket);
            ticketProducer.send(KafkaTopicsConfig.TICKETS_NEW, incidentId.toString(), outgoing);

            recordTerminal(executionId, pipelineId, traceId, startedAt, "TICKETED", wallStart);
        } catch (RuntimeException e) {
            long latencyMs = System.currentTimeMillis() - wallStart;
            agentExecutionRecorder.record(
                executionId, pipelineId, AGENT_NAME, startedAt, traceId, "FAILED", latencyMs);
            meterRegistry.counter("pipeline.completed_total", "terminal_state", "FAILED").increment();
            throw e;
        }
    }

    private void recordStep(
        UUID executionId,
        UUID pipelineId,
        String agentName,
        String traceId,
        Instant startedAt,
        String status,
        long wallStart) {
        long latencyMs = System.currentTimeMillis() - wallStart;
        agentExecutionRecorder.record(
            executionId,
            pipelineId == null ? executionId : pipelineId,
            agentName,
            startedAt,
            traceId,
            status,
            latencyMs);
    }

    private void recordTerminal(
        UUID executionId,
        UUID pipelineId,
        String traceId,
        Instant startedAt,
        String terminalState,
        long wallStart) {
        long latencyMs = System.currentTimeMillis() - wallStart;
        agentExecutionRecorder.record(
            executionId, pipelineId, AGENT_NAME, startedAt, traceId, terminalState, latencyMs);
        meterRegistry
            .counter("pipeline.completed_total", "terminal_state", terminalState)
            .increment();
        meterRegistry.summary("orchestrator.latency_ms").record(latencyMs);
    }

    private static String traceIdFromMdc() {
        String t = MDC.get(TRACE_ID_KEY);
        return t == null ? "" : t;
    }

    private static UUID computeIncidentId(String fingerprint, String anomalyType) {
        byte[] hash =
            Hashing.sha256()
                .hashString(fingerprint + ":" + anomalyType, StandardCharsets.UTF_8)
                .asBytes();
        return UUID.nameUUIDFromBytes(hash);
    }

    private String retrieveRagContext(
        AnomalyReport report,
        AgentContext ctx,
        Instant startedAt,
        String traceId,
        UUID pipelineId
    ){
        long ragStart = System.currentTimeMillis();

        try {
            List<RetrievedChunk> chunks = ragRetrievalAgent.execute(report, ctx);
            recordStep(
                UUID.randomUUID(), pipelineId, "RagRetrievalAgent", traceId, startedAt, "OK", ragStart);
            return RagChunkFormatter.format(chunks);
        } catch (Exception e) {
            log.warn("RAG retrieval skipped for pipeline {}: {}", pipelineId, e.toString());
            recordStep(
                UUID.randomUUID(), pipelineId, "RagRetrievalAgent", traceId, startedAt, "SKIPPED", ragStart);
            return "";
        }
    }
}
