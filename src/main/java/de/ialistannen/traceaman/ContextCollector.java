package de.ialistannen.traceaman;

import com.squareup.moshi.Moshi;
import de.ialistannen.traceaman.introspection.LineSnapshot;
import de.ialistannen.traceaman.introspection.ObjectIntrospection;
import de.ialistannen.traceaman.introspection.RuntimeReturnedValue;
import de.ialistannen.traceaman.introspection.RuntimeValue;
import de.ialistannen.traceaman.introspection.StackFrameContext;
import de.ialistannen.traceaman.util.Json;
import de.ialistannen.traceaman.util.LocalVariable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import okio.Okio;

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

    try {
      Files.writeString(
          Path.of("/home/i_al_istannen/.temp/log.txt"),
          MOSHI.adapter(LineSnapshot.class).lenient().toJson(lineSnapshot) + "\n",
          StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND
      );
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void logReturn(
      Object returnValue, String className
  ) {
    try {
      RuntimeReturnedValue returned = INTROSPECTOR.introspectReturnValue(
          className, returnValue, List.of(), StackFrameContext.getStacktrace()
      );

      Files.writeString(
          Path.of("/home/i_al_istannen/.temp/log.txt"),
          MOSHI.adapter(RuntimeReturnedValue.class).lenient().toJson(returned) + "\n",
          StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND
      );
    } catch (ReflectiveOperationException | IOException e) {
      throw new RuntimeException(e);
    }
  }

}
