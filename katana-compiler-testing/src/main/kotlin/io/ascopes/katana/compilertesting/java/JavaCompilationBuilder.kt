package io.ascopes.katana.compilertesting.java

import io.ascopes.katana.compilertesting.java.JavaCompilationResult.Failure
import io.ascopes.katana.compilertesting.java.JavaCompilationResult.FatalError
import io.ascopes.katana.compilertesting.java.JavaCompilationResult.Success
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.function.Consumer
import javax.annotation.processing.Processor
import javax.lang.model.SourceVersion
import javax.tools.JavaCompiler
import javax.tools.JavaFileManager.Location
import javax.tools.JavaFileObject.Kind
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.absolutePathString

/**
 * Support for compiling files located in a virtual in-memory filesystem.
 *
 * @author Ashley Scopes
 * @since 0.1.0
 * @param compiler the Java Compiler implementation to use.
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
class JavaCompilationBuilder internal constructor(
    private val compiler: JavaCompiler,
    private val diagnosticListener: JavaDiagnosticListener,
    private val fileManager: JavaRamFileManager,
) {

  private val options = mutableListOf<String>()
  private val modules = mutableListOf<String>()
  private val processors = mutableListOf<Processor>()

  /**
   * Set the source version to use.
   *
   * You cannot set this as well as the release version, and you can only specify this once.
   *
   * @param version the source version.
   * @return this object for further call chaining.
   */
  fun sourceVersion(version: Int) = this.options("-source", version.toString())

  /**
   * Set the source version to use.
   *
   * You cannot set this as well as the release version, and you can only specify this once.
   *
   * @param version the source version.
   * @return this object for further call chaining.
   */
  fun sourceVersion(version: SourceVersion) = this.sourceVersion(this.sourceToInt(version))

  /**
   * Set the target version to use.
   *
   * You cannot set this as well as the release version, and you can only specify this once.
   *
   * @param version the target version.
   * @return this object for further call chaining.
   */
  fun targetVersion(version: Int) = this.options("-target", version.toString())

  /**
   * Set the target version to use.
   *
   * You cannot set this as well as the release version, and you can only specify this once.
   *
   * @param version the target version.
   * @return this object for further call chaining.
   */
  fun targetVersion(version: SourceVersion) = this.targetVersion(this.sourceToInt(version))

  /**
   * Set the release version to use.
   *
   * You cannot set this as well as the source/target version, and you can only specify this once.
   *
   * @param version the release version.
   * @return this object for further call chaining.
   */
  fun releaseVersion(version: Int) = this.options("--release", version.toString())

  /**
   * Set the release version to use.
   *
   * You cannot set this as well as the source/target version, and you can only specify this once.
   *
   * @param version the release version.
   * @return this object for further call chaining.
   */
  fun releaseVersion(version: SourceVersion) = this.releaseVersion(this.sourceToInt(version))

  /**
   * Set the source and target version to use.
   *
   * You cannot set this as well as the release version, nor the source and target version if you
   * specified those individually. Additionally, you can only specify this once.
   *
   * @param version the source and target version.
   * @return this object for further call chaining.
   */
  fun sourceAndTargetVersion(version: Int) = this
      .sourceVersion(version)
      .targetVersion(version)

  /**
   * Set the source and target version to use.
   *
   * You cannot set this as well as the release version, nor the source and target version if you
   * specified those individually. Additionally, you can only specify this once.
   *
   * @param version the source and target version.
   * @return this object for further call chaining.
   */
  fun sourceAndTargetVersion(version: SourceVersion) = this
      .sourceVersion(version)
      .targetVersion(version)

  /**
   * Treat all warnings as errors.
   *
   * @return this object for further call chaining.
   */
  fun treatWarningsAsErrors() = this.apply { this.options += "-Werror" }

  /**
   * Request that the compiler produces native headers for JNI interop.
   *
   * @return this object for further call chaining.
   */
  fun generateHeaders() = this.apply {
    val headerLocation = this.fileManager.getOperationsFor(StandardLocation.NATIVE_HEADER_OUTPUT)
    this.options += listOf("-h", headerLocation.path.absolutePathString())
  }

  /**
   * Add the given options to the compiler.
   *
   * @param options the options to add.
   * @return this object for further call chaining.
   */
  fun options(vararg options: String) = this.apply { this.options += options }

  /**
   * Add the given processors to the compiler.
   *
   * @param processors the processors to add.
   * @return this object for further call chaining.
   */
  fun processors(vararg processors: Processor) = this.apply { this.processors += processors }

  /**
   * Perform operations on input source files.
   *
   * @param operation the operation to perform, in a closure.
   * @return this object for further call chaining.
   */
  fun sources(operation: JavaRamLocationOperations.() -> Unit) = this
      .files(StandardLocation.SOURCE_PATH, operation)

  /**
   * Perform operations on input source files.
   *
   * @param operation the operation to perform, as a JVM consumer functional monad.
   * @return this object for further call chaining.
   */
  fun sources(operation: Consumer<JavaRamLocationOperations>) = this
      .files(StandardLocation.SOURCE_PATH, operation)

  /**
   * Perform operations on input module source files.
   *
   * @param operation the operation to perform, in a closure.
   * @return this object for further call chaining.
   */
  fun moduleSources(moduleName: String, operation: JavaRamLocationOperations.() -> Unit) = this
      .moduleFiles(StandardLocation.MODULE_SOURCE_PATH, moduleName, operation)

  /**
   * Perform operations on input module source files.
   *
   * @param operation the operation to perform, as a JVM consumer functional monad.
   * @return this object for further call chaining.
   */
  fun moduleSources(moduleName: String, operation: Consumer<JavaRamLocationOperations>) = this
      .moduleFiles(StandardLocation.MODULE_SOURCE_PATH, moduleName, operation)

  /**
   * Perform operations on any generated source files.
   *
   * @param operation the operation to perform, in a closure.
   * @return this object for further call chaining.
   */
  fun generatedSources(operation: JavaRamLocationOperations.() -> Unit) = this
      .files(StandardLocation.SOURCE_OUTPUT, operation)

  /**
   * Perform operations on any generated source files.
   *
   * @param operation the operation to perform, as a JVM consumer functional monad.
   * @return this object for further call chaining.
   */
  fun generatedSources(operation: Consumer<JavaRamLocationOperations>) = this
      .files(StandardLocation.SOURCE_OUTPUT, operation)

  /**
   * Perform operations on any generated class files.
   *
   * @param operation the operation to perform, in a closure.
   * @return this object for further call chaining.
   */
  fun generatedClasses(operation: JavaRamLocationOperations.() -> Unit) = this
      .files(StandardLocation.CLASS_OUTPUT, operation)

  /**
   * Perform operations on any generated class files.
   *
   * @param operation the operation to perform, as a JVM consumer functional monad.
   * @return this object for further call chaining.
   */
  fun generatedClasses(operation: Consumer<JavaRamLocationOperations>) = this
      .files(StandardLocation.CLASS_OUTPUT, operation)

  /**
   * Perform operations on any generated header files.
   *
   * @param operation the operation to perform, in a closure.
   * @return this object for further call chaining.
   */
  fun generatedHeaders(operation: JavaRamLocationOperations.() -> Unit) = this
      .files(StandardLocation.NATIVE_HEADER_OUTPUT, operation)

  /**
   * Perform operations on any generated header files.
   *
   * @param operation the operation to perform, as a JVM consumer functional monad.
   * @return this object for further call chaining.
   */
  fun generatedHeaders(operation: Consumer<JavaRamLocationOperations>) = this
      .files(StandardLocation.NATIVE_HEADER_OUTPUT, operation)

  /**
   * Perform operations on files in the given location.
   *
   * @param location the module-oriented location.
   * @param operation the operation to perform, in a closure.
   * @return this object for further call chaining.
   */
  fun files(location: Location, operation: JavaRamLocationOperations.() -> Unit) = this.apply {
    this.fileManager
        .getOperationsFor(location)
        .operation()
  }

  /**
   * Perform operations on files in the given location.
   *
   * @param location the module-oriented location.
   * @param operation the operation to perform, in a closure.
   * @param operation the operation to perform, as a JVM consumer functional monad.
   * @return this object for further call chaining.
   */
  fun files(location: Location, operation: Consumer<JavaRamLocationOperations>) = this.apply {
    operation.accept(this.fileManager.getOperationsFor(location))
  }

  /**
   * Perform operations on the files in the given module-oriented location.
   *
   * @param location the module-oriented location.
   * @param moduleName the name of the module.
   * @param operation the operation to perform, in a closure.
   * @return this object for further call chaining.
   */
  fun moduleFiles(
      location: Location,
      moduleName: String,
      operation: JavaRamLocationOperations.() -> Unit
  ) = this.apply {
    this.fileManager
        .getOperationsFor(location, moduleName)
        .operation()
  }

  /**
   * Perform operations on the files in the given module-oriented location.
   *
   * @param location the module-oriented location.
   * @param moduleName the name of the module.
   * @param operation the operation to perform, as a JVM consumer functional monad.
   * @return this object for further call chaining.
   */
  fun moduleFiles(
      location: Location,
      moduleName: String,
      operation: Consumer<JavaRamLocationOperations>
  ) = this.apply {
    operation.accept(this.fileManager.getOperationsFor(location, moduleName))
  }


  /**
   * Invoke the compiler with the given inputs, and return the compilation result.
   *
   * @return the compilation result.
   */
  fun compile(): JavaCompilationResult {
    val nonModuleCompilationUnits = this.fileManager
        .list(StandardLocation.SOURCE_PATH, "", setOf(Kind.SOURCE), true)
        .toList()

    val moduleCompilationUnits = this.fileManager
        .listLocationsForModules(StandardLocation.MODULE_SOURCE_PATH)
        .flatten()
        .flatMap { this.fileManager.list(it, "", setOf(Kind.SOURCE), true) }
        .toList()

    val compilationUnits = if (nonModuleCompilationUnits.isEmpty()) {
      moduleCompilationUnits.isNotEmpty()
          || throw IllegalStateException("No sources or module sources were provided to compile")
      moduleCompilationUnits
    } else if (moduleCompilationUnits.isNotEmpty()) {
      throw IllegalStateException("Both sources and modular sources were provided")
    } else {
      nonModuleCompilationUnits
    }

    val stringWriter = StringWriter()

    val task = this.compiler.getTask(
        stringWriter,
        this.fileManager,
        this.diagnosticListener,
        this.options,
        null,
        compilationUnits
    )

    task.addModules(this.modules)
    task.setLocale(Locale.ROOT)
    task.setProcessors(this.processors)

    val outcome = try {
      if (task.call()) Success else Failure
    } catch (ex: Exception) {
      FatalError(ex)
    }

    return JavaCompilationResult(
        outcome = outcome,
        modules = this.modules,
        processors = this.processors,
        options = this.options,
        logs = stringWriter.toString(),
        diagnostics = this.diagnosticListener.diagnostics,
        fileManager = this.fileManager
    )
  }

  private fun sourceToInt(sourceVersion: SourceVersion) = sourceVersion
      .name
      .substringAfterLast('_')
      .toInt()

  companion object {
    /**
     * Get a virtual Java compiler for the system default Java compiler implementation.
     *
     * @return the compilation builder for testing compilation passes in-memory.
     */
    @JvmStatic
    fun javac() = this.compiler(ToolProvider.getSystemJavaCompiler())

    /**
     * Get a virtual compiler for the given Java compiler implementation.
     *
     * @param compiler the actual Java compiler to use.
     * @return the compilation builder for testing compilation passes in-memory.
     */
    @JvmStatic
    fun compiler(compiler: JavaCompiler): JavaCompilationBuilder {
      val diagnosticListener = JavaDiagnosticListener()
      val fileManager = JavaRamFileManager.create(
          compiler,
          diagnosticListener,
          Locale.ROOT,
          StandardCharsets.UTF_8
      )
      return JavaCompilationBuilder(compiler, diagnosticListener, fileManager)
    }
  }
}