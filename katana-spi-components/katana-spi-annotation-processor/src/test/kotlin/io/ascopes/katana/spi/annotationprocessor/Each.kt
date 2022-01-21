package io.ascopes.katana.spi.annotationprocessor

import org.junit.jupiter.params.provider.MethodSource
import javax.lang.model.SourceVersion

/**
 * Various re-usable fixtures for parameterized tests.
 */
object Each {
  @MethodSource("io.ascopes.katana.spi.annotationprocessor.Each#javaVersions")
  annotation class JavaVersion

  @JvmStatic
  fun javaVersions() = SourceVersion
      .values()
      .filter { it >= SourceVersion.RELEASE_11 }
      .toList()
}