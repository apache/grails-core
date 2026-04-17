---
name: grails-developer
description: Comprehensive guide for Grails 7 development, covering web applications, REST APIs, GORM, controllers, services, views, plugins, and testing with Spock and Geb
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

- Provide detailed guidance for building Grails 7 web applications and REST APIs.
- Assist with GORM for data modeling, controllers for request handling, services for business logic, and views (GSP, JSON, Markup).
- Support testing with Spock 2.3 (unit/integration tests) and Geb for browser automation.
- Guide plugin usage and development, security implementation, and deployment strategies.
- Help with configuration, internationalization, async programming, and performance optimization.

## When to Use Me

Activate this skill when developing with Grails 7, including:

- Building CRUD applications, RESTful APIs, or full-stack web applications.
- Working with GORM domain classes, constraints, and relationships.
- Creating controllers, services, interceptors, or tag libraries.
- Writing Spock specifications or Geb functional tests.
- Configuring plugins, security, caching, or database migrations.
- Deploying Grails applications to containers or cloud platforms.

## Technology Stack

Grails 7 is built on:
- **Spring Boot**: 4.0.x
- **Spring Framework**: 7.0.x
- **Groovy**: 4.0.x
- **Gradle**: 8.14.x
- **Spock**: 2.3-groovy-4.0
- **Jakarta EE**: 10 (migrated from javax.*)
- **Micronaut**: Optional via `grails-micronaut` plugin

## Project Structure

```
myapp/
├── grails-app/
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
└── gradle.properties         # Project properties
```

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

    static responseFormats = ['json', 'xml']

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
        xml { render book as XML }
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

### application.yml
```yaml
grails:
    profile: web
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

// Fields plugin for form rendering
implementation 'org.apache.grails.plugins:grails-fields:7.0.0'

// Asset Pipeline
runtimeOnly 'com.bertramlabs.plugins:asset-pipeline-grails:5.0.8'
```

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

## Build Commands

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

- **Grails 7 User Guide**: https://grails.apache.org/docs/latest/guide/single.html
- **GORM Documentation**: https://grails.apache.org/docs/latest/grails-data/
- **Grails Plugins**: https://grails.apache.org/plugins.html
- **Groovy 4 Documentation**: https://docs.groovy-lang.org/docs/groovy-4.0.30/html/documentation/
- **Spock Framework**: https://spockframework.org/spock/docs/2.3/all_in_one.html
- **Geb Manual**: https://groovy.apache.org/geb/manual/current/
