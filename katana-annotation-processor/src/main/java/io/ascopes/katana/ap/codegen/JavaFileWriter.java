package io.ascopes.katana.ap.codegen;

import com.squareup.javapoet.JavaFile;
import io.ascopes.katana.ap.utils.Diagnostics;
import io.ascopes.katana.ap.utils.Logger;
import io.ascopes.katana.ap.utils.Result;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import javax.annotation.processing.Filer;
import javax.tools.Diagnostic.Kind;

/**
 * Helper to write out generated Java sources and report any errors.
 *
 * @author Ashley Scopes
 * @since 0.0.1
 */
public final class JavaFileWriter {

  private final Logger logger;
  private final Filer filer;
  private final Diagnostics diagnostics;

  /**
   * @param filer       the filer to use to write out source code.
   * @param diagnostics the diagnostic templates to use to report errors.
   */
  public JavaFileWriter(Filer filer, Diagnostics diagnostics) {
    this.logger = new Logger();
    this.filer = filer;
    this.diagnostics = diagnostics;
  }

  /**
   * Write out the given source file.
   *
   * @param javaFile the Java file to write out.
   * @return the result with no data inside.
   */
  public Result<Void> writeOutFile(JavaFile javaFile) {
    // TODO: does this have much overhead?
    String fileName = javaFile.toJavaFileObject().getName();

    try {
      this.logger.info("Writing out generated source for {}", fileName);
      javaFile.writeTo(this.filer);
      this.logger.info("Written out generated source for {} successfully!", fileName);
      return Result.ok();

    } catch (IOException ex) {
      StringWriter stringWriter = new StringWriter();
      PrintWriter printWriter = new PrintWriter(stringWriter);
      ex.printStackTrace(printWriter);

      this.diagnostics
          .builder()
          .kind(Kind.ERROR)
          .template("ioException")
          .param("fileName", fileName)
          .param("stacktrace", stringWriter.toString())
          .log();

      return Result.fail();
    }
  }
}
