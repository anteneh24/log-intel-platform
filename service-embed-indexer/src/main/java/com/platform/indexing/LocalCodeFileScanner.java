package com.platform.indexing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class LocalCodeFileScanner {

  private static final Set<String> SOURCE_EXTENSIONS =
      Set.of(".java", ".kt", ".scala", ".go", ".py", ".ts", ".tsx", ".js", ".jsx");

  private static final int MAX_FILE_BYTES = 512 * 1024;

  private LocalCodeFileScanner() {}

  public static List<ScannedFile> scan(List<String> rawPaths, boolean recursive) throws IOException {
    List<ScannedFile> results = new ArrayList<>();
    for (String raw : rawPaths) {
      Path path = Path.of(raw).toAbsolutePath().normalize();
      if (!Files.exists(path)) {
        throw new IOException("Path does not exist: " + path);
      }
      if (Files.isRegularFile(path)) {
        scanFile(path, path.getParent() != null ? path.getParent() : path, results);
        continue;
      }
      if (!Files.isDirectory(path)) {
        continue;
      }
      int depth = recursive ? Integer.MAX_VALUE : 1;
      try (Stream<Path> walk =
          Files.walk(path, depth, FileVisitOption.FOLLOW_LINKS)) {
        walk.filter(Files::isRegularFile)
            .filter(LocalCodeFileScanner::isSourceFile)
            .forEach(p -> scanFile(p, path, results));
      }
    }
    return results;
  }

  private static void scanFile(Path file, Path root, List<ScannedFile> results) {
    try {
      long size = Files.size(file);
      if (size > MAX_FILE_BYTES) {
        results.add(ScannedFile.skipped(file, "file exceeds " + MAX_FILE_BYTES + " bytes"));
        return;
      }
      String text = Files.readString(file, StandardCharsets.UTF_8);
      String relative = root.relativize(file).toString().replace('\\', '/');
      results.add(ScannedFile.ok(file, relative, text));
    } catch (IOException e) {
      results.add(ScannedFile.skipped(file, e.getMessage()));
    }
  }

  private static boolean isSourceFile(Path path) {
    String name = path.getFileName().toString().toLowerCase();
    return SOURCE_EXTENSIONS.stream().anyMatch(name::endsWith);
  }

  public record ScannedFile(Path absolutePath, String relativePath, String content, String skipReason) {

    static ScannedFile ok(Path absolute, String relative, String content) {
      return new ScannedFile(absolute, relative, content, null);
    }

    static ScannedFile skipped(Path absolute, String reason) {
      return new ScannedFile(absolute, null, null, reason);
    }

    boolean ok() {
      return skipReason == null;
    }
  }
}
