package io.ascopes.katana.ap.codegen.init;

import com.squareup.javapoet.CodeBlock;
import io.ascopes.katana.ap.descriptors.Attribute;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;


/**
 * Base implementation for an initialization tracker.
 *
 * @author Ashley Scopes
 * @since 0.0.1
 */
abstract class AbstractInitTracker implements InitTracker {

  private final Map<Attribute, CodeBlock> attributes;
  private final CodeBlock allMask;

  AbstractInitTracker(SortedSet<Attribute> attributeSet) {
    Objects.requireNonNull(attributeSet);

    // Maintain the order of the input, just to keep results deterministic.
    this.attributes = new LinkedHashMap<>();

    CodeBlock one = this.cast(1);

    int offset = 0;
    for (Attribute attribute : attributeSet) {
      Objects.requireNonNull(attribute);
      CodeBlock bitflag = this.shl(one, this.cast(offset));
      this.attributes.put(attribute, bitflag);
    }

    this.allMask = this.shl(one, this.cast(offset));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final Optional<CodeBlock> getInitializedCheckFor(
      CodeBlock trackingVariable,
      Attribute attribute
  ) {
    Objects.requireNonNull(trackingVariable);
    Objects.requireNonNull(attribute);

    return this
        .getLiteralFor(attribute)
        .map(mask -> this.eq(mask, this.and(trackingVariable, mask)));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final Optional<CodeBlock> getUninitializedCheckFor(
      CodeBlock trackingVariable,
      Attribute attribute
  ) {
    Objects.requireNonNull(trackingVariable);
    Objects.requireNonNull(attribute);

    return this
        .getLiteralFor(attribute)
        .map(mask -> this.eq(trackingVariable, this.or(trackingVariable, mask)));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final CodeBlock getAnyUninitializedCheckFor(CodeBlock trackingVariable) {
    Objects.requireNonNull(trackingVariable);

    return this.eq(trackingVariable, this.or(trackingVariable, this.allMask));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final CodeBlock getTrackingVariableInitialValue() {
    return this.cast(0);
  }

  /**
   * Create a literal value for the desired tracking type from a given int.
   *
   * @param value the value to cast.
   * @return the resultant code block.
   */
  abstract CodeBlock cast(int value);

  /**
   * And {@code &} operator.
   * <p>
   * Returned values should always be surrounded by parenthesis.
   */
  abstract CodeBlock and(CodeBlock left, CodeBlock right);

  /**
   * Or {@code |} operator.
   * <p>
   * Returned values should always be surrounded by parenthesis.
   */
  abstract CodeBlock or(CodeBlock left, CodeBlock right);

  /**
   * Bitshift left {@code <<} operator.
   * <p>
   * Returned values should always be surrounded by parenthesis.
   */
  abstract CodeBlock shl(CodeBlock left, CodeBlock right);

  /**
   * Equality {@code ==} operator.
   * <p>
   * Returned values should always be surrounded by parenthesis.
   */
  abstract CodeBlock eq(CodeBlock left, CodeBlock right);

  private Optional<CodeBlock> getLiteralFor(Attribute attribute) {
    return Optional.ofNullable(this.attributes.get(attribute));
  }
}