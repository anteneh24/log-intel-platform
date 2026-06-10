package com.platform.model;

import java.util.Map;

/** Kafka payload shape consumed by {@code service-embedding-worker} (embed.requests). */
public record EmbedRequestMessage(
    String corpus, String contentId, String text, Map<String, String> metadata) {

  public EmbedRequestMessage {
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
