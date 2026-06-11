package com.platform.rag.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/** Deterministic 768-dim vectors for local dev without an external model. */
public class FakeEmbeddingModel implements EmbeddingModel {

  private static final int DIMENSION = 768;

  @Override
  public Response<Embedding> embed(String text) {
    return Response.from(Embedding.from(vectorFor(text)));
  }

  @Override
  public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
    List<Embedding> embeddings = new ArrayList<>(segments.size());
    for (TextSegment segment : segments) {
      embeddings.add(Embedding.from(vectorFor(segment.text())));
    }
    return Response.from(embeddings);
  }

  private static float[] vectorFor(String text) {
    byte[] digest = sha256(text);
    float[] vector = new float[DIMENSION];
    for (int i = 0; i < DIMENSION; i++) {
      int b = digest[i % digest.length] & 0xff;
      vector[i] = (b / 127.5f) - 1.0f;
    }
    return vector;
  }

  private static byte[] sha256(String text) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
