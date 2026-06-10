package com.platform.controller;

import com.platform.indexing.LocalCodeIndexService;
import com.platform.model.CodeIndexRequest;
import com.platform.model.CodeIndexResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/embed")
public class CodeEmbedController {

    private final LocalCodeIndexService indexService;

    public CodeEmbedController(LocalCodeIndexService indexService) {
        this.indexService = indexService;
    }

    /**
     * Queue local source files for background embedding. Paths must be readable by this process
     * (run the indexer on the machine that holds the repo).
     */
    @PostMapping("/code:index")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CodeIndexResponse indexLocalCode(@Valid @RequestBody CodeIndexRequest request)
        throws IOException {
        return indexService.index(request);
    }
}
