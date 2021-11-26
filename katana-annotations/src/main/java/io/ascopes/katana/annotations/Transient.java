package io.ascopes.katana.annotations;

import io.ascopes.katana.annotations.internal.AttributeFeature;
import io.ascopes.katana.annotations.internal.ExclusionAdvice;
import io.ascopes.katana.annotations.internal.InclusionAdvice;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Transient field marking policy. This determines whether internal fields are marked as {@code
 * transient} or not.
 *
 * @author Ashley Scopes
 * @since 0.0.1
 */
@ExclusionAdvice(Transient.Exclude.class)
@InclusionAdvice(Transient.Include.class)
@SuppressWarnings("unused")
public enum Transient implements AttributeFeature {
  /**
   * Make attributes transient unless marked with {@link Transient.Exclude}.
   */
  INCLUDE_ALL {
    @Override
    public boolean isIncludeAll() {
      return true;
    }
  },

  /**
   * Do not make attributes transient unless marked with {@link Transient.Include}.
   */
  EXCLUDE_ALL {
    @Override
    public boolean isExcludeAll() {
      return true;
    }
  };

  /**
   * Annotation to explicitly enable checking for an attribute.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(ElementType.METHOD)
  public @interface Include {

  }

  /**
   * Annotation to explicitly disable checking for an attribute.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(ElementType.METHOD)
  public @interface Exclude {

  }
}