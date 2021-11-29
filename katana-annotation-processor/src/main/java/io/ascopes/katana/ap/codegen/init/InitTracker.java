package io.ascopes.katana.ap.codegen.init;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import io.ascopes.katana.ap.descriptors.Attribute;
import java.util.Optional;
import org.checkerframework.checker.optional.qual.MaybePresent;

/**
 * Initialization tracker base interface. This defines the contract for a type that consumes a
 * collection of required attributes (e.g. on a builder) and provides the ability to track whether
 * attributes are explicitly initialized or not using some internal implementation detail. The
 * expressions to define and manipulate these checks are provided by the methods on this interface.
 *
 * @author Ashley Scopes
 * @since 0.0.1
 */
public interface InitTracker {

  /**
   * Get the expression to determine if an attribute is initialised. An empty optional if the
   * attribute is not tracked.
   *
   * @param trackingVariable the tracking variable.
   * @param attribute        the attribute to check if uninitialised.
   * @return the expression, or an empty optional if the attribute is not tracked.
   */
  @MaybePresent
  Optional<CodeBlock> getInitializedCheckFor(CodeBlock trackingVariable, Attribute attribute);

  /**
   * Get the expression to determine if an attribute is uninitialised. An empty optional if the
   * attribute is not tracked.
   *
   * @param trackingVariable the tracking variable.
   * @param attribute        the attribute to check if uninitialised.
   * @return the expression, or an empty optional if the attribute is not tracked.
   */
  @MaybePresent
  Optional<CodeBlock> getUninitializedCheckFor(CodeBlock trackingVariable, Attribute attribute);

  /**
   * Get the expression to determine if any required attributes are not assigned.
   *
   * @param trackingVariable the tracking variable to consider.
   * @return the expression.
   */
  CodeBlock getAnyUninitializedCheckFor(CodeBlock trackingVariable);

  /**
   * Get the initial value to assign to a tracking variable.
   *
   * @return the expression.
   */
  CodeBlock getTrackingVariableInitialValue();

  /**
   * Get the type name of the tracking variable.
   *
   * @return the type name.
   */
  TypeName getTypeName();

}
