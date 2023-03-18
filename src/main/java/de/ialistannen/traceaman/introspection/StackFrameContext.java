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

  public static StackFrameContext capture(int skipCount, List<RuntimeValue> runtimeValues) {
    List<String> stacktrace = StackWalker.getInstance()
        .walk(frames ->
            frames.skip(skipCount + 1)
                .map(StackFrameContext::stackFrameToString)
                .collect(Collectors.toList())
        );
    return new StackFrameContext(stacktrace, runtimeValues);
  }

  private static String stackFrameToString(StackFrame frame) {
    return frame.getMethodName() + ":" + frame.getLineNumber() + ", " + frame.getClassName();
  }
}
