package io.ascopes.katana.ap.utils;

import java.util.Objects;

/**
 * Helper methods for handlebars templates.
 *
 * @author Ashley Scopes
 * @since 0.0.1
 */
@SuppressWarnings("unused")
public abstract class HandlebarsHelpers {

  private HandlebarsHelpers() {
    throw new UnsupportedOperationException("static-only class");
  }

  /**
   * Quote the string representation of some object.
   *
   * @param value the object to quote.
   * @return the quoted representation.
   */
  public static String quoted(Object value) {
    String raw = Objects.toString(value);
    StringBuilder builder = new StringBuilder("\"");

    for (int i = 0; i < raw.length(); ++i) {
      char c = raw.charAt(i);

      switch (c) {
        case '\\':
          builder.append("\\\\");
          break;
        case '"':
          builder.append("\\\"");
          break;
        default:
          builder.append(c);
          break;
      }
    }

    return builder.append("\"").toString();
  }

  /**
   * Prefix a string with "an " if it starts with a vowel, or "a " otherwise.
   *
   * @param element the element to prefix.
   * @return the full string.
   */
  public static String a(Object element) {
    if (element == null) {
      return "null";
    }

    String name = Objects.toString(element);

    if (name.isEmpty()) {
      return "empty";
    }

    switch (Character.toLowerCase(name.charAt(0))) {
      case 'a':
      case 'e':
      case 'i':
      case 'o':
      case 'u':
        return "an " + name;
      default:
        return "a " + name;
    }
  }

  /**
   * Alias for {@link #a}
   */
  public static String an(Object element) {
    return a(element);
  }
}
