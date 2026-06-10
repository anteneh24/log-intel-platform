package com.platform.indexing;

import com.platform.model.EmbedRequestMessage;
import com.platform.queue.config.KafkaTopicsConfig;
import com.platform.util.JavaFqnExtractor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class CodeEmbedPublisher {


    private final KafkaTemplate<String, EmbedRequestMessage> kafkaTemplate;

    public CodeEmbedPublisher(KafkaTemplate<String,EmbedRequestMessage> kafkaTemplate){
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<?> publish(
        String repo, String gitSha, LocalCodeFileScanner.ScannedFile file) {
        String fqn = JavaFqnExtractor.extract(file.relativePath(), file.content());
        EmbedRequestMessage message =
            new EmbedRequestMessage(
                "CODE",
                file.relativePath(),
                file.content(),
                Map.of(
                    "repo", repo,
                    "git_sha", gitSha,
                    "file_path", file.relativePath(),
                    "fqn", fqn
                )
            );

        return kafkaTemplate.send(KafkaTopicsConfig.EMBED_REQUESTS,repo+":"+file.relativePath(),message);
    }
}
