package io.ascopes.katana.compilertesting.it

import io.ascopes.katana.compilertesting.compilation.JavaCompilationBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MultiModuleConflictITest {
  @Test
  fun `I cannot define a multi-module source after defining a single-module source`() {
    //@formatter:off
    val builder = JavaCompilationBuilder
        .javac()
        .sources()
            .createFile(
                fileName = "foo/bar/Baz.java",
                """
                  package foo.bar;
                  
                  public class Baz {
                  }
                """.trimIndent()
            )
        .and()
    //@formatter:on

    assertThrows<IllegalStateException> {
      //@formatter:off
      builder
          .multiModuleSources("some.modulename.here")
              .createFile(
                  fileName = "eggs/spam/Blah.java",
                  """
                    package eggs.spam;
                    
                    class Blah {
                    }
                  """.trimIndent()
              )
      //@formatter:on
    }
  }

  @Test
  fun `I cannot define a single-module source after defining a multi-module source`() {
    //@formatter:off
    val builder = JavaCompilationBuilder
        .javac()
        .multiModuleSources("some.modulename.here")
            .createFile(
                "eggs/spam/Blah.java",
                """
                  package eggs.spam;
                  
                  class Blah {
                  }
                """.trimIndent()
            )
            .and()
    //@formatter:on

    assertThrows<IllegalStateException> {
      //@formatter:off
      builder
          .sources()
              .createFile(
                  "foo/bar/Baz.java",
                  """
                    package foo.bar;
                    
                    public class Baz {
                    }
                  """.trimIndent()
              )
      //@formatter:on
    }
  }
}