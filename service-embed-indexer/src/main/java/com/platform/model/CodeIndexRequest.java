package com.platform.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Index Java/Kotlin/etc. sources from absolute paths on the host running this service. */
public record CodeIndexRequest(
    @NotBlank String repo,
    String gitSha,
    @NotEmpty List<@NotBlank String> paths,
    Boolean recursive) {

  public CodeIndexRequest {
    if (gitSha == null || gitSha.isBlank()) {
      gitSha = "local";
    }
    if (recursive == null) {
      recursive = true;
    }
  }
}
