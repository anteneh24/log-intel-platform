package com.platform.orchestrator;

import com.platform.core.agent.AgentContext;
import com.platform.core.model.NegotiatedFinding;
import com.platform.core.model.TicketResult;
import com.platform.model.PerspectiveFinding;
import com.platform.negotiation.ConsensusNegotiationAgent;
import com.platform.person.PersonOrchestrator;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Supplier;

@Service
@ConditionalOnBean({
    ConsensusNegotiationAgent.class
})
public class AgentOrchestratorServices {
    private final PersonOrchestrator personaOrchestrator;
    private final ConsensusNegotiationAgent consensusNegotiationAgent;
    private final MeterRegistry metrics;

    public AgentOrchestratorServices(
        PersonOrchestrator personaOrchestrator,
        ConsensusNegotiationAgent consensusNegotiationAgent,
        MeterRegistry metrics){
        this.personaOrchestrator = personaOrchestrator;
        this.consensusNegotiationAgent = consensusNegotiationAgent;
        this.metrics = metrics;
    }
    public PipelineOutcome run(IncidentPipelineInput input) {
        return run(input, AgentContext.fresh());
    }

    public PipelineOutcome run(IncidentPipelineInput input, AgentContext ctx){
        putIfAbsent(ctx, "projectRequirement", input.projectRequirement());
        putIfAbsent(ctx, "rag_code_output", input.codeContext());
        putIfAbsent(ctx, "service", input.service());

        if (!input.anomalyDetected()) {
            return emptyDiscarded();
        }

        OrchestratorState state = OrchestratorState.STARTED;

        state = step(
            state,
            "person_swarm",
            () -> personaOrchestrator.coordinateSwarm(input.projectRequirement(), input.stackTrace(), input.codeContext()),
            ctx,
            "perspectives"
        );
        metrics.counter("agent.invocations_total", "agent", "PersonaSwarm").increment();

        List<PerspectiveFinding> perspectives = ctx.get("perspectives");

        state = step(
            state,
            "consensus",
            ()-> consensusNegotiationAgent.execute(perspectives,ctx),
            ctx,
            "negotiated_consensus"
        );
        metrics.counter("agent.invocations_total", "agent", "ConsensusNegotiator").increment();

        NegotiatedFinding consensus = ctx.get("negotiated_consensus");

    }

    private static void putIfAbsent(AgentContext ctx, String key, Object value) {
        if (ctx.getOptional(key).isEmpty()) {
            ctx.put(key, value);
        }
    }
    private static PipelineOutcome emptyDiscarded() {
        return new PipelineOutcome(TicketResult.discarded(), null, null, null, null);
    }

    private <T> OrchestratorState step(
        OrchestratorState state,
        String stepName,
        Supplier<T> action,
        AgentContext ctx,
        String blackBoardKey){
        T result = action.get();
        ctx.put(blackBoardKey,result);
        return state.advance(stepName);
    }

    private enum OrchestratorState {
        STARTED,
        RUNNING,
        COMPLETED;

        public OrchestratorState advance(String step){
            return switch (this) {
                case STARTED -> "initialize".equals(step) ? RUNNING : STARTED;
                case RUNNING -> "finalize".equals(step) ? COMPLETED : RUNNING;
                case COMPLETED -> COMPLETED; // Terminal state
            };
        }
    }

}
