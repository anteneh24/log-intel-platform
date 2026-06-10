package com.platform.util;

import com.platform.model.RetrievedChunk;
import java.util.List;
import java.util.stream.Collectors;

public final class RagChunkFormatter {

  private RagChunkFormatter() {}

  public static String format(List<RetrievedChunk> chunks) {
    if (chunks == null || chunks.isEmpty()) {
      return "";
    }
    return chunks.stream()
        .map(
            c ->
                "--- CHUNK "
                    + c.id()
                    + " (score="
                    + c.score()
                    + ") ---\n"
                    + c.content())
        .collect(Collectors.joining("\n\n"));
  }
}
