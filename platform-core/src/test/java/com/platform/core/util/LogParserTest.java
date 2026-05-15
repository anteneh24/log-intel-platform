package com.platform.core.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogParserTest {

  @Test
  void parsesSpringBootConsoleLine() {
    LogParser parser = new LogParser();
    String line =
        "2026-05-15T13:06:15.943+03:00  INFO 45764 --- [service-orchestrator] [           main] o.s.boot.SpringApplication               : Application run failed";

    Map<String, Object> parsed = parser.parse(line);

    assertEquals("INFO", parsed.get("level"));
    assertEquals("45764", parsed.get("pid"));
    assertEquals("service-orchestrator", parsed.get("application"));
    assertEquals("main", parsed.get("thread").toString().trim());
    assertEquals("o.s.boot.SpringApplication", parsed.get("logger"));
    assertEquals("Application run failed", parsed.get("message"));
  }

  @Test
  void fallsBackToRawMessageForUnstructuredInput() {
    LogParser parser = new LogParser();
    Map<String, Object> parsed = parser.parse("plain log line without structure");
    assertEquals("plain log line without structure", parsed.get("message"));
    assertTrue(parsed.size() <= 1);
  }
}
