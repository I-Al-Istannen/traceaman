package de.ialistannen.traceaman;

import com.squareup.moshi.Moshi;
import de.ialistannen.traceaman.introspection.LineSnapshot;
import de.ialistannen.traceaman.introspection.ObjectIntrospection;
import de.ialistannen.traceaman.introspection.RuntimeValue;
import de.ialistannen.traceaman.introspection.StackFrameContext;
import de.ialistannen.traceaman.util.Json;
import de.ialistannen.traceaman.util.LocalVariable;
import java.util.ArrayList;
import java.util.List;

public class ContextCollector {

  /**
   * logImpl and log
   */
  private static final int SKIP_FRAMES_COUNT = 2;

  private static final Moshi MOSHI = Json.createMoshi();
  private static final ObjectIntrospection INTROSPECTOR = new ObjectIntrospection();

  public static void log(
      String className, int lineNumber, Object receiver, LocalVariable[] localVariables
  ) {
    try {
      logImpl(className, lineNumber, receiver, localVariables);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static void logImpl(
      String className, int lineNumber, Object receiver, LocalVariable[] localVariables
  ) throws ReflectiveOperationException {
    List<RuntimeValue> values = new ArrayList<>();
    for (LocalVariable variable : localVariables) {
      values.add(INTROSPECTOR.introspect(variable));
    }

    if (receiver != null) {
      values.addAll(INTROSPECTOR.introspectReceiver(receiver));
    }

    StackFrameContext stackFrameContext = StackFrameContext.capture(SKIP_FRAMES_COUNT, values);
    LineSnapshot lineSnapshot = new LineSnapshot(className, lineNumber, List.of(stackFrameContext));

    System.out.println(
        MOSHI.adapter(LineSnapshot.class).indent("  ").lenient().toJson(lineSnapshot)
    );
  }

}
