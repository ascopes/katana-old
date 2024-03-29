package io.ascopes.katana.compilertesting.java

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MultiModuleConflictITest {
  @Test
  fun `I cannot define a multi-module source after defining a single-module source`() {
    //@formatter:off
    val builder = JavaCompilationBuilder.javac()
        .sources()
            .create(
                newFileName = "foo/bar/Baz.java",
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
              .create(
                  newFileName = "eggs/spam/Blah.java",
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
    val builder = JavaCompilationBuilder.javac()
        .multiModuleSources("some.modulename.here")
            .create(
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
              .create(
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