package de.ialistannen.traceaman;

import com.squareup.moshi.Moshi;
import de.ialistannen.traceaman.introspection.LineSnapshot;
import de.ialistannen.traceaman.introspection.ObjectIntrospection;
import de.ialistannen.traceaman.introspection.RuntimeReturnedValue;
import de.ialistannen.traceaman.introspection.RuntimeValue;
import de.ialistannen.traceaman.introspection.StackFrameContext;
import de.ialistannen.traceaman.util.Json;
import de.ialistannen.traceaman.util.LocalVariable;
import java.util.ArrayList;
import java.util.List;

public class ContextCollector {

  private static final Moshi MOSHI = Json.createMoshi();
  private static final ObjectIntrospection INTROSPECTOR = new ObjectIntrospection();

  public static void logLine(
      String className, int lineNumber, Object receiver, LocalVariable[] localVariables
  ) {
    try {
      logLineImpl(className, lineNumber, receiver, localVariables);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static void logLineImpl(
      String className, int lineNumber, Object receiver, LocalVariable[] localVariables
  ) throws ReflectiveOperationException {
    List<RuntimeValue> values = new ArrayList<>();
    for (LocalVariable variable : localVariables) {
      values.add(INTROSPECTOR.introspect(variable));
    }

    if (receiver != null) {
      values.addAll(INTROSPECTOR.introspectReceiver(receiver));
    }

    StackFrameContext stackFrameContext = StackFrameContext.forValues(values);
    LineSnapshot lineSnapshot = new LineSnapshot(className, lineNumber, List.of(stackFrameContext));

    System.out.println(
        MOSHI.adapter(LineSnapshot.class).indent("  ").lenient().toJson(lineSnapshot)
    );
  }

  public static void logReturn(
      Object returnValue, String className
  ) {
    try {
      RuntimeReturnedValue returned = INTROSPECTOR.introspectReturnValue(
          className, returnValue, List.of(), StackFrameContext.getStacktrace()
      );

      System.out.println(
          MOSHI.adapter(RuntimeReturnedValue.class).indent("  ").lenient().toJson(returned)
      );
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

}
