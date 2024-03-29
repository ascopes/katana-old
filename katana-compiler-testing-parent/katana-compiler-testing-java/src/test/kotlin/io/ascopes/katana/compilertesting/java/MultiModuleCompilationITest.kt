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
class MultiModuleCompilationITest {
  @Each.JavaVersion
  @ParameterizedTest
  fun `I can compile a basic 'Hello, World!' application`(version: SourceVersion) {
    //@formatter:off
    val compilation = JavaCompilationBuilder.javac()
        .releaseVersion(version)
        .multiModuleSources("helloworld.greet")
            .create(
                newFileName = "io/me/helloworld/greet/Greeter.java",
                """
                  package io.me.helloworld.greet;
                    
                  public class Greeter {
                    public String greet(String name) {
                      return "Hello, " + name + "!"; 
                    }
                  }
                """.trimIndent()
            )
            .create(
                newFileName = "module-info.java",
                """
                  module helloworld.greet {
                    requires java.base;
                    exports io.me.helloworld.greet;
                  }
                """.trimIndent()
            )
            .and()
        .multiModuleSources("helloworld.main")
            .create(
                newFileName ="io/me/helloworld/main/Main.java",
                """
                  package io.me.helloworld.main;
                  
                  import io.me.helloworld.greet.Greeter;
                  
                  public class Main {
                    public static void main(String[] args) {
                      var greeter = new Greeter();
                      var greeting = greeter.greet("World");
                      System.out.println(greeting);
                    }
                  }
                """.trimIndent()
            )
            .create(
                newFileName = "module-info.java",
                """
                  module helloworld.main {
                    requires java.base;
                    requires helloworld.greet;
                    exports io.me.helloworld.main;
                  }
                """.trimIndent()
            )
            .and()
        .compile()

    assertThatJavaCompilation(compilation)
        .isSuccessful()
        .diagnostics()
        .hasNoWarnings()

    assertThatJavaCompilation(compilation)
        .files()
        .hasMultiModuleClassOutputs(
            moduleName = "helloworld.greet",
            "io/me/helloworld/greet/Greeter.class",
            "module-info.class"
        )
        .hasMultiModuleClassOutputs(
            moduleName = "helloworld.main",
            "io/me/helloworld/main/Main.class",
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
        .multiModuleSources("helloworld.greet")
            .create(
                newFileName = "io/ascopes/helloworld/greet/Greeter.java",
                """
                  package io.ascopes.helloworld.greet;
                  
                  public class Greeter {
                    public String greet(String name) {
                      return "Hello, " + name + "!"; 
                    }
                  }
                """.trimIndent()
            )
            .create(
                newFileName = "module-info.java",
                """
                module helloworld.greet {
                  requires java.base;
                  exports io.ascopes.helloworld.greet;
                }
              """.trimIndent()
            )
            .and()
        .multiModuleSources("helloworld.main")
            .create(
                newFileName = "io/ascopes/helloworld/main/Main.java",
                """
                  package io.ascopes.helloworld.main;
                  
                  import io.ascopes.helloworld.greet.Greeter;
                  
                  public class Main {
                    public static void main(String[] args) {
                      var greeter = new Greeter();
                      var greeting = greeter.greet("World");
                      System.out.println(greeting);
                    }
                  }
                """.trimIndent()
            )
            .create(
                newFileName = "module-info.java",
                """
                  module helloworld.main {
                    requires java.base;
                    requires helloworld.greet;
                    exports io.ascopes.helloworld.main;
                  }
                """.trimIndent()
            )
            .and()
        .processors(annotationProcessor)
        .compile()

    assertThatJavaCompilation(compilation)
        .isSuccessful()
        .diagnostics()
        .hasNoWarnings()

    assertThatJavaCompilation(compilation)
        .files()
        .hasMultiModuleClassOutputs(
            moduleName = "helloworld.greet",
            "io/ascopes/helloworld/greet/Greeter.class",
            "module-info.class"
        )
        .hasMultiModuleClassOutputs(
            moduleName = "helloworld.main",
            "io/ascopes/helloworld/main/Main.class",
            "module-info.class"
        )

    assertTrue(invoked, "annotation processor was not invoked")
    //@formatter:on
  }

  @Each.JavaVersion
  @ParameterizedTest
  fun `Headers get created for the given sources`(version: SourceVersion) {
    //@formatter:off
    val compilation = JavaCompilationBuilder.javac()
        .releaseVersion(version)
        .multiModuleSources("helloworld.greet")
            .create(
                "io/me/helloworld/greet/Greeter.java",
                """
                  package io.me.helloworld.greet;
                  
                  public class Greeter {
                    public native String greet(String name);
                  }
                """.trimIndent()
            )
            .create(
                "module-info.java",
                """
                  module helloworld.greet {
                    requires java.base;
                    exports io.me.helloworld.greet;
                  }
                """.trimIndent()
            )
            .and()
        .multiModuleSources("helloworld.main")
            .create(
                "io/me/helloworld/main/Main.java",
                """
                  package io.me.helloworld.main;
                  
                  import io.me.helloworld.greet.Greeter;
                  
                  public class Main {
                    public static void main(String[] args) {
                      var greeter = new Greeter();
                      var greeting = greeter.greet("World");
                      System.out.println(greeting);
                    }
                  }
                """.trimIndent()
            )
            .create(
                newFileName = "module-info.java",
                """
                  module helloworld.main {
                    requires java.base;
                    requires helloworld.greet;
                    exports io.me.helloworld.main;
                  }
                """.trimIndent()
            )
        .and()
        .generateHeaders()
        .compile()

    assertThatJavaCompilation(compilation)
        .isSuccessful()
        .diagnostics()
        .hasNoWarnings()

    assertThatJavaCompilation(compilation)
        .files()
        .hasMultiModuleClassOutputs(
            moduleName = "helloworld.greet",
            "io/me/helloworld/greet/Greeter.class",
            "module-info.class"
        )
        .hasMultiModuleClassOutputs(
            moduleName = "helloworld.main",
            "io/me/helloworld/main/Main.class",
            "module-info.class"
        )
        .hasMultiModuleHeaderOutput(
            moduleName = "helloworld.greet",
            "io_me_helloworld_greet_Greeter.h"
        )
    //@formatter:on
  }
}