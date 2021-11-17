package io.ascopes.katana.ap.descriptors;

import io.ascopes.katana.annotations.MutableModel;
import io.ascopes.katana.ap.settings.Setting;
import io.ascopes.katana.ap.settings.SettingsResolver;
import io.ascopes.katana.ap.utils.AnnotationUtils;
import io.ascopes.katana.ap.utils.NamingUtils;
import io.ascopes.katana.ap.utils.Result;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic.Kind;

/**
 * Mapper to read in the AST data from the compiler and produce meaningful information for other
 * components to follow to build the models later.
 *
 * @author Ashley Scopes
 * @since 0.0.1
 */
public final class ModelFactory {

  private final SettingsResolver settingsResolver;
  private final MethodClassifier methodClassifier;
  private final AttributeFactory attributeFactory;
  private final Elements elementUtils;
  private final Messager messager;

  /**
   * @param settingsResolver the settings resolver to use.
   * @param methodClassifier the method classifier to use.
   * @param attributeFactory the attribute factory to use.
   * @param messager         the messager to use to report errors.
   * @param elementUtils     the element utilities to use.
   */
  public ModelFactory(
      SettingsResolver settingsResolver,
      MethodClassifier methodClassifier,
      AttributeFactory attributeFactory,
      Messager messager,
      Elements elementUtils
  ) {
    this.settingsResolver = settingsResolver;
    this.methodClassifier = methodClassifier;
    this.attributeFactory = attributeFactory;
    this.elementUtils = elementUtils;
    this.messager = messager;
  }

  /**
   * Build a descriptor for an immutable model.
   *
   * @param modelAnnotation  the model annotation (ImmutableModel or MutableModel).
   * @param annotatedElement the annotated interface.
   * @return an optional containing the model if successful, or an empty optional if something
   * failed and an error was reported to the compiler.
   */
  public Result<Model> buildFor(
      TypeElement modelAnnotation,
      TypeElement annotatedElement
  ) {
    return AnnotationUtils
        .findAnnotationMirror(annotatedElement, modelAnnotation)
        .ifOkFlatMap(mirror -> this.buildFor(annotatedElement, modelAnnotation, mirror));
  }

  private Result<Model> buildFor(
      TypeElement modelInterface,
      TypeElement modelAnnotation,
      AnnotationMirror annotationMirror
  ) {
    boolean mutable = this.isMutable(modelAnnotation);

    Model.Builder builder = Model
        .builder()
        .modelInterface(modelInterface)
        .annotationMirror(annotationMirror)
        .mutable(mutable);

    return this.parseSettings(builder)
        .ifOkFlatMap(this::determinePackageName)
        .ifOkFlatMap(this::determineClassName)
        .ifOkFlatMap(this::classifyMethods)
        .ifOkFlatMap(this::generateAttributes)
        .ifOkMap(Model.Builder::build);
  }

  private boolean isMutable(TypeElement modelAnnotation) {
    return modelAnnotation
        .getSimpleName()
        .contentEquals(MutableModel.class.getSimpleName());
  }

  private Result<Model.Builder> parseSettings(Model.Builder builder) {
    return this.settingsResolver
        .parseSettings(
            builder.getModelInterface(),
            builder.getAnnotationMirror(),
            builder.isMutable()
        )
        .ifOkMap(builder::settingsCollection);
  }

  private Result<Model.Builder> determinePackageName(Model.Builder builder) {
    Setting<String> packageNameSetting = builder
        .getSettingsCollection()
        .getPackageName();

    String packageName = packageNameSetting.getValue()
        .replace("*", this.elementUtils.getPackageOf(builder.getModelInterface()).toString());

    try {
      NamingUtils.validatePackageName(packageName);
    } catch (IllegalArgumentException ex) {
      this.messager.printMessage(
          Kind.ERROR,
          ex.getMessage() + " (package name was determined from "
              + packageNameSetting.getDescription() + ")",
          packageNameSetting.getDeclaringElement().orElseGet(builder::getModelInterface),
          packageNameSetting.getAnnotationMirror().orElseGet(builder::getAnnotationMirror),
          packageNameSetting.getAnnotationValue().orElse(null)
      );
      return Result.fail();
    }

    return Result
        .ok(packageName)
        .ifOkMap(builder::packageName);
  }

  private Result<Model.Builder> determineClassName(Model.Builder builder) {
    Setting<String> classNameSetting = builder
        .getSettingsCollection()
        .getClassName();

    String className = classNameSetting.getValue()
        .replace("*", builder.getModelInterface().getSimpleName().toString());

    try {
      NamingUtils.validateClassName(className);
    } catch (IllegalArgumentException ex) {
      this.messager.printMessage(
          Kind.ERROR,
          ex.getMessage() + " (class name was determined from "
              + classNameSetting.getDescription() + ")",
          classNameSetting.getDeclaringElement().orElseGet(builder::getModelInterface),
          classNameSetting.getAnnotationMirror().orElseGet(builder::getAnnotationMirror),
          classNameSetting.getAnnotationValue().orElse(null)
      );
      return Result.fail();
    }

    return Result
        .ok(className)
        .ifOkMap(builder::className);
  }

  private Result<Model.Builder> classifyMethods(Model.Builder builder) {
    return this.methodClassifier
        .classifyMethods(builder.getModelInterface(), builder.getSettingsCollection())
        .ifOkMap(builder::methods);
  }

  private Result<Model.Builder> generateAttributes(Model.Builder builder) {
    return this.attributeFactory
        .buildFor(builder.getMethods(), builder.getSettingsCollection())
        .ifOkMap(builder::attributes);
  }
}
