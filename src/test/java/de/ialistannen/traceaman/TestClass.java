package de.ialistannen.traceaman;

import java.util.List;

public class TestClass {

  public static int foo(int input1, int input2) {
    int x = 20;
    int b = 10;
    if (x > 10) {
      int c = 42;
      Inner inner = new Inner(42, "this is nice");
      List<String> hello = List.of("hello");
      System.out.println(x + "< + " + b + " = " + (x + b));
      x += 10;
    } else {
      System.out.println(x + "> + " + b + " = " + (x + b));
    }
    return x + b + input1 + input2;
  }

  public static void main(String[] args) {
    foo(21, 42);
  }

  public static class Inner {

    private final int foo;
    private String bar;
    private final int[] intArray;

    public Inner(int foo, String bar) {
      this.foo = foo;
      this.bar = bar;
      this.intArray = new int[]{1, 2, 3, 4, 5};
    }
  }

}
