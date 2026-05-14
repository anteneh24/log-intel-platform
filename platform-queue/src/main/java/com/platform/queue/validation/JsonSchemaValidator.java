package com.platform.queue.validation;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class JsonSchemaValidator {

  private final Schema envelopeSchema;

  public JsonSchemaValidator(
      @Value("classpath:/schemas/envelope.json") Resource envelopeSchemaResource)
      throws IOException {
    try (InputStream in = envelopeSchemaResource.getInputStream()) {
      JSONObject rawSchema = new JSONObject(new JSONTokener(in));
      this.envelopeSchema = SchemaLoader.load(rawSchema);
    }
  }

  public void validateJSONObject(JSONObject payload) throws ValidationException {
    envelopeSchema.validate(payload);
  }

  public void validate(String json) {
    validateJSONObject(new JSONObject(json));
  }
}
