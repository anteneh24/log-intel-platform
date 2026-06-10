package com.platform.core.util;

import io.krakens.grok.api.Grok;
import io.krakens.grok.api.GrokCompiler;
import io.krakens.grok.api.Match;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class LogParser {
  private static final String PATTERNS_RESOURCE = "patterns/logs";
  /** Spring Boot console layout (Logback); overridden if classpath resource {@code patterns/logs} defines JAVA_LOG. */
  private static final String DEFAULT_JAVA_LOG_PATTERN =
      "%{TIMESTAMP_ISO8601:timestamp}%{SPACE}%{LOGLEVEL:level}%{SPACE}%{NUMBER:pid}%{SPACE}---%{SPACE}"
          + "\\[%{DATA:application}\\]%{SPACE}\\[%{DATA:thread}\\]%{SPACE}%{NOTSPACE:logger}%{SPACE}:%{SPACE}"
          + "%{GREEDYDATA:message}";

  private final Grok javaLogGrok;

  public LogParser() {
    GrokCompiler compiler = GrokCompiler.newInstance();
    compiler.registerDefaultPatterns();
    compiler.register("JAVA_LOG", DEFAULT_JAVA_LOG_PATTERN);
    registerCustomPatterns(compiler);
    this.javaLogGrok = compiler.compile("%{JAVA_LOG}");
  }

  public Map<String, Object> parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return Map.of("message", raw == null ? "" : raw);
    }

    Match match = javaLogGrok.match(raw);
    Map<String, Object> captured = match.capture();
    if (captured == null || captured.isEmpty()) {
      return Map.of("message", raw);
    }

    // java-grok returns a mutable map; we defensively copy for callers.
    return new LinkedHashMap<>(captured);
  }

  private static void registerCustomPatterns(GrokCompiler compiler) {
    InputStream is = openPatternsResource(LogParser.class.getClassLoader());
    if (is == null) {
      is = openPatternsResource(Thread.currentThread().getContextClassLoader());
    }
    try (InputStream stream = is) {
      if (stream == null) {
        return;
      }
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String trimmed = line.trim();
          if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            continue;
          }
          int firstSpace = indexOfWhitespace(trimmed);
          if (firstSpace <= 0) {
            continue;
          }
          String name = trimmed.substring(0, firstSpace).trim();
          String pattern = trimmed.substring(firstSpace).trim();
          if (!name.isEmpty() && !pattern.isEmpty()) {
            compiler.register(name, pattern);
          }
        }
      }
    } catch (IOException ignored) {
      // If patterns can't be loaded, parsing will fall back to raw message in parse().
    }
  }

  private static InputStream openPatternsResource(ClassLoader cl) {
    if (cl == null) {
      return null;
    }
    return cl.getResourceAsStream(PATTERNS_RESOURCE);
  }

  private static int indexOfWhitespace(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (Character.isWhitespace(s.charAt(i))) {
        return i;
      }
    }
    return -1;
  }
}

