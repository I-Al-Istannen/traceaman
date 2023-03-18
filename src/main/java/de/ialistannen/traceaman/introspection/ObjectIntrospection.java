package de.ialistannen.traceaman.introspection;

import static de.ialistannen.traceaman.util.Classes.isBasicallyPrimitive;

import de.ialistannen.traceaman.introspection.ObjectGraph.ObjectNode;
import de.ialistannen.traceaman.introspection.RuntimeValue.Kind;
import de.ialistannen.traceaman.util.LocalVariable;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class ObjectIntrospection {

  public static final int MAX_DEPTH = 5;
  private final ObjectGraph objectGraph;

  public ObjectIntrospection() {
    this.objectGraph = new ObjectGraph();
  }

  public RuntimeValue introspect(LocalVariable variable) throws IllegalAccessException {
    return introspect(
        Kind.LOCAL_VARIABLE,
        variable.getName(),
        variable.getType(),
        variable.getValue(),
        0
    );
  }

  public List<RuntimeValue> introspectReceiver(Object receiver) throws IllegalAccessException {
    return introspectFields(receiver, 0);
  }

  public RuntimeReturnedValue introspectReturnValue(
      String methodName, Object returned, List<Object> parameters, List<String> stacktrace
  ) throws IllegalAccessException {
    List<RuntimeValue> fields = introspectFields(returned, 0);
    List<Object> arrayValues = introspectArrayValues(returned, 0);

    return new RuntimeReturnedValue(
        Kind.RETURN, methodName, returned.getClass(), returned.toString(),
        fields, arrayValues, parameters, stacktrace
    );
  }

  private RuntimeValue introspect(
      Kind kind,
      String name,
      Class<?> type,
      Object object,
      int depth
  ) throws IllegalAccessException {
    List<RuntimeValue> fields = introspectFields(object, depth);
    List<Object> arrayElements = introspectArrayValues(object, depth);

    return new RuntimeValue(
        kind, name, type, object.toString(), fields, arrayElements
    );
  }

  private List<RuntimeValue> introspectFields(
      Object object, int depth
  ) throws IllegalAccessException {
    if (depth > MAX_DEPTH || isBasicallyPrimitive(object.getClass())) {
      return List.of();
    }

    ObjectNode node = objectGraph.getNode(object.getClass());
    List<RuntimeValue> fields = new ArrayList<>();

    for (Field field : node.getFields()) {
      field.setAccessible(true);
      fields.add(
          introspect(Kind.FIELD, field.getName(), field.getType(), field.get(object), depth + 1)
      );
    }

    return fields;
  }

  private List<Object> introspectArrayValues(
      Object array, int depth
  ) throws IllegalAccessException {
    if (!array.getClass().isArray()) {
      return List.of();
    }

    List<Object> arrayElements = new ArrayList<>();
    Class<?> componentType = array.getClass().getComponentType();

    for (int i = 0; i < Array.getLength(array); i++) {
      if (isBasicallyPrimitive(componentType)) {
        arrayElements.add(Array.get(array, i));
      } else {
        arrayElements.add(introspect(
            Kind.ARRAY_ELEMENT, null, componentType, Array.get(array, i), depth + 1
        ));
      }
    }

    return arrayElements;
  }

}
