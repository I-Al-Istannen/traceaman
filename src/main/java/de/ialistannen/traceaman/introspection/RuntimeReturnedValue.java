package de.ialistannen.traceaman.introspection;

import java.util.List;

public class RuntimeReturnedValue extends RuntimeValue {

  private final List<Object> arguments;
  private final List<String> stacktrace;

  RuntimeReturnedValue(
      Kind kind,
      String name,
      Class<?> type,
      String valueAsString,
      List<RuntimeValue> fields,
      List<Object> arrayElements,
      List<Object> parameters,
      List<String> stacktrace) {
    super(kind, name, type, valueAsString, fields, arrayElements);

    this.arguments = parameters;
    this.stacktrace = stacktrace;
  }

  public List<Object> getArguments() {
    return arguments;
  }

  public List<String> getStacktrace() {
    return stacktrace;
  }
}
