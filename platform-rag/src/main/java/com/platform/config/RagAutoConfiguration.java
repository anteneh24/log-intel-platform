package com.platform.config;

import com.platform.rag.embedding.FakeEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "com.platform.rag")
public class RagAutoConfiguration {

  @Bean
  @ConditionalOnProperty(name = "embedding.provider", havingValue = "openai")
  public EmbeddingModel openAiEmbeddingModel(
      @Value("${embedding.openai.api-key:}") String apiKey,
      @Value("${embedding.model-name:text-embedding-3-small}") String modelName) {
    return OpenAiEmbeddingModel.builder().apiKey(apiKey).modelName(modelName).build();
  }

  @Bean
  @ConditionalOnProperty(name = "embedding.provider", havingValue = "ollama", matchIfMissing = true)
  public EmbeddingModel ollamaEmbeddingModel(
      @Value("${embedding.ollama.base-url:http://localhost:11434}") String baseUrl,
      @Value("${embedding.model-name:embeddinggemma:300m}") String modelName,
      @Value("${embedding.ollama.timeout-seconds:600}") long timeoutSeconds) {
    return OllamaEmbeddingModel.builder()
        .baseUrl(baseUrl)
        .modelName(modelName)
        .timeout(Duration.ofSeconds(timeoutSeconds))
        .build();
  }

  @Bean
  @ConditionalOnProperty(name = "embedding.provider", havingValue = "fake")
  public EmbeddingModel fakeEmbeddingModel() {
    return new FakeEmbeddingModel();
  }
}
