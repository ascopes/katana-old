package io.ascopes.katana.ap.codegen;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import io.ascopes.katana.ap.codegen.components.BuilderFactory;
import io.ascopes.katana.ap.codegen.components.ConstructorFactory;
import io.ascopes.katana.ap.codegen.components.FieldFactory;
import io.ascopes.katana.ap.codegen.components.GetterFactory;
import io.ascopes.katana.ap.codegen.components.SetterFactory;
import io.ascopes.katana.ap.descriptors.Attribute;
import io.ascopes.katana.ap.descriptors.Model;
import io.ascopes.katana.ap.settings.gen.SettingsCollection;
import io.ascopes.katana.ap.utils.CodeGenUtils;
import io.ascopes.katana.ap.utils.Logger;
import javax.lang.model.element.Modifier;

/**
 * Factory for generating Java classes from descriptor definitions.
 *
 * @author Ashley Scopes
 * @since 0.0.1
 */
public final class JavaModelFactory {
  private final Logger logger;
  private final FieldFactory fieldFactory;
  private final GetterFactory getterFactory;
  private final SetterFactory setterFactory;
  private final ConstructorFactory constructorFactory;
  private final BuilderFactory builderFactory;

  public JavaModelFactory() {
    this.logger = new Logger();
    this.fieldFactory = new FieldFactory();
    this.getterFactory = new GetterFactory();
    this.setterFactory = new SetterFactory();
    this.constructorFactory = new ConstructorFactory();
    this.builderFactory = new BuilderFactory();
  }

  public JavaFile create(Model model) {
    this.logger.debug("Building Java file for {}", model);

    TypeSpec typeSpec = this.buildModelTypeSpecFrom(model);
    return this.wrapTypeSpecInPackage(typeSpec, model);
  }

  private JavaFile wrapTypeSpecInPackage(TypeSpec typeSpec, Model model) {
    return JavaFile
        .builder(model.getPackageName(), typeSpec)
        .skipJavaLangImports(true)
        .indent(model.getSettingsCollection().getIndent().getValue())
        .build();
  }

  private TypeSpec buildModelTypeSpecFrom(Model model) {
    TypeSpec.Builder builder = TypeSpec
        .classBuilder(model.getClassName())
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addSuperinterface(model.getSuperInterface().asType())
        .addAnnotation(CodeGenUtils.generated(model.getSuperInterface()));

    model.getDeprecatedAnnotation()
        .map(CodeGenUtils::copyDeprecatedFrom)
        .ifPresent(builder::addAnnotation);

    this.applyAttributes(builder, model);
    this.applyConstructors(builder, model);
    this.applyBuilders(builder, model);

    return builder.build();
  }

  private void applyAttributes(TypeSpec.Builder typeSpecBuilder, Model model) {
    SettingsCollection settings = model.getSettingsCollection();

    for (Attribute attribute : model.getAttributes()) {
      typeSpecBuilder
          .addField(this.fieldFactory.create(attribute, settings))
          .addMethod(this.getterFactory.create(attribute, settings));

      if (model.isMutable()) {
        typeSpecBuilder.addMethod(this.setterFactory.create(attribute, settings));
      }
    }
  }

  private void applyConstructors(TypeSpec.Builder typeSpecBuilder, Model model) {
    typeSpecBuilder.addMethods(this.constructorFactory.create(model));
  }

  private void applyBuilders(TypeSpec.Builder typeSpecBuilder, Model model) {
    model.getBuilderStrategy()
        .map(strategy -> this.builderFactory.create(model, strategy))
        .ifPresent(components -> {
          typeSpecBuilder.addMethod(components.getBuilderConstructor());
          typeSpecBuilder.addMethod(components.getBuilderInitializer());
          typeSpecBuilder.addType(components.getBuilderType());
          components.getToBuilderMethod().ifPresent(typeSpecBuilder::addMethod);
        });
  }
}
