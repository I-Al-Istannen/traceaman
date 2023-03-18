package de.ialistannen.traceaman.util;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Moshi.Builder;
import com.squareup.moshi.ToJson;

public class Json {

  public static Moshi createMoshi() {
    return new Builder()
        .add(new Object() {
          @FromJson
          public Class<?> fromJson(String name) {
            try {
              return Class.forName(name);
            } catch (ClassNotFoundException e) {
              throw new RuntimeException(e);
            }
          }

          @ToJson
          public String toJson(Class<?> value) {
            return Classes.className(value);
          }
        })
        .build();
  }

}
