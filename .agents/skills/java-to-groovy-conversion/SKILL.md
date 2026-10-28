---
name: java-to-groovy-conversion
description: Procedure and review checklist for converting a Gradle project of grails-core from Java to Groovy in two commits, so the Java history stays visible, and keeping the converted code statically compiled, Java-compatible and idiomatic.
license: Apache-2.0
compatibility: opencode, claude, grok, gemini, copilot, cursor, windsurf
metadata:
  audience: maintainers
  frameworks: grails
  versions: 10
---

<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

# Java to Groovy conversion

Use this skill when converting the Java sources of a Gradle project of this repository to Groovy, and when reviewing such a slice. It is a framework-development skill and is not published. Read `.agents/skills/groovy-developer/SKILL.md` and `.agents/skills/java-developer/SKILL.md` first.

A conversion changes the language and nothing else. It carries no feature, no fix, no documentation change and nothing from a pull request that has not reached the branch it targets; when such a pull request merges up, its `.java` files would land next to the Groovy twins and be reviewed twice.

## Procedure

1. **Two commits per slice.**
   - A move commit that only runs `git mv` from each `src/*/java/**/X.java` to its Groovy path `src/*/groovy/**/X.groovy`. Every line of `git diff -M --name-status <base> <move>` must be `R100`, and the commit changes no content.
   - A conversion commit that rewrites the moved files. `git log --follow` then reaches the Java history of every converted file, however little the Groovy resembles the Java.
2. **Merge with a merge commit, never squash.** The history only survives if both commits do. Say so in the pull request description of every slice; the pull request guidelines of `CLAUDE.md` otherwise ask for a squash.
3. **Move the other references.** Repoint the RAT exclusions and any path of the module that names a `.java` file, and make sure no `.java` file is left in the module except a `JavaCallerPin` (see below).
4. **Build and test.** Run the module suites before and after and compare the counts: JUnit tests are still discovered, and no test was dropped. Add a spec for every behaviour fixed during the conversion.
5. **Write the description.** List every intentional difference and every remaining dynamic site found by the `javap` sweep below, and say that Checkstyle reports `NO-SOURCE` on a converted module and `codenarcTest` is skipped, so CodeNarc on `main` is the only analyzer left on it.

## Static compilation and the Java-facing API

- `@CompileStatic` goes on every converted class, enum, record, trait, test and fixture, and on an interface with default or static methods, whose bodies are code. A pure interface or an annotation type needs none. Use `@CompileDynamic` or `TypeCheckingMode.SKIP` only with a comment saying why.
- Run `javap -c -p` over the converted classes. There must be no use of `ScriptBytecodeAdapter`, `InvokerHelper` or call sites beyond `$getStaticMetaClass`, the `$INIT` of an enum and the sites the description lists. A field of a private nested class written from the outer class compiles to a dynamic write; write `x.@field = value`.
- Compare `javap -protected` of the old and the new jar. `public static final` keeps `public`, otherwise it becomes a property. Package-private members get `@PackageScope`. A field without a modifier becomes a property with a public setter. A constructor of a private nested class stays private.
- Records take `@RecordOptions(getAt = false, toList = false, toMap = false, size = false)` and `@TupleConstructor(defaults = false, namedVariant = false)`, otherwise they gain a `Map` constructor and collection methods. A component named `isX` or `getX` hits GROOVY-12451 and GROOVY-12452.
- `@Nullable` and `@Nonnull`, `throws` clauses, generics, varargs and the names of public parameters stay as they were. A parameter named like a method of the same class shadows it in Groovy: call the method with an explicit receiver (`this.sqlRestriction(...)`) instead of renaming the parameter. `@Deprecated` and `@Override` stay.
- A class with `getProperty(String)`, `setProperty`, `invokeMethod` or `getMetaClass` gets `@POJO` or a rename. A method named `plus`, `minus`, `getAt`, `putAt`, `call`, `asBoolean`, `isCase`, `next`, `and` or `or` is an operator for Groovy callers.
- A `JavaCallerPin.java` in the test source set pins constants, constructors and checked exceptions at compile time, so a Java caller that stops compiling fails the build.

## Semantics that change silently

- `==` is `equals()`: `this == o` inside `equals()` becomes `is()`, sentinels compare with `is()`, and `==` on `Comparable` values goes through `compareTo`.
- `?:` falls back on empty strings, zero and `false`, while `Optional.orElse` falls back only on null; write `!= null ?` where the Java did. `Optional.map` collapses a null result to absent.
- `Stream.toList()` is unmodifiable. Try-with-resources stays try-with-resources. `assert` is always on. `int / int` is a `BigDecimal`, and `1.5` is a `BigDecimal` too. `'c'` is a `String`; write `as char`. A `$` in a double-quoted string interpolates.
- `return` in a closure returns from the closure, and `this` and `super` mean something else inside it. A closure coerced with `as` is a proxy and a lambda is not.
- Inert annotations become live (`@Slf4j` adds a `log` that shadows the caller's in a delegate-first closure; `@Canonical`). `in`, `as`, `def` and `trait` are keywords. `Closure.call(new Object[]{x})` double-wraps its argument.

## Style, from the Groovy style guide

- No semicolons, no `public` on classes and methods, single quotes unless a string interpolates, no `.class`, property access instead of getters and setters, GStrings instead of concatenation, list and map literals, Elvis and `?.` only where the semantics are identical, `def` only where the type is unknown, explicit imports.
- Do not keep Java syntax for similarity: the move commit preserves the history, so the semicolons and the plain double quotes go in the conversion commit, and CodeNarc enforces it from there. Drop the `@SuppressWarnings` entries for PMD, which does not run on Groovy sources.
- The deeper idioms (property access, Elvis, literals, GStrings) may follow in a separate groovyism pull request, provided it sits in the stack before the next slice copies the shape.

## Comments, headers and ownership

- Diff the comment text of each pair of files and keep every comment and every Javadoc tag. A rewording that follows a real change is fine.
- Drop `@author` on files that carry the ASF header; keep it on vendored code. A new Grails-authored file gets the ASF header, and its RAT exclusion goes. No reviewer names or review-process notes in source.

## Checklist for a review

- History: `git diff -M --name-status` of the move commit is all `R100`; `git log --follow` on the file with the lowest similarity reaches the Java history; no `.java` left; RAT exclusions repointed.
- Static compilation and API: the `javap` sweeps above, `JavaCallerPin`, the `@CompileStatic` grep (`grep -L '@CompileStatic'` over the moved files lists only pure interfaces and annotation types).
- Semantics: the list above, read against the diff.
- Style: CodeNarc on `main` is clean with no `UnnecessarySemicolon` or `UnnecessaryGString` suppression; tests are checked by hand, because `codenarcTest` is skipped.
- Scope: nothing from an open pull request, no feature, no documentation change; the description lists every difference.
- Build: the module suites pass with the same test count, and CI runs on the final tree before the merge.
