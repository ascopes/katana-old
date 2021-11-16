package io.ascopes.katana.annotations;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.JavaFileObjects.forSourceLines;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

class EqualityExcludeTest {

  @Test
  void Equality_Exclude_is_not_repeatable() {
    Compilation result = Compiler
        .javac()
        .compile(forSourceLines(
            "com.somecompany.userapi.models.User",
            "",
            "package com.somecompany.userapi.models;",
            "",
            "import java.util.SortedSet;",
            "",
            "import io.ascopes.katana.annotations.Equality;",
            "",
            "@Equality.Exclude",
            "@Equality.Exclude",
            "public interface User {",
            "  String getPrincipal();",
            "  String getCredential();",
            "  SortedSet<String> getAuthorities();",
            "}"
        ));

    assertThat(result)
        .failed();
    assertThat(result)
        .hadErrorContainingMatch("not (a )?repeat");
  }

  @Test
  void Equality_Exclude_cannot_be_applied_to_type() {
    Compilation result = Compiler
        .javac()
        .compile(forSourceLines(
            "com.somecompany.userapi.models.User",
            "",
            "package com.somecompany.userapi.models;",
            "",
            "import java.util.SortedSet;",
            "",
            "import io.ascopes.katana.annotations.Equality;",
            "",
            "@Equality.Exclude",
            "public interface User {",
            "  String getPrincipal();",
            "  String getCredential();",
            "  SortedSet<String> getAuthorities();",
            "}"
        ));

    assertThat(result)
        .failed();
    assertThat(result)
        .hadErrorCount(1);
    assertThat(result)
        .hadErrorContaining("not applicable");
  }

  @Test
  void Equality_Exclude_cannot_be_applied_to_annotation_type() {
    Compilation result = Compiler
        .javac()
        .compile(forSourceLines(
            "com.somecompany.userapi.models.Foo",
            "",
            "package com.somecompany.userapi.models;",
            "",
            "import java.util.SortedSet;",
            "",
            "import io.ascopes.katana.annotations.Equality;",
            "",
            "@Equality.Exclude",
            "public @interface Foo {",
            "}"
        ));

    assertThat(result)
        .failed();
    assertThat(result)
        .hadErrorCount(1);
    assertThat(result)
        .hadErrorContaining("not applicable");
  }

  @Test
  void Equality_Exclude_cannot_be_applied_to_package() {
    Compilation result = Compiler
        .javac()
        .compile(forSourceLines(
            "com.somecompany.userapi.models.package-info",
            "",
            "@Equality.Exclude",
            "package com.somecompany.userapi.models;",
            "",
            "import io.ascopes.katana.annotations.Equality;"
        ));

    assertThat(result)
        .failed();
    assertThat(result)
        .hadErrorCount(1);
    assertThat(result)
        .hadErrorContaining("not applicable");
  }

  @Test
  void Equality_Exclude_cannot_be_applied_to_constructor() {
    Compilation result = Compiler
        .javac()
        .compile(forSourceLines(
            "com.somecompany.userapi.models.User",
            "",
            "package com.somecompany.userapi.models;",
            "",
            "import io.ascopes.katana.annotations.Equality;",
            "",
            "public class User {",
            "  @Equality.Exclude",
            "  public User() {",
            "  }",
            "}"
        ));

    assertThat(result)
        .failed();
    assertThat(result)
        .hadErrorCount(1);
    assertThat(result)
        .hadErrorContaining("not applicable");
  }

  @Test
  void Equality_Exclude_can_be_applied_to_method() {
    Compilation result = Compiler
        .javac()
        .compile(forSourceLines(
            "com.somecompany.userapi.models.User",
            "",
            "package com.somecompany.userapi.models;",
            "",
            "import io.ascopes.katana.annotations.Equality;",
            "",
            "public class User {",
            "  @Equality.Exclude",
            "  public String getPrincipal() {",
            "    return \"Steve\";",
            "  }",
            "}"
        ));

    assertThat(result).succeeded();
  }

  @Test
  void Equality_Exclude_cannot_be_applied_to_field() {
    Compilation result = Compiler
        .javac()
        .compile(forSourceLines(
            "com.somecompany.userapi.models.User",
            "",
            "package com.somecompany.userapi.models;",
            "",
            "import io.ascopes.katana.annotations.Equality;",
            "",
            "public class User {",
            "  @Equality.Exclude",
            "  private String principal;",
            "}"
        ));

    assertThat(result)
        .failed();
    assertThat(result)
        .hadErrorCount(1);
    assertThat(result)
        .hadErrorContaining("not applicable");
  }

  @Test
  void Equality_Exclude_cannot_be_applied_to_parameter() {
    Compilation result = Compiler
        .javac()
        .compile(forSourceLines(
            "com.somecompany.userapi.models.User",
            "",
            "package com.somecompany.userapi.models;",
            "",
            "import io.ascopes.katana.annotations.Equality;",
            "",
            "public class User {",
            "  private String principal;",
            "",
            "  public void setPrincipal(@Equality.Exclude String principal) {",
            "    this.principal = principal;",
            "  }",
            "}"
        ));

    assertThat(result)
        .failed();
    assertThat(result)
        .hadErrorCount(1);
    assertThat(result)
        .hadErrorContaining("not applicable");
  }

  @Test
  void Equality_Exclude_cannot_be_applied_to_type_argument() {
    Compilation result = Compiler
        .javac()
        .compile(forSourceLines(
            "com.somecompany.userapi.models.User",
            "",
            "package com.somecompany.userapi.models;",
            "",
            "import java.util.Iterator;",
            "import java.util.SortedSet;",
            "",
            "import io.ascopes.katana.annotations.Equality;",
            "",
            "public class User implements Iterable<@Equality.Exclude String> {",
            "  private SortedSet<String> authorities;",
            "",
            "  @Override",
            "  public Iterator<String> iterator() {",
            "    return this.authorities.iterator();",
            "  }",
            "}"
        ));

    assertThat(result)
        .failed();
    assertThat(result)
        .hadErrorCount(1);
    assertThat(result)
        .hadErrorContaining("not applicable");
  }

  @Test
  void Equality_Exclude_cannot_be_applied_to_type_use() {
    Compilation result = Compiler
        .javac()
        .compile(forSourceLines(
            "com.somecompany.userapi.models.User",
            "",
            "package com.somecompany.userapi.models;",
            "",
            "import io.ascopes.katana.annotations.Equality;",
            "",
            "public class User {",
            "",
            "  @Override",
            "  public String toString() {",
            "    @Equality.Exclude",
            "    StringBuilder sb = new StringBuilder();",
            "",
            "    sb.append(\"User{}\");",
            "",
            "    return sb.toString();",
            "  }",
            "}"
        ));

    assertThat(result)
        .failed();
    assertThat(result)
        .hadErrorCount(1);
    assertThat(result)
        .hadErrorContaining("not applicable");
  }

  @EnabledForJreRange(min = JRE.JAVA_16)
  @Test
  void Equality_Exclude_cannot_be_applied_to_record_type() {
    Compilation result = Compiler
        .javac()
        .compile(forSourceLines(
            "com.somecompany.userapi.models.User",
            "",
            "package com.somecompany.userapi.models;",
            "",
            "import java.util.SortedSet;",
            "",
            "import io.ascopes.katana.annotations.Equality;",
            "",
            "@Equality.Exclude",
            "public record User(String principal, String credential, SortedSet<String> authorities) {",
            "}"
        ));

    assertThat(result)
        .failed();
    assertThat(result)
        .hadErrorCount(1);
    assertThat(result)
        .hadErrorContaining("not applicable");
  }

}