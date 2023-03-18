package de.ialistannen.traceaman.introspection;

import java.lang.StackWalker.StackFrame;
import java.util.List;
import java.util.stream.Collectors;

public class StackFrameContext {

  private final int positionFromTopInStackTrace;
  private final String location;
  private final List<String> stackTrace;
  private final List<RuntimeValue> runtimeValueCollection;

  private StackFrameContext(List<String> stackTrace, List<RuntimeValue> runtimeValueCollection) {
    this.positionFromTopInStackTrace = 1;
    this.location = stackTrace.get(0);
    this.stackTrace = stackTrace;
    this.runtimeValueCollection = runtimeValueCollection;
  }

  public static StackFrameContext forValues(List<RuntimeValue> runtimeValues) {
    return new StackFrameContext(getStacktrace(), runtimeValues);
  }

  public static List<String> getStacktrace() {
    return StackWalker.getInstance()
        .walk(frames ->
            frames.dropWhile(StackFrameContext::isOurCode)
                .map(StackFrameContext::stackFrameToString)
                .collect(Collectors.toList())
        );
  }

  private static String stackFrameToString(StackFrame frame) {
    return frame.getMethodName() + ":" + frame.getLineNumber() + ", " + frame.getClassName();
  }

  private static boolean isOurCode(StackFrame frame) {
    if (!frame.getClassName().startsWith("de.ialistannen.traceaman")) {
      return false;
    }
    return !frame.getClassName().endsWith("TestClass");
  }
}
