package io.ascopes.katana.compilertesting.java

import io.ascopes.katana.compilertesting.core.CompilationAssertions
import org.opentest4j.AssertionFailedError

/**
 * Assertions for the result of a Java compilation.
 *
 * @author Ashley Scopes
 * @since 0.1.0
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class JavaCompilationAssertions
  : CompilationAssertions<JavaCompilation, JavaCompilationAssertions> {

  /**
   * @param target the target of the assertions to perform.
   */
  @Suppress("ConvertSecondaryConstructorToPrimary")
  internal constructor(target: JavaCompilation) : super(target)

  override fun isSuccessfulWithoutWarnings() = apply {
    // Just a short-cut.
    isSuccessful()
    diagnostics().hasNoWarnings()
  }

  /**
   * Get an assertion object for the diagnostics produced by the compilation.
   *
   * @return the assertion object for the diagnostics.
   */
  fun diagnostics() = JavaDiagnosticsAssertions(target.diagnostics)

  /**
   * Get an assertion object for the files produced by the compilation.
   *
   * @return the assertion object for the files.
   */
  fun files() = JavaRamFileManagerAssertions(target.fileManager)

  /**
   * Assert that the compiler ran in the "legacy" single-project mode.
   *
   * @return this assertion object for further checks.
   */
  fun ranInLegacyMode() = apply {
    assert(target.fileManager.moduleMode != null) {
      "No legacy/multi-module sources were provided, so the mode has not been set."
    }

    if (target.fileManager.moduleMode != JavaCompilationModuleMode.LEGACY) {
      throw AssertionFailedError(
          "Expected the compiler to run in legacy mode",
          JavaCompilationModuleMode.LEGACY,
          target.fileManager.moduleMode
      )
    }
  }

  /**
   * Assert that the compiler ran in the "legacy" single-project mode.
   *
   * @return this assertion object for further checks.
   */
  fun ranInMultiModuleMode() = apply {
    assert(target.fileManager.moduleMode == null) {
      "No legacy/multi-module sources were provided, so the mode has not been set."
    }

    if (target.fileManager.moduleMode != JavaCompilationModuleMode.MULTI_MODULE) {
      throw AssertionFailedError(
          "Expected the compiler to run in multi-module mode",
          JavaCompilationModuleMode.LEGACY,
          target.fileManager.moduleMode
      )
    }
  }

  override fun describeActualOutcomeType(): String {
    val messageBuilder = StringBuilder(super.describeActualOutcomeType())

    if (target.diagnostics.isNotEmpty()) {
      messageBuilder
          .appendLine()
          .appendLine()
          .appendLine("===== Diagnostics =====")
          .apply {
            target.diagnostics.forEach { diagnostic ->
              appendLine(diagnostic)
              diagnostic.stacktrace.forEach { frame ->
                append("\tat ")
                appendLine(frame)
              }
              appendLine()
            }
          }
    }

    target
        .logs
        .takeIf { it.isNotBlank() }
        ?.apply {
          messageBuilder
              .appendLine()
              .appendLine()
              .appendLine("===== Compiler output =====")
              .appendLine(this)
        }

    return messageBuilder.toString()
  }
}