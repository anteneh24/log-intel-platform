package com.platform.model;

import java.util.List;

public record CodeIndexResponse(
    int filesQueued, int filesSkipped, List<String> skippedReasons) {}
