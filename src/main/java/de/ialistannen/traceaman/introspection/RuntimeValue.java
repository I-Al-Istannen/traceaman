package de.ialistannen.traceaman.introspection;

import java.util.List;

public class RuntimeValue {

  private final Kind kind;
  private final String name;
  private final Class<?> type;
  private final String valueAsString;
  private final List<RuntimeValue> fields;
  private final List<Object> arrayElements;

  RuntimeValue(
      Kind kind,
      String name,
      Class<?> type,
      String valueAsString,
      List<RuntimeValue> fields,
      List<Object> arrayElements
  ) {
    this.kind = kind;
    this.name = name;
    this.type = type;
    this.valueAsString = valueAsString;
    this.fields = fields;
    this.arrayElements = arrayElements;
  }

  public Kind getKind() {
    return kind;
  }

  public Object getValueAsString() {
    return valueAsString;
  }

  public String getName() {
    return name;
  }

  public Class<?> getType() {
    return type;
  }

  public List<RuntimeValue> getFields() {
    return fields;
  }

  public List<Object> getArrayElements() {
    return arrayElements;
  }

  public enum Kind {
    FIELD,
    LOCAL_VARIABLE,
    RETURN,
    ARRAY_ELEMENT,
  }
}
