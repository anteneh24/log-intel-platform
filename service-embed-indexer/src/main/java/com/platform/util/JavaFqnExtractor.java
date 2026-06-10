package com.platform.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaFqnExtractor {

  private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
  private static final Pattern TYPE =
      Pattern.compile(
          "(?:public\\s+)?(?:final\\s+)?(?:class|interface|enum|record)\\s+([A-Za-z_][\\w]*)");

  private JavaFqnExtractor() {}

  public static String extract(String relativePath, String source) {
    String fileName = relativePath.replace('\\', '/');
    int slash = fileName.lastIndexOf('/');
    String simpleName = slash >= 0 ? fileName.substring(slash + 1) : fileName;
    if (simpleName.endsWith(".java")) {
      simpleName = simpleName.substring(0, simpleName.length() - 5);
    }

    Optional<String> pkg = packageName(source);
    if (pkg.isPresent()) {
      return pkg.get() + "." + simpleName;
    }
    Matcher typeMatcher = TYPE.matcher(source);
    if (typeMatcher.find()) {
      return typeMatcher.group(1);
    }
    return fileName.replace('/', '.');
  }

  private static Optional<String> packageName(String source) {
    Matcher matcher = PACKAGE.matcher(source);
    if (matcher.find()) {
      return Optional.of(matcher.group(1));
    }
    return Optional.empty();
  }
}
