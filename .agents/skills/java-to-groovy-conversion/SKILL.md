---
name: java-to-groovy-conversion
description: Expert guide and checklist for converting Java sources to statically-compiled, idiomatic Groovy across grails-core modules
license: Apache-2.0
---
<!--
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Java to Groovy Conversion Guide

This skill governs the systematic refactoring of Java source code to statically compiled, idiomatic Groovy across `grails-core` modules.

## What I Do

- Define the mandatory two-commit slice procedure that preserves Git file history at 100% rename similarity.
- Detail the Java-facing API preservation rules (`javap -protected`, `@PackageScope`, constants visibility, compile-time pins).
- Provide the technical checklist of Groovy compilation and semantics gotchas (`is()` vs `==`, division, Elvis, records, lambdas).
- Outline the idiomatic Groovy code style and verification gates.

## When to Use Me

Activate this skill when:
- Converting any Java source file or module to Groovy in `apache/grails-core`.
- Reviewing pull requests in the Java-to-Groovy migration series.
- Investigating binary compatibility regressions or bytecode issues after conversion.

---

## 1. Commit Structure & History Protocol

Every conversion slice must strictly follow a **two-commit structure** to guarantee that Git tracks file history without breaking similarity thresholds.

### Commit 1: Move Only (`git mv`)
- Move all targeted `.java` files to `.groovy`:
  ```bash
  git mv path/to/MyClass.java path/to/MyClass.groovy
  ```
- **Zero code edits** in this commit. Whitespace, imports, and syntax must remain completely untouched.
- Verify that every file in the diff is recognized at 100% rename similarity:
  ```bash
  git diff -M --name-status HEAD~1 HEAD
  ```
  Every line must start with `R100`.

### Commit 2: Conversion to Idiomatic Groovy
- Apply all Groovy syntax transformations, static compilation, and idiomatic cleanups directly in this commit.
- Do not retain legacy semicolons, suppressions, or non-idiomatic syntax under the guise of rename similarity; Commit 1 has already anchored the file history.

### Merge Requirement: Merge Commits Only
> [!IMPORTANT]
> **Always merge with a merge commit (`--no-ff`). Never squash and never rebase-merge.**
> If squashed or rebased into a single commit, the move commit is lost, similarity drops below Git's detection threshold, and `git log --follow` loses the Java history.

---

## 2. Verification Protocol & Tooling

Every conversion slice must pass these mechanical checks before submission:

### 1. Rename History Verification
Confirm `git log --follow` traverses past the move commit into the original Java history:
```bash
git log --follow --oneline path/to/ConvertedClass.groovy
```

### 2. Binary API Preservation (`javap -protected`)
Diff the public/protected API surface of the old jar (built from Java) against the new jar (built from Groovy):
```bash
javap -protected -cp old-module.jar com.example.MyClass > /tmp/java-api.txt
javap -protected -cp new-module.jar com.example.MyClass > /tmp/groovy-api.txt
diff -u /tmp/java-api.txt /tmp/groovy-api.txt
```
Acceptable differences are limited to standard Groovy runtime artefacts (`GroovyObject`, `$getLookup`, `__$stMC`, enum `next`/`previous`/`MIN_VALUE`/`MAX_VALUE`) and arrays converted to varargs. There must be **no missing fields, missing methods, modified visibility, or altered checked exception signatures**.

### 3. Static Bytecode Sweep (`javap -c -p`)
Ensure all methods dispatch statically without unintended dynamic call sites:
```bash
javap -c -p build/classes/groovy/main/com/example/MyClass.class | grep -E "ScriptBytecodeAdapter|InvokerHelper|callCurrent|callSite"
```
Dispatches must not use dynamic call sites, except for known Groovy compiler internals (e.g. enum `$INIT`, `$getStaticMetaClass`, or anonymous inner class bridges).

### 4. Compile-Time Pin (`JavaCallerPin.java`)
Add or update a `JavaCallerPin.java` file in the module's test source set (`src/test/java/org/grails/.../JavaCallerPin.java`). This Java class directly references:
- Public constants and fields as fields (e.g. `String s = ConvertedClass.MY_CONSTANT;`).
- Constructors and varargs methods.
- Checked exceptions declared in `throws` clauses.

If a field was inadvertently converted to a Groovy property (getter/setter without a public field), `JavaCallerPin.java` fails compilation immediately.

### 5. Repository Validation
```bash
./gradlew :<module>:codeStyle
./gradlew :<module>:test
./gradlew validateRepositoryConventions
./gradlew rat
```

---

## 3. Technical Conversion Checklist

### A. Static Compilation
- [ ] `@CompileStatic` is present on **every** converted class, enum, record, trait, test, and fixture.
- [ ] Interfaces with default or static methods **must have `@CompileStatic`**. In Groovy, interface default methods contain executable bytecode; without `@CompileStatic`, their bodies compile dynamically with `CallSite` overhead.
- [ ] Pure interfaces (no default/static methods) and marker interfaces do not need `@CompileStatic`.
- [ ] Never use `@CompileDynamic` or `TypeCheckingMode.SKIP` without an explicit, documented reason.

### B. Java-Facing API Preservation
- [ ] **Constants (`public static final`)**: In Groovy, omitting `public` on a field turns it into a *property* with generated getters and private backing field. Java callers reading `MyClass.CONSTANT` fail with `NoSuchFieldError`. All `public static final` fields must explicitly retain `public`.
- [ ] **Package-Private Members**: Omitting a visibility modifier in Groovy defaults to `public` (for methods) or a property (for fields). Use `@PackageScope` on all package-private fields, methods, constructors, and classes.
- [ ] **Constructors of Private Nested Classes**: Must remain explicitly `private`.
- [ ] **Annotations & Signatures**:
  - Preserve all `@Nullable`, `@Nonnull`, `@Deprecated`, and `@Override` annotations.
  - Preserve exact parameter names and generic type arguments.
  - Preserve `throws` clauses on methods and constructors.
- [ ] **Records**:
  - Add `@RecordOptions(getAt = false, toList = false, toMap = false, size = false)` and `@TupleConstructor(defaults = false, namedVariant = false)` to prevent Groovy from generating a dynamic `Map` constructor that pulls in `InvokerHelper`.
  - Beware of record components named `getX` or `isX` (GROOVY-12451 / GROOVY-12452).
- [ ] **Meta-Methods & Operators**:
  - If a class defines `getProperty(String)`, `setProperty`, `invokeMethod`, or `getMetaClass`, annotate with `@POJO` or rename to avoid Groovy MOP interception.
  - Methods named `plus`, `minus`, `getAt`, `putAt`, `call`, `asBoolean`, `isCase`, `next`, `and`, or `or` become operators for Groovy callers.

### C. Groovy Semantics & Gotchas
- [ ] **Equality (`==` vs `is()`)**:
  - `==` in Groovy invokes `.equals()`.
  - Use `.is()` for reference identity checks (`this.is(o)`, sentinel objects).
  - Note: `==` on `Comparable` types delegates to `compareTo() == 0`.
- [ ] **Truthiness & Elvis (`?:`)**:
  - In Groovy, empty strings, empty collections, `0`, and `false` evaluate to `false`.
  - If Java code did `foo != null ? foo : defaultVal`, do **not** blindly convert to `foo ?: defaultVal` if `foo` could be an empty string, `0`, or `false`. Keep `foo != null ? foo : defaultVal` where strict null-checking is required.
  - `Optional.map`: Groovy transforms null to absent.
- [ ] **Division**:
  - In Groovy, `int / int` produces `BigDecimal`. Use `intdiv()` for integer division or explicitly cast if integer truncation is required.
  - Float/double literals without suffix (e.g. `1.5`) are `BigDecimal` instances in Groovy.
- [ ] **Character Literals**:
  - `'c'` in Groovy is a single-character `String`. Use `'c' as char` or `(char) 'c'` when interacting with char primitives or methods overloading `char` vs `String`.
- [ ] **String Interpolation**:
  - Use single quotes (`'...'`) for all literal strings.
  - Use double quotes (`"..."`) only when GString interpolation (`${...}`) is needed.
  - A literal `$` in double quotes will attempt interpolation; escape it (`\$`) or use single quotes.
- [ ] **Assertions**:
  - Groovy's `assert` statement is always executed at runtime regardless of JVM `-ea` flags.
  - If throwing `IllegalArgumentException` or `NullPointerException` is required by public API contracts, use `Objects.requireNonNull(...)` or explicit `if (...) throw ...`.
- [ ] **Closures vs. Lambdas**:
  - A closure coerced with `as` (`as Runnable`) allocates a proxy instance per call. Under `@CompileStatic`, standard Java lambdas (`() -> ...`) compile directly to `invokedynamic` without proxy overhead.
  - Inside a closure, `return` returns from the closure itself, not the enclosing method.
- [ ] **Streams**:
  - `Stream.toList()` in Java produces an unmodifiable list; keep `Stream.toList()` rather than coercing to a mutable Groovy list unless mutability is intended.
- [ ] **Keywords**:
  - `as`, `in`, `def`, `trait` are Groovy keywords. If Java methods or variables use these names, quote them (e.g. `builder.'in'(values)`).

### D. Idiomatic Groovy Style
- [ ] Strip all semicolons (`;`).
- [ ] Remove redundant `public` modifiers on classes, methods, and constructors (except `public static final` constants).
- [ ] Remove `.class` syntax (use `TargetClass` instead of `TargetClass.class`).
- [ ] Use property notation instead of explicit getter/setter calls (`user.name` instead of `user.getName()`).
- [ ] Use Groovy list (`[]`) and map (`[:]`) literals instead of `new ArrayList<>()` or `new HashMap<>()`.
- [ ] Drop dead PMD and SpotBugs suppressions (PMD and SpotBugs do not run on Groovy sources).
- [ ] Eliminate wildcard imports (`import java.util.*` -> explicit imports).

### E. Comments, Headers & Provenance
- [ ] Retain all existing Javadoc tags, parameter descriptions, and comments. Diff comment text between `.java` and `.groovy` to ensure nothing was lost.
- [ ] Remove `@author` tags on ASF-licensed files. Retain `@author` on third-party/vendored files.
- [ ] Preserve the Apache License header on all files.
- [ ] Repoint RAT exclusion rules in `gradle/rat-root-config.gradle` if files were renamed.

### F. Scope Discipline
- [ ] **Conversion Only**: Do not sneak in new features, bug fixes, dependency upgrades, or unrelated documentation edits.
- [ ] **No In-Flight Code**: Never include classes or modifications from open, unmerged PRs on other branches.
