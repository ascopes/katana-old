package io.ascopes.katana.ap.builders;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.MethodSpec.Builder;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import io.ascopes.katana.ap.attributes.AttributeDescriptor;
import io.ascopes.katana.ap.builders.Stages.DedicatedStage;
import io.ascopes.katana.ap.builders.Stages.FinalStage;
import io.ascopes.katana.ap.builders.Stages.Stage;
import io.ascopes.katana.ap.types.ModelDescriptor;
import io.ascopes.katana.ap.types.TypeSpecMembers.TypeSpecMembersBuilder;
import io.ascopes.katana.ap.utils.CodeGenUtils;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.lang.model.element.Modifier;

/**
 * Builder factory implementation that provides initialization checking at compile time through the
 * use of a staged builder.
 *
 * <p>This will define a custom interface for every mandatory attribute, forcing the builder type
 * to implement these interfaces as to force the user to call specific methods to be able to reach
 * the {@code .build} method at the end successfully. attributeDescriptor
 *
 * @author Ashley Scopes
 * @since 0.0.1
 */
public final class CompileCheckedBuilderFactory extends AbstractBuilderFactory<Stages> {

  @Override
  public TypeSpecMembersBuilder createMembersFor(
      ModelDescriptor model,
      BuilderStrategyDescriptor strategy,
      Stages context
  ) {
    TypeSpecMembersBuilder membersBuilder = super.createMembersFor(model, strategy, context);
    TypeSpec type = this.createFinalStageFor(model, strategy, context.getFinalStage()).build();
    membersBuilder.type(type);

    context
        .dedicatedStageIterator()
        .stream()
        .map(stage -> this.dedicatedStageFor(model, strategy, stage))
        .map(TypeSpec.Builder::build)
        .forEach(membersBuilder::type);

    return membersBuilder;
  }

  @Override
  Builder builderMethodFor(
      ModelDescriptor model,
      BuilderStrategyDescriptor strategy,
      Stages context
  ) {

    Stage firstStage = context
        .dedicatedStageIterator()
        .stream()
        .findFirst()
        .map(Stage.class::cast)
        .orElseGet(context::getFinalStage);

    ClassName firstStageTypeName = model.getQualifiedName().nestedClass(firstStage.getName());

    return super
        .builderMethodFor(model, strategy, context)
        .returns(firstStageTypeName);
  }

  @Override
  TypeSpec.Builder builderTypeFor(
      ModelDescriptor model,
      BuilderStrategyDescriptor strategy,
      Stages context
  ) {
    return super
        .builderTypeFor(model, strategy, context)
        .addSuperinterfaces(context
            .dedicatedStageIterator()
            .stream()
            .map(DedicatedStage::getName)
            .map(model.getQualifiedName()::nestedClass)
            .collect(Collectors.toList()))
        .addSuperinterface(model.getQualifiedName().nestedClass(context.getFinalStage().getName()));
  }

  @Override
  MethodSpec.Builder builderSetterFor(
      ModelDescriptor model,
      AttributeDescriptor attribute,
      BuilderStrategyDescriptor strategy,
      Stages context
  ) {
    return super
        .builderSetterFor(model, attribute, strategy, context)
        .addAnnotation(CodeGenUtils.override());
  }

  @Override
  Builder builderBuildFor(
      ModelDescriptor model,
      BuilderStrategyDescriptor strategy,
      Stages context
  ) {
    return super.builderBuildFor(model, strategy, context)
        .addAnnotation(CodeGenUtils.override());
  }

  private TypeSpec.Builder dedicatedStageFor(
      ModelDescriptor model,
      BuilderStrategyDescriptor strategy,
      DedicatedStage dedicatedStage
  ) {
    AttributeDescriptor attributeDescriptor = dedicatedStage.getAttribute();
    Stage nextStage = dedicatedStage.getNextStage();

    ClassName thisStageTypeName = model.getQualifiedName().nestedClass(dedicatedStage.getName());
    TypeName nextStageTypeName = model.getQualifiedName().nestedClass(nextStage.getName());

    MethodSpec.Builder stageMethod = this
        .stageMethodStubFor(attributeDescriptor, nextStageTypeName);

    TypeSpec.Builder type = TypeSpec
        .interfaceBuilder(thisStageTypeName)
        .addModifiers(Modifier.PUBLIC);

    this.deprecatedAnnotationForEither(model, attributeDescriptor)
        .ifPresent(type::addAnnotation);

    return type
        .addMethod(stageMethod.build());
  }

  private TypeSpec.Builder createFinalStageFor(
      ModelDescriptor model,
      BuilderStrategyDescriptor strategy,
      FinalStage finalStage
  ) {
    ClassName finalStageTypeName = model.getQualifiedName().nestedClass(finalStage.getName());

    MethodSpec buildMethod = MethodSpec
        .methodBuilder(strategy.getBuildMethodName())
        .returns(model.getQualifiedName())
        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
        .build();

    TypeSpec.Builder finalType = TypeSpec
        .interfaceBuilder(finalStageTypeName)
        .addModifiers(Modifier.PUBLIC);

    finalStage
        .getAttributes()
        .stream()
        .map(attr -> this.stageMethodStubFor(attr, model.getQualifiedName()))
        .map(MethodSpec.Builder::build)
        .forEach(finalType::addMethod);

    return finalType
        .addMethod(buildMethod);
  }

  private MethodSpec.Builder stageMethodStubFor(
      AttributeDescriptor attribute,
      TypeName returnType
  ) {
    ParameterSpec parameterSpec = this.builderSetterParamFor(attribute).build();

    MethodSpec.Builder method = MethodSpec
        .methodBuilder(this.builderSetterNameFor(attribute))
        .addParameter(parameterSpec)
        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
        .returns(returnType);

    attribute
        .getDeprecatedAnnotation()
        .map(CodeGenUtils::copyDeprecatedFrom)
        .ifPresent(method::addAnnotation);

    return method;
  }

  private Optional<AnnotationSpec> deprecatedAnnotationForEither(
      ModelDescriptor model,
      AttributeDescriptor attribute
  ) {
    if (model.getDeprecatedAnnotation().isPresent()) {
      return model.getDeprecatedAnnotation()
          .map(CodeGenUtils::copyDeprecatedFrom);
    }

    return attribute.getDeprecatedAnnotation()
        .map(CodeGenUtils::copyDeprecatedFrom);
  }
}