package io.ascopes.katana.compilertesting.java

import com.google.common.jimfs.Jimfs
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import javax.lang.model.element.NestingKind
import javax.tools.JavaFileObject.Kind
import javax.tools.SimpleJavaFileObject
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.io.path.toPath


/**
 * Simple wrapper around a reference to an in memory file, assumed to reside in a
 * jimfs in-memory file system.
 *
 * @author Ashley Scopes
 * @since 0.1.0
 */
class JavaRamFileObject : SimpleJavaFileObject {
  private val location: JavaRamLocation

  /**
   * @param location the location of the file.
   * @param uri the URI of the in-memory file.
   * @param kind the kind of the file.
   */
  internal constructor(
      location: JavaRamLocation,
      uri: URI,
      kind: Kind
  ) : super(uri, kind) {
    if (!uri.isAbsolute) {
      throw IllegalArgumentException("Expected absolute URI, but got '$uri'")
    }

    this.location = location
    val jimfsScheme = Jimfs.URI_SCHEME

    if (uri.scheme != jimfsScheme) {
      throw IllegalArgumentException("Expected scheme '$jimfsScheme' for in-memory file '$uri'")
    }
  }

  /**
   * Create a file object for the location and path.
   *
   * @param location the file location in-memory.
   * @param path the path to the in-memory file.
   */
  internal constructor(location: JavaRamLocation, path: Path)
      : this(location, path.toUri(), determineKindOf(path))

  /**
   * Determine if the file exists or not.
   *
   * @return `true` if it exists, or `false` if it has not yet been created.
   */
  fun exists() = this.uri.toPath().exists(*linkOptions)

  /**
   * Delete the file, if it existed.
   *
   * @return `true` if the file existed and was deleted, or `false` if it did not exist.
   */
  override fun delete() = this.uri.toPath().deleteIfExists()

  /**
   * Get the character content of the file.
   *
   * @param ignoreEncodingErrors `true` to ignore encoding errors, `false` to throw an exception.
   * @return the character content of the file.
   */
  override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence {
    val errorAction = if (ignoreEncodingErrors) {
      CodingErrorAction.IGNORE
    } else {
      CodingErrorAction.REPORT
    }

    val buffer = ByteBuffer.wrap(this.uri.toPath().readBytes())

    return StandardCharsets.UTF_8
        .newDecoder()
        .onUnmappableCharacter(errorAction)
        .onMalformedInput(errorAction)
        .decode(buffer)
  }

  /**
   * Get the last-modified timestamp.
   */
  override fun getLastModified() = this.uri.toPath().getLastModifiedTime().toMillis()

  /**
   * Virtual files are always top-level to keep things simple.
   */
  override fun getNestingKind() = NestingKind.TOP_LEVEL

  /**
   * Open an input stream and return it.
   *
   * The result is not buffered, and there is no need to buffer it, since it is already in-memory.
   *
   * @throws FileNotFoundException if the file does not exist.
   * @return the input stream.
   */
  override fun openInputStream(): InputStream {
    val path = this.uri.toPath()

    if (!path.isRegularFile(*linkOptions)) {
      throw FileNotFoundException(path.toString())
    }

    // Don't bother buffering, it is already in-memory.
    return path.inputStream(*readOptions)
  }

  /**
   * Open an output stream and return it.
   *
   * The result is not buffered, and there is no need to buffer it, since it is already in-memory.
   *
   * @return the output stream.
   */
  override fun openOutputStream() = this.uri.toPath().outputStream(*writeOptions)

  companion object {
    private val readOptions = arrayOf(StandardOpenOption.READ)
    private val writeOptions = arrayOf(
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
    )
    private val linkOptions = arrayOf<LinkOption>()

    private fun determineKindOf(path: Path): Kind {
      // .endsWith acts differently for paths, where it checks the entire last segment for equality.
      // We don't want this.
      val fileName = path.fileName.toString()

      return Kind
          .values()
          .first { fileName.endsWith(it.extension) }
    }
  }
}