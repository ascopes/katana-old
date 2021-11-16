package io.ascopes.katana.ap.settings;

import io.ascopes.katana.annotations.ImmutableModel;
import io.ascopes.katana.annotations.MutableModel;
import io.ascopes.katana.annotations.Settings;
import io.ascopes.katana.ap.iterators.PackageIterator;
import io.ascopes.katana.ap.iterators.SupertypeIterator;
import io.ascopes.katana.ap.settings.gen.SettingsCollection;
import io.ascopes.katana.ap.settings.gen.SettingsSchemas;
import io.ascopes.katana.ap.utils.AnnotationUtils;
import io.ascopes.katana.ap.utils.Functors;
import io.ascopes.katana.ap.utils.Result;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Resolver for processor settings. This contains a load of reflection evil to simplify the
 * specification for new settings. The internal parts of API is fragile so be very careful with it.
 *
 * @author Ashley Scopes
 * @since 0.0.1
 */
public final class SettingsResolver {

  private final Elements elementUtils;
  private final Types typeUtils;

  /**
   * @param elementUtils the element utilities to use.
   * @param typeUtils    the type utilities to use.
   */
  public SettingsResolver(Elements elementUtils, Types typeUtils) {
    this.elementUtils = elementUtils;
    this.typeUtils = typeUtils;
  }

  /**
   * Parse all inherited settings for the given interface element and return a collection of the
   * actual resolved settings to use, taking inheritance into account.
   *
   * @param interfaceElement the interface to compute the settings for.
   * @param annotationMirror the model annotation that contains a {@code settings} member.
   * @param mutable          true if the default mutable settings should be considered, or false if
   *                         the default immutable settings should be considered.
   * @return the computed settings, or an empty optional if an error occurred and was reported.
   */
  public Result<SettingsCollection> parseSettings(
      TypeElement interfaceElement,
      AnnotationMirror annotationMirror,
      boolean mutable
  ) {
    List<SettingsAnnotation> allEntries = this.allEntriesFor(interfaceElement, annotationMirror,
        mutable
    );

    // Generated by src/gen/groovy/GenerateSettingsBoilerplate.groovy based on the attributes in
    // the @Settings annotation.
    SettingsCollection.Builder builder = SettingsCollection.builder();

    // Generated by src/gen/groovy/GenerateSettingsBoilerplate.groovy based on the attributes in
    // the @Settings annotation.
    SettingsSchemas
        .schemas()
        .map(schema -> this.determineSettingFor(schema, mutable, allEntries))
        .forEach(setting -> setting.getSettingSchema().getBuilderSetter().accept(builder, setting));

    return Result.ok(builder.build());
  }

  private List<SettingsAnnotation> allEntriesFor(
      TypeElement interfaceElement,
      AnnotationMirror annotationMirror,
      boolean mutable
  ) {
    Stream<SettingsAnnotation> modelAnnotationEntries = this
        .findSettingsOnAnnotation(interfaceElement, annotationMirror)
        .map(Stream::of)
        .orElseGet(Stream::empty);

    Stream<SettingsAnnotation> interfaceEntries =
        new SupertypeIterator(this.typeUtils, interfaceElement)
            .stream()
            .map(this::findSettingsOnTypeElement)
            .flatMap(Functors.removeEmpties());

    Stream<SettingsAnnotation> packageEntries =
        new PackageIterator(this.elementUtils, interfaceElement)
            .stream()
            .flatMap(packageElement -> this.findSettingsOnPackage(packageElement, mutable));

    return Stream
        .of(modelAnnotationEntries, interfaceEntries, packageEntries)
        .flatMap(Functors.flattenStream())
        .collect(Collectors.toList());
  }

  private <T> Setting<T> determineSettingFor(
      SettingSchema<T> schema,
      boolean mutable,
      List<SettingsAnnotation> annotations
  ) {
    EqualityFunction<T> equalityFunction = schema.getEqualityCheck();
    T inheritedValue = schema.getInheritedValue();

    for (SettingsAnnotation annotation : annotations) {
      Optional<? extends AnnotationValue> possibleValue = AnnotationUtils
          .getValue(annotation.mirror, schema.getName());

      if (!possibleValue.isPresent()) {
        // Ignore attributes that haven't been explicitly defined by the user.
        continue;
      }

      T actualValue = possibleValue
          .map(AnnotationValue::getValue)
          .map(schema.getType()::cast)
          .orElse(inheritedValue);

      if (equalityFunction.test(inheritedValue, actualValue)) {
        // Ignore inherited default values explicitly defined by the user.
        continue;
      }

      String description = "setting '" + schema.getName() + "' with value '" + actualValue + "'"
          + ", from " + annotation.description;

      // We found the setting, so return it.
      SettingValueHolder<T> valueHolder = new SettingValueHolder<>(
          actualValue,
          annotation.declaringElement,
          annotation.mirror,
          possibleValue.get()
      );

      return new Setting<>(
          valueHolder,
          description,
          schema
      );
    }

    T defaultValue = mutable
        ? schema.getMutableDefaultValue()
        : schema.getImmutableDefaultValue();

    String description = "default Katana setting '" + schema.getName() + "'";

    return new Setting<>(new SettingValueHolder<>(defaultValue), description, schema);
  }

  private Optional<SettingsAnnotation> findSettingsOnAnnotation(Element declaringElement,
      AnnotationMirror annotationMirror) {
    return annotationMirror
        .getElementValues()
        .entrySet()
        .stream()
        .filter(pair -> pair.getKey().getSimpleName().contentEquals("value"))
        .findAny()
        .map(Entry::getValue)
        .map(AnnotationValue::getValue)
        .map(AnnotationMirror.class::cast)
        .map(settings ->
            new SettingsAnnotation(
                "annotation applied to your usage of @" + annotationMirror
                    .getAnnotationType()
                    .asElement()
                    .getSimpleName(),
                declaringElement,
                settings
            ));
  }

  private Optional<SettingsAnnotation> findSettingsOnTypeElement(TypeElement typeElement) {
    return this
        .findSettingsOn(
            typeElement,
            "annotation on class '" + typeElement.getQualifiedName() + "'"
        );
  }

  private Stream<SettingsAnnotation> findSettingsOnPackage(
      PackageElement packageElement,
      boolean mutable
  ) {
    Optional<SettingsAnnotation> allModelsSettings = this.findSettingsOn(
        packageElement,
        "annotation on package '" + packageElement.getQualifiedName() + "'"
    );

    String mutabilityAnnotationName = mutable
        ? MutableModel.class.getCanonicalName()
        : ImmutableModel.class.getCanonicalName();

    TypeElement mutabilityAnnotation = this.elementUtils.getTypeElement(mutabilityAnnotationName);

    // Find a usage of @MutableModel(@Settings(...)) or @ImmutableModel(@Settings(...)) on the
    // package level.
    Optional<SettingsAnnotation> mutabilitySpecificSettings = AnnotationUtils
        .findAnnotationMirror(packageElement, mutabilityAnnotation)
        .ifOkMap(mirror -> this.findSettingsOnAnnotation(packageElement, mirror))
        .elseGet(Optional::empty);

    return Stream
        .of(allModelsSettings, mutabilitySpecificSettings)
        .flatMap(Functors.removeEmpties());
  }

  private Optional<SettingsAnnotation> findSettingsOn(
      Element annotatedElement,
      String qualifiedName
  ) {
    return annotatedElement
        .getAnnotationMirrors()
        .stream()
        .filter(this::isSettingsAnnotationMirror)
        .findAny()
        .map(settings -> new SettingsAnnotation(qualifiedName, annotatedElement, settings));
  }

  private boolean isSettingsAnnotationMirror(AnnotationMirror mirror) {
    return mirror
        .getAnnotationType()
        .asElement()
        .getSimpleName()
        .contentEquals(Settings.class.getSimpleName());
  }

  /*
   * A single set of settings, from a specific location. This is just used as an intermediary
   * value while reading in settings.
   */
  private static final class SettingsAnnotation {

    private final String description;
    private final Element declaringElement;
    private final AnnotationMirror mirror;

    public SettingsAnnotation(
        String description,
        Element declaringElement,
        AnnotationMirror mirror
    ) {
      this.description = Objects.requireNonNull(description);
      this.declaringElement = Objects.requireNonNull(declaringElement);
      this.mirror = Objects.requireNonNull(mirror);
    }
  }
}
