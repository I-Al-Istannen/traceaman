package de.ialistannen.traceaman.introspection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SahabOutput {

  private final List<LineSnapshot> breakpoints;
  private final List<RuntimeReturnedValue> returns;

  public SahabOutput() {
    this.breakpoints = Collections.synchronizedList(new ArrayList<>());
    this.returns = Collections.synchronizedList(new ArrayList<>());
  }

  public List<LineSnapshot> getBreakpoints() {
    return breakpoints;
  }

  public List<RuntimeReturnedValue> getReturns() {
    return returns;
  }

}
