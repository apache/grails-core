---
name: mutation-testing
description: Guide for running targeted PITest bytecode mutation testing and verifying Java/Groovy semantic mutants across grails-core modules
license: Apache-2.0
---
<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

## What I Do

- Guide targeted bytecode mutation testing on Java and `@CompileStatic` Groovy classes using PITest (`GrailsCodeAnalysisPlugin`).
- Provide a structured **Java & Groovy Semantic Mutation Checklist** for catching high-risk Groovy/Java behavioral regressions that line coverage (JaCoCo) and bytecode mutators miss.
- Enforce assertion depth in Spock specifications so surviving mutants caused by smoke-only assertions (e.g. `result != null`, `predicates.length == 1`) are eliminated before committing.

## When to Use Me

Activate this skill when:

- Writing, refactoring, or fixing production classes in any `grails-core` module and verifying that the accompanying Spock tests actually kill behavioral mutants.
- Converting Java source files to statically-compiled Groovy (`java-to-groovy-conversion`).
- Auditing existing test suites where JaCoCo line coverage is high but assertions only check non-nullness or collection sizes.

---

## Tier 1: Targeted PITest Execution (Bytecode Mutation)

`GrailsCodeAnalysisPlugin` integrates `info.solidsoft.pitest` as an opt-in analysis tool with Groovy synthetic bytecode exclusions pre-configured.

> [!IMPORTANT]
> Always scope PITest runs to the specific class(es) and test spec(s) you touched using `-Pgrails.code-analysis.pitest.targetClasses` and `-Pgrails.code-analysis.pitest.targetTests`. Running unscoped mutation testing across an entire module can take tens of minutes.

### Quick Commands

```bash
# Run PITest on a single class (requesting :pitest auto-enables the plugin for that module; targetTests defaults to *)
./gradlew :grails-data-hibernate7-core:pitest \
  -Pgrails.code-analysis.pitest.targetClasses=org.grails.orm.hibernate.query.PredicateGenerator \
  -Pgrails.code-analysis.pitest.targetTests=grails.gorm.tests.hibernatequery.PredicateGeneratorSpec

# Run PITest on multiple classes/wildcards and multiple specs
./gradlew :grails-data-hibernate7-core:pitest \
  -Pgrails.code-analysis.pitest.targetClasses="org.grails.orm.hibernate.query.PredicateGenerator*" \
  -Pgrails.code-analysis.pitest.targetTests="grails.gorm.tests.hibernatequery.PredicateGeneratorSpec,grails.orm.HibernateCriteriaBuilderDirectSpec"

# Override mutator group (e.g. STRONGER, DEFAULTS, ALL) or thread count
./gradlew :grails-core:pitest \
  -Pgrails.code-analysis.pitest.targetClasses="grails.core.DefaultGrailsApplication" \
  -Pgrails.code-analysis.pitest.targetTests="grails.core.DefaultGrailsApplicationSpec" \
  -Pgrails.code-analysis.pitest.mutators=STRONGER \
  -Pgrails.code-analysis.pitest.threads=4
```

### Opting In from a Module `build.gradle`

A module can also opt in permanently via the `grailsCodeAnalysis` extension:

```groovy
grailsCodeAnalysis {
    enablePitest()
}
```

### Gradle Properties

| Property | Default | Description |
|----------|---------|-------------|
| `grails.code-analysis.enabled.pitest` | `false` (auto-enabled when `:pitest` task is requested) | Enable/disable PITest across all projects |
| `grails.code-analysis.enabled.pitest.projects` | *(empty)* | Comma-separated project paths (e.g. `:grails-core`) |
| `grails.code-analysis.pitest.targetClasses` | `org.apache.grails.*,org.grails.*,grails.*,${project.group}.*` | Comma-separated class globs to mutate |
| `grails.code-analysis.pitest.targetTests` | `*` (all tests in module) | Comma-separated test class globs to run |
| `grails.code-analysis.pitest.threads` | `4` | Number of concurrent mutation threads |
| `grails.code-analysis.pitest.mutators` | PITest `DEFAULTS` | Comma-separated mutator names or group (`DEFAULTS`, `STRONGER`) |

### Reports Location

PITest writes both XML and HTML reports (with `timestampedReports = false`) to:

```text
build/reports/code-analysis/pitest/<module-name>/
├── index.html
└── mutations.xml
```

Inspect surviving mutants from the command line with:

```bash
grep "status='SURVIVED'" build/reports/code-analysis/pitest/<module-name>/mutations.xml
```

### Pre-Configured Groovy Bytecode Exclusions

By default, upstream PITest enables its `FGROOVY` feature (`GroovyFilter`), which drops all mutations in every class implementing `GroovyObject`. `GrailsCodeAnalysisPlugin` disables that blanket filter (`features = ['-FGROOVY']`) so `@CompileStatic` Groovy classes are mutated alongside Java classes.

Compiler-generated methods annotated with `@groovy.transform.Generated` (such as `getMetaClass`, `setMetaClass`, `invokeMethod`, `getProperty`, and `setProperty`) remain filtered out by PITest's `@Generated` bytecode filter while preserving mutation coverage for handwritten methods with those names. In addition, `GrailsCodeAnalysisPlugin` excludes synthetic Groovy compiler helpers and closure classes that produce unkillable junk mutants:

- **Synthetic methods**: `$getStaticMetaClass`, `$getLookup`, `pfget$*`, `pfset$*`, `super$*`, `this$*`
- **Synthetic classes**: `*$_closure*`, `*$_*`
- **Runtime calls**: `org.codehaus.groovy.runtime`, `org.apache.groovy.runtime`, `groovy.lang.MetaClass`

---

## Tier 2: Java & Groovy Semantic Mutation Checklist

Bytecode mutators do not catch dynamic Groovy `CallSite` dispatches or subtle Java-to-Groovy semantic shifts. Before concluding work on any touched or converted class, verify that your Spock tests kill each of the following semantic mutants:

### 1. No Smoke-Only Assertions (`RETURN_VALS` / Operator Swap)
- **Anti-pattern**: Asserting only `result != null` or `predicates.length == 4`. A mutant that swaps `greaterThan` with `lessThan` or returns a dummy object survives.
- **Requirement**: Every test must assert the **observable outcome** (e.g. executing the query against test data and checking the returned entities, or inspecting the generated AST/expression operator and operands).

### 2. Relational & Boundary Operators (`CONDITIONALS_BOUNDARY`)
- For every `<`, `<=`, `>`, `>=`, `==`, `!=` branch:
  - Test the **exact boundary value** (where `<` and `<=` differ) as well as values on either side.
  - Ensure swapping `>` with `>=` or `==` with `!=` causes at least one Spock assertion to fail.

### 3. Groovy Truth vs. Explicit Null Checks (`?:` vs `!= null`)
- In Groovy, `""` (empty string), `0`, `0.0`, `false`, `[]`, and `[:]` evaluate to **falsy**.
- Whenever code branches on nullness or defaults a value (`val != null ? val : fallback` vs `val ?: fallback`), include test cases passing `""`, `0`, `false`, or `[]` to ensure valid falsy inputs are not treated as missing/null.

### 4. Reference Identity vs. Value Equality (`.is()` vs `==`)
- In Groovy, `a == b` calls `a.equals(b)` (or `a.compareTo(b) == 0` for `Comparable` types), whereas `a.is(b)` checks reference identity (`==` in Java).
- If reference identity or sentinel comparison is intended, verify with two distinct instances that are value-equal (`equals() == true`, `is() == false`).

### 5. Integer vs. `BigDecimal` Division (`intdiv` vs `/`)
- In Groovy, `int / int` produces a `BigDecimal` (`3 / 2 == 1.5`), whereas Java `int / int` truncates (`3 / 2 == 1`).
- Test division logic with non-evenly-divisible inputs (e.g. `3` and `2`) so a mutant swapping `/` and `.intdiv()` fails immediately.

### 6. Input & Collection Immutability (Side-Effect Mutants)
- Whenever a method accepts a mutable input (e.g. a `Collection`, `Map`, or `DetachedCriteria` / `QueryableCriteria`), assert that the caller's object is **not mutated in-place** (or reuse the same input across two distinct calls in the test) so in-place `.add()` / `.remove()` mutants are killed.

### 7. Fail-Fast Exception Contracts (`NegateConditionals` on Guard Clauses)
- For every guard clause that throws `ConfigurationException`, `IllegalArgumentException`, or `UnsupportedOperationException` on invalid/unresolved input:
  - Include an explicit `thrown(ExpectedException)` test.
  - Verify that replacing the exception with `return null` fails a test.
