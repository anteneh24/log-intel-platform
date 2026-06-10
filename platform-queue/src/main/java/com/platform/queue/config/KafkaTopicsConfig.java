package com.platform.queue.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.util.List;

/**
 * Declares Kafka topics via {@link NewTopic}; Spring invokes {@link
 * org.springframework.kafka.core.KafkaAdmin} to create them when {@code auto-create-topics} is
 * enabled (default true). Set {@code com.platform.kafka.auto-create-topics=false} in production if
 * topics are provisioned by infrastructure.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "com.platform.kafka",
    name = "auto-create-topics",
    havingValue = "true",
    matchIfMissing = true)
public class KafkaTopicsConfig {

  public static final String RAW_LOGS = "logs.raw";
  public static final String PREPROCESSED_LOGS = "logs.preprocessed";
  public static final String NORMALIZED_LOGS = "logs.normalized";
  public static final String ALERTS = "alerts.notifications";
  public static final String INCIDENTS_EVENTS = "incidents.events";
  public static final String TICKETS_EVENTS = "tickets.events";
  public static final String TICKETS_NEW = "tickets.new";
  public static final String EMBEDDING_JOBS = "embedding.jobs";
  public static final String EMBED_REQUESTS = "embed.requests";
  public static final String RAG_QUERIES = "rag.queries";

  /** All platform topic names (for observability / admin tooling). */
  public static List<String> allTopicNames() {
    return List.of(
        RAW_LOGS,
        PREPROCESSED_LOGS,
        NORMALIZED_LOGS,
        ALERTS,
        INCIDENTS_EVENTS,
        TICKETS_EVENTS,
        TICKETS_NEW,
        EMBEDDING_JOBS,
        EMBED_REQUESTS,
        RAG_QUERIES);
  }

  @Bean
  public NewTopic rawLogs() {
    return TopicBuilder.name(RAW_LOGS).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic preprocessedLogs() {
    return TopicBuilder.name(PREPROCESSED_LOGS).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic normalizedLogs() {
    return TopicBuilder.name(NORMALIZED_LOGS).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic alerts() {
    return TopicBuilder.name(ALERTS).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic incidentsEvents() {
    return TopicBuilder.name(INCIDENTS_EVENTS).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic ticketsEvents() {
    return TopicBuilder.name(TICKETS_EVENTS).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic ticketsNew() {
    return TopicBuilder.name(TICKETS_NEW).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic embeddingJobs() {
    return TopicBuilder.name(EMBEDDING_JOBS).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic embedRequests() {
    return TopicBuilder.name(EMBED_REQUESTS).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic ragQueries() {
    return TopicBuilder.name(RAG_QUERIES).partitions(3).replicas(1).build();
  }
}
