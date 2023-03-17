package de.ialistannen.traceaman;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class Consumer {

  public static void log(Map.Entry<String, Object>[] entries) {
    for (Entry<String, Object> entry : entries) {
      String name = entry.getKey();
      Object object = entry.getValue();
      List<Field> fields = new ArrayList<>();
      getAllFields(object.getClass(), fields);

      for (Field field : fields) {

      }
    }
  }

  private static void getAllFields(Class<?> clazz, List<Field> fields) {
    Collections.addAll(fields, clazz.getDeclaredFields());
    if (clazz.getSuperclass() != null) {
      getAllFields(clazz.getSuperclass(), fields);
    }
  }

}
