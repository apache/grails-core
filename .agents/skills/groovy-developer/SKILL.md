---
name: groovy-developer
description: Expert guide for Groovy 4 development, covering concise syntax, closures, DSLs, metaprogramming, static compilation, and integration with Java 17 and Grails
license: Apache-2.0
---
<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0. 
-->

## What I Do

- Provide deep expertise in Groovy 4.0, including core language features: syntax, closures, traits, GStrings, operators, and Groovy Truth.
- Guide metaprogramming techniques: runtime (categories, ExpandoMetaClass, extension modules) and compile-time (AST transformations like @Immutable, @Builder, @Delegate, @Singleton, @Log, @Memoized).
- Assist with DSL creation and usage (MarkupBuilder, JsonBuilder, builders for configuration).
- Support testing with Spock Framework (BDD, data-driven tests, mocking) and built-in power assertions.
- Help integrate Groovy with Java 17: joint compilation, Gradle builds, Grails, and Spring Boot.
- Cover data handling: JSON/XML parsing and building, Groovy SQL, collection processing.
- Advise on performance: @CompileStatic, @TypeChecked, @GrailsCompileStatic for optimal execution.

## When to Use Me

Activate this skill for any Groovy-related task, including:

- Writing or debugging Groovy classes in Grails domain, controllers, services, or taglibs.
- Building or enhancing Gradle build scripts (build.gradle).
- Creating DSLs for configuration, testing, or business rules.
- Writing Spock specifications for unit and integration tests.
- Migrating from older Groovy versions (2.x/3.x) to 4.0.
- Performance optimization with static compilation.
- Working with JSON/XML data, GORM criteria queries, or builders.

## Groovy 4 Key Features

### Concise Syntax
```groovy
// No semicolons required
def name = 'Grails'

// Optional parentheses for method calls
println 'Hello World'
list.each { println it }

// Optional return keyword
String greet(String name) {
    "Hello, $name"  // Last expression is returned (GString needs double quotes)
}
```

### GStrings (String Interpolation)
```groovy
def user = "Alice"
def count = 42

// Simple interpolation
println "User: $user"

// Expression interpolation
println "Items: ${count * 2}"

// Lazy GStrings (evaluated when accessed)
def lazy = "Count is ${ -> counter }"

// Multi-line strings
def sql = """
    SELECT * FROM users
    WHERE name = '$user'
    ORDER BY created_at
"""
```

### Closures
```groovy
// Basic closure
def greet = { name -> "Hello, $name" }
println greet("World")

// Implicit 'it' parameter
def twice = { it * 2 }
println [1, 2, 3].collect(twice)

// Closure with delegate (for DSLs)
def configure = {
    name = "MyApp"
    version = "1.0"
}
configure.delegate = new Config()
configure.resolveStrategy = Closure.DELEGATE_FIRST
configure()

// Method references
def upper = String.&toUpperCase
println ["a", "b"].collect(upper)
```

### Safe Navigation and Elvis Operator
```groovy
// Safe navigation - returns null if any part is null
def city = user?.address?.city

// Elvis operator - default value if null
def name = user?.name ?: "Anonymous"

// Safe method calls
def length = text?.length() ?: 0

// Combine with collections
def firstTitle = books?.first()?.title ?: "No books"
```

### Spread Operator
```groovy
// Spread operator for collections
def names = users*.name  // Same as users.collect { it.name }

// Spread for method arguments
def args = [1, 2, 3]
someMethod(*args)

// Spread in list literals
def combined = [*list1, *list2]
```

### Collection Literals and Methods
```groovy
// List literal
def list = [1, 2, 3, 4, 5]

// Map literal
def map = [name: "Grails", version: 7]

// Range
def range = 1..10
def exclusive = 1..<10

// Powerful collection methods
list.find { it > 3 }           // First match: 4
list.findAll { it > 3 }        // All matches: [4, 5]
list.any { it > 3 }            // true
list.every { it > 0 }          // true
list.groupBy { it % 2 }        // [0:[2,4], 1:[1,3,5]]
list.collectEntries { [it, it * 2] }  // [1:2, 2:4, ...]

// Sorting
list.sort { a, b -> b <=> a }  // Descending
list.sort { -it }              // Descending (numeric)
```

### Groovy Truth
```groovy
// Falsy values: null, empty string, empty collection, 0, false
if (list) { }      // True if not null and not empty
if (string) { }    // True if not null and not blank
if (number) { }    // True if not 0

// Useful in conditionals
def items = getItems() ?: []  // Default to empty list
```

### Records
```groovy
// Immutable data class
record Point(int x, int y) {}

def p = new Point(10, 20)
println p.x()  // 10
println p      // Point[x=10, y=20]
```

### Traits
```groovy
trait Auditable {
    Date dateCreated
    Date lastUpdated

    def beforeInsert() {
        dateCreated = new Date()
    }

    def beforeUpdate() {
        lastUpdated = new Date()
    }
}

class Book implements Auditable {
    String title
}
```

## Static Compilation

### @CompileStatic
Enables static type checking and compilation for better performance:
```groovy
import groovy.transform.CompileStatic

@CompileStatic
class BookService {
    List<Book> findByAuthor(String author) {
        Book.findAllByAuthor(author)
    }

    int calculateTotal(List<Integer> prices) {
        prices.sum() as int
    }
}
```

### @TypeChecked
Type checking without full static compilation:
```groovy
import groovy.transform.TypeChecked

@TypeChecked
void process(String input) {
    println input.toUppercase()  // Compile error: method not found
}
```

### @GrailsCompileStatic
Grails-aware static compilation that understands dynamic finders:
```groovy
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
class BookController {
    def show(Long id) {
        respond Book.findByIdAndActive(id, true)  // Dynamic finder works with @GrailsCompileStatic
    }
}
```

## AST Transformations

### @Immutable
```groovy
import groovy.transform.Immutable

@Immutable
class Person {
    String name
    int age
}

def p = new Person(name: "Alice", age: 30)
// p.name = "Bob"  // Error: read-only
```

### @Builder
```groovy
import groovy.transform.builder.Builder

@Builder
class Email {
    String to
    String subject
    String body
}

def email = Email.builder()
    .to("user@example.com")
    .subject("Hello")
    .body("Content here")
    .build()
```

### @Delegate
```groovy
import groovy.lang.Delegate

class EnhancedList<T> {
    @Delegate List<T> list = []

    T secondLast() {
        list[-2]
    }
}

def enhanced = new EnhancedList<String>()
enhanced.add("a")  // Delegated to list
enhanced.add("b")
println enhanced.secondLast()  // "a"
```

### @Singleton
```groovy
import groovy.lang.Singleton

@Singleton
class ConfigManager {
    Map settings = [:]
}

ConfigManager.instance.settings['key'] = 'value'
```

### @Memoized
```groovy
import groovy.transform.Memoized

class Calculator {
    @Memoized
    BigInteger fibonacci(int n) {
        if (n <= 1) return n
        fibonacci(n - 1) + fibonacci(n - 2)
    }
}
```

### @Log Variants
```groovy
import groovy.util.logging.Slf4j

@Slf4j
class BookService {
    def save(Book book) {
        log.info "Saving book: ${book.title}"
        // ...
    }
}
```

## Builders and DSLs

### JsonBuilder
```groovy
import groovy.json.JsonBuilder

def builder = new JsonBuilder()
builder {
    name "Grails"
    version 7
    features(["GORM", "GSP", "Plugins"])
    metadata {
        author "Grails Team"
        license "Apache 2.0"
    }
}
println builder.toPrettyString()
```

### JsonSlurper
```groovy
import groovy.json.JsonSlurper

def json = '{"name": "Grails", "version": 7}'
def data = new JsonSlurper().parseText(json)
println data.name  // Grails
```

### MarkupBuilder (XML/HTML)
```groovy
import groovy.xml.MarkupBuilder

def writer = new StringWriter()
def xml = new MarkupBuilder(writer)
xml.books {
    book(id: 1) {
        title "Grails Guide"
        author "Team"
    }
}
println writer.toString()
```

### Custom DSL Example
```groovy
class HtmlBuilder {
    def html(@DelegatesTo(HtmlBuilder) Closure c) {
        c.delegate = this
        c.resolveStrategy = Closure.DELEGATE_FIRST
        "<html>${c()}</html>"
    }

    def body(String content) {
        "<body>$content</body>"
    }
}

def builder = new HtmlBuilder()
def result = builder.html {
    body "Hello World"
}
```

## Testing with Spock

### Basic Specification
```groovy
class BookServiceSpec extends Specification {

    def service = new BookService()

    def "should find book by title"() {
        given: "a book title"
        def title = "Grails Guide"

        when: "searching for the book"
        def book = service.findByTitle(title)

        then: "the book is found"
        book != null
        book.title == title
    }
}
```

### Data-Driven Tests
```groovy
@Unroll
def "max of #a and #b is #expected"() {
    expect:
    Math.max(a, b) == expected

    where:
    a | b || expected
    1 | 3 || 3
    7 | 4 || 7
    0 | 0 || 0
}
```

### Mocking
```groovy
def "should call repository when saving"() {
    given:
    def repo = Mock(BookRepository)
    def service = new BookService(repo)
    def book = new Book(title: "Test")

    when:
    service.save(book)

    then:
    1 * repo.save(book)
}
```

### Exception Testing
```groovy
def "should throw exception for invalid input"() {
    when:
    service.process(null)

    then:
    thrown(IllegalArgumentException)
}
```

## Groovy SQL
```groovy
import groovy.sql.Sql

def sql = Sql.newInstance(dataSource)

// Query
sql.eachRow("SELECT * FROM books WHERE author = ?", [authorName]) { row ->
    println "${row.title} by ${row.author}"
}

// Insert
sql.execute("INSERT INTO books (title, author) VALUES (?, ?)",
            [title, author])

// With GString (auto-parameterized)
sql.execute("INSERT INTO books (title) VALUES ($title)")
```

## Best Practices

### Performance
- Use `@CompileStatic` or `@GrailsCompileStatic` in production code.
- Avoid runtime metaprogramming in hot paths.
- Prefer `collect`/`findAll` over manual loops.
- Use `@Memoized` for expensive computations.

### Code Style
- Use safe navigation (`?.`) instead of null checks.
- Prefer Elvis operator (`?:`) for default values.
- Use GStrings for string interpolation.
- Prefer traits over inheritance for mixins.
- Use explicit types for public APIs, `def` for local variables.
- Always use parentheses in method calls for clarity.

### Grails-Specific
- Use `@GrailsCompileStatic` instead of `@CompileStatic` in Grails artefact classes if needed.
- Leverage GORM dynamic finders and where queries.
- Use command objects for form binding and validation.
- Follow convention over configuration.

## Common Pitfalls

- **`==` vs `is`**: `==` calls `equals()`, use `is` for identity comparison (same as Java).
- **GString in maps**: GStrings don't work as map keys; use `"key$idx".toString()` if needed.
- **Closure scope**: Understand `delegate`, `owner`, and `this` in closures.
- **Optional parentheses**: Can cause ambiguity; always use parentheses for clarity.
- **Static imports**: Always use explicit imports for statically imported methods.

## Resources

- **Groovy 4 Documentation**: https://docs.groovy-lang.org/docs/groovy-4.0.30/html/documentation/
- **Groovy Style Guide**: https://groovy-lang.org/style-guide.html
- **GORM Documentation**: https://grails.apache.org/docs/latest/grails-data/
- **Spock Framework**: https://spockframework.org/spock/docs/2.3/all_in_one.html
