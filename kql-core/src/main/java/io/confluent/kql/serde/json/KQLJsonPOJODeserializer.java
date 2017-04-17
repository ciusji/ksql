/**
 * Copyright 2017 Confluent Inc.
 **/
package io.confluent.kql.serde.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.confluent.kql.util.KQLException;
import io.confluent.kql.util.SchemaUtil;
import io.confluent.kql.physical.GenericRow;

public class KQLJsonPOJODeserializer implements Deserializer<GenericRow> {

  private ObjectMapper objectMapper = new ObjectMapper();

  private final Schema schema;
  private final Map<String, String> caseSensitiveKeyMap = new HashMap<>();

  /**
   * Default constructor needed by Kafka
   */
  public KQLJsonPOJODeserializer(Schema schema) {
    this.schema = schema;
    this.objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);

  }

  @Override
  public void configure(Map<String, ?> map, boolean b) {

  }

  @Override
  public GenericRow deserialize(final String topic, final byte[] bytes) {
    if (bytes == null) {
      return null;
    }

    GenericRow data;
    try {
      data = getGenericRow(bytes);
    } catch (Exception e) {
      throw new SerializationException(e);
    }

    return data;
  }

  private GenericRow getGenericRow(byte[] rowJSONBytes) throws IOException {
    JsonNode jsonNode = objectMapper.readTree(rowJSONBytes);
    List columns = new ArrayList();
    for (Field field: schema.fields()) {
      String jsonFieldName = field.name().substring(field.name().indexOf(".")+1).toLowerCase();
      JsonNode fieldJsonNode = jsonNode.get(jsonFieldName);
      if (fieldJsonNode == null) {
        columns.add(null);
      } else {
        columns.add(enforceFieldType(field.schema(), fieldJsonNode));
      }

    }
    return new GenericRow(columns);
  }

  private Object enforceFieldType(Schema fieldSchema, JsonNode fieldJsonNode) {

    switch (fieldSchema.type()) {
      case BOOLEAN:
        return fieldJsonNode.asBoolean();
      case INT32:
        return fieldJsonNode.asInt();
      case INT64:
        return fieldJsonNode.asLong();
      case FLOAT64:
        return fieldJsonNode.asDouble();
      case STRING:
        return fieldJsonNode.asText();
      case ARRAY:
        ArrayNode arrayNode = (ArrayNode) fieldJsonNode;
        Class elementClass = SchemaUtil.getJavaType(fieldSchema.valueSchema());
        Object[] arrayField =
            (Object[]) java.lang.reflect.Array.newInstance(elementClass, arrayNode.size());
        for (int i = 0; i < arrayNode.size(); i++) {
          arrayField[i] = enforceFieldType(fieldSchema.valueSchema(), arrayNode.get(i));
        }
        return arrayField;
      case MAP:
        Map<String, Object> mapField = new HashMap<>();
        while (fieldJsonNode.fields().hasNext()) {
          Map.Entry<String, JsonNode> entry = fieldJsonNode.fields().next();
          mapField.put(entry.getKey(), enforceFieldType(fieldSchema.valueSchema(), entry.getValue()));
        }
        return mapField;
      default:
        throw new KQLException("Type is not supported: " + fieldSchema.type());

    }

  }

  @Override
  public void close() {

  }
}
