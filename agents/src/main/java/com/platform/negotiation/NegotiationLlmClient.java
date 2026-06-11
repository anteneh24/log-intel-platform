package com.platform.negotiation;

/** Mock-friendly LLM port for consensus negotiation (MID-tier routing). */
@FunctionalInterface
public interface NegotiationLlmClient {

  String chat(String prompt, String agentName);
}
