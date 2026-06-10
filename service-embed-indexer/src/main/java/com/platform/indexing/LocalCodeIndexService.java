package com.platform.indexing;

import com.platform.model.CodeIndexRequest;
import com.platform.model.CodeIndexResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class LocalCodeIndexService {

    private final CodeEmbedPublisher publisher;
    private final long sendTimeoutMs;

    public LocalCodeIndexService(
        CodeEmbedPublisher publisher,
        @Value("${com.platform.embed.index.send-timeout-ms:5000}") long sendTimeoutMs) {
        this.publisher = publisher;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    public CodeIndexResponse index(CodeIndexRequest request) throws IOException{
        List<LocalCodeFileScanner.ScannedFile> scanned = LocalCodeFileScanner.scan(request.paths(), request.recursive());

        int queued = 0;
        List<String> skipped = new ArrayList<>();

        for (LocalCodeFileScanner.ScannedFile file : scanned){
            if (!file.ok()){
                skipped.add(file.absolutePath() + ":" + file.skipReason());
                continue;
            }

            try {
                publisher
                    .publish(request.repo(), request.gitSha(), file)
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
                queued++;
            }catch (Exception e){
                skipped.add(file.absolutePath() + ":" + file.skipReason());
            }
        }

        return new CodeIndexResponse(queued, skipped.size(), List.copyOf(skipped));
    }
}
