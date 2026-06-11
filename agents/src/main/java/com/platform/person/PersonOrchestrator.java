package com.platform.person;

import com.platform.model.PerspectiveFinding;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PersonOrchestrator {

    private static final Pattern STACK_TRACE_PATTERN =
        Pattern.compile("at\\s+([a-zA-Z0-9._$]+)\\.([a-zA-Z0-9_$]+)\\(");

    private final List<BasePersonAgent> personas;

    public PersonOrchestrator(List<BasePersonAgent> personas) {
        this.personas = personas;
    }

    public List<PerspectiveFinding> coordinateSwarm(
        String requirement, String stackTrace, String rawDbCodeChunks) {
        String anchoredCodeContext = anchorContextFilter(stackTrace, rawDbCodeChunks);

        try(ExecutorService executer = Executors.newVirtualThreadPerTaskExecutor()){
            List<Future<PerspectiveFinding>> futures = new ArrayList<>();
            for (BasePersonAgent agent: personas){
                futures.add(
                    executer.submit(() -> agent.generatePerspective(requirement, stackTrace, anchoredCodeContext))
                );
            }
            List<PerspectiveFinding> results = new ArrayList<>(futures.size());
            for (Future<PerspectiveFinding> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    throw new RuntimeException("Subtask transaction pool failed", e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Parallel execution pipeline was aborted", e);
                }
            }
            return results;
        }

    }

    private String anchorContextFilter(String stackTrace, String codeContext) {
        Matcher matcher = STACK_TRACE_PATTERN.matcher(stackTrace);
        if (matcher.find()) {
            String extractedFqn = matcher.group(1);
            return String.format(
                "--- CRITICAL STACK CONTEXT MATCHED FOR CLASS %s ---\n%s", extractedFqn, codeContext);
        }
        return codeContext;
    }

}
