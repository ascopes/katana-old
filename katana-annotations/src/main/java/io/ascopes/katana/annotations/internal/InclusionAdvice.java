package io.ascopes.katana.annotations.internal;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Internal annotation to advise that a feature can be included per attribute by a given
 * annotation value.
 *
 * @author Ashley Scopes
 * @since 0.0.1
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface InclusionAdvice {
  /**
   * @return the annotation to use to include the feature on an attribute.
   */
  Class<? extends Annotation> value();
}