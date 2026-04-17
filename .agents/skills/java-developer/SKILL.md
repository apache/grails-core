---
name: java-developer
description: Guide for developing in Java 17 LTS, including modern features, best practices, and integration with Groovy/Grails projects
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

## What I Do

- Provide guidance on Java 17 LTS syntax, features, and APIs for use in Grails/Groovy projects.
- Assist with code generation, refactoring, and debugging using Java 17 enhancements like records, sealed classes, pattern matching, and text blocks.
- Recommend best practices for Java code that interoperates with Groovy in mixed-language projects.
- Guide on tooling: Gradle builds, JDK setup, testing with JUnit 5/Spock, and profiling.

## When to Use Me

Use this skill when working on Java code within this repository, especially for:
- Writing Java classes that will be used alongside Groovy code.
- Implementing features using records, sealed classes, or pattern matching for instanceof.
- Migrating older Java code (e.g., Java 8/11) to Java 17 idioms.
- Performance optimization, security hardening, or module system (JPMS) questions.
- Understanding how Java code integrates with Groovy's dynamic features.

## Java 17 Key Features

### Records (JEP 395)
Immutable data carriers with auto-generated constructors, accessors, equals, hashCode, and toString:
```java
public record Book(String title, String author, int year) {
    // Compact constructor for validation
    public Book {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title cannot be blank");
        }
    }
}
```

### Sealed Classes (JEP 409)
Restrict which classes can extend or implement a type:
```java
public sealed interface Shape permits Circle, Rectangle, Triangle {
    double area();
}

public final class Circle implements Shape {
    private final double radius;
    public Circle(double radius) { this.radius = radius; }
    public double area() { return Math.PI * radius * radius; }
}

public non-sealed class Rectangle implements Shape {
    // Can be further extended
}
```

### Pattern Matching for instanceof (JEP 394)
Eliminate redundant casts:
```java
// Before Java 17
if (obj instanceof String) {
    String s = (String) obj;
    System.out.println(s.length());
}

// Java 17
if (obj instanceof String s) {
    System.out.println(s.length());
}
```

### Text Blocks (JEP 378)
Multi-line string literals with proper formatting:
```java
String json = """
    {
        "name": "Grails",
        "version": "7.0"
    }
    """;

String sql = """
    SELECT id, name, created_at
    FROM users
    WHERE status = 'ACTIVE'
    ORDER BY created_at DESC
    """;
```

### Switch Expressions (JEP 361)
Use switch as an expression with arrow syntax:
```java
String result = switch (day) {
    case MONDAY, FRIDAY, SUNDAY -> "Relaxed";
    case TUESDAY -> "Productive";
    case THURSDAY, SATURDAY -> "Moderate";
    case WEDNESDAY -> {
        var temp = calculateWorkload();
        yield temp > 5 ? "Busy" : "Normal";
    }
};
```

### Enhanced NullPointerException Messages (JEP 358)
Detailed NPE messages showing exactly which variable was null:
```
Cannot invoke "String.length()" because "user.getAddress().getCity()" is null
```

### Helpful Stream and Collection APIs
```java
// Stream.toList() - unmodifiable list
List<String> names = users.stream()
    .map(User::getName)
    .toList();

// Collectors improvements
Map<Status, List<User>> byStatus = users.stream()
    .collect(Collectors.groupingBy(User::getStatus));
```

## Java/Groovy Interoperability

### Calling Java from Groovy
Groovy seamlessly calls Java code:
```groovy
// Java record used in Groovy
def book = new Book("Grails Guide", "Author", 2024)
println book.title()  // Groovy can use property syntax too
println book.title    // Also works
```

### Calling Groovy from Java
```java
// Groovy classes are just Java classes
GroovyService service = new GroovyService();
service.process(data);

// Working with Groovy closures in Java
Closure<String> closure = ...;
String result = closure.call("input");
```

### Best Practices for Mixed Projects
- Use Java for performance-critical code with `@CompileStatic` equivalent behavior.
- Use Java records for DTOs shared between Java and Groovy code.
- Prefer Java interfaces that Groovy classes implement.
- Avoid Groovy-specific features (like categories) in APIs consumed by Java.

## Build and Testing

### Gradle Configuration
```groovy
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.compilerArgs += ['-parameters']  // Preserve parameter names
}
```

### Testing with JUnit 5
```java
@Test
@DisplayName("Book record should validate title")
void bookShouldValidateTitle() {
    assertThrows(IllegalArgumentException.class, () ->
        new Book("", "Author", 2024)
    );
}

@ParameterizedTest
@ValueSource(strings = {"", " ", "  "})
void blankTitlesShouldBeRejected(String title) {
    assertThrows(IllegalArgumentException.class, () ->
        new Book(title, "Author", 2024)
    );
}
```

### Testing with Spock (from Groovy)
```groovy
def "Java record should work in Spock tests"() {
    when:
    def book = new Book("Test", "Author", 2024)

    then:
    book.title() == "Test"
    book.author() == "Author"
}
```

## Performance and Profiling

### JVM Options for Java 17
```bash
# Recommended GC for most workloads
-XX:+UseG1GC

# For low-latency requirements
-XX:+UseZGC

# Memory settings
-Xms512m -Xmx2g

# Enable JFR for profiling
-XX:StartFlightRecording=duration=60s,filename=recording.jfr
```

### Profiling Tools
- **JDK Flight Recorder (JFR)**: Built-in low-overhead profiler
- **VisualVM**: GUI-based monitoring and profiling
- **async-profiler**: Low-overhead sampling profiler

## Common Patterns

### Null Handling with Optional
```java
public Optional<User> findById(Long id) {
    return Optional.ofNullable(repository.get(id));
}

// Usage
String name = findById(id)
    .map(User::getName)
    .orElse("Unknown");
```

### Resource Management with try-with-resources
```java
try (var reader = new BufferedReader(new FileReader(path));
     var writer = new BufferedWriter(new FileWriter(output))) {
    reader.lines()
        .map(String::toUpperCase)
        .forEach(line -> writer.write(line + "\n"));
}
```

### Immutable Collections
```java
// Create immutable collections
List<String> list = List.of("a", "b", "c");
Set<String> set = Set.of("x", "y", "z");
Map<String, Integer> map = Map.of("one", 1, "two", 2);

// Copy to immutable
List<String> copy = List.copyOf(mutableList);
```

## Code Style Guidelines

- Follow existing patterns in the codebase.
- Use `var` for local variables when the type is obvious from context.
- Prefer records for simple data carriers.
- Use sealed classes to model restricted type hierarchies.
- Add `@Override` annotation when overriding methods.
- Use meaningful parameter names (preserved with `-parameters` flag).

## Resources

- **Java 17 Documentation**: https://docs.oracle.com/en/java/javase/17/
- **Java Language Updates**: https://docs.oracle.com/en/java/javase/17/language/
- **JEP Index**: https://openjdk.org/jeps/0
