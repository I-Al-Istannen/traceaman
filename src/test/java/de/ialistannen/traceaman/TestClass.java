package de.ialistannen.traceaman;

public class TestClass {

  public static int foo() {
    int x = 20;
    int b = 10;
    if (x > 10) {
      int c = 42;
      System.out.println(x + "< + " + b + " = " + (x + b));
      x += 10;
      return x;
    } else {
      System.out.println(x + "> + " + b + " = " + (x + b));
    }
    return x + b;
  }

  public static void main(String[] args) {
    foo();
  }

}
