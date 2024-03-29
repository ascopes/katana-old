package io.ascopes.katana.compilertesting.java

import io.ascopes.katana.compilertesting.java.JavaAssertions.assertThatJavaCompilation
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest

@Execution(ExecutionMode.CONCURRENT)
class SingleModuleCompilationITest {
  @Each.JavaVersion
  @ParameterizedTest
  fun `I can compile a basic 'Hello, World!' application`(version: SourceVersion) {
    //@formatter:off
    val compilation = JavaCompilationBuilder.javac()
        .releaseVersion(version)
        .sources()
            .create(
                newFileName = "io/ascopes/helloworld/nonmodular/HelloWorld.java",
                """
                  package io.ascopes.helloworld.nonmodular;
                  
                  public class HelloWorld {
                    public static void main(String[] args) {
                      System.out.println("Hello, World!");
                    }
                  }
                """.trimIndent()
            )
            .and()
        .compile()

    assertThatJavaCompilation(compilation)
        .isSuccessful()

    assertThatJavaCompilation(compilation)
        .diagnostics()
        .hasNoWarnings()

    assertThatJavaCompilation(compilation)
        .files()
        .hasClassOutput("io/ascopes/helloworld/nonmodular/HelloWorld.class")
    //@formatter:on
  }

  @Each.JavaVersion
  @ParameterizedTest
  fun `I can compile a basic 'Hello, World!' application with modules`(version: SourceVersion) {
    //@formatter:off
    val compilation = JavaCompilationBuilder.javac()
        .releaseVersion(version)
        .sources()
            .create(
                newFileName = "io/ascopes/helloworld/modular/HelloWorld.java",
                """
                  package io.ascopes.helloworld.modular;
                  
                  public class HelloWorld {
                    public static void main(String[] args) {
                      System.out.println("Hello, World!");
                    }
                  }
                """.trimIndent()
            )
            .create(
                newFileName = "module-info.java",
                """
                  module helloworld {
                    requires java.base;
                    exports io.ascopes.helloworld.modular;
                  }
                """.trimIndent()
            )
            .and()
        .compile()

      assertThatJavaCompilation(compilation)
          .isSuccessful()

      assertThatJavaCompilation(compilation)
          .diagnostics()
          .hasNoWarnings()

        assertThatJavaCompilation(compilation)
            .files()
            .hasClassOutputs(
                "io/ascopes/helloworld/modular/HelloWorld.class",
                "module-info.class"
            )
    //@formatter:on
  }

  @Each.JavaVersion
  @ParameterizedTest
  fun `Annotation Processors get invoked on the given sources`(version: SourceVersion) {
    var invoked = false

    val annotationProcessor = object : AbstractProcessor() {
      override fun getSupportedSourceVersion() = version
      override fun getSupportedAnnotationTypes() = setOf("*")

      override fun process(
          annotations: MutableSet<out TypeElement>,
          roundEnv: RoundEnvironment
      ): Boolean {
        invoked = true
        return true
      }
    }

    //@formatter:off
    val compilation = JavaCompilationBuilder.javac()
        .releaseVersion(version)
        .sources()
            .create(
                newFileName = "io/ascopes/helloworld/HelloWorld.java",
                """
                  package io.ascopes.helloworld;
                  
                  public class HelloWorld {
                    public static void main(String[] args) {
                      System.out.println("Hello, World!");
                    }
                  }
                """.trimIndent()
            )
            .and()
        .processors(annotationProcessor)
        .compile()

    assertThatJavaCompilation(compilation)
        .isSuccessful()

    assertThatJavaCompilation(compilation)
        .diagnostics()
        .hasNoWarnings()

    assertThatJavaCompilation(compilation)
        .files()
        .hasClassOutput("io/ascopes/helloworld/HelloWorld.class")

    assertTrue(invoked, "annotation processor was not invoked")
    //@formatter:on
  }

  @Each.JavaVersion
  @ParameterizedTest
  fun `Headers get created for the given sources`(version: SourceVersion) {
    //@formatter:off
    val compilation = JavaCompilationBuilder.javac()
        .releaseVersion(version)
        .sources()
            .create(
                newFileName = "io/ascopes/helloworld/HelloWorld.java",
                """
                  package io.ascopes.helloworld;
                  
                  public class HelloWorld {
                    public static void main(String[] args) {
                      System.out.println(createGreeting());
                    }
                    
                    private static native String createGreeting();
                  }
                """.trimIndent()
            )
            .and()
        .generateHeaders()
        .compile()

    assertThatJavaCompilation(compilation)
        .isSuccessful()

    assertThatJavaCompilation(compilation)
        .diagnostics()
        .hasNoWarnings()

    assertThatJavaCompilation(compilation)
        .files()
        .hasClassOutputs("io/ascopes/helloworld/HelloWorld.class")
        .hasHeaderOutputs("io_ascopes_helloworld_HelloWorld.h")
    //@formatter:on
  }
}