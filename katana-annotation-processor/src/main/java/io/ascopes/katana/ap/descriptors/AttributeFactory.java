package io.ascopes.katana.ap.descriptors;

import com.squareup.javapoet.TypeName;
import io.ascopes.katana.annotations.FieldVisibility;
import io.ascopes.katana.annotations.Visibility;
import io.ascopes.katana.ap.descriptors.Attribute.Builder;
import io.ascopes.katana.ap.settings.gen.SettingsCollection;
import io.ascopes.katana.ap.utils.AnnotationUtils;
import io.ascopes.katana.ap.utils.NamingUtils;
import io.ascopes.katana.ap.utils.Result;
import io.ascopes.katana.ap.utils.ResultCollector;
import java.util.Comparator;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * Factory for inspecting and generating attributes to apply to models.
 *
 * @author Ashley Scopes
 * @since 0.0.1
 */
public final class AttributeFactory {

  private final AttributeFeatureInclusionManager attributeFeatureInclusionManager;
  private final Elements elementUtils;

  public AttributeFactory(
      AttributeFeatureInclusionManager attributeFeatureInclusionManager,
      Elements elementUtils
  ) {
    this.attributeFeatureInclusionManager = attributeFeatureInclusionManager;
    this.elementUtils = elementUtils;
  }

  /**
   * Parse attribute specific metadata and return the results in a stream.
   *
   * @param classifiedMethods the classified methods to consider.
   * @param settings          the settings to consider.
   * @return the sorted set of successfully created attributes, sorted by name, or the error if any
   * failed to be parsed.
   */
  public Result<SortedSet<Attribute>> buildFor(
      ClassifiedMethods classifiedMethods,
      SettingsCollection settings
  ) {
    return classifiedMethods
        .getGetters()
        .keySet()
        .stream()
        .map(attr -> this.buildFor(attr, classifiedMethods, settings))
        .collect(ResultCollector.aggregating(
            Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(Attribute::getName)))
        ));
  }

  private Result<Attribute> buildFor(
      String attributeName,
      ClassifiedMethods classifiedMethods,
      SettingsCollection settings
  ) {
    // Expect this to always be present.
    ExecutableElement getter = Objects
        .requireNonNull(classifiedMethods.getGetters().get(attributeName));
    TypeName typeName = TypeName.get(getter.getReturnType());

    // Ensure we have a valid identifier.
    String identifierName = NamingUtils.transmogrifyIdentifier(attributeName);

    return Result
        .ok(Attribute
            .builder()
            .name(attributeName)
            .identifier(identifierName)
            .type(typeName)
            .getterToOverride(getter))
        .ifOkFlatMap(builder -> this.processFinal(builder, settings))
        .ifOkFlatMap(builder -> this.processTransience(builder, settings))
        .ifOkFlatMap(builder -> this.processFieldVisibility(builder, settings))
        .ifOkFlatMap(builder -> this.processSetter(builder, settings))
        .ifOkFlatMap(builder -> this.processToString(builder, settings))
        .ifOkFlatMap(builder -> this.processEqualsAndHashCode(builder, settings))
        .ifOkFlatMap(this::processAttributeLevelDeprecation)
        .ifOkMap(Builder::build);
  }

  private Result<Attribute.Builder> processFinal(
      Attribute.Builder builder,
      // TODO(ascopes): handle final
      @SuppressWarnings("unused") SettingsCollection settings
  ) {
    return Result.ok(false)
        .ifOkMap(builder::final_);
  }

  private Result<Attribute.Builder> processTransience(
      Attribute.Builder builder,
      SettingsCollection settings
  ) {
    return this.attributeFeatureInclusionManager
        .check(builder.getName(), settings.getFieldTransience(), builder.getGetterToOverride())
        .ifOkMap(builder::transient_);
  }

  private Result<Attribute.Builder> processFieldVisibility(
      Attribute.Builder builder,
      SettingsCollection settings
  ) {
    TypeElement visibilityAnnotationType = this.elementUtils
        .getTypeElement(FieldVisibility.class.getCanonicalName());

    Visibility visibility = AnnotationUtils
        .findAnnotationMirror(builder.getGetterToOverride(), visibilityAnnotationType)
        .ifOkFlatMap(mirror -> AnnotationUtils.getValue(mirror, "value"))
        .ifOkMap(AnnotationValue::getValue)
        .ifOkMap(Visibility.class::cast)
        .elseGet(() -> settings.getFieldVisibility().getValue());

    return Result
        .ok(builder.fieldVisibility(visibility));
  }

  private Result<Attribute.Builder> processSetter(
      Attribute.Builder builder,
      SettingsCollection settings
  ) {
    return this.attributeFeatureInclusionManager
        .check(builder.getName(), settings.getSetters(), builder.getGetterToOverride())
        .ifOkThen(builder::setterEnabled)
        // TODO(ascopes): allow overriding explicitly defined setters in the future
        .ifOkReplace(() -> Result.ok(builder));
  }

  private Result<Attribute.Builder> processToString(
      Attribute.Builder builder,
      SettingsCollection settings
  ) {
    return this.attributeFeatureInclusionManager
        .check(builder.getName(), settings.getToStringMethod(), builder.getGetterToOverride())
        .ifOkMap(builder::includeInToString);
  }

  private Result<Attribute.Builder> processEqualsAndHashCode(
      Attribute.Builder builder,
      SettingsCollection settings
  ) {
    return this.attributeFeatureInclusionManager
        .check(builder.getName(), settings.getEqualsAndHashCode(), builder.getGetterToOverride())
        .ifOkMap(builder::includeInEqualsAndHashCode);
  }

  private Result<Attribute.Builder> processAttributeLevelDeprecation(Attribute.Builder builder) {
    TypeElement deprecatedAnnotation = this.elementUtils
        .getTypeElement(Deprecated.class.getCanonicalName());

    return AnnotationUtils
        .findAnnotationMirror(builder.getGetterToOverride(), deprecatedAnnotation)
        .ifOkMap(builder::deprecatedAnnotation)
        .ifIgnoredReplace(builder);
  }
}
