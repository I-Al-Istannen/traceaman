package de.ialistannen.traceaman.util;

public class Classes {

  public static String className(Class<?> type) {
    String canonicalName = type.getCanonicalName();
    if (canonicalName != null) {
      // Ensure `$` is used as separator, even if the canonical name actually contains a `.`
      if (type.getDeclaringClass() != null) {
        return className(type.getDeclaringClass()) + "$" + type.getSimpleName();
      }
      // and fix the same thing for arrays if inner types...
      if (type.isArray()) {
        return className(type.getComponentType()) + "[]";
      }
      return canonicalName;
    }
    return type.getName();
  }


  public static boolean isBasicallyPrimitive(Class<?> clazz) {
    return clazz.isPrimitive() || clazz == String.class || isBoxed(clazz);
  }

  private static boolean isBoxed(Class<?> clazz) {
    return clazz == Byte.class || clazz == Short.class || clazz == Integer.class
           || clazz == Long.class
           || clazz == Float.class || clazz == Double.class
           || clazz == Boolean.class || clazz == Character.class;
  }

  public static Class<?> getClassFromString(String className) {
    return switch (className) {
      case "byte" -> byte.class;
      case "short" -> short.class;
      case "int" -> int.class;
      case "long" -> long.class;
      case "float" -> float.class;
      case "double" -> double.class;
      case "boolean" -> boolean.class;
      case "char" -> char.class;
      case "void" -> void.class;
      default -> {
        try {
          yield Class.forName(className);
        } catch (ClassNotFoundException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }
}
