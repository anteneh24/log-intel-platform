package com.platform.util;

import com.platform.core.model.ParsedLog;
import com.platform.core.model.ParsedLogBatch;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogPipelineMapper {

  private static final Pattern STACK_LINE =
      Pattern.compile("(?m)^\\s*at\\s+[\\w$.]+\\([^)]+\\)");

  private LogPipelineMapper() {}

  public static ParsedLogBatch toBatch(ParsedLog log) {
    String stack = extractStackTrace(log);
    return new ParsedLogBatch(
        log.fingerprint(),
        log.raw().service(),
        log.template(),
        stack,
        List.of());
  }

  public static String extractStackTrace(ParsedLog log) {
    if (log.structured() != null) {
      Object st = log.structured().get("stackTrace");
      if (st != null && !st.toString().isBlank()) {
        return st.toString();
      }
      Object trace = log.structured().get("trace");
      if (trace != null && !trace.toString().isBlank()) {
        return trace.toString();
      }
    }
    String message = log.raw().message();
    if (message == null) {
      return "";
    }
    Matcher matcher = STACK_LINE.matcher(message);
    if (matcher.find()) {
      return message;
    }
    if (message.contains("Exception") || message.contains("Error")) {
      return message;
    }
    return message;
  }

  public static String requirementFor(ParsedLog log) {
    return "Investigate anomaly for service "
        + log.raw().service()
        + " fingerprint "
        + log.fingerprint();
  }
}
