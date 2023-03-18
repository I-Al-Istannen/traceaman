package de.ialistannen.traceaman;

public class TestClass {

  public static int foo() {
    int x = 20;
    int b = 10;
    if (x > 10) {
      int c = 42;
      Inner inner = new Inner(42, "this is nice");
      System.out.println(x + "< + " + b + " = " + (x + b));
      x += 10;
    } else {
      System.out.println(x + "> + " + b + " = " + (x + b));
    }
    return x + b;
  }

  public static void main(String[] args) {
    foo();
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
