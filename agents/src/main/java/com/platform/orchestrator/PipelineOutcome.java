package com.platform.orchestrator;

import com.platform.core.model.*;

public record PipelineOutcome(
    TicketResult ticketResult,
    NegotiatedFinding consensus,
    RootCauseHypothesis rootCause,
    DraftTicket draft,
    PrioritizedTicket prioritized) {}
