package io.ascopes.katana.compilertesting.java

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import java.io.File
import java.lang.ref.Cleaner
import java.nio.file.FileSystem
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import java.util.concurrent.CompletableFuture
import javax.tools.FileObject
import javax.tools.ForwardingJavaFileManager
import javax.tools.JavaFileManager.Location
import javax.tools.JavaFileObject
import javax.tools.JavaFileObject.Kind
import javax.tools.StandardJavaFileManager
import javax.tools.StandardLocation
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.notExists
import kotlin.io.path.relativeTo
import kotlin.io.path.toPath
import kotlin.io.path.writeBytes
import kotlin.streams.asSequence
import me.xdrop.fuzzywuzzy.FuzzySearch


/**
 * Java file manager that decides whether to delegate to a given [StandardJavaFileManager]
 * based on the locations of the paths it is attempting to handle.
 *
 * For paths such as input sources, output sources, and output classes, this will delegate
 * IO operations to an in-memory file system hosted by `commons-vfs2`, and will
 * purge those locations when this object is garbage collected.
 *
 * This makes this very useful in tests where we need to present a full file system
 * to the compiler API.
 *
 * This also supports basic multimodule compilation, at the time of writing...
 *
 * @author Ashley Scopes
 * @since 0.1.0
 */
class JavaRamFileManager : ForwardingJavaFileManager<StandardJavaFileManager> {

  /**
   * The module mode that was used for compilation.
   *
   * Describes whether the compiler ran in "single-project" mode or "multiple-module" mode.
   */
  var moduleMode: JavaCompilationModuleMode?
    private set

  private val fs: FileSystem
  private val rootPath: Path
  private val inMemoryLocations: Map<StandardLocation, JavaRamFileLocation>

  /**
   * @param fileManager the compiler-supplied standard file manager to delegate most calls to.
   */
  @Suppress("ConvertSecondaryConstructorToPrimary")
  internal constructor(fileManager: StandardJavaFileManager) : super(fileManager) {
    val fsName = UUID.randomUUID().toString()
    // Keep as a local to ensure it remains in scope for the cleaner operation.
    val fs = Jimfs.newFileSystem(fsName, Configuration.unix())

    this.fs = fs
    this.rootPath = this.fs.getPath("/").toAbsolutePath()

    this.inMemoryLocations = mapOf(
        this.mapLocationFor(StandardLocation.SOURCE_PATH, "input/main"),
        this.mapLocationFor(StandardLocation.MODULE_SOURCE_PATH, "input/modules"),
        this.mapLocationFor(StandardLocation.SOURCE_OUTPUT, "output/sources"),
        this.mapLocationFor(StandardLocation.CLASS_OUTPUT, "output/classes"),
        this.mapLocationFor(StandardLocation.NATIVE_HEADER_OUTPUT, "output/headers"),
    )
    this.inMemoryLocations.values.forEach { it.path.createDirectories() }
    this.moduleMode = null

    // On garbage collection, destroy any created files within the temporary root we created.
    // We apparently have to run this asynchronously to prevent starving the cleaner thread,
    // strangely.
    cleaner.register(this) { CompletableFuture.runAsync { fs.close() } }
  }

  // Visible for testing only.
  internal val standardFileManager: StandardJavaFileManager
    get() = super.fileManager

  /**
   * Create a file in the given location.
   *
   * @param location the location.
   * @param fileName the name of the file.
   * @param bytes the byte content to write to the file.
   */
  fun createFile(location: JavaRamLocation, fileName: String, bytes: ByteArray) {
    val mappedLocation = this.wrapExpectedInMemoryLocation(location)
    this.checkOrAssignModuleModeFor(mappedLocation)

    val fullPath = this.fileToPath(mappedLocation, fileName)
    this.createNewFileWithDirectories(fullPath)
        .writeBytes(bytes)
  }

  /**
   * Perform a fuzzy search in the given location for the given file name, returning the
   * closest matches.
   *
   * @param location the location to look in.
   * @param fileName the file name to look for.
   * @return a list of matches, or an empty list if nothing was found.
   */
  fun findClosestFileNameMatchesFor(location: JavaRamLocation, fileName: String): List<String> {
    val mappedLocation = this.wrapInMemoryLocation(location)
        ?: return emptyList()

    val files = this
        .list(mappedLocation, "", setOf(Kind.OTHER), true)
        .map { it.toUri().toPath().relativeTo(mappedLocation.path).toString() }

    return FuzzySearch
        .extractTop(fileName, files, 3, 70)
        .map { it.string }
        .toList()
  }

  /**
   * Get an in-memory file, if it exists.
   *
   * @param location the location of the file.
   * @param fileName the name of the file.
   * @return the file object, or null if it does not exist.
   */
  fun getFile(location: JavaRamLocation, fileName: String): JavaRamFileObject? {
    val mappedLocation = this.wrapExpectedInMemoryLocation(location)
    val path = this.fileToPath(mappedLocation, fileName)
    val fileObject = JavaRamFileObject(mappedLocation, path)
    return if (fileObject.exists()) fileObject else null
  }

  /**
   * Get the in-memory location for the given location.
   *
   * @param location the location to wrap in an in-memory location.
   * @return the corresponding in-memory location.
   */
  fun getInMemoryLocationFor(location: Location) =
      this.wrapExpectedInMemoryLocation(location)

  /**
   * Get the in-memory location for the given module in a given parent location.
   *
   * @param parent the location that contains the module to create the location for.
   * @param moduleName the name of the module to create the location for.
   * @return the corresponding in-memory location.
   */
  fun getInMemoryLocationFor(parent: Location, moduleName: String) =
      this.wrapExpectedInMemoryLocation(parent, moduleName)

  /**
   * Get an iterable across all in-memory files.
   *
   * @return the iterable sequence.
   */
  fun listAllInMemoryFiles(): Iterable<Path> {
    val files = mutableSetOf<Path>()

    val visitor = object : SimpleFileVisitor<Path>() {
      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        files.add(file.toAbsolutePath())
        return FileVisitResult.CONTINUE
      }
    }

    Files.walkFileTree(this.rootPath, visitor)

    return files
  }

  /**
   * Set the classpath to a set of files.
   *
   * @param files the files to add.
   */
  fun setClassPath(files: Iterable<File>) {
    // TODO: can I pull these in-memory so that I don't get restricted by the File API?
    // JIMFS and other file systems cannot build File objects like they can Path objects
    // as the API only allows the default file manager to do this :(
    // TODO: should I also be setting MODULE_PATH anywhere?
    this.standardFileManager.setLocation(StandardLocation.CLASS_PATH, files)
  }


  /**
   * Determine if the location contains a given file object.
   *
   * @param location the location to check.
   * @param fileObject the file object to look for.
   * @return `true` if it exists and is within the location, `false` otherwise.
   */
  override fun contains(location: Location, fileObject: FileObject): Boolean {
    val mappedLocation = this.wrapInMemoryLocation(location)
        ?: return super.contains(location, fileObject)

    return fileObject is JavaRamFileObject
        && fileObject.exists()
        && fileObject.toUri().toPath().startsWith(mappedLocation.path)
  }

  /**
   * Get a file for use as an input.
   *
   * @param location the location of the file.
   * @param packageName the package name of the file.
   * @param relativeName the relative name of the file.
   * @return a file object, or null if the file does not exist.
   */
  override fun getFileForInput(
      location: Location,
      packageName: String,
      relativeName: String
  ): FileObject? {
    val mappedLocation = this.wrapInMemoryLocation(location)
        ?: return super.getFileForInput(location, packageName, relativeName)

    this.checkOrAssignModuleModeFor(mappedLocation)

    val fullRelativeName = Path.of(packageName, relativeName).toString()
    val path = this.fileToPath(mappedLocation, fullRelativeName)
    val obj = JavaRamFileObject(mappedLocation, path)
    return if (obj.exists()) obj else null
  }

  /**
   * Get a source file for use as an input.
   *
   * @param location the location of the file.
   * @param className the qualified class name.
   * @param kind the file kind.
   * @return a file object, or null if the file does not exist.
   */
  override fun getJavaFileForInput(
      location: Location,
      className: String,
      kind: Kind
  ): JavaFileObject? {
    val mappedLocation = this.wrapInMemoryLocation(location)
        ?: return super.getJavaFileForInput(location, className, kind)

    this.checkOrAssignModuleModeFor(mappedLocation)

    val path = this.sourceToPath(mappedLocation, className, kind)
    val obj = JavaRamFileObject(mappedLocation, path)
    return if (obj.exists()) obj else null
  }

  /**
   * Get a file for use as an output.
   *
   * @param location the location of the file.
   * @param packageName the package name of the file.
   * @param relativeName the relative name of the file.
   * @param sibling a nullable sibling to the file.
   * @return a file object.
   */
  override fun getFileForOutput(
      location: Location,
      packageName: String,
      relativeName: String,
      sibling: FileObject?
  ): FileObject {
    val mappedLocation = this.wrapInMemoryLocation(location)
        ?: return super.getFileForOutput(location, packageName, relativeName, sibling)

    val fullRelativeName = Path.of(packageName, relativeName).toString()
    val path = this.fileToPath(mappedLocation, fullRelativeName)
    this.createNewFileWithDirectories(path)
    return JavaRamFileObject(mappedLocation, path)
  }

  /**
   * Get a source file for use as an output.
   *
   * @param location the location of the file.
   * @param className the qualified class name.
   * @param kind the file kind.
   * @param sibling a nullable sibling to the file.
   * @return a file object.
   */
  override fun getJavaFileForOutput(
      location: Location,
      className: String,
      kind: Kind,
      sibling: FileObject?
  ): JavaFileObject {
    val mappedLocation = this.wrapInMemoryLocation(location)
        ?: return super.getJavaFileForOutput(location, className, kind, sibling)

    val path = this.sourceToPath(mappedLocation, className, kind)
    this.createNewFileWithDirectories(path)
    return JavaRamFileObject(mappedLocation, path)
  }

  /**
   * Get the location for a module.
   *
   * @param location the location all modules are held in.
   * @param moduleName the name of the module.
   * @return the location of the module.
   */
  override fun getLocationForModule(location: Location, moduleName: String): Location {
    return this.wrapInMemoryLocation(location, moduleName)
        ?: super.getLocationForModule(location, moduleName)
  }

  /**
   * Get the location for the module that the given file object is in.
   *
   * @param location the location that all modules are held in.
   * @param fileObject the file object for an element in the module.
   * @return the location of the module.
   */
  override fun getLocationForModule(location: Location, fileObject: JavaFileObject): Location {
    val mappedLocation = this.wrapInMemoryLocation(location)
        ?: return super.getLocationForModule(location, fileObject)

    if (!mappedLocation.isModuleOrientedLocation) {
      // Multi-module is enabled but the location doesn't have nested modules, I guess.
      // TODO: check if this is the correct behaviour?
      return mappedLocation
    }

    // The first directory in the module-oriented location
    val moduleName = mappedLocation
        .path
        .relativize(fileObject.toUri().toPath())
        .first()
        .toString()

    // The first nested directory is the module name.
    // Remember module-oriented means it contains module locations, not that the location
    // itself is of a module.
    return this.getLocationForModule(location, moduleName)
  }

  /**
   * Determine if the given location exists or not.
   *
   * @param location the location to look up.
   * @return true if the location exists, or false otherwise.
   */
  override fun hasLocation(location: Location): Boolean {
    if (location == StandardLocation.MODULE_SOURCE_PATH) {
      // We flag this to implicitly enable this feature when we want it. Otherwise, it results in
      // our source paths being moved around for non-modular compilation which causes issues
      // elsewhere for us.
      val path = this.inMemoryLocations[StandardLocation.MODULE_SOURCE_PATH]!!.path
      return Files.list(path).count() > 0
    }

    val mappedLocation = this.wrapInMemoryLocation(location)
    return mappedLocation != null
  }

  /**
   * Determine if two file objects are considered to be the same file.
   *
   * @param a the first file.
   * @param b the second file.
   */
  override fun isSameFile(a: FileObject, b: FileObject): Boolean {
    return a.toUri() == b.toUri()
  }

  /**
   * Infer the canonical name for a class represented in a Java class file.
   *
   * @param location the location of the file.
   * @param file the source file.
   * @return the canonical name.
   */
  override fun inferBinaryName(location: Location, file: JavaFileObject): String {
    val mappedLocation = this.wrapInMemoryLocation(location)
        ?: return super.inferBinaryName(location, file)

    return file.toUri().toPath().relativeTo(mappedLocation.path)
        .toString()
        .removeSuffix(file.kind.extension)
        .replace('/', '.')
  }

  /**
   * Infer the correct module name for a location that has a module bound to it.
   *
   * @param location the location.
   * @return the module name.
   */
  override fun inferModuleName(location: Location): String {
    val mappedLocation = this.wrapInMemoryLocation(location)

    if (mappedLocation is JavaRamModuleLocation) {
      return mappedLocation.moduleName
    }

    return super.inferModuleName(location)
  }

  /**
   * List the files in the given location and package, filtering by their kinds.
   *
   * @param location the location to enter.
   * @param packageName the package name to enter.
   * @param kinds the kinds of file to return.
   * @param recurse true to recursively list files in a top-down order, false to only list the
   *    given location and package name's files directly.
   * @return an iterable across the files that were found.
   */
  override fun list(
      location: Location,
      packageName: String,
      kinds: Set<Kind>,
      recurse: Boolean
  ): Iterable<JavaFileObject> {
    val mappedLocation = this.wrapInMemoryLocation(location)
        ?: return super.list(location, packageName, kinds, recurse)

    val basePath = this.sourceToPath(mappedLocation, packageName, Kind.OTHER)

    if (basePath.notExists()) {
      // Package does not exist yet, probably.
      return emptyList()
    }

    val visitOpts = emptySet<FileVisitOption>()
    val maxDepth = if (recurse) Int.MAX_VALUE else 1
    val filesFound = mutableListOf<JavaRamFileObject>()

    val visitor = object : SimpleFileVisitor<Path>() {
      override fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {
        if (path.isRegularFile()) {
          // Without the .toString(), endsWith checks if the last path segment equals the argument
          // instead.
          val kind = kinds.find { path.toString().endsWith(it.extension) }

          if (kind != null) {
            filesFound += JavaRamFileObject(mappedLocation, path.toUri(), kind)
          }
        }

        return FileVisitResult.CONTINUE
      }
    }

    Files.walkFileTree(basePath, visitOpts, maxDepth, visitor)

    return filesFound
  }

  /**
   * List the locations for modules in the given location.
   *
   * @param location the location to consider.
   * @return the iterable across the set of locations.
   */
  override fun listLocationsForModules(location: Location): Iterable<Set<Location>> {
    val mappedLocation = this.wrapInMemoryLocation(location)
        ?: return super.listLocationsForModules(location)

    val path = mappedLocation.path

    val files = Files
        .list(path)
        .filter { it.isDirectory() }
        .filter { dir ->
          Files
              .list(dir)
              .filter { it.isRegularFile() }
              .anyMatch { it.fileName.toString() == "module-info.java" }
        }
        .map { JavaRamModuleLocation(mappedLocation, it, it.fileName.toString()) }
        .asSequence()
        .toSet()

    return listOf(files)
  }

  private fun sourceToPath(location: JavaRamLocation, className: String, kind: Kind): Path {
    val classNameAsPath = this.classNameAsPath(className, kind)

    return location
        .path
        .resolve(classNameAsPath)
        .normalize()
        .toAbsolutePath()
  }

  private fun fileToPath(location: JavaRamLocation, relativeName: String): Path {
    return location
        .path
        .resolve(relativeName)
        .normalize()
        .toAbsolutePath()
  }

  private fun classNameAsPath(className: String, kind: Kind) = className
      .removePrefix("/")
      .replace('.', '/') + kind.extension

  private fun wrapInMemoryLocation(inputLocation: Location): JavaRamLocation? {
    return when (inputLocation) {
      is JavaRamLocation -> inputLocation
      in this.inMemoryLocations -> this.inMemoryLocations[inputLocation]!!
      else -> null
    }
  }

  private fun wrapInMemoryLocation(parent: Location, moduleName: String): JavaRamModuleLocation? {
    val mappedParent = when (parent) {
      is JavaRamFileLocation -> parent
      in this.inMemoryLocations -> this.inMemoryLocations[parent]!!
      else -> return null
    }

    val modulePath = mappedParent.path.resolve(moduleName)
    return JavaRamModuleLocation(mappedParent, modulePath, moduleName)
  }

  private fun wrapExpectedInMemoryLocation(
      inputLocation: Location
  ) = this.wrapInMemoryLocation(inputLocation)
      ?: throw IllegalArgumentException("Location $inputLocation was not located in-memory")

  private fun wrapExpectedInMemoryLocation(
      parentLocation: Location,
      moduleName: String
  ) = this.wrapInMemoryLocation(parentLocation, moduleName)
      ?: throw IllegalArgumentException(
          "Location $parentLocation/$moduleName was not located in memory, or " +
              "$parentLocation is not module-oriented")

  private fun createNewFileWithDirectories(path: Path): Path {
    path.parent.createDirectories()
    path.deleteIfExists()
    return path.createFile()
  }

  private fun mapLocationFor(location: StandardLocation, dirName: String) =
      location to JavaRamFileLocation(
          location,
          this.rootPath.resolve(dirName)
      )

  private fun checkOrAssignModuleModeFor(location: JavaRamLocation) {
    if (this.moduleMode == null) {
      this.moduleMode = JavaCompilationModuleMode.getModuleModeFor(location)
    } else {
      this.moduleMode!!.assertLocationAllowed(location)
    }
  }

  companion object {
    // Garbage collector hook to free memory after we've finished with it.
    private val cleaner = Cleaner.create()
  }
}