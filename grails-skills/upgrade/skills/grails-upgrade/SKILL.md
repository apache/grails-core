---
name: grails-upgrade
description: "Upgrades a Grails application to Grails 9.0 (release 9.0.0-M1). Base version: Grails 8; use it for an application on Grails 8 or on an earlier Grails 9.0 release. An application on Grails 7 or earlier first upgrades to the last Grails 8 release. Covers Groovy 6, Spring Boot 4.2 and Spring Framework 7.1, graphql-java 26, the GPars deprecation in favour of groovy.concurrent, invokedynamic compilation, the separate grails-xml module, the opt-in settings for Spring JSON rendering, Jackson request binding, Spring MVC exception handlers and response bodies, the Jackson-backed JSON converter and domain class rendering, RFC 9457 validation problems, format resolution, web unit tests, generated GORM id and version properties, RxGORM on RxJava 3, the deprecated RxJava 1 and 2 modules, the character encoding filter, configuration keys below a value that is not a map, JLine 4, and the command lines of application commands"
license: Apache-2.0
---

<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

## What I Do

- Guide upgrades of Grails applications from Grails 8.x to Grails 9.
- Guide upgrades from one Grails 9 release to a later one, with the changes of each release listed under that release.
- Turn the Grails 9 upgrade guide into a practical migration checklist.
- Identify the Groovy 6, Spring Boot 4.2, Spring Framework 7.1, graphql-java 26, GPars, invokedynamic, XML, JSON rendering and binding, REST error, content negotiation, unit test, GORM, RxGORM, RxJava, character encoding, JLine and command line changes that can break an application.
- Prepare an application for the defaults Grails 10 changes, by checking each Grails 9 opt-in setting while the Grails 8 behaviour is still the default.
- Keep migration work focused on public application behavior, not internal Grails framework implementation details.

## When to Use Me

Activate this skill when:

- Updating an application to Grails 9.
- Updating a Grails 9 application to a later Grails 9 release.
- Reviewing a Grails 9 upgrade branch or pull request.
- Fixing tests, build failures, runtime failures, or behavior changes after moving from Grails 8.x to Grails 9.
- Fixing what Spring Boot 4.2 and Spring Framework 7.1 change: forwarded headers behind a reverse proxy, relative redirect locations, `TestRestTemplate`, the `instance` of a problem, `bootBuildImage`, HTTP server metrics, the RabbitMQ starter, and the Spring Security plugin's saved requests, Digest authentication and `fii.alwaysReauthenticate`.
- Fixing GraphQL queries that fail with `MaxQueryDepthExceeded` or `MaxQueryFieldsExceeded`, or a schema that fails validation, in an application using `grails-data-graphql`.
- Deciding whether an application keeps or excludes `grails-xml`.
- Turning on `grails.web.rendering.json.spring`, `grails.converters.json.legacy: false`, `grails.databinding.json.jackson`, `grails.exceptionresolver.springHandlers`, `grails.controllers.responseBody` or `grails.mime.negotiation.spring`, and checking what changes.
- Replacing `JSON.createNamedConfig(...)`, `JSON.use(...)` or marshallers that customize `respond` with Jackson serializers and the `NamedJsonConfigurationRegistry`.
- Fixing web unit tests (`ControllerUnitTest`, `TagLibUnitTest`, `UrlMappingsUnitTest`, `JsonViewUnitTest`) that fail after the upgrade.
- Porting code that uses RxGORM, `grails-async-rxjava`, `grails-async-rxjava2`, `grails-events-rxjava`, or `grails-events-rxjava2` to RxJava 3.
- Replacing GPars (`grails-async-gpars`, `grails-events-gpars`, or direct `GParsPool` use) with Groovy's `groovy.concurrent` toolkit.
- Fixing form input or request bodies decoded in the wrong charset, or moving `grails.filter.encoding` and `grails.filter.forceEncoding` to `spring.servlet.encoding`.

## Which Parts Apply

Read the Grails version the application is on from the `grailsVersion` in its `gradle.properties`, and find the version it is going to.

The base version of this skill is Grails 8. After these introductory sections, each part of this skill is one Grails 9 release, under a level-1 heading named after the release, and the last part is [After the Upgrade](#after-the-upgrade).

- Apply the part of every release after the application's current version, up to and including the target version.
- An application on Grails 8 starts at [Grails 9.0.0-M1](#grails-900-m1).
- An application on Grails 7 or earlier first upgrades to the last Grails 8 release, with the `grails-upgrade` skill of that release.
- Finish with [After the Upgrade](#after-the-upgrade), whatever the version the application started from.

Each change is listed under the release that first carried it. A change that adds a configuration setting names the setting and its default. Where there is a way to keep the previous result, the entry gives it. A later release gets a part of its own after `# Grails 9.0.0-M1` when it has upgrade notes.

## Primary Sources

Use the published Grails documentation as the source of truth before changing an application. The URLs below use `<version>`, which stands for the Grails 9 version the application is upgrading to, such as the `grailsVersion` in its `gradle.properties`; use `snapshot` for a snapshot version.

| URL | Use for |
|-----|---------|
| `https://grails.apache.org/docs/<version>/guide/upgrading.html#upgrading90x` | Main Grails 8 to Grails 9 upgrade guide |
| `https://grails.apache.org/docs/<version>/guide/introduction.html#whatsNew` | Grails 9 feature and platform overview |
| `https://grails.apache.org/docs/<version>/guide/introduction.html#dependencyUpgrades` | Platform dependency baseline |
| `https://grails.apache.org/docs/<version>/guide/theWebLayer.html` | Controllers, JSON and XML rendering, the converters, Spring MVC responses and exception handlers, command objects |
| `https://grails.apache.org/docs/<version>/guide/REST.html` | `respond`, renderers, content negotiation, and validation errors as problems |
| `https://grails.apache.org/docs/<version>/guide/testing.html#unitTesting` | Web unit test traits |
| `https://grails.apache.org/docs/<version>/guide/async.html` | Promises and events, including the RxJava 3 modules |
| `https://groovy-lang.org/releasenotes/groovy-6.0.html#concurrency-toolkit` | The `groovy.concurrent` toolkit that replaces GPars |
| `https://grails.apache.org/docs/<version>/ref/Versions/Grails%20BOM.html` | Dependency versions of the Grails BOM |
| `https://groovy-lang.org/releasenotes/groovy-6.0.html` | Groovy 6 language and runtime changes |
| `https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.2-Release-Notes` | Spring Boot 4.2 changes |
| `https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.1-Release-Notes` | Spring Framework 7.1 changes |
| `https://github.com/graphql-java/graphql-java/releases/tag/v26.0` | graphql-java 26.0 breaking changes, for applications using the GraphQL plugin |
| `https://github.com/ReactiveX/RxJava/wiki/What's-different-in-3.0` | RxJava 3 API differences, with the RxJava 1 to 2 differences it links to |

# Grails 9.0.0-M1

## Upgrade Baseline

Verify these before touching application code; `gradle.properties`, the Gradle wrapper, and the Grails BOM are the sources of truth:

- Java 21 remains the minimum to build and run.
- Groovy moves from 5.x to 6.0, and Spock to the `groovy-6.0` variant. Do not pin `groovy-all` or Spock yourself; the Grails BOM manages both.
- Spring Boot moves from 4.1 to 4.2 and Spring Framework from 7.0 to 7.1; the Grails BOM manages the exact versions. The managed versions that change include Spring Security 7.2, Spring Data 2026.1, Micrometer 1.18 and Reactor 2026.0. Spring Boot 4.2 renames configuration properties, so a Grails 8 application's Boot configuration needs review; see [Spring Boot 4.2 and Spring Framework 7.1](#spring-boot-42-and-spring-framework-71).
- Applications using the GraphQL plugin (`grails-data-graphql`) move from graphql-java 25 to graphql-java 26, which Spring Boot 4.2 manages.
- Gradle 9.7 or later, the minimum Grails 8 already required. The requirements page of the guide names the Gradle version Grails 9 generates applications with.
- RxGORM (`grails-datamapping-rx`) depends on `io.reactivex.rxjava3:rxjava`. The RxJava 1 artifact `io.reactivex:rxjava` is no longer transitively available through it.
- Groovy source sets compile with invokedynamic by default; `grails { indy = false }` was the Grails 8 default and is now unset.
- XML support is the separate `org.apache.grails:grails-xml` module, which the Grails 9 web starter and `grails-web-boot` still bring.
- Every changed JSON, XML and Spring MVC path keeps its Grails 8 behaviour by default and has a setting that opts in, listed under [Opt-In Settings Ahead of Grails 10](#opt-in-settings-ahead-of-grails-10). A `ProblemDetail` given to `respond` is the exception: it is written as Spring MVC writes it, whatever the settings. Smaller changes also apply whatever the settings, such as `Accept` headers matched whatever their case; they are in the sections on `grails-xml`, `grails.converters.JSON`, single values, problems, format resolution, web unit tests and Spring MVC responses, and `grails-converters` now brings Boot's `JsonMapper`, which Spring MVC's JSON converter then writes with.

## Migration Workflow

1. Update `grailsVersion` in `gradle.properties` and refresh the Gradle wrapper; let the Grails BOM manage Groovy, Spock, and RxJava versions.
2. Run `./gradlew clean`, then build without tests (`./gradlew compileGroovy compileTestGroovy`) and fix compilation errors module by module. The clean build matters: classes Grails 8 compiled can keep generated `id` and `version` fields on a plain superclass.
3. Apply the Spring Boot 4.2 and Spring Framework 7.1 changes, following [Spring Boot 4.2 and Spring Framework 7.1](#spring-boot-42-and-spring-framework-71): run with the Spring Boot properties migrator to find renamed properties, and check forwarded headers, redirects, tests and metrics. For an application using the GraphQL plugin, follow [graphql-java 26 for GraphQL Applications](#graphql-java-26-for-graphql-applications).
4. Replace GPars with `groovy.concurrent`, following the sections below.
5. Port RxGORM and RxJava code, and replace the deprecated RxJava 1 and 2 async and events modules with their RxJava 3 counterparts.
6. Decide on XML: declare `grails-xml` explicitly if the application serves or reads XML, or exclude it if the application is JSON-only.
7. Run the test suite and fix the web unit tests, following [Web Unit Tests](#web-unit-tests).
8. Work through the opt-in settings one at a time: turn each on, check the responses and tests it affects, and set it explicitly in `application.yml` either way. Change the three JSON settings together.
9. Run the full test suite, then start the application and read its log for `[DEPRECATED]` and `deprecated` warnings from Grails, and for the warnings that name types `respond` still writes with the legacy converter.
10. Review the published upgrade guide section by section, confirming every item either does not apply or has been handled.

## Groovy 6

Groovy 6 is a major Groovy release. Read its release notes before assuming that a Groovy 5 construct behaves the same way. Compile the application statically where it was already static; a construct that compiles dynamically but fails under `@CompileStatic` is a sign of a Groovy change rather than a Grails one. Keep the Spock version the Grails BOM supplies, which is built for Groovy 6.

## Spring Boot 4.2 and Spring Framework 7.1

Grails 9 moves from Spring Boot 4.1 and Spring Framework 7.0, which Grails 8 uses, to Spring Boot 4.2 and Spring Framework 7.1. Review the Spring Boot 4.2 and Spring Framework 7.1 release notes listed under [Primary Sources](#primary-sources). The changes most likely to affect a Grails application:

- **Forwarded headers.** With `server.forward-headers-strategy: framework`, Spring Framework 7.1's `ForwardedHeaderFilter` reads either the RFC 7239 `Forwarded` header or the `X-Forwarded-*` headers, and Spring Boot 4.2 configures it for `X-Forwarded-*`. Before, it read `Forwarded` when the request carried one and `X-Forwarded-*` otherwise. If the reverse proxy sends `Forwarded`, set `spring.mvc.forwarded-headers.header-format: standard`. The filter also ignores `X-Forwarded-Prefix` now, where it used to add it to the context path, so behind a proxy that strips a path prefix and sends that header, absolute links and redirects lose the prefix until `spring.mvc.forwarded-headers.use-forwarded-prefix: true` is set. Spring Boot 4.2 registers the filter from its Spring MVC auto-configuration, which backs off when the application declares `@EnableWebMvc`; such an application registers a `ForwardedHeaderFilter` itself.
- **Relative redirects.** `server.tomcat.servlet.use-relative-redirects`, renamed from `server.tomcat.use-relative-redirects`, now defaults to `true`, Tomcat's own default. A redirect sent through `HttpServletResponse.sendRedirect` with a path, such as a controller `chain` call, a Spring Security redirect or a `redirect:` view name, has a relative `Location` header instead of an absolute URL. A controller `redirect` sets the `Location` header itself and still sends an absolute URL unless it is given `absolute: false`. Browsers resolve relative locations; set the property to `false` if a client or test expects an absolute URL.
- **`TestRestTemplate` is deprecated for removal.** Spring Framework 7.1 deprecates `RestTemplate`, and Spring Boot 4.2 deprecates `TestRestTemplate` and `@AutoConfigureTestRestTemplate` with it. Move `@SpringBootTest` tests to `org.springframework.test.web.servlet.client.RestTestClient`, which `@AutoConfigureRestTestClient` (`org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient`) injects in place of the `@Autowired TestRestTemplate`.
- **A problem detail no longer gets the request path as its `instance`.** RFC 9457 defines `instance` as a URI that identifies the specific occurrence of a problem, so Spring Framework 7.1 no longer sets it to the request path when a handler method returns a `ProblemDetail` or an `ErrorResponse`. `respond` follows it: a `ProblemDetail` or `ErrorResponse` it writes, and the problem it writes for validation errors, no longer get one either. A client that reads `instance` gets it only where the application sets it: in a `ResponseEntityExceptionHandler` for Spring MVC controllers, and on the problem an action responds with, including one `validationProblemDetailFactory.create(errors)` makes:

  ```groovy
  ProblemDetail problem = validationProblemDetailFactory.create(book.errors)
  problem.instance = URI.create("urn:uuid:${UUID.randomUUID()}")
  respond problem
  ```

- **The JAXB XML converter.** Spring Framework 7.1 stops auto-detecting the JAXB message converter for server applications. Grails `render` and `respond` write XML through Grails' own converters and are unaffected. A Spring MVC `@RestController` that returns JAXB-annotated objects needs the converter registered explicitly, as the Spring Framework 7.1 release notes show.
- **Nested `@Configuration` classes in tests.** The Spring TestContext Framework now adds a static nested `@Configuration` class of a test's superclasses and enclosing classes to the test's application context, where it ignored such a class unless its class hierarchy declared `@ContextConfiguration`. Grails 8 already logs a warning for each one it ignores, so a test run on Grails 8 shows the affected tests. Remove the `@Configuration` annotation, or move the class to a top-level class, if the test should not load it.
- **`bootBuildImage` builds on Ubuntu 26.04.** The default builder is `paketobuildpacks/ubuntu-resolute-builder`, in place of `paketobuildpacks/builder-noble-java-tiny`, and images are based on Ubuntu 26.04 instead of 24.04. With no `builder` configured, the run image is `paketobuildpacks/ubuntu-resolute-run-tiny:latest`, which still has no shell; with one configured, it is the run image of the builder, which for `ubuntu-resolute-builder` has a shell. The builder no longer pins the version of the run image, so set both `builder` and `runImage` to specific versions for repeatable images, or set `builder` to `paketobuildpacks/builder-noble-java-tiny` to keep the previous one.
- **An exception an action throws is recorded as an error on the HTTP server observation.** When an error view answered, as the `"500"(view: '/error')` mapping of a generated application does, `http.server.requests` tagged the 500 with `error` and `exception` values of `none`, and tracing did not see a failure. A `500` mapping to a controller tagged it with `GrailsWrappedRuntimeException` and with the `uri` of that controller. Both now report the exception the action threw, an `Error` such as a failed `assert` included, and the route of the action, and the span of the request is marked as failed. These requests were already counted with `outcome=SERVER_ERROR`, but an alert that counts a non-`none` `error` value sees more server errors than before, and a dashboard that reads the `uri` of an error controller finds those failures under the routes of the actions. A client error is not recorded, and neither is a failure inside `g:include`. After a forward to an error controller, the `jakarta.servlet.error.exception` request attribute holds the exception the action threw rather than a `GrailsWrappedRuntimeException`, for a filter that reads it once the chain has returned; inside the error controller it still holds the wrapper. The route is now set before the Grails interceptors run, so a request an interceptor's `before()` stops by returning `false`, to render a 403 or to redirect, is reported under the route of the action, where it was reported with a `uri` of `UNKNOWN`, or `REDIRECTION` for a redirect; a dashboard or alert keyed on those `uri` values sees those requests move.
- **On Undertow, a request reads as its own dispatch again once a forward returns.** Undertow left a request reading as a forward once a forward to a path had returned, so Spring Framework 7.1's observation filter took an asynchronous request that had forwarded, for example to an error controller, for a plain one and reported its failure twice. `grails-undertow` now restores the dispatcher type when the forward returns, as Tomcat and Jetty do: a filter that reads `getDispatcherType()` once its chain has returned sees `REQUEST` or `ASYNC` rather than `FORWARD`.
- **`http.server.requests.active` is no longer registered by default.** Spring Boot 4.2 makes this long task timer opt-in. A dashboard or alert that reads it sets `management.metrics.observations.include-active-long-task-timer` to `true`, which replaces `management.metrics.observations.ignored-meters`.
- **`spring-boot-starter-amqp` no longer means RabbitMQ.** It is now the starter for generic AMQP 1.0. An application that uses RabbitMQ through `spring-boot-starter-amqp` or `spring-boot-starter-amqp-test` changes to `spring-boot-starter-rabbitmq` or `spring-boot-starter-rabbitmq-test`, and any import of `org.springframework.boot.amqp.autoconfigure` to `org.springframework.boot.rabbitmq.autoconfigure`. RabbitMQ over AMQP 1.0 has `spring-boot-starter-amqp-rabbitmq`.
- **The Spring Security plugin no longer saves the requests browsers send on their own.** A denied request for `/apple-touch-icon.png`, `/manifest.json` or the `/.well-known/appspecific/com.chrome.devtools.json` that Chrome DevTools sends used to be saved, so a login redirected to it instead of to the page the user asked for. The plugin now ignores these paths, with `grails.plugin.springsecurity.requestCache.ignoredPatterns`. An application that secures a page at one of these paths and wants a login to return to it leaves the path out of that list.
- **Digest authentication in the Spring Security plugin starts and signs users in.** `useDigestAuth` with its default of hashed passwords used to fail at startup, naming an encoder that no longer exists. What a user has stored is now the hash a browser computes, which the application computes, as the security guide shows under Basic and Digest authentication. With `digest.useCleartextPasswords`, nobody could sign in with Digest authentication, because the plugin replaced `passwordEncoder` with one that stored `{noop}secret`. `passwordEncoder` now stays as configured, and the application stores the password as it was typed, so an application with stored values such as `{noop}secret` removes the `{noop}` prefix from them and no longer stores them with `springSecurityService.encodePassword`. A form or Basic authentication signs a user in with their password in either mode.
- **`fii.alwaysReauthenticate` in the Spring Security plugin takes effect.** The setting did nothing. An application that sets it now has the `Authentication` of a request to a secured URL authenticated again, while it still holds its credentials, as it does with `providerManager.eraseCredentialsAfterAuthentication` set to `false`. By default the credentials are erased at sign-in, so a user signed in through a form is not authenticated again.

The Spring Boot properties migrator, described in the Grails 8 upgrade guide, finds the configuration properties Spring Boot 4.2 renames, such as `server.tomcat.use-relative-redirects`. Spring Boot 4.2 can also make the application fail to start while such a property is left: set `spring.properties.migrator.fail` to `on-warning` to fail on renamed and removed properties, or to `on-error` to fail on removed ones only. The default, `never`, only logs them.

## graphql-java 26 for GraphQL Applications

Spring Boot 4.2 manages graphql-java 26, so applications using the GraphQL plugin (`grails-data-graphql`) move from graphql-java 25 to graphql-java 26.1. graphql-java 26.0 contains breaking changes, listed in its release notes. The ones that can affect a Grails application:

- **Query complexity limits are enforced by default.** A query nested more than 100 levels deep, or selecting more than 100,000 fields, fails validation with a `MaxQueryDepthExceeded` or `MaxQueryFieldsExceeded` error. To raise the limits, or to turn them off, set new defaults when the application starts, for example in `BootStrap.groovy`:

  ```groovy
  import graphql.validation.QueryComplexityLimits

  class BootStrap {

      def init = {
          QueryComplexityLimits.setDefaultLimits(QueryComplexityLimits.newLimits()
                  .maxDepth(150)
                  .maxFieldsCount(200_000)
                  .build())
          // or, to turn the limits off:
          // QueryComplexityLimits.setDefaultLimits(QueryComplexityLimits.NONE)
      }
  }
  ```

  graphql-java's parser separately rejects a query nested more than about 165 levels deep, so a `maxDepth` above that has no effect.
- **Schemas built in code are validated like schemas built from SDL.** A required argument or input field (non-null with no default value) can no longer be deprecated. The schema the plugin generates never deprecates an argument or an input field, but arguments and input types an application adds through a `GraphQLSchemaInterceptor` must follow the rule.
- **Additional schema types must be named types.** `GraphQLSchemaInterceptor.interceptSchema` still receives the additional types as a `Set<GraphQLType>`, and the plugin unwraps list and non-null wrappers before it builds the schema, so existing interceptors need no change. Code that builds a `GraphQLSchema` directly must pass `GraphQLNamedType` instances to `GraphQLSchema.Builder.additionalTypes`.

The other breaking changes (the validation rule filter, the removal of `DirectiveInfo`, and the JSpecify nullability annotations) only affect code that uses graphql-java directly; the nullability annotations do not change what graphql-java returns at runtime. The plugin continues to use graphql-java-extended-scalars 24.0, which works with graphql-java 26.1.

## GPars Deprecated in Favour of Groovy Concurrency

Groovy 6 brings the concurrency and parallel-processing patterns of GPars into the language as `groovy.concurrent`, so Grails no longer needs the separate GPars library. `grails-async-gpars` and `grails-events-gpars` still work in Grails 9, carry `@Deprecated(since = '9.0', forRemoval = true)`, log a warning when selected, and will be removed in a future release.

Replace the dependency, one for one:

| Grails 8 | Grails 9 |
|----------|----------|
| `org.apache.grails.async:grails-async-gpars` | `org.apache.grails.async:grails-async-groovy-concurrent` |
| `org.apache.grails.events:grails-events-gpars` | `org.apache.grails.events:grails-events-groovy-concurrent` |

The replacements provide `org.grails.async.factory.groovy.AwaitablePromiseFactory` and `org.grails.events.groovy.ActorEventBus`, and are selected the same way the GPars modules are, by adding the dependency in place of the GPars one:

```groovy
runtimeOnly 'org.apache.grails.async:grails-async-groovy-concurrent'
runtimeOnly 'org.apache.grails.events:grails-events-groovy-concurrent'
```

Do not leave both on the classpath: only the first service-loaded `PromiseFactory` is used, and the event bus builder refuses to start when it finds more than one `EventBus` implementation.

The Groovy promise factory follows the failure contract of the default `CompletableFuture` factory rather than the GPars one. A direct or chained `get()` throws `ExecutionException` with the original failure as its cause, a single-promise `onError` recovers with its callback result, the aggregate `onError` observes the failure and leaves the returned promise failed, and `onComplete` and `then` return a new promise instead of the receiver. A test that asserted the GPars behaviour needs updating.

The Groovy actor event bus delivers each event to its subscribers in publication order on one actor. A subscriber that throws without a reply handler is logged and does not stop delivery to the remaining subscribers or to later events.

Application code that uses GPars directly moves to the Groovy equivalents:

| GPars | `groovy.concurrent` |
|-------|---------------------|
| `GParsPool.withPool { list.eachParallel { } }` | `list.eachParallel { }`, which needs no pool |
| `GParsPool.withPool(n) { list.collectParallel { } }` | `ParallelScope.withPool(n) { scope -> list.collectParallel { } }` |
| `GParsConfig.setPoolFactory(..)` | `Awaitable.setExecutor(..)`, which also hosts the actor event bus |

Promises the Groovy factory creates are Groovy `Awaitable` instances, so they can be passed to `await` and to combinators such as `Awaitable.all` without conversion. Once the deprecated modules are removed, Grails no longer brings GPars onto the classpath, so an application that still needs it declares `org.codehaus.gpars:gpars` itself.

Groovy marks the toolkit as incubating. The Grails modules on top of it expose only the Grails `Promise` and `EventBus` contracts, so later Groovy changes are absorbed by Grails rather than by application code.

## Groovy Code Compiles with invokedynamic

Grails 8 compiled every Groovy source set without invokedynamic, because `grails { indy = false }` was the default. Grails 9 leaves the setting unset, so the Groovy compiler's own default applies, and on Groovy 6 that is invokedynamic.

- Remove `grails { indy = true }`; it is now the effective default.
- Build logic that read the property must allow for it having no value: use `grails.indy.getOrElse(true)` or check `grails.indy.present` instead of `grails.indy.get()`.
- An application that sets `indy = false` to keep classic call sites must also declare `implementation 'org.apache.groovy:groovy-callsite'` itself, at the BOM's version, and again for every source set that does not see implementation dependencies, such as `java-test-fixtures` test fixtures. Without it the Groovy compiler stops with a message naming the module.
- An application that applies GraalVM's native image plugin cannot set `indy = false`; the build fails with the reason, because an image needs invokedynamic.
- A library compiled with classic call sites needs `groovy-callsite` whatever the application's setting, or loading one of its classes fails with `NoClassDefFoundError: org/codehaus/groovy/runtime/callsite/CallSiteArray`. Spock 2.5, Geb 8 and the Asset Pipeline 5.2 are compiled with invokedynamic; `org.testcontainers:testcontainers-spock` is not, so declare `testRuntimeOnly 'org.apache.groovy:groovy-callsite'` next to it.

## XML Web Support Moves to `grails-xml`

XML conversion, XML request-body binding, and the XML, Atom, HAL XML and Vnd.Error renderers live in `org.apache.grails:grails-xml`. The Grails 9 web starter and `grails-web-boot` still bring it, so existing XML endpoints keep working. In Grails 10 neither brings it, and `@Resource` defaults to JSON only.

- An application that serves XML, or uses `grails.converters.XML`, XML marshallers, XML data binding, `request.XML`, or the XML and Atom renderers, declares the module now, so that the Grails 10 upgrade does not drop it:

  ```groovy
  dependencies {
      implementation 'org.apache.grails:grails-xml'
  }
  ```

- A JSON-only application can exclude it:

  ```groovy
  configurations.configureEach {
      exclude group: 'org.apache.grails', module: 'grails-xml'
  }
  ```

  Without the module, `request.XML` throws an `IllegalStateException` that names the module, and a class compiled with `@CompileStatic` or `@GrailsCompileStatic` that reads it does not compile. An XML request body is not read and raises no error, while the request parameters bind as for a request without a body, except that binding it to a collection with `bindData` fails, as for any body of a type nothing binds. `respond` answers an XML client in the next type it accepts that the action responds in, and a client that accepts any type, or a browser, in the first format the action lists that it can render the value in, so an action that lists `['xml', 'json']` answers those clients in JSON. Where no type the client accepts can be rendered, `respond` sends `406 Not Acceptable`, or a `404` for validation errors where the client accepts one of the action's formats, so that the application's error pages apply, and writes a `ProblemDetail` as `application/problem+json`. `encodeAsXML` and the `xml` codec still escape text, but escape an object other than text as its string. A markup view still renders XML for an action that has one.
- A build that lists `grails-web-boot` without the REST, controllers, data source, converters, data binding, domain class, MIME types and i18n modules now loads their plugins through `grails-xml`, among them the Grails locale resolver; excluding `grails-xml` leaves out those that nothing else brings.
- A plugin that reads `request.XML` in a statically compiled class, compiled against Grails 9, declares `grails-xml` as an `implementation` dependency. A library compiled against Grails 8 keeps working without being recompiled, but still needs the module at run time.
- New REST controllers, resources, and the `rest-api` profile's scaffolded controllers advertise JSON only. Add `xml` to an artefact's `responseFormats` or `formats` where that endpoint should negotiate XML.
- Legacy XML conversion is deprecated for removal in Grails 11: `grails.converters.XML`, `XmlRenderer`, `XmlCollectionRenderer`, the HAL XML, Atom and Vnd.Error XML renderers, and the XML validation errors marshaller. `grails.converters.XML` logs a warning when it first renders XML, and the HAL XML, Atom and Vnd.Error XML renderers when they are used. Plan the move to DTOs, explicit renderers, or Spring's XML converter.
- `grails.web.rendering.xml.spring: true`, with `runtimeOnly 'tools.jackson.dataformat:jackson-dataformat-xml'`, writes ordinary beans with Spring's `JacksonXmlHttpMessageConverter`, backed by the Spring Boot-managed Jackson XML mapper. The setting defaults to `false`, and adding the dependency alone does not bypass registered XML marshallers. Enabling it selects Spring for ordinary beans even when a legacy marshaller is registered: `respond` writes such a bean as Spring MVC writes the value a `@ResponseBody` handler method of the controller returns, so the application's `ResponseBodyAdvice` beans apply, in UTF-8 whatever `grails.converters.encoding` says. Without the Jackson XML dependency, `grails-xml` still renders XML through the Grails converter. `null`, domain objects, collections, arrays, maps, validation errors, `includes` and `excludes` projections, and renderers with a legacy `namedConfiguration` still use the Grails XML converter.
- With the `DEFAULT` circular reference behaviour, an object that refers to itself is written with `ref="."`, where Grails 8 failed with a `ConverterException`.
- The `groovyPageLocator` and `rendererRegistry` properties of the XML renderers, and of an application's `XmlRenderer` beans, are no longer autowired; the renderers find both in the application context when they write.

## Opt-In Settings Ahead of Grails 10

| Setting | What it governs | Grails 9 default | Later default |
|---------|-----------------|------------------|---------------|
| `grails.web.rendering.json.spring` | JSON that `respond` writes, including validation errors as problems | `false` | `true` in Grails 10 |
| `grails.converters.json.legacy` | `grails.converters.JSON`: `render ... as JSON`, `JSON.parse`, `request.JSON`, and `respond` while `grails.web.rendering.json.spring` is `false` | `true` | `false` in Grails 10 |
| `grails.databinding.json.jackson` | JSON request bodies bound to command objects and domain classes | `false` | `true` in Grails 10 |
| `grails.converters.json.domain.jackson.enabled` | Domain classes written by Spring Boot's own `JsonMapper` | `false` | unchanged |
| `grails.web.rendering.xml.spring` | XML that `respond` writes for ordinary beans | `false` | unchanged until Grails 11 removes legacy XML conversion |
| `grails.exceptionresolver.springHandlers` | Exceptions actions throw, resolved by Spring MVC `@ExceptionHandler` methods | `false` | `true` in Grails 10 |
| `grails.controllers.responseBody` | The values of actions annotated `@ResponseBody`, written by Spring MVC as the body | `false` | `true` in Grails 10 |
| `grails.mime.negotiation.spring` | `request.format`, `response.format`, `withFormat` and the format `respond` writes in, resolved with Spring MVC's content negotiation manager | `false` | `true` in Grails 10 |

- Grails 10 changes the three JSON settings together, so that `respond`, `render ... as JSON`, `request.JSON` and data binding write and read JSON with the same mapper. Test them together. At the least, set `grails.converters.json.legacy: false` and `grails.databinding.json.jackson: true` together, or `request.JSON` and data binding accept different JSON.
- Grails 9 logs a warning while a legacy path is used: once when the legacy `respond` path renders, at startup for `JsonSlurper` request binding, and at startup while `grails.converters.json.legacy` is unset. Setting `grails.converters.json.legacy` to either value turns its warning off.
- Set every setting the application depends on explicitly, so that the Grails 10 upgrade changes nothing unannounced.

## `respond` Through Spring MVC's JSON Converters

With `grails.web.rendering.json.spring: true`, `respond` writes JSON with Spring MVC's message converters, backed by Boot's configured `JsonMapper`. Grails 10 enables it by default, and the legacy `respond` path is scheduled for removal in Grails 11:

- The value is written as Spring MVC writes what a `@ResponseBody` handler method of the controller returns: the application's `ResponseBodyAdvice` beans apply, the action's `@JsonView` selects the properties, and the response is UTF-8 whatever `grails.converters.encoding` says.
- Jackson decides date formats, bean properties, circular references and validation errors, and writes an enum by its `@JsonValue` or its `toString()`, where the legacy converter writes its name. Compare the JSON of every `respond` endpoint before and after.
- Registering a marshaller does not change which path `respond` uses, and marshallers registered with `JSON.registerObjectMarshaller(...)` do not customize Spring's path. Move those representations to Jackson serializers, as [JSON Customization Moves to Jackson](#json-customization-moves-to-jackson) shows, before turning the setting on.
- `grails.converters.json.pretty.print` gives way to `spring.jackson.serialization.indent-output`, and `grails.converters.json.date` to Boot's `spring.jackson.*` date settings.
- `respond` still uses the legacy converter for a renderer with a legacy named configuration, for an `includes` or `excludes` projection when the selected converter is not Spring's standard Jackson converter, and for a type and media type no configured MVC converter writes. Grails logs a warning for each such type and reason, up to 100, so read the log to find responses the setting did not change.
- A domain object that leads back to itself while it is being written, such as an embedded value that points at its owner, is written as `{"_ref": "..", "class": "..."}` by default, with a `..` for each JSON object or array from the one the reference is written in up to the value the object is written as, or `.` where it repeats the object the reference is written in, on which Grails 8 failed; or as `grails.converters.json.circular.reference.behaviour` selects. With `IGNORE`, an element of a list is left out and a property is written as `null`, where the legacy converter fails on the property. A plain bean that leads back to itself is written the same way by a response and by the converter, and fails on the mapper, as in a plain Spring MVC controller.
- Renderer beans an application declares, such as a `JsonRenderer` or an `XmlRenderer` for one of its types, follow the same settings, apply their own `includes` and `excludes`, and reach the same named configurations and message converters. A `JsonCollectionRenderer` bean ignores the `excludes` `respond` is given on the legacy path, as in Grails 8, and applies them on Spring's path where it sets none of its own. On Spring's path, a `JsonRenderer` for a domain class writes its associated instances as references unless `grails.converters.json.default.deep` is set, and its version and class name as the `grails.converters.json.domain.*` settings select, which naming them in its `includes` does not change; the legacy converter writes the associated instances in full, and the version or the class name its `includes` names.
- Where the converter is Spring's `JacksonJsonHttpMessageConverter`, the value follows what the converter writes ahead of it, such as the prefix its `setPrefixJson(true)` or `setJsonPrefix(...)` adds against JSON hijacking. The prefix is read when the converter first writes a response, so configure it as Spring MVC starts, as a `WebMvcConfigurer` does. A response `respond` writes with a named configuration, `render json:` and `grails.converters.JSON` are written without the prefix.
- A value Spring's converter fails to write fails as in a Spring MVC controller. A type definition error, such as a property a bean unwraps with the name of one of its own, is an `HttpMessageConversionException`, which reaches the Grails exception resolver as any other exception does. Any other failure is an `HttpMessageNotWritableException`, which Spring MVC answers with a 500 before the Grails exception resolver sees it, and the error page finds the exception in the `jakarta.servlet.error.exception` request attribute rather than in its model. `render json:` throws the mapper's own `JacksonException`, and a `ControllerExecutionException` where the response's writer fails.
- Boot's shared `JsonMapper`, and with it a plain `@RestController`, keeps Jackson's own domain property model unless `grails.converters.json.domain.jackson.enabled` is set.
- `grails-converters` now depends on `spring-boot-jackson`, so an application without `spring-boot-starter-web` gains Boot's `JsonMapper`, and Spring MVC's JSON converter writes with it.
- In controller unit tests, supply a `JsonMapper` or `JsonMapperBuilderCustomizer` from a nested Spring `@Configuration` class of the spec. One registered with `doWithSpring` or `beanRegistrar()` is too late to make Boot back off.

## JSON Customization Moves to Jackson

| Grails 8 | Grails 9 |
|----------|----------|
| `JSON.registerObjectMarshaller(Book) { ... }` to shape `respond` | A Jackson `ValueSerializer<Book>` added through a `JsonMapperBuilderCustomizer` bean; the marshaller still applies to `render ... as JSON` and to the legacy `respond` path |
| `JSON.createNamedConfig('catalog') { ... }` | `namedJsonConfigurationRegistry.register('catalog') { it.serializer(Type, new TypeValueSerializer()) }`, with `NamedJsonConfigurationRegistry` injected, for example in `BootStrap` |
| `JSON.use('catalog') { render book as JSON }` | `render json: book, jsonConfiguration: 'catalog'` or `respond book, jsonConfiguration: 'catalog'` |
| `JSON.use('deep') { render book as JSON }` | `render json: book, jsonConfiguration: 'deep'`, a configuration every registry has |
| `JSON.use(...)` around direct serialization | `namedJsonConfigurationRegistry.writeValueAsString('catalog', book)` or `writeValue('catalog', writer, book)` |

```groovy
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import tools.jackson.databind.module.SimpleModule

@Bean
JsonMapperBuilderCustomizer bookJsonCustomizer() {
    { builder ->
        builder.addModule(new SimpleModule()
                .addSerializer(Book, new BookValueSerializer()))
    } as JsonMapperBuilderCustomizer
}
```

- `JSON.createNamedConfig(...)`, `JSON.use(...)`, `JSON.getNamedConfig(...)`, `JSON.withDefaultConfiguration(...)`, the `namedConfiguration` property of `JsonRenderer` and `JsonCollectionRenderer` beans, and the `renderJson(JSON, RenderContext)` method their subclasses override are deprecated for removal in Grails 11. `JSON.registerObjectMarshaller(...)` is not deprecated.
- A registered configuration derives an isolated mapper from Boot's `JsonMapper` and is immutable: configure it inside the registration callback, and register the name again to replace it.
- A configuration writes as the mapper writes: registered marshallers do not apply, an enum is written as Jackson writes it, and `</` is not escaped. Write associations in full with `it.deep()`.
- `respond` writes a `ProblemDetail`, and validation errors, as it does without a configuration, whatever configuration it names; a name that is not registered still throws.
- `includes` and `excludes` on `render json:` and `respond` project the type written, or each element's type for a collection or array, by bean property name or JSON name. A value with no properties to project, such as a map or a string, throws `IllegalArgumentException` before anything is written. A type that a Jackson serializer of its own writes is not filtered, and a warning names it the first time, as one does an `Iterable` that is not a `Collection`, whose elements are not walked before writing. Objects of other types inside the projected ones are written in full. The legacy converter projects only the objects of the class of the value written, so not the elements of a collection, unless a `JsonCollectionRenderer` bean renders it: that applies the `includes` `respond` is given to the elements but, on the legacy path, its own `excludes`, an empty list unless the bean sets them, in place of those `respond` is given, as in Grails 8.
- `writeValue` leaves the writer open and unflushed; flush or close a writer of your own once it returns.

## `grails.converters.JSON` Can Write JSON With Spring Boot's JsonMapper

`grails.converters.JSON` still renders as Grails 8 did by default, because `grails.converters.json.legacy` defaults to `true`. Set it explicitly either way, and check the application's JSON with `false` before Grails 10 makes that the default:

```yaml
grails:
    converters:
        json:
            legacy: false
```

With `legacy: false`, the converter writes through the application's Jackson `JsonMapper`, so `spring.jackson.*` settings and registered Jackson modules apply, among them `spring.jackson.time-zone` and `spring.jackson.serialization.wrap-root-value`. What Jackson applies to a bean applies to the beans the converter renders: `@JsonProperty`, `@JsonIgnore`, `@JsonInclude`, `@JsonView`, `@JsonFormat`, `@JsonPropertyOrder`, mix-ins, and the naming strategy. Properties come in Jackson's order, where Grails 8 sorted them alphabetically. The converter's own `setIncludes` and `setExcludes` still apply, by bean property name or JSON name.

Check these values in the application's JSON contract and in every test that asserts JSON text, because they render differently with `legacy: false`:

| Value | Grails 8 | `legacy: false` |
|-------|----------|-----------------|
| `Month` | `"SEPTEMBER"` | `9` |
| `Locale` | `"zh_TW_#Hant"` | `"zh-Hant-TW"`, its language tag |
| `OffsetTime` | `"03:00:00-03:00"` | `"03:00-03:00"`, from `toString()` |
| `char`, `Character`, `char[]` | `{}`, `[{},{}]` | `"c"`, `"ab"` |
| `byte[]`, `ByteBuffer` | `[1,2,3]`, a failure | `"AQID"`, Base64 |
| `UUID`, `URI`, a non-enum `@JsonValue` type | an object of its bean properties | its value |
| `Pattern`, `Charset`, `InetAddress` | an object of its bean properties | its string |
| `File`, `Path` | a failure to render it | its path or URI string |
| An enum constant with a body, whose class is a subclass of its enum | an object of its bean properties | its name, as any enum |
| `JsonNode` | an object of its bean properties | the JSON it holds |
| `Optional` | `{"empty":false,"present":true}` | its value, or `null` |
| A `Map.Entry` | `{"key":"k","value":1}` | `{"k":1}` |
| A map with a `null` key | the entry left out | a `ConverterException` |
| Validation errors (`render errors as JSON`) | entries with `rejected-value` | entries with `codes` and no rejected value |
| A control character without a short escape | `"\u001b"` | `"\u001B"` |
| A value nested deeper than 500 levels | the JSON | a `ConverterException`; `spring.jackson.factory.constraints.write.max-nesting-depth` sets the limit |
| Pretty printed JSON | `"a": 1` | `"a" : 1`, Jackson's default pretty printer |

With `legacy: false`, map keys use the mapper's key serializers: a `Date` or `Calendar` key follows the mapper's date format and time zone, and a `Class` key is its name, without the `class ` prefix. A `NaN` or infinite `double` renders as `"NaN"`, and a single value, such as `render Role.HEAD as JSON`, renders as a JSON value, whatever the setting.

Two things deliberately differ from Spring Boot: enums, including one with a `@JsonValue`, render by `name()`, and strings are escaped for an HTML `<script>` element: `</` is written as `<\u002f`, and U+2028 and U+2029 as `\u2028` and `\u2029`. The parsed values are the same as Spring Boot's.

More differences with `legacy: false`:

- An object is reached again, and written as the circular reference behaviour selects, only where it is the very object being written. One that is only equal to it, such as a domain class instance loaded twice, is written in full, where Grails 8 wrote a reference to it.
- A bean whose `@JsonUnwrapped` property writes a name the bean writes too, such as an `id` of both, is written with both, where Spring Boot's mapper, `render json:` and `respond` with `grails.web.rendering.json.spring` reject it. With `legacy: true` the converter writes the property as a nested object, as Grails 8 did.
- `JSON#toString(false)` does not indent the JSON when `grails.converters.json.pretty.print` is `true`; Grails 8 ignored the argument. JSON that is not pretty printed is not indented either when `spring.jackson.serialization.indent-output` is set.

Parsing changes with `legacy: false` too:

- `JSON.parse` and `request.JSON` parse with the application's Jackson mapper, so they accept strict JSON. Single quotes, comments, unquoted names and values, uppercase literals, trailing commas and `new Date(...)` are rejected. Re-enable the forms Jackson has a feature for with `spring.jackson.json.read.allow-single-quotes`, `allow-java-comments`, `allow-unquoted-property-names` or `allow-trailing-comma`. Text after the value is rejected unless `spring.jackson.deserialization.fail-on-trailing-tokens` is `false`.
- The returned `JSONObject` and `JSONArray` containers, and their number types, do not change. `new JSONObject(String)` and `new JSONArray(String)` always use Grails' own parser. With `legacy: true`, a number beyond the range of a `double`, such as `1e400`, parses as a `BigDecimal`, where Grails 8 threw a `NumberFormatException`.
- `grails.converters.json.date: javascript` needs `legacy: true`, because its `new Date(...)` output is not JSON; an application that sets both fails to start with a message naming them. Remove `grails.converters.json.date` and configure dates with `spring.jackson.*`.

To keep one type's Grails 8 rendering under `legacy: false`, register a marshaller for it, for example in `BootStrap.init`; a registered marshaller takes precedence over the mapper:

```groovy
JSON.registerObjectMarshaller(Month) { Month month -> month.name() }
JSON.registerObjectMarshaller(Locale) { Locale locale -> locale.toString() }
```

With `legacy: false`, the deprecated `DateMarshaller`, `CalendarMarshaller`, `ToStringBeanMarshaller`, `ByteArrayMarshaller`, `InstantMarshaller`, `LocalDateMarshaller`, `LocalDateTimeMarshaller`, `OffsetDateTimeMarshaller` and `ZonedDateTimeMarshaller` are not registered, so a marshaller registered with an explicit negative priority may now come before or after different defaults.

Whatever the setting:

- A subclass of `org.grails.web.json.JSONWriter` needs review: it now writes through a Jackson `JsonGenerator`, its `array()`, `object()`, `key()` and `value` methods no longer call the protected `append` and `comma` methods, and its text reaches the `Writer` as the buffer fills, when the JSON is complete, or on `flush()`, rather than per value. Use the public methods instead of overriding `append` or writing to the `writer` field. Wherever a `JSONWriter` writes, `</` is written as `<\u002f`, also where `grails-converters` is not on the classpath, where Grails 8 wrote `<\/`; there it also wrote a collection given to `value` as a JSON array, U+2028 and U+2029 unescaped and the vertical tab as `\u000b`, where such a value is now written as its `toString()` and the characters as the converter writes them. It also accepts a single value outside any object or array, where it threw a `JSONException`.
- `PrettyPrintJSONWriter` is deprecated in favour of `new JSONWriter(writer, true)`, and the JavaCC `JSONParser` in `org.grails.web.json.parser` in favour of `JSON.parse` or `new JSONObject(String)`.
- The `includes` and `excludes` a converter is given apply to the instance a proxy stands for, such as a lazy Hibernate association, where Grails 8 ignored them and rendered every property. A custom marshaller that writes the instance a proxy stands for in its place calls `standIn(proxy, instance)` on the `JSON` converter it is given before it writes the instance.
- The `PATH` circular reference behaviour renders an object with a `null` property, and writes the right path after a number.
- A value nested deeper than the thread's stack allows fails with a `ConverterException` instead of a `StackOverflowError`.

JSON views, the XML converter and the HAL renderer are not changed.

## Domain Classes Render as Jackson Beans of Their Persistent Properties

With `grails.converters.json.legacy: false`, `render ... as JSON` writes a domain class instance with Jackson's bean serializer, shaped to the domain class as `DomainClassMarshaller` shaped it:

- A property annotated `@JsonIgnore` or `@JsonProperty(access = WRITE_ONLY)`, or named by a class-level `@JsonIgnoreProperties`, is not rendered; `@JsonProperty`, `@JsonNaming` and `spring.jackson.property-naming-strategy` rename properties; `@JsonInclude` and `spring.jackson.default-property-inclusion` decide whether a null or empty property is written. A property that was rendered before may now be absent or renamed.
- `@JsonView`, `@JsonFormat`, a `@JsonSerialize` serializer on a property, mix-ins and filters apply. A domain class with `@JsonTypeInfo` is written with its type id, and an instance in a `@JsonUnwrapped` property is written into the enclosing object; an instance with a type id fails there, unless `SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS` is disabled.
- What a `@JsonAnyGetter` method returns is not rendered, as a transient property or a getter with nothing persisted behind it is not. The entries of a persistent `Map` property annotated `@JsonAnyGetter` are written in its place, where Grails 8 wrote the property as an object.
- Only persistent properties render, as before. The class name, id and version come first when they apply, then the properties in the order the domain class declares them, unless `@JsonPropertyOrder` says otherwise.
- The `tenantId` of a multi-tenant domain class and the `dbo` of a MongoDB domain class, which Grails 8 rendered, are no longer rendered.
- A to-many association whose value is a `Map` renders as an object of references, where it failed before.
- A domain class with its own Jackson serializer (`@JsonSerialize`, `@JsonValue`, `JacksonSerializable`, or a module) is written by it, also as the value of an association.
- Subclasses of `DomainClassMarshaller` and `DeepDomainClassMarshaller` still render as they did.

The application's `JsonMapper` is not changed, so a domain class instance a Spring MVC controller returns is still written as a plain Jackson bean. Set `grails.converters.json.domain.jackson.enabled: true` to have Grails register the `domainClassJacksonModule` bean, which Spring Boot adds to its `JsonMapper` and to every mapper built from its `JsonMapper.Builder`; inject it as a `tools.jackson.databind.JacksonModule` and pass it to `addModule` for a mapper built otherwise. The mapper then renders associated instances as references of their id, such as `{"id":3}`, or in full with `grails.converters.json.default.deep`, and proxies as the instances they proxy, without the marshallers and the rule for enums the converter applies on top.

## Rendering Single Values as JSON

`render(Role.HEAD as JSON)`, which threw a `ConverterException` in Grails 8, renders `"HEAD"`. The converter accepts any scalar root: a string, a number, a boolean, `null` or an enum. Groovy's own casts do not support `value as JSON` for strings and numbers, so construct `new JSON(value)` for those. A non-null scalar given to `respond` renders the same way. Update a test that expected the exception.

## Opt Into Jackson JSON Request Binding

Grails 9 keeps `JsonSlurper` request binding by default and logs a deprecation notice at startup. Jackson becomes the default in Grails 10, and legacy parsing is removed in Grails 11. With `grails.databinding.json.jackson: true`, JSON, HAL JSON and JSON API request bodies are parsed with the mapper Spring MVC reads a `@RequestBody` map with, usually Boot's `JsonMapper`:

- The mapper is that of the first of Spring MVC's message converters that reads JSON, where that is Spring's Jackson converter, so that a converter a `WebMvcConfigurer` configures parses both. Where that converter is not Jackson's, such as Gson's with `spring.http.converters.preferred-json-mapper: gson`, or without Spring MVC, it is the application's only or primary `JsonMapper`, or else Spring Boot's `jacksonJsonMapper`; where the application context has none of those, as when it has several and none is primary, a default `JsonMapper` parses the bodies.
- Two kinds of body that Grails 8 always parsed with a default `JsonSlurper`, whatever `JsonSlurper` bean the application declared, are parsed with Jackson too: the bodies of a type a `HalJsonRenderer` bean renders, and the JSON bodies of a type no creator bean handles, as where an application replaces `jsonDataBindingSourceCreator` with a creator for one class.

- An application `JsonSlurper` bean, such as one using `JsonParserType.LAX`, no longer applies. Configure parser features with a `JsonMapperBuilderCustomizer` or `spring.jackson.json.read.*`, and limits with `spring.jackson.factory.constraints.read.*` or a `JsonFactoryBuilderCustomizer`.
- Jackson rejects input the legacy parser accepted: trailing content after a document, trailing commas, leading-zero numbers, unescaped tabs inside strings, trailing comments, and input beyond its `StreamReadConstraints`. Review what clients send before turning it on.
- The body is read as plain maps, lists, strings, numbers and booleans, which the Grails data binder binds by name as before. Jackson annotations on command objects and domain classes, the naming strategy, the date format, module deserializers and default typing (`activateDefaultTyping`) do not apply to binding. Nor do `spring.jackson.deserialization.unwrap-root-value`, `use-java-array-for-json-array`, `use-big-integer-for-ints` and `use-long-for-ints`. They still apply where the application reads with the mapper itself, such as a Spring MVC `@RequestBody`.
- Numbers keep their types: a fractional value is a `BigDecimal`, and a whole one an `Integer`, `Long` or `BigInteger`.
- A malformed body is still a binding error, or a `DataBindingSourceCreationException` when bound to a collection with `bindData`.

## REST Validation Errors Use RFC 9457 Problems

With `grails.web.rendering.json.spring: true`, `respond` renders Spring `Errors` as `application/problem+json` instead of the legacy `{"errors": [...]}` body. Grails 9 keeps the legacy body by default; this follows the same schedule as JSON rendering, the default in Grails 10 and the legacy body removed in Grails 11. Update the API clients and tests that read validation errors:

- The status is 422, the title `Validation failed`, and the detail counts the errors, such as `Request validation failed with 2 errors.`; the `grails.validation.problem.title` and `grails.validation.problem.detail` message codes translate them.
- An `errors` extension lists the object name, field, message and codes of each error. Rejected values are left out, because they can hold passwords or tokens. Validation errors written as XML are not problems and keep their rejected values.
- To build the problem explicitly, inject `ValidationProblemDetailFactory` and `respond validationProblemDetailFactory.create(book.errors)`.
- To include rejected values after reviewing the exposure, declare your own factory bean, `new ValidationProblemDetailFactory(true, messageSource)`. It replaces the default whatever its name; one named `validationProblemDetailFactory` needs bean definition overriding, which Grails allows unless `spring.main.allow-bean-definition-overriding` is `false`.

With JSON views, an errors template, `grails-app/views/errors/_errors.gson` or one in the controller's view directory, still renders validation errors. Applications generated with the JSON views feature or the `rest-api` profile have one; delete it to answer with the problem. Without an errors template, Grails 8 rendered validation errors with `_object.gson`, writing `null` with a 200. Whatever the settings, they are now written as without JSON views, with a 422.

Whatever the settings, `respond` writes a `ProblemDetail`, or a Spring `ErrorResponse` such as an `ErrorResponseException`, as Spring MVC writes a returned `ResponseEntity`, where Grails 8 wrote it as a bean with a 200:

- The response has the status the problem carries, and a problem without an `instance` is written without one, as Spring MVC writes it; a client that reads `instance` gets it only where the application sets it. A `status` argument decides the status instead, as the status of a `ResponseEntity` does in Spring MVC: a problem that carries another keeps its own, and Spring MVC logs a warning. An `ErrorResponse` is answered with its status and headers.
- The application's `ResponseBodyAdvice` and `ErrorResponse.Interceptor` beans apply, and an `ErrorResponse`'s message codes are resolved with the application's messages.
- Spring MVC's content negotiation picks the type, as `spring.mvc.contentnegotiation.*` or a `WebMvcConfigurer` configures it, not `grails.mime`. With `favor-parameter: true` a `format` parameter decides ahead of the `Accept` header, so `?format=json` and `?format=xml` are answered with `application/problem+json`, as neither `application/json` nor `application/xml` is a problem type, and `default-content-types` decides for a client that sends no `Accept` header or only `*/*`. The problem is `application/problem+xml` only where the client asks for a problem in XML, the action responds in XML, and Spring MVC has an XML converter, which needs `jackson-dataformat-xml`. Unless `respond` names a view, a client asking for `application/xml`, a browser, or a client accepting neither JSON nor XML is answered with `application/problem+json`, where Grails 8 wrote the problem as a bean or rendered the action's view, with a 200.
- A request Spring MVC's content negotiation rejects, such as one whose `Accept` header has a quality value above 1 or more than 50 media types, or, with `favor-parameter: true`, one whose `format` parameter names a format Spring MVC does not know, is answered as Spring MVC answers it: with the status alone, without a body or a `Content-Type`, for a 4xx or 5xx problem, and otherwise with `406 Not Acceptable`.
- A view renders a problem only when `respond` names it with the `view` argument. `respond` then picks the format as for any value, and the view of that format renders the problem with the status it carries. Where that format has no view of the name, the problem is written in that format where it is JSON or XML, the JSON view of the name renders it in any other format if there is one, and otherwise it is written as without a view. In HTML the view is a GSP: a JSON or markup view of the name, or a JSP, does not render a problem as HTML, and a browser is answered with `application/problem+json` where the application has no GSP of the name.
- A renderer the application registers for `ProblemDetail`, or for a type other than `Object` that problems share, such as `Serializable`, renders them in its format. The default renderer of a format, and one the application registers for `Object`, do not. A `JsonRenderer` or `XmlRenderer` bean registered for problems writes them as Spring MVC writes them, whatever its `includes` and `excludes`.
- The problem's properties are written by Spring Boot's mapper as in a Spring MVC controller, whatever the `grails.converters.json` settings, unless `grails.converters.json.domain.jackson.enabled` gives that mapper the domain class serializer.

## HAL Rendering

The Grails HAL JSON renderers remain available through explicit renderer registration. Adding `spring-boot-starter-hateoas` does not register a Grails renderer for Spring HATEOAS `EntityModel` values; return those models from a Spring MVC controller, or register a Grails renderer for the media type.

## Grails Format Resolution and Spring MVC Content Negotiation

`withFormat`, `respond` and `response.format` apply the `grails.mime.*` rules; Spring MVC handlers keep their own content negotiation. Check these changes in functional and unit tests:

- Media type names in `Accept` and `Content-Type` match ignoring case, as do their parameters: `Accept: Application/JSON` resolves to `json`, where Grails 8 resolved it to `all`, and a body sent as `Content-Type: Application/JSON` now binds, where Grails 8 ignored it. `text/html;Q=0.1` has the quality 0.1.
- The RFC 9457 types are the `json` and `xml` formats: `Accept: application/problem+json` selects `json`, so `respond` sends `406` for an action that does not respond in JSON, and `withFormat` runs no block without a `json` or `*` one. Listed ahead of another type, a problem type counts as its format, so `Accept: application/problem+json, application/hal+json` resolves to `json`, where Grails 8 resolved it to `hal`.
- In `Content-Type`, where Grails 8 gave `null`, a body sent as `application/problem+json` is the `json` format, which `withFormat` selects and the JSON binding source binds, and one sent as `application/problem+xml` the `xml` format, which the XML binding source of `grails-xml` binds. This holds unless `grails.mime.types`, or a `MimeTypeProvider` bean such as a renderer or binding source creator, provides a type of that name. A binding source creator an application adds to the binding source registry rather than declares as a bean is not among those: a body sent as `application/problem+json` is bound by the creator of `application/json`, where Grails 8 found the creator the application added for the problem type. Declare it as a bean, or name the type in `grails.mime.types`.
- A binding source creator registered for an interface a binding target implements now handles its bodies in a content type no creator for `Object` handles.
- `text/xml` and `application/xml` merge into one `xml` type with the larger quality and the place of the one listed first: `Accept: text/xml, application/xml;q=0.1, application/json;q=0.5` resolves to `xml`, where Grails 8 resolved it to `json`. A more specific XML type listed after the first of the two takes that place ahead of the merged type: `Accept: text/xml, application/json, application/atom+xml, application/xml` resolves to `atom`, where Grails 8 resolved it to `json`.
- A client that lists a format the action does not respond in ahead of one it does is answered, and labelled, in the one it does.
- A client that accepts any type, by `*/*`, no `Accept` header, or as a browser, is answered in the first format the action lists that a renderer can render the value in, where Grails 8 answered `406`, or `404` for validation errors.
- Each text entry of `grails.mime.disable.accept.header.userAgents` is its own regular expression, matched ignoring case; a string value is a comma-separated list; a blank entry is left out; an entry that is not a regular expression fails at startup, naming the setting. A `java.util.regex.Pattern` the configuration gives, on its own or in the list, is used as it is, with its own case and flags, where Grails 8 joined the entries into one expression. In Grails 8 a string value was ignored, so that the `Accept` header of every client was honoured.
- The `json`, `xml`, `hal`, `atom`, `rss`, `csv` and `text` formats declared in `grails.mime.types`, or among the defaults where it declares none or `grails.mime.mergeDefaults` adds them, are registered with Spring as format aliases, which Spring MVC uses only with `spring.mvc.contentnegotiation.favor-parameter: true`; Spring Boot leaves it disabled, so `?format=json` still does not change what a Spring MVC endpoint returns. The MIME types a `MimeTypeProvider` bean adds are not registered, and neither are browser formats such as `html` and `js`, which preserves Spring's reflected-file-download protection. Where a format has several media types, an `application/*` one is preferred over a `text/*` one, and `spring.mvc.contentnegotiation.media-types.*` overrides these defaults.
- A renderer or binding source creator registered for an interface of `java.lang.Class`, such as `Serializable`, no longer matches every class.
- The MIME types and the `grails.mime.use.accept.header` and `grails.mime.disable.accept.header.*` settings live in the application context instead of static fields. Remove test code that reset those fields or called `HttpServletResponseExtension.loadMimeTypeConfig`, and set the settings in a spec's `doWithConfig`, since they are read once for each application context. A subclass of `DefaultAcceptHeaderParser` that overrode `createMimeTypeAndAddToList` must take the requested `MimeType` now.

### Opt Into Spring MVC Content Negotiation for Grails Formats

With `grails.mime.negotiation.spring: true`, which Grails 10 makes the default, Grails formats are resolved with Spring MVC's content negotiation manager, and the `format` parameter, URL extensions and the `grails.mime` `Accept` settings also decide what Spring MVC writes for an action. Before turning it on, check these in functional tests:

- `Accept: */*, application/json` is `json`, where it is `all` without the setting: a more specific type comes ahead of a wildcard of equal quality. A `+xml` type still comes ahead of the XML type of equal quality, and now also ahead of a `text/xml` listed without `application/xml`, which the Grails rules leave first: `Accept: text/xml, application/atom+xml` is `atom`, where it is `xml` without the setting, and `text/xml, application/xhtml+xml` is `html` rather than `xml`.
- A type with the `+json` or `+xml` suffix that no configured type names, such as `application/merge-patch+json`, is the `json` or `xml` format, in `Accept` and in `Content-Type`, so data binding reads such a body as JSON.
- A request that accepts no configured type, such as `Accept: image/png` alone, or whose `Accept` header Spring cannot read, has no format: `response.format` and `response.mimeType` are `null`, `respond` answers `406` and `withFormat` runs its `*` block, or none. Make code that reads them allow for `null`.
- A format parameter or URL extension no type is configured for is not acceptable, where it was answered in the first configured format. A format only Spring MVC's content negotiation manager maps, such as one `spring.mvc.contentnegotiation.media-types` adds, one Spring MVC registers for the types its message converters support, or a data format Grails registers with it written in another case (`?format=JSON`, `?format=HAL`), still decides what Spring MVC writes for an action, such as a `ResponseEntity`, but `respond` answers it with `406`. `?format=all` leaves the format to the `Accept` header.
- A `ResponseEntity`, `ProblemDetail` or other value Spring MVC writes for an action follows the URL extension and the format parameter: `/book/show/1.json` is JSON whatever the `Accept` header says, and a map at `/book/show/1.html` answers `406`.
- A wildcard such as `text/*` selects the first `withFormat` block, or the first format of the action, with a type compatible with it.

## Web Unit Tests

- `ControllerUnitTest`, `TagLibUnitTest` and the other web test traits always include the `restResponder` plugin, even when a spec overrides `getIncludePlugins()`. They also register JSON conversion beans with Boot's Jackson configuration, Spring MVC's message converters, the `mvcContentNegotiationManager` and `requestMappingHandlerAdapter` beans unless the spec declares beans of those names, and the `grails-xml` beans when that module is on the test classpath. Update specs that assert on the beans of their context.
- The content negotiation manager applies `spring.mvc.contentnegotiation.*` from `application.yml` or `doWithConfig`. A spec for an application that declares `@EnableWebMvc` declares the application's manager as its `mvcContentNegotiationManager` bean.
- Add a `format` parameter for `favor-parameter` with `request.addParameter`, not `params.format`.
- A test request resolves its format from its content type as a request to the application does: `application/json;charset=UTF-8` is `json`, where Grails 8 gave no format, and `text/xml` is read as `application/xml`. A format the test sets, directly or through `request.json` or `request.xml`, is kept.
- A URL mappings holder or link generator a spec declares, in `doWithSpring`, `beanRegistrar()` or a `@Bean` method of a nested `@Configuration` class, is kept, where the traits replaced it; one of a class that configuration class imports is replaced, as in Grails 8. The URL mappings plugin's own holder and link generator are still replaced, so that links follow the application's `UrlMappings` and absolute links start with `grails.serverURL`, or `http://localhost:8080` without it, so a spec that includes the `controllers` or `urlMappings` plugin declares its holder in `doWithSpring` or `beanRegistrar()`. A `UrlMappingsUnitTest` replaces the holder for each feature with one for the URL mappings it tests, as in Grails 8.
- The test's locale resolver is kept, the i18n plugin's included, where the traits replaced it with a session resolver. A spec that changes a request's locale where the application configures a fixed or accept-header resolver sets `grails.i18n.localeResolver` to `session` in `doWithConfig`. `JsonViewUnitTest` keeps them the same way.
- The bean of a `@Bean` method that a nested `@Configuration` class inherits replaces the stand-in the test's context registers under that name, such as the `StaticMessageSource` named `messageSource`, in every unit test.
- A controller a test creates with `new` responds with the application's renderer registry, including its JSON and markup views.
- Without `grails-xml` on the test classpath, setting `request.xml` to an object rather than a `String` of XML, or reading `request.XML`, throws an `IllegalStateException` that names the module. `GrailsUnitTest` and `ServiceUnitTest` do not load XML or the web plugins because those modules are on the classpath.

## Opt Into Spring MVC Exception Handlers

With `grails.exceptionresolver.springHandlers: true`, the `@ExceptionHandler` methods of the controller and of the `@ControllerAdvice` beans that apply to it, Spring Boot's `spring.mvc.problemdetails.enabled` handler among them, resolve the exceptions actions throw before the Grails exception resolver. An exception none of them handles is resolved as before. Grails 10 makes it the default, so scope `@ControllerAdvice` beans meant only for Spring MVC controllers by `assignableTypes`, `basePackages` or `annotations` before then.

Do not turn on Spring Framework 7.1's `ProblemDetailsConfigurer.renderUnhandledExceptions` in a `WebMvcConfigurer`: it answers every exception of every action with a problem, a browser's included, ahead of the Grails exception resolver, so `"500"` URL mappings no longer apply. Set `grails.exceptionresolver.problemDetails: true` instead, which writes the same problems and leaves the error page to the clients that prefer HTML and to an exception a URL mapping names.

## Actions Can Return Spring MVC Responses

- Whatever the settings, an action that returns a `ResponseEntity` or another `HttpEntity`, a `ProblemDetail`, an `ErrorResponse`, `HttpHeaders`, a `StreamingResponseBody` or a `ResponseBodyEmitter` has it written as Spring MVC writes a handler method's return value, where Grails 8 did not write it. An action that returns `null`, renders with `render`, `respond` or `redirect`, or forwards, is unaffected. Look for actions that return one of these types.
- With `grails.controllers.responseBody: true`, Spring MVC writes the value of an action annotated `@ResponseBody`, or of an action of a controller annotated so, directly or through `@RestController`, as the response body, where Grails 8 ignored the annotation and rendered the action's view. Grails 10 makes it the default, so remove annotations that had no effect before then, or the model of such an action is written in place of its view, and an interceptor's `after` sees no model.

## Generated GORM Identity and Version Properties Stay Within Domain Classes

Grails 9 no longer adds the generated `id` and `version` properties to a non-domain superclass of a domain class. Domain subclasses still inherit them from domain superclasses, and `id` and `version` a superclass declares explicitly are inherited as before. This affects every plain base class compiled with a domain subclass:

- A `@CompileStatic` method of the plain base that refers to `id` or `version`, such as `isNew()` returning `id == null`, fails with `[Static type checking] - The variable [id] is undeclared.` Move the method to the domain class, or give the base an explicit property or accessor its subclasses implement.
- `@ToString` and `@EqualsAndHashCode` on the plain base no longer see `id` and `version`. Review their property lists, and hand-written `equals` and `hashCode` methods that assume the fields are on the base.
- Remove constraints and other workarounds on command objects that existed only for the `id` and `version` they inherited from a base shared with a domain class.
- Keep a superclass shared by domain classes and command objects in `src/main/groovy`, outside `grails-app/domain`, and do not annotate it as an entity, or share behaviour through a trait instead.
- Classes compiled by Grails 8 keep the generated fields until rebuilt: run `./gradlew clean build` for the application and every affected plugin.

## RxGORM on RxJava 3

RxGORM returns RxJava 3 types. Every `rx.Observable` becomes `io.reactivex.rxjava3.core.Observable`, and an `@RxSchedule` service method that returned `rx.Single` returns `io.reactivex.rxjava3.core.Single`.

### Imports

| RxJava 1 | RxJava 3 |
|----------|----------|
| `rx.Observable` | `io.reactivex.rxjava3.core.Observable` |
| `rx.Single` | `io.reactivex.rxjava3.core.Single` |
| `rx.Subscriber` | `io.reactivex.rxjava3.core.Observer` |
| `rx.Subscription` | `io.reactivex.rxjava3.disposables.Disposable` |
| `rx.Scheduler`, `rx.schedulers.Schedulers` | `io.reactivex.rxjava3.core.Scheduler`, `io.reactivex.rxjava3.schedulers.Schedulers` |
| `rx.functions.Func1`, `Func2`, `FuncN` | `io.reactivex.rxjava3.functions.Function`, `BiFunction`, `Function<Object[], R>` |
| `rx.observers.TestSubscriber` | `io.reactivex.rxjava3.observers.TestObserver` |

A declared return type such as `rx.Observable<Book>` in a `@Service` interface must change too, or the service implementer no longer recognises the method as reactive.

### Blocking Calls

| RxJava 1 | RxJava 3 |
|----------|----------|
| `observable.toBlocking().first()` | `observable.blockingFirst()` |
| `observable.toBlocking().single()` | `observable.blockingSingle()` |
| `observable.toList().toBlocking().single()` | `observable.toList().blockingGet()` |
| `single.toBlocking().value()` | `single.blockingGet()` |
| `observable.toBlocking().first()` compared with `null` | `observable.firstElement().blockingGet()`, which returns `null` when empty, or expect `blockingFirst()` to throw `NoSuchElementException` |

`Observable.toList()` returns a `Single<List<T>>` in RxJava 3. The RxGORM methods that emit a list, such as `list()`, `DetachedCriteria.toList()`, criteria `list()` and `toListObservable()` on a lazy association, still return `Observable<List<T>>`, so subscribers to them do not change. `findAll()` on a `DetachedCriteria` returns an `Observable<T>`, so `findAll().toList()` is RxJava's own `toList()` and gives a `Single<List<T>>`.

### Subscribers

- Closure subscriptions, `observable.subscribe { Book book -> ... }` and the two-closure `subscribe(onNext, onError)`, are unchanged.
- A full subscriber implements `Observer<T>`: add `onSubscribe(Disposable d)`, rename `onCompleted()` to `onComplete()`, and keep the `Disposable` to cancel instead of calling `unsubscribe()` on a `Subscription`.
- `DetachedCriteria`, `ObservableProxy` and the lazy association collections implement `ObservableSource<T>`. Their `subscribe(Observer)` returns `void`; code that kept the returned `Subscription` keeps the `Disposable` from `onSubscribe` instead. They can be passed to `Observable.wrap(..)`, `concatWith(..)` and any other operator that takes an `ObservableSource`.
- `TestSubscriber` tests become `TestObserver` tests: `awaitTerminalEvent(..)` is `await(..)`, `assertCompleted()` is `assertComplete()`, and `onNextEvents` is `values()`.

### Empty Results and Null

RxJava 3 never emits `null`.

- A query with no result completes empty, as before.
- `save()`, `insert()` and `persist()` on an instance the datastore did not write complete empty instead of emitting `null`.
- An `@RxSchedule` service method returning `Single` whose body returns `null` signals `java.util.NoSuchElementException`; one returning `Observable` completes empty. Where a missing result is expected, declare `Observable` and use `firstElement()`, which gives a `Maybe` whose `blockingGet()` returns `null` when empty, rather than catching the exception. RxJava 3's own zero-or-one type, `Maybe`, is not yet recognised as a data service return type.
- A `switchMap` or `flatMap` function must return an `ObservableSource`, never `null`; use `Observable.empty()`.

### Datastore Implementations

A datastore implementation of RxGORM compiled for Grails 8 or earlier, such as the RxGORM for MongoDB plugin, links against RxJava 1 and fails at runtime with `NoClassDefFoundError: rx/Observable`. It must be rebuilt against this release. The `rx-mongodb` profile feature is removed, because the plugin it generated has had no release since GORM 6.1 and was never published to Maven Central. `RxEntity` has had no datastore implementation for several releases and its removal is under discussion for Grails 9, so an application that implements it should plan to move to `GormEntity` with the RxJava data services, which work on every GORM datastore. The import and blocking-call changes above apply to both.

## Deprecated RxJava 1 and RxJava 2 Modules

`grails-async-rxjava`, `grails-async-rxjava2`, `grails-events-rxjava` and `grails-events-rxjava2` are deprecated for removal. They still work and log a warning such as `The RxJava 1 promise factory (grails-async-rxjava) is deprecated` when the promise factory or event bus is created.

- Replace `org.apache.grails.async:grails-async-rxjava` or `grails-async-rxjava2` with `grails-async-rxjava3`.
- Replace `org.apache.grails.events:grails-events-rxjava` or `grails-events-rxjava2` with `grails-events-rxjava3`.
- The `Promise` and `EventBus` APIs are the same for every implementation, so an application that only uses the Grails APIs needs no code change. The RxJava 3 promise factory carries a `null` value as the RxJava 1 one does: `createPromise()` resolves to `null`, a task that returns `null` resolves to `null`, and a `null` keeps its place in the list `onComplete` and `waitAll` deliver. The RxJava 2 factory does not: it fails with a `NullPointerException` or resolves to the `java.lang.Void` class instead.
- Code that hands RxJava types to the Grails ones, such as the `rx.Scheduler` given to the `RxEventBus` constructor, moves to the RxJava 3 types as described above.
- Configure RxJava 3 scheduling through `io.reactivex.rxjava3.plugins.RxJavaPlugins` instead of RxJava 1 `RxJavaHooks` or RxJava 2 `RxJavaPlugins`.

## Spring Boot Registers the Character Encoding Filter

Grails no longer registers a character encoding filter of its own. Spring Boot's runs at `Ordered.HIGHEST_PRECEDENCE`, ahead of every other filter, and `spring.servlet.encoding` configures it. In Grails 8, Grails' filter made Spring Boot's back off, so `spring.servlet.encoding` had no effect.

- The request encoding is now forced by default, as in any Spring Boot application: a request that declares another charset is decoded as UTF-8 anyway, for its parameters, a body read through `getReader()`, `request.JSON` and `request.XML`, and a JSON or XML body bound to a command object or domain class. A PUT, PATCH, DELETE or QUERY form is still decoded with the charset it declares, as in Grails 8, because `FormContentFilter` parses it. Set `spring.servlet.encoding.force-request: false` to keep the charset a request declares, as Grails 8 did.
- `grails.filter.encoding` and `grails.filter.forceEncoding` are deprecated. Grails translates them into `spring.servlet.encoding.charset` and `spring.servlet.encoding.force` with a warning, also from files named by `grails.config.locations`, and the application's own `spring.servlet.encoding` setting wins over them. Move to the Spring Boot properties.
- `GrailsFilters.CHARACTER_ENCODING_FILTER` is deprecated, and its order is now `Ordered.HIGHEST_PRECEDENCE`, the order of Spring Boot's encoding filter, where Grails 8.0 had -180, so a filter at `CHARACTER_ENCODING_FILTER.order + 1` runs right after the encoding filter, ahead of `FormContentFilter` and of Grails' security headers filter at `GrailsFilters.FIRST`: it no longer sees the parameters of a PUT, PATCH, DELETE or QUERY form, and a response it sends without continuing the chain gets no security headers. Use `Ordered.HIGHEST_PRECEDENCE + 1` instead, or `GrailsFilters.ASSET_PIPELINE_FILTER.order + 1` to keep the place it had in Grails 8.0.
- `spring.servlet.encoding.enabled: false` turns the filter off.
- Grails 8.1 already registers its encoding filter at `Ordered.HIGHEST_PRECEDENCE`, so coming from 8.1 the encoding filter runs where it did. Coming from 8.0, filters ordered between `Ordered.HIGHEST_PRECEDENCE + 1` and -181 now run after the encoding filter, with the request encoding set. In a packaged application, asset-pipeline's filter then declares that charset on every asset response, for example `text/css;charset=UTF-8`. A filter among them that reads a request body sent without a charset through `getReader()` now decodes it as UTF-8 instead of ISO-8859-1.
- A filter that reads request parameters needs a later order than `Ordered.HIGHEST_PRECEDENCE`. The CAS plugin registers its single sign-out filter at `Ordered.HIGHEST_PRECEDENCE + 1`.

## A Configuration Key Below a Value That Is Not a Map Is Absent

`getProperty` on `grailsApplication.config` answers a dotted key that runs past a value that is not a map, such as `server.url.port` when `server.url` is a string, `server.ports.0` when `server.ports` is a list, or `grails.gorm.default.constraints.nullable` when the parent is a closure from `application.groovy`, with `null` or the default, and `containsProperty` of such a key is `false`; Grails 8 answered with the parent value. `navigate` answers `null` too. Below a list, a whole-number segment names an element instead, as in Spring Boot: `server.ports.0` is the first port, where Grails 8 answered with the whole list, and `server.ports[0]` is the same element for a list from `application.yml` or `application.groovy`. A list of maps reads into its elements, `app.rows.0.name`; an index past the end, or a number with a leading zero such as `01`, is absent; a map whose keys are numbers reads as before.

- A typed read with a default changes silently, because the default takes over: with `x.y: false` set as a shorthand for the block, `getProperty('x.y.enabled', Boolean, true)` answered `false` in Grails 8 and answers `true`.
- A property source of lower precedence that sets the deeper key now answers it: with `a.b: x` in one source and `a.b.c: y` in a source of lower precedence, `getProperty('a.b.c')` answers `y`, where Grails 8 answered `x`, as a Spring `Environment` over the same sources does, and `containsProperty('a.b.c')` stays `true`. `navigate('a', 'b', 'c')` answers `null`.
- Code that reads a child of a string or a closure and relied on being answered with the parent reads the parent instead.
- Code that read a whole list through a key ending in a whole number, such as `server.ports.0`, now gets the element; read the list as `server.ports`.
- A typed read with a default below such a value, such as `getProperty('x.y.enabled', Boolean, true)` when `x.y` is `false`, reads the parent, or the configuration sets the deeper key.

## The Spring Security User Cache Stores Copies

With `cacheUsers`, the Spring Security plugin's user cache stores users by value: each lookup gets a serialized copy, where Grails 8 handed out the cached instance. Erasing the password of a signed-in user no longer erases the cached one, so the cache is used on the next sign-in, and the session's user holds no password.

- A custom `UserDetails` with a field that cannot be serialized is not cached: it is loaded on every sign-in, with a warning. Make the field `transient` or hold an id.
- Code that changed a user read from `userCache` puts it back with `userCache.putUserInCache`.
- `cacheConfig` on the `userCache` bean, a `MutableConfiguration` with `setStoreByValue(false)`, restores storing by reference.
- In a native image, the plugin registers `User`, `GrailsUser` with a `Long` id and `SimpleGrantedAuthority` for serialization; a custom user class or id type needs metadata, which `traceNativeMetadata` records when the login form is in `forms`.

## A Class That Is Not Multi-Tenant Keeps Its Datastore Inside a Tenant Block

With a database or a schema per tenant, a domain class that does not implement `MultiTenant` stays on its own datastore inside `Tenants.withId(...) { }` and other blocks that bind a tenant, as in Grails 7. Only classes that implement `MultiTenant` follow the tenant. Grails 8.0 changed this, so an application upgrading from Grails 7 sees no change, and for most applications no changes are required. In Grails 8, with GORM for MongoDB and the in-memory datastore of unit tests, every operation on such a class inside the block went to the tenant's database: `save`, `get`, `list`, `count`, `where` queries, data service methods, `withSession` and `GormEnhancer.findDatastore`. With GORM for Hibernate, `save`, `get`, `list` and `count` already used the default database, while `where` queries and data service methods failed there and now use the default database too.

- Writes of such a class inside the block go to the default datastore and run in a transaction of that datastore. Its data service runs in its own datastore's transaction. Its own `withTransaction`, `withNewTransaction` or data service inside the tenant's transaction is independent: what it saves stays when the tenant's transaction rolls back. A `save`, `insert`, `delete`, `saveAll` or `deleteAll` of the class inside the tenant's transaction but outside one of its own, such as in a `@Transactional` method running in the tenant's transaction, now throws `IllegalTransactionStateException` with GORM for MongoDB and the in-memory datastore (in Grails 8 the tenant's transaction wrote it to the tenant's database). Move it into the class's data service or `withTransaction`, or save with `flush: true`, which writes at once. With GORM for Hibernate such a save already failed in Grails 8.
- With GORM for MongoDB, documents of such a class saved inside a tenant block in Grails 8 are in the tenant's database, which Grails 9 no longer reads for it. A class whose instances belong to one tenant implements `MultiTenant` before the upgrade. A class that a multi-tenant class references is loaded from the tenant's database, so it implements `MultiTenant` too. Only shared instances that no multi-tenant class references move to the default database.
- A data service compiled against an earlier release keeps following the tenant until it is compiled again.

## Unit Tests Run Domain Class Events for Every Data Source and Tenant

In a `DataTest` or `DomainUnitTest` spec, a save through a data source other than the default one, such as `book.secondary.save()`, and a save for a tenant with a database or a schema of its own, now run the domain class's events, such as `beforeInsert`, and set `dateCreated` and `lastUpdated`, as in the application. Grails 8 ran neither. Unit tests still do not set `@CreatedBy` and `@LastModifiedBy`, even with an `AuditorAware` bean in the test.

- A test that expected such a save to keep the timestamps it was given saves inside `withoutTimestamps { }` of that data source's listener, such as `Book.secondary.autoTimestampEventListener`.
- A test that expected a domain class event not to run on such a save expects what the event does.
- The in-memory datastore follows `grails.gorm.events.autoTimestampInsertOverwrite` and `grails.gorm.autoTimestampCacheAnnotations` on every data source, the default one included. With `autoTimestampInsertOverwrite: false`, a test that expected a given `dateCreated` or `lastUpdated` to be overwritten expects it kept.

## A Domain Class Has an `autoTimestampEventListener` Property

Every domain class has a static `autoTimestampEventListener` property: the listener that sets its timestamp and auditor properties, for the data source its saves go to.

- A domain class that declares a property named `autoTimestampEventListener` of another type no longer compiles, as its `getAutoTimestampEventListener()` return type is incompatible with the one in `GormEntity`. One typed `AutoTimestampEventListener`, such as an injected bean, compiles but hides the static property, so `Book.autoTimestampEventListener` throws `MissingPropertyException`. Rename the property.
- Code that used an injected `autoTimestampEventListener` bean in a domain class reads `Book.autoTimestampEventListener` instead.

## Every Data Source Sets the Auditor Properties and Follows the Auto-Timestamp Settings

With several data sources, or a database or a schema per tenant, a save through a data source other than the default one:

- sets `@CreatedBy` and `@LastModifiedBy` from the application's `AuditorAware`; Grails 8 set them only on the default data source.
- follows `grails.gorm.events.autoTimestampInsertOverwrite` and `grails.gorm.autoTimestampCacheAnnotations`; Grails 8 overwrote a given `dateCreated` or `lastUpdated` whatever the setting said.
- with GORM for Neo4j, runs the domain class's events and sets `dateCreated` and `lastUpdated`; Grails 8 ran neither on another Neo4j connection.

Most applications need no change. Check the domain classes saved through another data source whose auditor properties, given timestamps or, with Neo4j, events were expected to be left alone.

## JLine 4

Grails 9 manages JLine 4 (`org.jline:jline`), the line Groovy 6's `groovysh` is built on, in place of JLine 3. It is the terminal library behind the prompts of the `grails` command line tools and `grails shell`, and behind `GrailsConsole`. The `org.jline` packages are unchanged.

- Recompile code that uses JLine, such as an `org.jline.reader.Completer` of a command, against JLine 4, which removed APIs JLine 3 had deprecated: use `org.jline.builtins.Completers.FileNameCompleter` for `org.jline.reader.impl.completer.FileNameCompleter`, and `Terminal.inputEncoding()`/`outputEncoding()` for `stdinEncoding()`, `stdoutEncoding()` and `stderrEncoding()`; `TerminalBuilder.codepage(int)`, `PROP_JNA` and `PROP_JANSI` are gone.
- Remove dependencies on `org.jline:jline-terminal-jna` and `org.jline:jline-terminal-jansi`, which have no JLine 4 version.
- `info.picocli:picocli-shell-jline3` still runs, but its `PicocliCommands` implements the deprecated `org.jline.console.CommandRegistry`; `org.jline:jline-picocli`, at the JLine version the Grails BOM manages, is the JLine 4 bridge to move to.

## Command Lines of Application Commands

The command line parser of the `grails` tool and of `runCommand`, which application and plugin commands read their command line through, changed in Grails 9:

- An empty argument after an option without a value is that option's empty value: `--name "" value` gives an empty `name` and the argument `value`, where it used to give `name=value`.
- The text inside a pair of double quotes is trimmed: `" a "` reaches the command as `a`, where it used to be ` a `.
- `--name=` gives an empty `name` (it threw `ArrayIndexOutOfBoundsException`), a lone `"` argument is passed on (it threw `StringIndexOutOfBoundsException`), and an option's value is everything after the first `=`: `--filter=status=active` gives `status=active`, where it gave `status`. Remove workarounds for these.

## A Sort by an Alias That the Query Does Not Define Is Rejected

With GORM for Hibernate 5 and Hibernate 7, a sort whose path starts with a name that is neither a property of the queried class nor an alias that the query defines throws `IllegalArgumentException: Invalid sort property`. Hibernate 5 ignored it; Hibernate 7 either ignored it or failed inconsistently. This applies to where queries, detached criteria, static list, dynamic finders, example queries and criteria sorts. Stored sorts are validated for `count()` and `get()` too, even though a count does not order its result. An empty example rejects an invalid sort; with a valid sort Hibernate 7 still returns `null`.

- Check dotted sort paths, including those from request parameters, against properties or aliases defined by the same query.
- Remove the sort or declare its alias, as in `Book.where { def a = author; a.name ==~ 'S%' }.list(sort: 'a.name')`.
- Prefer an association path such as `author.name` when optional filters change the aliases available. Check the count query too when it shares a table's sort parameter.

## Cache Annotations Use `grailsCacheManager`

The cache annotations and `GrailsCacheAdminService` cache with the bean named `grailsCacheManager`, or the bean with that alias, like the cache tags. Grails 8 injected them a cache manager by type, so a cache manager of the application's own marked primary or with a `@Priority` took them from `grailsCacheManager`; now no other cache manager is taken or created for them.

- Search for `CacheManager` beans the application defines, marked primary or with a `@Priority`, and for `grails.cache.enabled: false`. Where the cached methods should keep caching with such a cache manager, name it `grailsCacheManager` or register that alias; declaring `@Qualifier("grailsCacheManager")` on its `@Bean` method or class does not select it. With caching enabled it replaces the plugin's and must implement `GrailsCacheManager`.
- Without a `grailsCacheManager`, as with `grails.cache.enabled: false`, the annotated methods run uncached, where Grails 8 used the only or primary cache manager, such as Spring Security's `cacheUsers` one.
- A `grailsCacheManager` created from a bean with cached methods no longer starts; create it from that bean's dependencies.
- Plugins compiled against Grails 8 keep the Grails 8.0.1 behaviour until they are compiled against Grails 9.

## One Plugin Contract

Every plugin, an application's own included, has to be changed: a Grails 8 plugin is not discovered by Grails 9.

- A plugin is a concrete `grails.plugins.Plugin` subclass annotated with `@grails.plugins.metadata.GrailsPluginDescriptor`. A `*GrailsPlugin` class without the annotation is not a plugin; a class that does not extend `Plugin` cannot be one.
- Move the metadata properties into the annotation as constants: `after` (was `loadAfter`), `before` (was `loadBefore`), `observe`, `evict`, `watchedResources`, `providedArtefacts`, `pluginExcludes`, `environmentsIncludes`/`environmentsExcludes` (was `environments`), `grailsVersion` (defaults to the compiling Grails version and later), `name`. `dependsOn` becomes a list of `'name'` or `'name:range'` strings: `def dependsOn = [core: version]` is `dependsOn = ['core:${plugin.version}']`.
- Delete an `enabled` property: a plugin cannot be declared disabled (the annotation has no `enabled`); leave it out with `grails.plugin.excludes`.
- An unmet `dependsOn` stops the application with one `PluginException` naming every plugin and missing, wrong-version, failed, excluded or other-environment dependency (Grails 8 logged at ERROR and ran without the plugin). `grails.plugin.excludes` no longer drops the dependents of an excluded plugin: list them too. `grails.plugin.includes` still pulls in dependencies; a dependent of an evicted plugin still loads. `PluginDiscovery.hasFailedPlugin`/`getFailedPlugin`/`getFailedPlugins` and `GrailsPluginManager.getFailedPlugin`/`getFailedLoadPlugins` are removed.
- `doWithDynamicMethods` is removed (from `Plugin`, `GrailsApplicationLifeCycle`, `GrailsAutoConfiguration`, `GrailsPlugin` and the manager's `doDynamicMethods`): move its code into `doWithApplicationContext`, which runs plugin by plugin in plugin order, so declare `after`/`dependsOn` on a plugin whose dynamic methods you call there.
- `Plugin.artefacts` and `doArtefactConfiguration()` are removed: the compiler registers Groovy `ArtefactHandler`s in `META-INF/grails.factories`; register a Java one in `src/main/resources/META-INF/grails.factories` (`grails.core.ArtefactHandler=...`).
- `getTypeFilters()` (on `Plugin`, `GrailsPlugin` and the manager), `GrailsPlugin.getPropertySource()`, `GrailsPlugin.getPluginExcludes()` (read `PluginInfo.getDefinition().getPluginExcludes()`), `GrailsPluginManager.informOfClassChange(Class)`, `ProfilingGrailsPluginManager` and `PluginManagerLoader` are removed.
- `pluginExcludes` are artefact class-name patterns (`'com/example/demo/**'`); source-path patterns such as `'grails-app/views/error.gsp'` are compilation errors - delete them and exclude files from the `jar` task instead.
- `grails.plugins.metadata.GrailsPlugin`, `GrailsVersionUtils.getPluginName(Class)` and `getPluginVersion(Class)` are removed; use `pluginManager.getPluginForClass(type)`, which finds the plugin whose descriptor lists the class.
- Delete `version` (the project version is used), `title`, `author`, `authorEmail`, `description`, `documentation`, `license`, `organization`, `developers`, `issueManagement`, `scm` and `profiles` (nothing reads them; the POM from `grailsPublish` carries them).
- Replace closure-valued hooks (`def onChange = { event -> }`) with the `Plugin` methods.
- `doWithSpring` is removed from plugins, both `Closure doWithSpring()` and `doWithSpring(BeanBuilder)`; a plugin declaring either fails to compile. Move its beans: a class with a supplier, scope, `primary` or `lazy` goes to `beanRegistrar()` (`registry.registerBean(name, Type) { spec -> spec.supplier { ... } }`); an abstract parent (`bean.parent`), alias (`springConfig.addAlias`), `bean.autowire = 'byName'`, `ref(...)` property values, factory bean/method, destroy method or inner bean goes to a `BeanDefinitionRegistryPostProcessor, PriorityOrdered` (`Ordered.HIGHEST_PRECEDENCE`) that `beanRegistrar()` registers (`registry.registerBean(name, Type) { it.infrastructure().supplier { ... } }`), using `GenericBeanDefinition` (`setParentName`, `setAbstract`, `setAutowireMode`, `setFactoryBeanName`/`setFactoryMethodName`, `setDestroyMethodName`), `RuntimeBeanReference`, `ManagedList`/`ManagedMap` and `registry.registerAlias`; compile-time beans go to a `beans` block. A large closure can run unchanged from such a post-processor: `new grails.spring.BeanBuilder(null, classLoader)`, set a `Binding` with the variables it read (`application`, `manager`), `beans(closure)`, `registerBeans(registry)`; `springConfig` no longer holds other plugins' beans, use `ref('name')`/`registry.containsBeanDefinition`.
- Plugin beans still register before Spring Boot auto-configuration (`@ConditionalOnMissingBean` backs off), in plugin order: a later plugin now replaces an earlier plugin's bean of the same name however either registers it (Grails 8 let an earlier plugin's registrar bean beat a later plugin's `doWithSpring` bean). Declare `after`/`dependsOn` on the plugin whose bean you replace. A registrar bean also replaces a bean of the same name another plugin's `beans` block declares; that is how to replace one. Application `resources.groovy`/`doWithSpring` beans still win.
- Applications keep `doWithSpring` (moved from `GrailsApplicationLifeCycle` to `GrailsApplicationClass`); `GrailsApplicationLifeCycleAdapter` no longer has it. `GrailsPlugin.doWithRuntimeConfiguration`, `GrailsPluginManager.doRuntimeConfiguration` (both), `DefaultGrailsPluginManager.reloadPlugin`/`setParentApplicationContext` are removed. Tests apply a plugin's beans with `defineBeans(plugin)` (registrar plus its post-processors) or `getIncludePlugins()`.
- The compiler writes `META-INF/grails/plugins.imports` and `META-INF/grails/plugins/<name>/plugin.properties` beside the classes, and copies `grails-app/conf/plugin.yml` (or `plugin.groovy`) to `META-INF/grails/plugins/<name>/`. Precompiled plugin GSP views list themselves in `META-INF/grails/plugins/<name>/views.properties`. Update shadow/fat-jar rules or resource lookups that assumed `META-INF/grails-plugin.xml`, a root `plugin.yml` or `gsp/views.properties`; merge `plugins.imports` lines when combining jars.
- `META-INF/grails-plugin.xml` is not written, published or read. A broken descriptor, or one naming a missing or non-`Plugin` class, stops the application from starting.
- A Java plugin adds `grails-core` to `annotationProcessor` so its descriptor is written.
- One plugin order: a plugin comes after its `dependsOn` (now an ordering too) and `after` plugins and before its `before` plugins; ties go by plugin name, never class path order; a circular order stops startup with a `PluginException` naming the circle. `PluginDiscovery.getPluginsInLoadOrder()` is removed (use `getPluginsInTopologicalOrder()`), and `GrailsPlugin.getLoadAfterNames()`/`getLoadBeforeNames()` are `getAfterNames()`/`getBeforeNames()`. A descriptor still carrying `loadAfter`/`loadBefore` (an un-rebuilt plugin) stops startup: rebuild the plugin.
- Where two plugins provide the same config, message, view (precompiled, source or served by `GroovyPagesServlet`), static resource or URL mapping, the later plugin wins (Grails 8 let the earlier one win for source views, static resources and `GroovyPagesServlet`); the application still wins.
- `dependsOn` must be provided by a dependency of the plugin's project: the Grails Gradle plugin's `verifyPluginDependencies` (run by `check`) fails otherwise. Move an ordering-only `dependsOn` to `after`. The framework did so: `mimeTypes`/`converters` no longer depend on `controllers`, `groovyPages`/`layout` not on `i18n`, `controllers` not on `urlMappings`; `urlMappings` depends on `controllers`.
- The compiler orders a `beans` block's auto-configuration by the plugin order and records it in `plugin.properties` (`autoConfiguration=`); `@AutoConfigureAfter`/`@AutoConfigureBefore`/`@AutoConfiguration(after/before/afterName/beforeName)` naming another Grails plugin's auto-configuration is a compile error (use `after`/`before`); ordering against non-Grails Boot auto-configurations is kept.
- Every loaded plugin is an `org.grails.plugins.DefaultGrailsPlugin`, whether found on the class path or registered directly; `BinaryGrailsPlugin` and `AbstractGrailsPlugin` are removed, so replace `instanceof BinaryGrailsPlugin` with `instanceof DefaultGrailsPlugin` (it has `resolveView`, `getResource`, `getProperties(Locale)` and `getProjectDirectory`).
- `PluginDescriptor` and `PluginMetadata` are removed: `org.apache.grails.core.plugins.PluginInfo` holds the `PluginDefinition`, plugin class, descriptor resource and configuration; `PluginUtils.scanPluginDescriptorResources` returns a map of descriptor resource to definition.
- `GrailsPlugin.getInstance()` is removed (use `getPluginInstance()`, typed `Plugin`), as are `refresh()`, `getProperties()`, `getDescriptor()`, `getPluginDir()`, `setDescriptor`, `setBasePlugin`, `addExclude`, `getEvictionNames()`, `Comparable` and `GrailsPluginInfo`; the public `Plugin.enabled` field, `GrailsPlugin.isEnabled()` and `PluginInfo.isEnabled()` are removed.
- Plugin sources are no longer compiled at run time: `PluginDiscovery.setPluginResources`/`getPluginResources`/`getDynamicPlugins`, the resource-taking `DefaultPluginDiscovery` constructors, `GrailsPluginManager.refreshPlugin` and `getUserPlugins` are removed; register compiled classes with `setPluginClasses` or `new DefaultPluginDiscovery(Class[])`.

# After the Upgrade

## Verification Checklist

- [ ] `gradle.properties` names the Grails 9 version and the wrapper is Gradle 9.7 or later
- [ ] No configuration names a property Spring Boot 4.2 renames, such as `server.tomcat.use-relative-redirects` or `management.metrics.observations.ignored-meters`
- [ ] Behind a reverse proxy that sends `Forwarded` or `X-Forwarded-Prefix`, `spring.mvc.forwarded-headers.header-format` or `use-forwarded-prefix` is set, and an application that declares `@EnableWebMvc` registers a `ForwardedHeaderFilter` itself
- [ ] No client or test expects an absolute `Location` from a `chain`, Spring Security or `redirect:` redirect, or `server.tomcat.servlet.use-relative-redirects: false` is set
- [ ] No test uses `TestRestTemplate` or `@AutoConfigureTestRestTemplate`
- [ ] No client reads the `instance` of a problem unless the application sets it
- [ ] No test has a static nested `@Configuration` class in a superclass or enclosing class that it should not load
- [ ] A `bootBuildImage` build that needs repeatable images sets both `builder` and `runImage`
- [ ] Dashboards and alerts allow for the `error`, `exception` and `uri` tags of `http.server.requests` now naming the action's exception and route, and do not read `http.server.requests.active` unless `management.metrics.observations.include-active-long-task-timer` is `true`
- [ ] An application that uses RabbitMQ declares `spring-boot-starter-rabbitmq`, not `spring-boot-starter-amqp`
- [ ] With the Spring Security plugin's `digest.useCleartextPasswords`, stored passwords carry no `{noop}` prefix
- [ ] In an application using the GraphQL plugin, queries stay within 100 levels and 100,000 fields or `QueryComplexityLimits.setDefaultLimits` is called at startup, and no `GraphQLSchemaInterceptor` deprecates a required argument or input field
- [ ] The application and every affected plugin were rebuilt from `./gradlew clean`
- [ ] No source file imports `rx.` or `io.reactivex.` without the `rxjava3` segment
- [ ] No test calls `toBlocking()`
- [ ] No build file declares `io.reactivex:rxjava`, `io.reactivex.rxjava2:rxjava`, `grails-async-rxjava`, `grails-async-rxjava2`, `grails-events-rxjava` or `grails-events-rxjava2`
- [ ] Every `Observer` implementation has `onSubscribe(Disposable)` and `onComplete()`
- [ ] Every `switchMap` and `flatMap` function returns an `ObservableSource`, never `null`
- [ ] No build file declares `grails-async-gpars` or `grails-events-gpars`, and no source file imports `groovyx.gpars`
- [ ] No source file calls `GParsPool.withPool` or `GParsConfig`
- [ ] No build file sets `grails { indy = true }`, and build logic reads `grails.indy` with `getOrElse` or `present`
- [ ] An application that sets `indy = false` declares `org.apache.groovy:groovy-callsite` for every source set that needs it
- [ ] An application that serves or reads XML declares `org.apache.grails:grails-xml`, and a JSON-only one excludes it
- [ ] Every opt-in setting the application depends on is set explicitly, and the tests pass with the Grails 10 values of the three JSON settings
- [ ] `grails.converters.json.date: javascript` is not combined with `grails.converters.json.legacy: false`
- [ ] Marshallers that shaped `respond` output have Jackson serializers before `grails.web.rendering.json.spring` is turned on, and `JSON.createNamedConfig` and `JSON.use` calls are moved to `NamedJsonConfigurationRegistry` or noted for Grails 11
- [ ] API clients and tests that read validation errors expect a 422, and an `application/problem+json` body where `grails.web.rendering.json.spring` is `true`
- [ ] Web unit tests set `grails.mime.*` and `grails.i18n.localeResolver` in `doWithConfig`, and no longer reset static MIME state
- [ ] `@ControllerAdvice` beans meant only for Spring MVC controllers are scoped, and no Grails controller that renders views carries `@ResponseBody` or `@RestController`
- [ ] No plain superclass of a domain class refers to the generated `id` or `version` in `@CompileStatic` code, `@ToString` or `@EqualsAndHashCode`
- [ ] No configuration sets `grails.filter.encoding` or `grails.filter.forceEncoding`
- [ ] Clients that send a charset other than UTF-8 still work now that UTF-8 decoding is forced, or `spring.servlet.encoding.force-request: false` is set
- [ ] No application filter that reads request parameters is registered at `Ordered.HIGHEST_PRECEDENCE`
- [ ] No filter is ordered from `GrailsFilters.CHARACTER_ENCODING_FILTER`, which is deprecated
- [ ] The application starts with no `deprecated` warning from the Grails async or events modules
- [ ] No code reads a child of a string or closure configuration value expecting the parent
- [ ] No code reads a whole list through a key ending in a whole number, such as `server.ports.0`
- [ ] No typed read with a default, such as `getProperty('x.y.enabled', Boolean, true)`, names a key below a configuration value that is not a map
- [ ] With a database or a schema per tenant, every domain class saved inside a tenant block whose instances belong to a tenant, or that a multi-tenant class references, implements `MultiTenant`
- [ ] Every write of a class that is not multi-tenant inside a tenant's transaction runs in the class's data service or its own `withTransaction`, or uses `flush: true`
- [ ] No domain class declares a property named `autoTimestampEventListener`
- [ ] Unit tests that save through another data source or a tenant expect the domain class events to run and the timestamps to be set, or save inside `withoutTimestamps { }`
- [ ] Domain classes saved through a data source other than the default one are checked for auditor properties, given timestamps and, with Neo4j, events that were expected to be left alone
- [ ] Code that uses JLine compiles against JLine 4, and no build file declares `org.jline:jline-terminal-jna` or `org.jline:jline-terminal-jansi`
- [ ] Commands that read an option followed by an empty argument, or quoted arguments with spaces at their ends, expect the Grails 9 values, and no command works around a value cut at its second `=`
- [ ] Every plugin class, the application's own included, is a `Plugin` subclass annotated with `@GrailsPluginDescriptor`, declares no metadata properties, and every plugin dependency is a Grails 9 build; no custom publishing, shadow jar or resource-lookup code references `META-INF/grails-plugin.xml` or a plugin's root `plugin.yml` / `gsp/views.properties`
- [ ] The full test suite passes on Groovy 6

## Pitfalls to Avoid

- Do not add `io.reactivex:rxjava` back to make old imports compile; port the code instead. Two RxJava generations on one classpath hide the mismatch until a datastore or plugin hands the application the wrong `Observable`.
- Do not compare a blocking result with `null` to detect an empty result; RxJava 3 throws instead.
- Do not keep a `Subscription` field for an RxGORM subscription; the handle is the `Disposable` passed to `onSubscribe`.
- Do not pin Groovy, Spock or RxJava versions in the application build; the Grails BOM manages them.
- Do not add the Groovy concurrency module alongside the GPars one to migrate gradually; only one implementation of each contract is used, and the event bus builder fails when it finds two.
- Do not set `grails { indy = false }` to avoid porting code; it needs `groovy-callsite` on every source set and rules out a GraalVM native image.
- Do not leave `grails.converters.json.legacy` unset; the default changes in Grails 10, so an application that has not checked its JSON with `false` will change output without a code change.
- Do not turn on `grails.converters.json.legacy: false` without `grails.databinding.json.jackson: true`; `request.JSON` and data binding then accept different JSON.
- Do not register a marshaller to customize `respond` once `grails.web.rendering.json.spring` is `true`; Spring's path ignores it. Use a Jackson serializer.
- Do not configure a Jackson mapper for a controller unit test in `doWithSpring` or `beanRegistrar()`; Boot has already created its own. Use a nested `@Configuration` class.
- Do not exclude `grails-xml` from an application that reads `request.XML` or renders XML; the failures appear only at run time.
- Do not rely on an incremental build after the upgrade; classes compiled by Grails 8 keep the generated `id` and `version` on a plain superclass until rebuilt.
- Do not define a `ValidationProblemDetailFactory` that includes rejected values without reviewing what they can expose.
- Do not register a filter that reads request parameters at exactly `Ordered.HIGHEST_PRECEDENCE`. A `FilterRegistrationBean` with the same order runs before Spring Boot's encoding filter, so form input arrives decoded as ISO-8859-1.
