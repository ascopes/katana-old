package io.ascopes.katana.compilertesting.core

/**
 * Interface for the provider of a stack trace related to the caller.
 *
 * @author Ashley Scopes
 * @since 0.1.0
 */
interface StackTraceProvider : () -> List<StackTraceElement> {
  companion object {
    /**
     * Stack trace provider that checks the current invocation thread.
     */
    @JvmStatic
    val threadStackTraceProvider = object : StackTraceProvider {
      override fun invoke() = Thread
          .currentThread()
          .stackTrace
          // 3 appears to be the magic number to drop these calls in this file.
          .drop(3)
          .takeWhile { !it.className.startsWith("org.junit.platform.") }
    }
  }
}