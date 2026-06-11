package com.platform.model;

import java.util.List;

public record PerspectiveFinding(
    String role,
    String errorType,
    String layer,
    List<String> suspectedFiles,
    double confidence,
    String rationale) {}
