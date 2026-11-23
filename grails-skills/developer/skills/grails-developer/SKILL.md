---
name: grails-developer
description: Comprehensive guide for Grails development, covering web applications, REST APIs, GORM, controllers, services, views, plugins, and testing with Spock and Geb, and the Grails 9 grails command line (create-app and the other create commands, feature add, run-app, test-app, war, the create-* code generation commands, commands of plugins such as dbm-update, the grailsw wrapper, the .grails project record, exit codes and JSON output for tools)
license: Apache-2.0
---
<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0. 
-->

## What I Do

- Provide detailed guidance for building Grails web applications and REST APIs.
- Assist with GORM for data modeling, controllers for request handling, services for business logic, and views (GSP, JSON, Markup).
- Support testing with Spock (unit/integration tests) and Geb for browser automation.
- Guide plugin usage and development, security implementation, and deployment strategies.
- Help with configuration, internationalization, async programming, and performance optimization.
- Drive the Grails 9 `grails` command line: create a project, generate its classes, add features later, build, test and run it, and read its JSON output from a tool.

## When to Use Me

Activate this skill when developing with Grails, including:

- Building CRUD applications, RESTful APIs, or full-stack web applications.
- Working with GORM domain classes, constraints, and relationships.
- Creating controllers, services, interceptors, or tag libraries.
- Writing Spock specifications or Geb functional tests.
- Configuring plugins, security, caching, or database migrations.
- Deploying Grails applications to containers or cloud platforms.
- Running `grails` or `./grailsw`: `create-app` and the other `create-` commands, `run-app`, `test-app`, `war`, `feature add`, the `project` commands, or a command of a plugin such as `dbm-update`.

## Technology Stack

Grails is built on:
- **Spring Boot**: 4.2.x
- **Spring Framework**: 7.1.x
- **Groovy**: 6.0.x
- **Gradle**: 9.7 or later
- **Spock**: 2.5-groovy-6.0
- **Jakarta EE**: 10 (migrated from javax.*)
- **Micronaut**: Optional via `grails-micronaut` plugin

## Project Structure

```
myapp/
├── .grails/                  # Record of how the project was generated (commit it, never edit it)
├── grails-app/
│   ├── commands/             # Commands of the application (grails NAME)
│   ├── conf/                 # Configuration
│   │   ├── application.yml   # Main config
│   │   ├── logback-spring.xml # Logging config
│   │   └── spring/resources.groovy # Spring bean definitions
│   ├── controllers/          # Request handlers
│   ├── domain/               # GORM domain classes
│   ├── i18n/                 # Message bundles
│   ├── init/                 # Bootstrap classes
│   ├── services/             # Business logic
│   ├── taglib/               # Custom GSP tags
│   ├── utils/                # Utility classes
│   └── views/                # GSP templates
├── src/
│   ├── main/groovy/          # Additional Groovy classes
│   ├── main/java/            # Java classes
│   ├── test/groovy/          # Test specifications
│   └── integration-test/groovy/ # Integration test specifications
├── build.gradle              # Build configuration
├── gradle.properties         # Project properties (grailsVersion)
├── gradlew, gradle/wrapper/  # Gradle wrapper: what every build command runs
└── grailsw, grails-wrapper.jar # Grails wrapper: the grails command of the project's version
```

Create it with `grails create-app` (see [The `grails` Command Line](#the-grails-command-line)), or at https://start.grails.org.

## Domain Classes (GORM)

### Basic Domain Class
```groovy
class Book {
    String title
    String author
    Date datePublished
    Integer pages

    static constraints = {
        title blank: false, maxSize: 255
        author blank: false
        datePublished nullable: true
        pages min: 1, nullable: true
    }

    static mapping = {
        table 'books'
        title column: 'book_title'
        datePublished type: 'date'
    }
}
```

### Relationships
```groovy
class Author {
    String name

    static hasMany = [books: Book]

    static mapping = {
        books cascade: 'all-delete-orphan'
    }
}

class Book {
    String title

    static belongsTo = [author: Author]
}

// Many-to-Many
class Book {
    static hasMany = [categories: Category]
    static belongsTo = Category
}

class Category {
    String name
    static hasMany = [books: Book]
}
```

### GORM Queries
```groovy
// Dynamic finders
Book.findByTitle("Grails Guide")
Book.findAllByAuthorLike("%Smith%")
Book.findByTitleAndAuthor("Guide", "Smith")
Book.countByPagesGreaterThan(100)

// Where queries
Book.where {
    title =~ "%Grails%" && pages > 100
}.list()

// Criteria queries
Book.withCriteria {
    like('title', '%Grails%')
    gt('pages', 100)
    order('datePublished', 'desc')
    maxResults(10)
}

// HQL queries
Book.executeQuery(
    "from Book b where b.author.name = :name",
    [name: 'Smith']
)

// Detached criteria (reusable)
def criteria = Book.where {
    pages > 100
}
criteria.list()
criteria.count()
```

### Transactions
```groovy
// In services (default transactional)
@Transactional
class BookService {
    def saveBook(Book book) {
        book.save(failOnError: true)
    }
}

// Manual transaction control
Book.withTransaction { status ->
    def book = new Book(title: "Test")
    book.save()
    if (someCondition) {
        status.setRollbackOnly()
    }
}
```

## Controllers

### Basic Controller
```groovy
class BookController {

    BookService bookService

    static allowedMethods = [save: 'POST', update: 'PUT', delete: 'DELETE']

    def index() {
        respond Book.list(), model: [bookCount: Book.count()]
    }

    def show(Long id) {
        respond Book.get(id)
    }

    def create() {
        respond new Book(params)
    }

    def save(Book book) {
        if (book.hasErrors()) {
            respond book.errors, view: 'create'
            return
        }
        bookService.save(book)
        redirect action: 'show', id: book.id
    }

    def edit(Long id) {
        respond Book.get(id)
    }

    def update(Book book) {
        if (book.hasErrors()) {
            respond book.errors, view: 'edit'
            return
        }
        bookService.save(book)
        redirect action: 'show', id: book.id
    }

    def delete(Long id) {
        bookService.delete(id)
        redirect action: 'index'
    }
}
```

### REST Controller
```groovy
import grails.rest.RestfulController

class BookController extends RestfulController<Book> {

    static responseFormats = ['json']   // add 'xml' only with the grails-xml module

    BookController() {
        super(Book)
    }

    // Override to customize
    @Override
    protected Book queryForResource(Serializable id) {
        Book.where { id == id && active == true }.find()
    }
}
```

### Command Objects
```groovy
class BookCommand implements Validateable {
    String title
    String author
    Integer pages

    static constraints = {
        title blank: false
        author blank: false
        pages min: 1, nullable: true
    }
}

class BookController {
    def save(BookCommand cmd) {
        if (cmd.hasErrors()) {
            respond cmd.errors
            return
        }
        // Process valid command
    }
}
```

### Content Negotiation
```groovy
class BookController {
    def show(Long id) {
        def book = Book.get(id)
        respond book  // Auto-negotiates based on Accept header
    }
}

// Or explicit
def show(Long id) {
    def book = Book.get(id)
    withFormat {
        html { render view: 'show', model: [book: book] }
        json { render book as JSON }
        // xml { render book as XML } needs org.apache.grails:grails-xml; grails.converters.XML is deprecated for removal in Grails 11
    }
}
```

## Services

### Basic Service
```groovy
import grails.gorm.transactions.Transactional

@Transactional
class BookService {

    def findByTitle(String title) {
        Book.findByTitle(title)
    }

    def save(Book book) {
        book.save(failOnError: true)
    }

    @Transactional(readOnly = true)
    def list(Map params) {
        Book.list(params)
    }

    def delete(Long id) {
        Book.get(id)?.delete()
    }
}
```

### Service with Events
```groovy
import grails.events.annotation.Publisher
import grails.events.annotation.Subscriber

class BookService {

    @Publisher
    def save(Book book) {
        book.save()
        // Automatically publishes 'book.saved' event
    }
}

class NotificationService {

    @Subscriber('book.saved')
    def onBookSaved(Book book) {
        // Handle event
        log.info "Book saved: ${book.title}"
    }
}
```

### Async Service
```groovy
import grails.async.Promise
import static grails.async.Promises.*

class BookService {

    Promise<Book> findAsync(Long id) {
        task {
            Book.get(id)
        }
    }

    def processBooks() {
        def p1 = task { Book.findAllByActive(true) }
        def p2 = task { Author.list() }

        waitAll(p1, p2)
        // Both complete
    }
}
```

## Views

### GSP (Groovy Server Pages)
```html
<!-- grails-app/views/book/list.gsp -->
<!DOCTYPE html>
<html>
<head>
    <title>Books</title>
    <asset:stylesheet src="application.css"/>
</head>
<body>
    <h1>Books (${bookCount})</h1>

    <g:each in="${bookList}" var="book">
        <div class="book">
            <h2>${book.title}</h2>
            <p>By ${book.author}</p>
            <g:link action="show" id="${book.id}">View</g:link>
        </div>
    </g:each>

    <g:if test="${bookList.empty}">
        <p>No books found.</p>
    </g:if>

    <g:paginate total="${bookCount}"/>

    <asset:javascript src="application.js"/>
</body>
</html>
```

### Common GSP Tags
```html
<!-- Links -->
<g:link controller="book" action="show" id="${book.id}">View</g:link>
<g:createLink action="list"/>

<!-- Forms -->
<g:form action="save">
    <g:textField name="title" value="${book?.title}"/>
    <g:textArea name="description" value="${book?.description}"/>
    <g:select name="category" from="${categories}" optionKey="id" optionValue="name"/>
    <g:checkBox name="active" value="${book?.active}"/>
    <g:submitButton name="submit" value="Save"/>
</g:form>

<!-- Conditionals -->
<g:if test="${condition}">...</g:if>
<g:elseif test="${other}">...</g:elseif>
<g:else>...</g:else>

<!-- Iteration -->
<g:each in="${items}" var="item" status="i">
    ${i}: ${item.name}
</g:each>

<!-- Rendering -->
<g:render template="bookRow" model="[book: book]"/>
<g:render template="bookRow" collection="${books}" var="book"/>

<!-- Security (with Spring Security plugin) -->
<sec:ifLoggedIn>Welcome, ${sec.username()}</sec:ifLoggedIn>
<sec:ifNotLoggedIn><g:link controller="login">Login</g:link></sec:ifNotLoggedIn>
```

### JSON Views
```groovy
// grails-app/views/book/show.gson
import myapp.Book

model {
    Book book
}

json {
    id book.id
    title book.title
    author book.author
    datePublished book.datePublished?.format('yyyy-MM-dd')
    _links {
        self {
            href "/books/${book.id}"
        }
    }
}
```

### Layouts
```html
<!-- grails-app/views/layouts/main.gsp -->
<!DOCTYPE html>
<html>
<head>
    <title><g:layoutTitle default="My App"/></title>
    <asset:stylesheet src="application.css"/>
    <g:layoutHead/>
</head>
<body>
    <nav><!-- Navigation --></nav>
    <main>
        <g:layoutBody/>
    </main>
    <footer><!-- Footer --></footer>
    <asset:javascript src="application.js"/>
</body>
</html>

<!-- Use in views -->
<html>
<head>
    <meta name="layout" content="main"/>
    <title>Books</title>
</head>
<body>
    <!-- Page content -->
</body>
</html>
```

## Interceptors

```groovy
class AuthInterceptor {

    AuthInterceptor() {
        matchAll()
            .excludes(controller: 'login')
            .excludes(controller: 'error')
    }

    boolean before() {
        if (!session.user) {
            redirect(controller: 'login')
            return false
        }
        true
    }

    boolean after() {
        // After action executes
        true
    }

    void afterView() {
        // After view renders
    }
}
```

## Tag Libraries

```groovy
class BookTagLib {

    static namespace = 'book'
    static defaultEncodeAs = [taglib: 'html']

    def formatTitle = { attrs, body ->
        def title = attrs.title ?: body()
        out << "<span class='book-title'>${title}</span>"
    }

    def ifHasBooks = { attrs, body ->
        if (Book.count() > 0) {
            out << body()
        }
    }
}

// Usage in GSP
<book:formatTitle title="${book.title}"/>
<book:ifHasBooks>
    <p>We have books!</p>
</book:ifHasBooks>
```

## Configuration

### Groovy Call Sites (invokedynamic)

Grails 9 compiles Groovy with invokedynamic, the Groovy 6 compiler's default, and does not ship the `groovy-callsite` module. There is nothing to configure, so the default is to **remove** what Grails 7 and 8 builds carried for the old classic-call-site default:

- `grails { indy = true }` (now redundant) and any `tasks.withType(GroovyCompile) { groovyOptions.optimizationOptions.indy = ... }` block
- an `org.apache.groovy:groovy-callsite` dependency

Offer classic call sites only as the alternative, for an application that has measured a difference and wants them. It opts out and declares the module itself, at the version the Grails BOM manages, once per source set that does not see `implementation` (for example the test fixtures of `java-test-fixtures`):

```groovy
grails {
    indy = false
}

dependencies {
    implementation 'org.apache.groovy:groovy-callsite'
}
```

Without the dependency the Groovy compiler stops and names the module. An application that applies GraalVM's native image plugin cannot opt out: a classic call site defines a class at run time, which an image cannot do, so the build fails with the reason.

One more case needs the module whatever the application's own setting: a library compiled with classic call sites, which Groovy 6 can load only with it. The symptom is `NoClassDefFoundError: org.codehaus.groovy.runtime.callsite.CallSiteArray` when the class is first used. Spock 2.5, Geb 8 and the Asset Pipeline 5.2 are fine; `org.testcontainers:testcontainers-spock` is not, so declare the module next to it, in the configuration that library is in:

```groovy
dependencies {
    testImplementation 'org.testcontainers:testcontainers-spock'
    testRuntimeOnly 'org.apache.groovy:groovy-callsite'
}
```

### application.yml

A Grails 9 application has no `grails.profile` key: nothing reads it, and the type of the application is in `.grails/project.yml`. Do not add it.

```yaml
grails:
    codegen:
        defaultPackage: myapp
    gorm:
        reactor:
            events: false
    mime:
        types:
            json: ['application/json', 'text/json']
            xml: ['text/xml', 'application/xml']

environments:
    development:
        dataSource:
            dbCreate: create-drop
            url: jdbc:h2:mem:devDb
    test:
        dataSource:
            dbCreate: update
            url: jdbc:h2:mem:testDb
    production:
        dataSource:
            dbCreate: none
            url: jdbc:postgresql://localhost/myapp
            driverClassName: org.postgresql.Driver
            dialect: org.hibernate.dialect.PostgreSQLDialect
            properties:
                jmxEnabled: true
                initialSize: 5
                maxActive: 50

server:
    port: 8080
    servlet:
        context-path: /myapp
```

### URL Mappings
```groovy
// grails-app/controllers/myapp/UrlMappings.groovy
class UrlMappings {

    static mappings = {
        // Default
        "/$controller/$action?/$id?(.$format)?" {
            constraints {
                // apply constraints here
            }
        }

        // REST resources
        "/api/books"(resources: 'book')

        // Custom mappings
        "/books/$id"(controller: 'book', action: 'show')
        "/search"(controller: 'book', action: 'search')

        // Named URL mappings
        name bookShow: "/books/$id" {
            controller = 'book'
            action = 'show'
        }

        // Error pages
        "500"(view: '/error')
        "404"(view: '/notFound')
    }
}
```

## Testing with Spock

### Unit Test - Controller
```groovy
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

class BookControllerSpec extends Specification
        implements ControllerUnitTest<BookController> {

    def setup() {
        controller.bookService = Mock(BookService)
    }

    void "index returns list of books"() {
        given:
        def books = [new Book(title: "Test")]
        controller.bookService.list(_) >> books

        when:
        controller.index()

        then:
        model.bookList == books
        view == '/book/index'
    }

    void "show returns 404 for missing book"() {
        when:
        controller.show(999)

        then:
        response.status == 404
    }
}
```

### Unit Test - Service
```groovy
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class BookServiceSpec extends Specification
        implements ServiceUnitTest<BookService> {

    void "save persists book"() {
        given:
        def book = new Book(title: "Test", author: "Author")

        when:
        def result = service.save(book)

        then:
        result.id != null
        Book.count() == 1
    }
}
```

### Unit Test - Domain
```groovy
import grails.testing.gorm.DomainUnitTest
import spock.lang.Specification

class BookSpec extends Specification
        implements DomainUnitTest<Book> {

    void "title cannot be blank"() {
        when:
        domain.title = ""
        domain.author = "Test"

        then:
        !domain.validate(['title'])
        domain.errors['title'].code == 'blank'
    }

    void "valid book passes validation"() {
        when:
        domain.title = "Valid Title"
        domain.author = "Author"

        then:
        domain.validate()
    }
}
```

### Integration Test
```groovy
import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import spock.lang.Specification

@Integration
@Rollback
class BookServiceIntegrationSpec extends Specification {

    BookService bookService

    void "save and retrieve book"() {
        given:
        def book = new Book(title: "Integration Test", author: "Tester")

        when:
        bookService.save(book)
        def found = Book.findByTitle("Integration Test")

        then:
        found != null
        found.author == "Tester"
    }
}
```

### Functional Test with Geb
```groovy
import geb.spock.ContainerGebSpec
import grails.testing.mixin.integration.Integration

@Integration
class BookFunctionalSpec extends ContainerGebSpec {

    void "can view book list"() {
        when:
        go '/book/index'

        then:
        title == 'Book List'
        $('h1').text() == 'Books'
    }

    void "can create new book"() {
        when:
        go '/book/create'
        $('form').title = 'New Book'
        $('form').author = 'Test Author'
        $('input[type=submit]').click()

        then:
        $('h1').text().contains('New Book')
    }
}
```

## Plugins

### Common Plugins
```groovy
// build.gradle

// Spring Security
implementation 'org.apache.grails.plugins:grails-spring-security-core:7.0.1'

// Database Migration (Liquibase)
implementation 'org.apache.grails.plugins:grails-database-migration:5.0.1'

// Caching
implementation 'org.apache.grails.plugins:grails-cache:7.0.0'

// Async support
implementation 'org.apache.grails.plugins:grails-async:7.0.0'

// AI models through Spring AI (Grails 8.1+); see the grails-ai-developer skill
implementation 'org.apache.grails:grails-spring-ai'

// Fields plugin for form rendering
implementation 'org.apache.grails.plugins:grails-fields:7.0.0'

// Asset Pipeline
runtimeOnly 'cloud.wondrify:asset-pipeline-grails'
```

Where Grails has a feature for a plugin, `grails feature add NAME` adds the dependency together with its configuration, as `create-app` would have; `grails feature list` names the features a project can get. `grails plugin-info NAME` gives the `dependencies` line of a plugin and the feature that adds it, and `grails list-plugins TEXT` searches the plugins.

### Spring Security Configuration
```groovy
// grails-app/conf/application.groovy
grails.plugin.springsecurity.userLookup.userDomainClassName = 'myapp.User'
grails.plugin.springsecurity.userLookup.authorityJoinClassName = 'myapp.UserRole'
grails.plugin.springsecurity.authority.className = 'myapp.Role'

grails.plugin.springsecurity.controllerAnnotations.staticRules = [
    [pattern: '/',               access: ['permitAll']],
    [pattern: '/error',          access: ['permitAll']],
    [pattern: '/index',          access: ['permitAll']],
    [pattern: '/admin/**',       access: ['ROLE_ADMIN']],
    [pattern: '/api/**',         access: ['isAuthenticated()']],
]
```

## The `grails` Command Line

Grails 9 has one command line, `grails`. It creates projects with the Grails Forge generator (the one https://start.grails.org uses), writes the classes of a project, adds features to it later, and runs its Gradle build. There are no profiles and no separate Grails shell. In a project, use the project's Grails wrapper, `./grailsw`, which runs the command line of the project's `grailsVersion` and takes the same commands.

`grails help` lists the commands of the current directory (a project has more than any other directory), and `grails help NAME` describes one without running anything. Check there before writing a command from memory.

The details are in the guide; `<version>` is the application's `grailsVersion`, or `snapshot`:

| URL | Use for |
|-----|---------|
| `https://grails.apache.org/docs/<version>/guide/commandLine.html` | The environment, short forms, help, exit codes, commands of plugins, environment variables |
| `https://grails.apache.org/docs/<version>/guide/commandLine.html#tour` | One pass from installing to maintaining a project |
| `https://grails.apache.org/docs/<version>/guide/commandLine.html#creatingProject` | Every `create-` option, the `.grails` record, `feature add`, the `project` commands |
| `https://grails.apache.org/docs/<version>/guide/commandLine.html#gradleBuild` | What `compile`, `test-app`, `run-app` and `stop-app` run in Gradle |
| `https://grails.apache.org/docs/<version>/ref/Command%20Line/<command>.html` | One command: `create-app.html`, `feature-add.html`, `project-health.html`, ... |
| `https://grails.apache.org/docs/<version>/guide/upgrading.html#cliMappingGrails8` | Every Grails 8 command line and what it is in Grails 9 |

### Creating a Project

| Command | Creates |
|---------|---------|
| `grails create-app NAME` (also `create-webapp`) | A web application |
| `grails create-restapi NAME` | A REST API application |
| `grails create-plugin NAME` | A plugin without a web layer |
| `grails create-web-plugin NAME` | A plugin with a web layer |
| `grails create-rest-api-plugin NAME` | A plugin with a REST API |

```bash
grails create-app com.example.shop                 # directory shop, default package com.example
grails create-app --list-features                  # the features by category, defaults marked (+)
grails create-app com.example.shop --features postgres,grails-quartz --exclude-features mockito
grails create-restapi com.example.api --data hibernate7 --servlet jetty --jdk 25
cd my-shop && grails create-app --inplace          # in the current directory: project my-shop, package my.shop
grails create-app com.example.shop --preset com.acme:acme-service-preset:1.0.0
grails create-app com.example.shop --features com.acme:acme-audit-grails:2.3.0   # the feature pack of a plugin
```

- The command is the application type. There is no `--profile`.
- `--features` adds to the default features: do not repeat the defaults. `--exclude-features` leaves a default out. A name that is no feature, or features that cannot be had together, end the command with exit code 2, and nothing is created.
- `--data` (`hibernate5` by default, `hibernate7`, `mongodb`, `neo4j`), `--servlet` (`tomcat` by default, `jetty`, `undertow`, `none`), `--reloading` (`devtools` by default, `jrebel`, `none`) and `--jdk` (`21` by default) choose between alternatives. An option you give is a choice, and a feature that contradicts it is refused; an option you leave out gives way to a feature that needs another value, so `--features gorm-hibernate7` alone gives a Hibernate 7 project.
- `--inplace` creates the project in the current directory, named after it when no name is given. `--subproject` creates it as a project of the Gradle build the directory is in.
- A preset (`--preset`) and the feature pack of a plugin (given by `group:artifact:version`, `--feature-pack-dir`, or a catalog of `--feature-catalog` or `GRAILS_FORGE_CATALOG`) run code while the project is generated: use them only from a source you trust.
- A generated project has the Gradle wrapper, the Grails wrapper and the `.grails` record. Commit all three.

### The Record of a Project: `.grails/`

| File | Holds |
|------|-------|
| `.grails/project.yml` | What was asked for: the type, name, package, options, and the features asked for and left out |
| `.grails/forge.lock.yml` | What that came to: the Grails version and every feature, in the order they were applied |
| `.grails/baseline/` | A copy of every file as it was generated |

- Commit `.grails` with the project. The generated `.gitignore` leaves out only what belongs to one machine: `.grails/cache/`, `.grails/run/`, `.grails/transactions/` and `.grails/history`.
- Never edit, reformat or regenerate these files by hand. They carry digests of each other, and a record that was changed by hand is not used: restore it from version control.
- The application does not read the record; the command line does. The `create-*` commands take the type, the package and the features from it, which is why `create-job` is there only with the `grails-quartz` feature and `create-restful-controller` only in a REST API project. `feature add`, `feature list`, `project show` and `project regenerate` work from it.
- An application that Grails 8 or earlier created has no record: `grails project migrate` writes one, and the `create-*` commands are there afterwards. `feature add` and `project regenerate` still refuse such an application, because it has no baseline.

### Working in a Project

The commands work in every directory of the project. The ones that build, test or run start the project's Gradle wrapper, so they build exactly what `./gradlew` builds.

| Command | Does |
|---------|------|
| `grails run-app` (also `run`) | Gradle `bootRun` in `development`, until Ctrl-C. `--port 9090`, `--https` (a self-signed certificate, port 8443 unless one is given), `--debug-jvm` |
| `grails run-app --detach` | Returns once the application answers on its port and leaves it running; its output is in `.grails/run/app.log` |
| `grails stop-app` (also `stop`) | Stops what `grails run-app` started; not an application of `./gradlew bootRun` |
| `grails test-app` | Every test phase, which is every `Test` task of the build (Gradle `testApp`), in `test` |
| `grails test-app --unit`, `--integration`, `--functional` | Only those phases: a phase is the name of its task without `Test`, so `--functional` is `functionalTest` |
| `grails test-app BookSpec`, `grails test-app --unit "*ControllerSpec"` | The named tests, in whichever phase has them. `--clean` runs them again, `--continuous` on every change |
| `grails compile`, `grails clean` | Gradle `classes`, `clean` |
| `grails assemble` (also `war`, `package`) | Gradle `assemble`, in `production`; `--clean` runs `clean` first |
| `grails gradle ARGS` | The Gradle wrapper with exactly these arguments: `grails gradle dependencies --configuration runtimeClasspath` |
| `grails create-controller Book` | A class and its test, in the default package unless the name has one; likewise `create-domain-class`, `create-service`, `create-taglib`, `create-interceptor`, `create-unit-test`, `create-integration-test`, `create-functional-test`, `create-command`, `create-script`, and `create-restful-controller`, `create-domain-resource`, `create-job` where the project has them |
| `grails create-controller Book --force` | Replaces files that exist. Without `--force` an existing file ends the command with exit code 1 and nothing is written |
| `grails add-property Book title:String` | Adds a property to a domain class |
| `grails install-templates` | Copies the templates of the `create-*` commands to `src/main/templates`, where the project can change them |
| `grails feature add cache` | Adds a feature as if the project had been created with it, keeping your own changes; review the diff and commit it with `.grails` |
| `grails feature add postgres --dry-run` | Lists what would change and changes nothing; `--diff` shows the lines |
| `grails feature add mysql --replace` | Replaces a feature that was asked for by name (here `postgres`); a default such as `h2` is replaced without `--replace` |
| `grails feature list` | The features the project has, those `feature add` can add with what each replaces, and why the others cannot be added |
| `grails project show` | How the project was generated, ending with the `create-` command that makes it again |
| `grails project regenerate --diff` | The files that are not as generated; exit code 1 when it lists one. `--output DIR` writes the project as generated |
| `grails project migrate` | Writes the record of an application that has none, or moves the record to the running Grails 9 version |
| `grails project health` | What breaks the build, what the upgrade notes still ask and what the guide recommends, each with a fix; `--strict` fails on warnings as well, `-v` lists the checks that passed |
| `grails shell`, `grails console` | The Groovy shell or the Swing console of the application, with `ctx` and `grailsApplication` |
| `grails run-script scripts/load.groovy` | A Groovy script with the application started around it (not a file of `src/main/scripts`) |
| `grails stats`, `grails dependency-report`, `grails bug-report` | Lines of code; Gradle `dependencies`; a ZIP of the sources for an issue (read it for secrets before you attach it) |
| `grails list-plugins security`, `grails plugin-info spring-security` | Plugins and their coordinates, and how one is added |
| `grails update-wrapper` | Replaces `grailsw`, `grailsw.bat` and `grails-wrapper.jar` with those of the running version |

`feature add` never removes a feature, never changes an option such as `--data` or `--servlet`, and does not move the project to another Grails version. When your change and the feature's change touch the same lines it writes nothing and names the conflict; separate them and run it again.

### How a Command Line Is Written

```bash
grails run-app -Dgrails.env=test                     # the environment follows the name of the command
grails war -Dgrails.env=production
grails compile -- --refresh-dependencies            # options of Gradle follow --
grails test-app -- -x functionalTest                 # every phase but one
grails --plain-output --non-interactive test-app     # options of grails itself
grails cDC Book                                      # short form of create-domain-class
```

- The environment is `-Dgrails.env=NAME` after the name of the command. Without it, `run-app`, `console`, `shell`, `run-script` and `run-command` run in `development`, `test-app` in `test`, and `assemble`, `war` and `package` in `production`. An environment or a `-D` in front of the command (`grails prod war`, `grails test run-app`, `grails -Dgrails.env=staging war`) is refused with exit code 2. The command line does not read `GRAILS_ENV`.
- Options and tasks of Gradle follow `--`. An option the command does not have is refused with exit code 2, never ignored.
- In front of the command only the options of `grails` itself are taken: `--plain-output`, `--non-interactive`, `-x` (`--stacktrace`), `-v` (`--verbose`), `-h` (`--help`) and `-V` (`--version`). `-v` is not the version.
- `--non-interactive`: nothing waits for input; the builds it starts get no input and see `GRAILS_NON_INTERACTIVE=true`, and `shell` and `console` are refused. `--plain-output`: plain text, no colours, no progress display of Gradle. Give both in scripts, CI jobs and agent runs.
- Short forms are the starts of the words of a name: `cDC` is `create-domain-class`, `rA` is `run-app`, `tA` is `test-app`, `cA` is `create-app`. All-lowercase initials work as well: `ra`, `sa` (`stop-app`), `cdc`. A short form that fits two commands runs neither and ends with exit code 2: in a project `cC` fits `create-controller` and `create-command`, `cS` fits `create-service` and `create-script`, so type more (`cCont`, `cSe`). Write whole names in scripts and documentation.
- Exit codes: `0` the command did what it was asked; `2` a mistake in the command line (a command or option there is not, features that cannot be had together), and nothing ran; `1` any other failure (a failed build, no project, a file in the way).

### Commands of the Application and Its Plugins

```bash
grails dbm-update                                    # Database Migration plugin
grails s2-quickstart com.example User Role           # Spring Security plugin
grails url-mappings-report
grails create-command data-import                    # grails-app/commands/<package>/DataImportCommand.groovy
grails data-import orders.csv
grails run-command -Dgrails.env=production dbm-update
./gradlew runCommand -Pargs="dbm-update"             # the same command without grails
```

- The commands of the plugins and of `grails-app/commands` are commands of `grails` by their names. They run through the Gradle task `runCommand`, in `development`, most of them with the application started. Everything after the name is the command's own: `grails dbm-update -Dgrails.env=production` still runs in `development`, with a warning.
- `grails run-command NAME ARGS` runs the same command. Use it to put the environment in front of the name, for a command whose name a command of `grails` has (`grails run-command install-templates` copies the scaffolding templates; `grails install-templates` is the built-in one), and in scripts and CI jobs.
- The build lists these commands every time it compiles the project, in `.grails/cache/command-catalog.json` (not committed). In a checkout that was never built, the first such name you type has the build list them, which compiles the project, and then runs the command; `grails gradle grailsCommandCatalog` lists them on purpose. With `GRAILS_LIST_COMMANDS=false` a name that is not listed is refused at once with exit code 2 and no build is started.
- `grails help NAME` describes such a command without running it. `grails NAME --help` runs the command and gives it `--help`.
- Scripts in `src/main/scripts/<name>.groovy`, written by `create-script`, are commands by their names too. The build runs them (`runScriptCommand`) without the application and its classes. They are deprecated: write a new command with `create-command`, which runs with the application started.

### Interactive Mode

```console
$ grails
grails> create-domain-class Book
grails> run-app
grails> !git status --short
grails> exit
```

- `grails` without a command starts interactive mode in a project, and shows the help elsewhere. It takes the same commands, completes them on TAB, and runs builds faster.
- `run-app` there returns to the prompt and the application runs until `stop-app` or `exit`; `run-app --detach` leaves it running after `exit`.
- `!program` runs the rest of the line in your shell, and `!` alone runs the last command again. `exit` or Ctrl-D leaves; there is no `quit` and no `open` (use `!open FILE`).
- An agent runs one command per invocation, with `--non-interactive`, and does not start interactive mode.

### The Wrappers

- `./grailsw` (`grailsw.bat` on Windows) downloads the command line of the project's `grailsVersion` to `~/.grails/wrapper` and runs it. Commit `grailsw`, `grailsw.bat` and `grails-wrapper.jar`. Use it in the project and in CI: `feature add` and `project regenerate` need the version whose record the project has.
- To move to a later Grails 9 release: change `grailsVersion` in `gradle.properties`, run `./grailsw update-wrapper` and commit the three files, then `./grailsw project migrate` (it moves the record) and `./grailsw project health`. Upgrading from Grails 8 is the job of the `grails-upgrade` skill.
- Commit the Gradle wrapper too (`gradlew`, `gradlew.bat`, `gradle/wrapper/`). `grails` has no Gradle of its own: in a project without the wrapper the commands that build end with exit code 1. Change the Gradle version with `./gradlew wrapper --gradle-version VERSION --gradle-distribution-sha256-sum SHA256`, the checksum of that version from https://gradle.org/release-checksums/: the generated wrapper has `distributionSha256Sum` and refuses a download that does not match it.
- Memory and JVM options: of the build, `org.gradle.jvmargs` in `gradle.properties`; of the application and the tests, the `bootRun` and test tasks in `build.gradle`; of the `grails` command itself, `GRAILS_OPTS`.

### Output for Tools and Agents

| Command | Writes |
|---------|--------|
| `grails help --json` | The commands of the current directory with their options, parameters, choices and defaults; `complete` is `false` while the commands of the application are not listed |
| `grails feature add NAME --dry-run --json` | `outcome`, `reason`, `applicable`, the features `added` and `replaced`, and the `files` with what would be done to each |
| `grails project regenerate --diff --json` | `outcome`, and the `files` that were `added`, `removed` or `changed` |
| `grails project health --json` | Every finding with its id, level and fix |
| `grails project show --json` | The record, and the `create-` command that makes the project again |

Each writes one JSON document on one line of the standard output and nothing else, and ends with the exit code it has without `--json`. `format` is `1`; ignore members you do not know, and go by `outcome` and `reason`, not by `message`. `--json` of `feature add` needs `--dry-run`, and that of `project regenerate` needs `--diff`; without them it is exit code 2. `list-plugins` and `plugin-info` have `--json` as well.

### Environment Variables

| Variable | Effect |
|----------|--------|
| `JAVA_HOME` | The Java that runs `grails` and `grailsw`: 21 or later |
| `GRAILS_OPTS`, `JAVA_OPTS` | JVM options of the `grails` command, such as `-Dhttps.proxyHost=...`; not of the build or the application |
| `GRAILSW_OPTS` | JVM options of the Grails wrapper |
| `GRAILS_REPO_URL` | Maven repositories ahead of the default ones: for the wrapper, and in the build of a project that is generated |
| `GRAILS_PLUGIN_INDEX` | The plugin index of `list-plugins` and `plugin-info` (an `https` or `file:` address, or `none`), and the feature catalog of the `create-` commands when `GRAILS_FORGE_CATALOG` is not set |
| `GRAILS_FORGE_CATALOG` | The feature catalogs of the `create-` commands, separated by `;`, or `none` |
| `GRAILS_LIST_COMMANDS` | `false`: a name that is no command is refused at once, without having the build list the commands of the application |
| `GRAILS_GRADLE_RUNNER` | `process`: every command starts the Gradle wrapper, also in interactive mode, the way out when a build fails through the Gradle Tooling API. `tooling-api` and `auto` (the default) are the others |
| `GRAILS_CLI_DIAGNOSTICS` | `true`: the standard error says how each command runs Gradle, for a bug report |
| `GRAILS_SAFE_DIRECTORY` | Projects to use although another user owns them, as in a container, a CI job or under `sudo` |
| `GRAILS_WRAPPER_HOME`, `GRAILS_WRAPPER_ALLOWED_TYPES` | Where the wrapper keeps the command lines it downloads, and the kinds of release it may use (`SNAPSHOT,RC`) |
| `NO_COLOR` | No colours |
| `GRAILS_NON_INTERACTIVE` | Not read by `grails`: set to `true` for the build when `--non-interactive` is given, for your own commands and scripts to read |

### Do Not (Habits from Grails 8 and Earlier)

- No `--profile`, `list-profiles`, `profile-info` or `create-profile`: use the `create-` command of the type, `--list-features` and `feature list`, and `--preset` for a starting point of your own.
- No `profile` dependency in `build.gradle` (the build fails with it) and no `grails.profile` in `application.yml` (nothing reads it).
- No `grails test`: `grails test-app`; `test` in front of a command is no environment either. (`grails run` and `grails stop` still work as other names of `run-app` and `stop-app`; write the whole names in scripts.)
- No environment in front of the command (`grails prod war`, `grails test run-app`, `grails -Dgrails.env=staging war`): `grails war -Dgrails.env=production`.
- No Gradle options without `--` (`grails --offline run-app`): `grails run-app -- --offline`.
- No `grails install`: `grails gradle publishToMavenLocal`.
- No `-t forge`, `--type forge`, `grails-forge-cli`, `grails-shell-cli` or `GRAILS_PREFERRED_SHELL`: there is one `grails`.
- No `quit` or `open` in interactive mode: `exit`, `!open FILE`.
- No `grails -v` for the version: `grails --version`.
- No hand edits of `.grails`, and no `grails-forge-cli.yml` for a new project.
- No new script commands: `create-command`.
- Do not count on an unknown option being ignored, on `cC` meaning `create-controller`, or on `stop-app` stopping `./gradlew bootRun`.

## Build Commands

The `grails` commands above run these tasks through the Gradle wrapper; `./gradlew` runs them without `grails`:

```bash
# Run application
./gradlew bootRun

# Run tests
./gradlew test                                    # All tests
./gradlew test --tests "BookServiceSpec"          # Single spec
./gradlew test --tests "*Spec.save*"              # Pattern match
./gradlew integrationTest                         # Integration tests

# Build
./gradlew build                                   # Build with tests
./gradlew bootJar                                 # Executable JAR
./gradlew bootWar                                 # WAR file

# Code quality
./gradlew check                                   # Run all verification tasks

# Clean
./gradlew clean
```

## Best Practices

### Convention Over Configuration
- Follow Grails naming conventions for automatic wiring.
- Use standard directory structure.
- Let Grails infer configurations where possible.

### Performance
- Use `@GrailsCompileStatic` on controllers, services, and domain classes.
- Enable query caching for read-heavy operations.
- Use pagination for large result sets.
- Avoid N+1 queries with eager fetching or batch size.

### Security
- Use CSRF tokens in forms: `<g:form useToken="true">`.
- Apply Spring Security for authentication/authorization.
- Use parameterized queries (GORM handles this automatically).
- Encode output to prevent XSS.

### Testing
- Write unit tests for domain constraints.
- Test controllers with `ControllerUnitTest`.
- Use integration tests for service/database interactions.
- Use Geb for critical user workflows.

### Code Style
- Use `@GrailsCompileStatic` or `@CompileStatic` for type safety and performance.
- Prefer services for business logic over controllers.
- Use command objects for complex form handling.
- Keep controllers thin, services fat.

## Resources

- **Grails User Guide**: https://grails.apache.org/docs/latest/guide/single.html
- **The Command Line**: https://grails.apache.org/docs/latest/guide/commandLine.html
- **GORM Documentation**: https://grails.apache.org/docs/latest/grails-data/
- **Grails Plugins**: https://grails.apache.org/plugins.html
- **Groovy 5 Documentation**: https://groovy-lang.org/documentation.html#all-versions (select latest 5.1.x)
- **Spock Framework**: https://spockframework.org/spock/docs/2.4/all_in_one.html
- **Geb Manual**: https://groovy.apache.org/geb/manual/current/
