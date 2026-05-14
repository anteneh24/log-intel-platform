package com.platform.core.util;

import com.google.common.hash.Hashing;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class Fingerprinter {
  public String compute(String service, String level, String normalizedMsg) {
    String input = String.format("%s:%s:%s", service, level, normalizedMsg);
    return Hashing.sha256().hashString(input, StandardCharsets.UTF_8).toString();
  }
}

