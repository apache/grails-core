---
name: grails-upgrade
description: "Upgrades a Grails application to Grails 9.0 (release 9.0.0-M1). Base version: Grails 8; use it for an application on Grails 8 or on an earlier Grails 9.0 release. An application on Grails 7 or earlier first upgrades to the last Grails 8 release. Covers Groovy 6, Spring Boot 4.2 and Spring Framework 7.1, graphql-java 26, the GPars deprecation in favour of groovy.concurrent, invokedynamic compilation, the separate grails-xml module, the opt-in settings for Spring JSON rendering, Jackson request binding, Spring MVC exception handlers and response bodies, the Jackson-backed JSON converter and domain class rendering, RFC 9457 validation problems, format resolution, web unit tests, generated GORM id and version properties, RxGORM on RxJava 3, the deprecated RxJava 1 and 2 modules, the character encoding filter, configuration keys below a value that is not a map, JLine 4, the command lines of application commands, and the Grails wrapper"
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
- Fixing `grails` and `grailsw` commands that fail after the upgrade: `There is no command ...`, an environment in front of a command, a missing `create-*` command, a plugin command or script that is not found, a `profile` dependency, or a Grails wrapper that ends with `ClassNotFoundException`.
- Updating scripts, CI jobs and documentation that call the Grails shell (`grails-shell-cli`), `grails -t ...`, or commands of profiles.

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

1. Update `grailsVersion` in `gradle.properties` and refresh the Gradle wrapper; let the Grails BOM manage Groovy, Spock, and RxJava versions. When the application has a Grails wrapper (`grailsw`), replace it in the same step, following [The `grails` Command and the Grails Wrapper](#the-grails-command-and-the-grails-wrapper): the one committed with Grails 8 cannot start Grails 9.
2. Run `grails project migrate` in the application (it writes or moves the record under `.grails`), and then `grails project health --json` (or `./grailsw project health --json` once the wrapper is that of Grails 9). Resolve every finding of level `error` and `warning`: its `id` names what it is, `fix` says what to do, and `upgradeNote` links the note of the upgrade guide. Run it again after each of the steps below, until `grails project health --strict` exits 0. It reads the files of the project only; the steps below cover what it cannot see.
3. Run `./gradlew clean`, then build without tests (`./gradlew compileGroovy compileTestGroovy`) and fix compilation errors module by module. The clean build matters: classes Grails 8 compiled can keep generated `id` and `version` fields on a plain superclass.
4. Apply the Spring Boot 4.2 and Spring Framework 7.1 changes, following [Spring Boot 4.2 and Spring Framework 7.1](#spring-boot-42-and-spring-framework-71): run with the Spring Boot properties migrator to find renamed properties, and check forwarded headers, redirects, tests and metrics. For an application using the GraphQL plugin, follow [graphql-java 26 for GraphQL Applications](#graphql-java-26-for-graphql-applications).
5. Replace GPars with `groovy.concurrent`, following the sections below.
6. Port RxGORM and RxJava code, and replace the deprecated RxJava 1 and 2 async and events modules with their RxJava 3 counterparts.
7. Decide on XML: declare `grails-xml` explicitly if the application serves or reads XML, or exclude it if the application is JSON-only.
8. Run the test suite and fix the web unit tests, following [Web Unit Tests](#web-unit-tests).
9. Work through the opt-in settings one at a time: turn each on, check the responses and tests it affects, and set it explicitly in `application.yml` either way. Change the three JSON settings together.
10. Run the full test suite, then start the application and read its log for `[DEPRECATED]` and `deprecated` warnings from Grails, and for the warnings that name types `respond` still writes with the legacy converter.
11. Update every use of the command line in scripts, CI jobs, container files and documentation, and give an application that the Grails shell created its record, following [The Command Line of Grails 9: Where to Start](#the-command-line-of-grails-9-where-to-start).
12. Review the published upgrade guide section by section, confirming every item either does not apply or has been handled.

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

## A GSP Error Names the Line of the Page It Is On

The line a GSP error names was wrong for many pages and is now the line of the page: in the message of `GroovyPagesException` and `GrailsTagException` and in their `getLineNumber()`.

- An error on the last line of a page that does not end with a line break was reported on line 1.
- What follows a `<%-- --%>` or `%{-- --}%` comment, also one of a single line, could be reported on an earlier line.
- After an expression, scriptlet or tag attribute of several lines, compile errors and mapped stack trace lines were too late; in a page with more than 1,000 generated lines they could be on another line, and near the end of the page on line 1.
- An expression that is never closed is reported on the line it starts on, not on the line the text before it starts on.
- A line ends with a line feed; a carriage return by itself ends no line of a page.
- What is found only at the end of a page, such as a tag that is never closed, is reported on the last line of the page.
- What a page renders is not changed, but for an escaped expression `\${` about 64,000 characters after the last expression, scriptlet or tag, which was written with its backslash and is now written as `${`. Precompiled pages carry the lines, so they differ from those of Grails 8 and are compiled again by the build.
- No action for an application. A test that asserts the earlier line of such an error asserts the line of the page instead. No setting restores the earlier numbers.

## An Application Has No Profile at Run Time

Grails 9 does not read `grails.profile`. Until Grails 9 its value, or `web` when the key was absent, was added to the active Spring profiles while the application started, so a running application reported a Spring profile named after the profile it was created from (`web`, `rest-api`, `plugin`, `web-plugin`, `rest-api-plugin`). Now the only active Spring profiles are the ones asked for (`spring.profiles.active`, `spring.profiles.include`) and the name of the Grails environment. The key is ignored whether it is there or not, and nothing fails.

The profile was added late, after configuration files were read and bean conditions evaluated. In Grails 8 it did not select `application-web.yml` or a `spring.config.activate.on-profile: web` document and did not register `@Profile('web')` beans; leave those as they are, they behave as before.

- Search the application and its own plugins for `acceptsProfiles`, `activeProfiles` and `getActiveProfiles` used with one of those names in code that runs after startup (services, controllers, `BootStrap`, tag libraries, lazily created beans). Such a check was `true` and is now `false`; a negated one is now `true`. An application without the key reported `web`.
- For each match, either remove the check, which keeps what the application did, or ask for the profile in the main document of `application.yml`:

```yaml
spring:
    profiles:
        include: web
```

  A profile asked for this way is a real one: it also registers `@Profile('web')` beans and reads `application-web.yml`, which the profile the framework added did not. Check that the application has none it does not want before adding it.
- Delete `def profiles = [...]` from a plugin class of the project. Grails has not read the property since 7.1.0 and a plugin is enabled whatever it declares. A plugin that should contribute beans only for a Spring profile asks `applicationContext.environment.acceptsProfiles(Profiles.of('name'))` in `doWithSpring`; one that needs a web application uses a condition such as `@ConditionalOnWebApplication` on its auto-configuration.
- Delete `grails.profile` from `application.yml` when nothing of the project reads it, and remove a "Profile" row from a welcome page that shows it. Replace `grails.config.Settings.PROFILE`, which is removed, with the literal `'grails.profile'` in code that still reads the key. `GrailsPlugin.PROFILES` is deprecated for removal.
- Do not add `grails.profile` or a `profile` dependency to make something work: the key has no effect at run time, and the dependency fails the build (see "The Grails Shell and the Profiles Are Not Published").

## The `grails` Command and the Grails Wrapper

Grails 9 has one command line. The `grails` command no longer chooses between the Grails shell and Grails Forge, and nothing starts the Grails shell. The class it starts moved from `org.apache.grails.cli.DelegatingShellApplication` to `org.apache.grails.forge.cli.GrailsCli` (artifact `grails-forge-cli`), which the `Main-Class` of the `grails-cli` jars names.

The Grails wrapper committed with Grails 7 or 8 (`grailsw`, `grailsw.bat`, `grails-wrapper.jar` at the root of the application) has the old class name compiled into `grails-wrapper.jar`. Grails 9 keeps that class as the one every wrapper starts: once `grailsVersion` names a Grails 9 version, the old wrapper downloads the Grails 9 command line, which replaces the three wrapper files with those of Grails 9, prints one line that names them, and then runs the command that was typed. `update-wrapper` is a command of Grails 9 (`./grailsw update-wrapper`, `grails update-wrapper`) and does exactly this replacement.

- Check whether the application has a Grails wrapper: `grailsw`, `grailsw.bat` and `grails-wrapper.jar` at its root. Without them there is nothing to update.
- Set `grailsVersion` in `gradle.properties` to the Grails 9 version first. In a project that still names Grails 7 or 8 nothing is replaced.
- Run the update in the root of the application, check it, and commit the three files with the version change:

```bash
./grailsw update-wrapper   # ends with: The Grails wrapper of the project in ... was that of Grails 8.x and is now that of Grails 9.x: grailsw, grailsw.bat, grails-wrapper.jar were replaced. Commit them.
./grailsw --version        # prints "Grails Version:" with the grailsVersion of gradle.properties, and no line about the wrapper
git add gradle.properties grailsw grailsw.bat grails-wrapper.jar
```

- `./grailsw update-wrapper` and `./grailsw` without a command end after the replacement (exit code 0) and do not start the interactive console; any other command is run after it. Do not treat the one line about the wrapper as an error.
- Read the standard error and the exit code. A file that could not be replaced is named there with the reason: a symbolic link (not followed), a read-only file, a directory that cannot be written to. `./grailsw update-wrapper` then exits with 1. Replace such a file by hand: extract `apache-grails-wrapper-VERSION-bin.zip` from the release page (https://github.com/apache/grails-core/releases), or create an application elsewhere with the Grails 9 `grails` command (`grails create-app scratch`), copy `grailsw`, `grailsw.bat` and `grails-wrapper.jar` over the ones in the application and run `chmod +x grailsw`. Do not edit `grails-wrapper.jar` and do not copy it from a Grails 7 or 8 application.
- On Windows `grails-wrapper.jar` and `grailsw.bat` are in use while the wrapper runs. The command writes `grails-wrapper.jar.new` and `grailsw.bat.new` beside them, puts a short stand-in `grailsw.bat` in place, says that the wrapper replaces both when it is next run, and exits with 0. Run `grailsw --version` once more: that run moves both files into place by itself. Then commit the three files. Only if the command exits with 1 and names a step, run it (`cmd /c move /y grailsw.bat.new grailsw.bat` works in cmd and PowerShell). Do not commit the `.new` files.
- With an installed Grails 9, `grails update-wrapper` in the application writes the same three files without the download.
- `update-wrapper` takes no arguments in Grails 9. `./grailsw update-wrapper COMMAND`, which ran the command after the download in Grails 7 and 8, is refused with exit code 2 once the wrapper is that of Grails 9: run the two separately.
- Every Grails wrapper starts `org.apache.grails.cli.DelegatingShellApplication` of the `grails-cli` jar it downloads, which starts the `Main-Class` of the jar. The Grails 9 wrapper therefore also starts Grails 7 and 8, never replaces its own files except through `update-wrapper`, and does not need replacing when the command line moves again. Later version changes use the same step: change `grailsVersion`, run `./grailsw update-wrapper`, commit.
- Remove `-t forge`, `--type forge`, `-t shell` and `--type shell` from scripts, CI jobs and run configurations that call `grails` or `grailsw`. As the first argument the option is refused with exit code 2. A command given with `-t forge` is the same command without it; look a Grails shell command up in `grails help` and in the section "Commands of Earlier Versions" of the command line chapter of the guide.
- Replace calls of `grails-forge-cli` with `grails`, with the same arguments. `grails-shell-cli` and `grails-forge-cli` are not in the distribution or an SDKMAN installation any more.
- Remove `GRAILS_PREFERRED_SHELL` from environments; it is not read.
- A tool that loads `org.apache.grails.cli.DelegatingShellApplication` from a `grails-cli` jar reads the jar's `Main-Class` (Grails 7, 8 and 9) or loads `org.apache.grails.forge.cli.GrailsCli` (`main(String[])` exits with the exit code, `execute(String...)` returns it). In Grails 9 `DelegatingShellApplication` is the class the wrappers start: started without the system property `grails.wrapper.version`, which only the Grails 9 wrapper sets, it has the command line replace the wrapper files of the project before it runs the command. The artifact `org.apache.grails:grails-cli` no longer brings `grails-shell-cli`.

## `grails run <file>.groovy` Outside a Project Is Removed

Outside a project, `grails run app.groovy` compiled a loose Groovy script and started it as a Spring Boot application. Grails 9 removes it with no replacement, and with it `org.grails.datastore.gorm.boot.compiler.GormCompilerAutoConfiguration` of `grails-data-hibernate5-spring-boot` and `grails-data-hibernate7-spring-boot`, which added GORM to such a script that had an `@Entity` class, and the `META-INF/services/org.grails.cli.compiler.CompilerAutoConfiguration` file that registered it.

- Scripts inside a project are unaffected: `run-script` and the script commands run through `runScriptCommand` work as before. `HibernateGormAutoConfiguration` is unchanged.
- A script that was started with `grails run` becomes a project: create an application with the features it needs and move the code into it, or run it in an existing application with `run-script`. `run` is another name of `run-app` again, so `grails run app.groovy` is refused with exit code 2 and the `grails run-script app.groovy` to type instead.
- Remove code that names `GormCompilerAutoConfiguration`. Nothing takes its place.

## The Grails Shell and the Profiles Are Not Published

Grails 9 publishes no `org.apache.grails:grails-shell-cli` and no `org.apache.grails.profiles:base`, `plugin`, `profile`, `rest-api`, `rest-api-plugin`, `web` or `web-plugin`. The Grails BOMs manage no version for them or for `org.springframework.boot:spring-boot-cli`, and the Gradle plugins `org.apache.grails.gradle.grails-profile` and `org.apache.grails.gradle.grails-publish-profile` are removed. The `profile` configuration of the Grails Gradle plugins only refuses what is declared in it: a build with a `profile` dependency fails while it is configured, at the line that declares it, with

```
Grails 9 has no profiles: remove the 'profile' dependency 'org.apache.grails.profiles:web' from the build of root project 'myapp'. Nothing reads it, and the profiles are not published for Grails 9. See "The Grails Shell and the Profiles Are Not Published, and a Build Cannot Declare a Profile" in the Grails 9 upgrade notes: https://grails.apache.org/docs/latest/guide/upgrading.html
```

- Remove every `profile ...` line from the `dependencies` block of each `build.gradle`, and every `dependencies.add('profile', ...)` of the project's build logic. Nothing replaces it.
- Remove dependencies on `org.apache.grails:grails-shell-cli` and `org.apache.grails.profiles:*` from every configuration.
- Give a dependency on `org.springframework.boot:spring-boot-cli` an explicit version, or remove it when only the shell needed it.
- Remove the plugin ids `org.apache.grails.gradle.grails-profile` and `org.apache.grails.gradle.grails-publish-profile`; there is nothing to apply in their place. A profile of the project's own becomes a preset (a preset-only project with the `grails-plugin` Gradle plugin and a `kind: preset` descriptor in `src/main/forge`: `applicationType`, `options`, `features`, dependencies, configuration, files), and what only some applications need a plugin with a feature pack. Skeleton files that replace Grails-generated files, multi-project skeletons, profile inheritance and profile commands are converted by hand (guide: Making Applications from a Preset).
- Remove references to `BuildSettings.PROFILE` and `BuildSettings.PROFILE_REPOSITORIES`, which are removed. A subclass of `GrailsGradlePlugin`, `GrailsWebGradlePlugin` or `GrailsPluginGradlePlugin` no longer passes a `ToolingModelBuilderRegistry` to the constructor.

## Spring Security REST: Logout of an Unknown Token Answers 404

With the Redis and the Grails cache token storage of the Spring Security REST plugin, the logout endpoint (`/api/logout`) answered 200 for a token the storage did not have. In Grails 9 `RedisTokenStorageService.removeToken` and `GrailsCacheTokenStorageService.removeToken` throw `TokenNotFoundException` for such a token, as the GORM and memcached storage do, and the endpoint answers 404. The memcached storage treats a token that memcached does not accept as a key (longer than 250 bytes, or with a space or a line break) as not found: 401 on validation and 404 on logout, where it answered 500.

- Search clients and tests for calls of the logout endpoint that expect 200 for a token that is expired, was logged out before or was never issued, and let them accept 404.
- Wrap calls of `removeToken` on the two services in code of the project's own with a handler for `TokenNotFoundException` where the token may be gone.

## Scaffolding: Generated Service Specs Belong to the Integration Tests

In Grails 9 `generate-controller` and `generate-all` write `<Name>ServiceSpec.groovy`, an `@Integration` spec, to `src/integration-test/groovy`. The application command of earlier versions (`./gradlew runCommand -Pargs="generate-controller ..."`) wrote it to `src/test/groovy`. `create-scaffold-controller`, `create-scaffold-service` and `generate-scaffold-all` also take the full name of a domain class the project has no source of.

- Search `src/test/groovy` for generated `*ServiceSpec.groovy` files annotated with `@Integration` and move each to the same package under `src/integration-test/groovy`.
- The scaffolding plugin has no scripts in Grails 9. Replace `./gradlew runScriptCommand --arg=generate-all ...` (and the other scaffolding names) with `grails generate-all ...` or `./gradlew runCommand -Pargs="generate-all ..."`, and remove calls of `generateController(...)` or `generateViews(...)` from scripts of the project.

## The Command Line of Grails 9: Where to Start

Grails 8 had the Grails shell (`grails-shell-cli`, with profiles) beside Grails Forge. Grails 9 has one command line, `grails`, which is not the shell. The sections from here to [After the Upgrade](#after-the-upgrade) say what differs for commands that people type and that scripts, CI jobs, Dockerfiles, Makefiles, READMEs and IDE run configurations contain. Work through them in this order:

1. Collect every use of the command line in the repository: search all files, not only sources, for `grails ` and `grailsw ` (also `grails.bat`, `grailsw.bat`), for example with `grep -rnE '(^|[^a-zA-Z-])(\./)?grailsw?(\.bat)? ' --include='*.sh' --include='*.yml' --include='*.yaml' --include='*.md' --include='Makefile' --include='Dockerfile*' --include='Jenkinsfile' --include='*.gradle' .`.
2. For each match, apply [Grails 8 to Grails 9 Command Mapping](#grails-8-to-grails-9-command-mapping), then [Environment, Short Forms, Options and Exit Codes](#environment-short-forms-options-and-exit-codes), then the section of the kind of command it is.
3. Check the project itself: [The Gradle Wrapper Is What the Commands Run](#the-gradle-wrapper-is-what-the-commands-run) and [The Record of the Project for the `create-*` Commands](#the-record-of-the-project-for-the-create--commands). `grails project health --json` reports what the files of the project show of both, among other things.

Run `grails help` in the project to see the commands it has, and `grails help <command>` for one of them. Do not guess a command from its Grails 8 name.

## Grails 8 to Grails 9 Command Mapping

Use these tables to translate any `grails`, `grailsw`, `grails-forge-cli` or `grails-shell-cli` command line found in the repository. They are the compact form of note 55 of the upgrade guide (`https://grails.apache.org/docs/<version>/guide/upgrading.html#cliMappingGrails8`). A name that is no command ends with exit code 2 and runs nothing; for a command of the shell one line says what there is in its place.

**Unchanged names** (still write them as [How a Command Line Is Written](#how-a-command-line-is-written) says): `create-app`, `create-plugin`, `create-web-plugin`, `create-controller`, `create-service`, `create-domain-class`, `create-taglib`, `create-interceptor`, `create-unit-test`, `create-integration-test`, `create-command`, `create-domain-resource`, `create-restful-controller`, `create-functional-test`, `add-property`, `compile`, `clean`, `assemble`, `war`, `package`, `test-app`, `run-app`, `stop-app`, `console`, `shell`, `run-command`, `run-script`, `dependency-report`, `stats`, `bug-report`, `package-plugin`, `publish-plugin`, `list-plugins`, `plugin-info`, `gradle`, `help`, and `exit` and `quit` in interactive mode.

Where an unchanged name is a command:

- `create-domain-resource`, `create-restful-controller`: REST API applications and REST API plugins only.
- `create-functional-test`: REST API applications and plugins (a test with the JDK HTTP client), and web applications and web plugins whose record has `geb-with-testcontainers` (writes a `ContainerGebSpec`) or `geb-with-local-browsers` (writes a `GebSpec`). It is no script of the Geb plugin any more.
- `create-integration-test`: not in a plain plugin.
- `create-job`: only where the record of the project has the `grails-quartz` feature. It is no script of the Quartz plugin any more.
- `package-plugin`, `publish-plugin`: only where `src/main/groovy` has a `*GrailsPlugin.groovy`.
- `list-plugins`, `plugin-info`: also outside a project.

### Commands of the Grails Shell and Its Profiles That Changed

| Grails 8 | Grails 9 |
|----------|----------|
| `grails test ...` (other name of `test-app`) | `grails test-app ...` |
| `grails test run-app` (`test` as the environment) | `grails run-app -Dgrails.env=test` |
| `grails run`, `grails stop` (other names of `run-app`, `stop-app`) | unchanged: still other names; nothing is short for them (`r` is no command, `st` is `stats`) |
| `grails sh` | still runs `shell`, as its short form; write `grails shell` |
| `!program`, `!` in interactive mode | kept: `!program` runs the rest of the line in the user's shell (`$SHELL`, else `/bin/sh`; `cmd.exe` on Windows) with the terminal, and shows a non-zero exit code; `!` alone runs the last command again |
| `grails open <file>` | removed: `open`, `xdg-open` or `start` of the operating system |
| `grails install` (plugin) | `./gradlew publishToMavenLocal` (`grails install` runs nothing and exits 1) |
| `create-app NAME --profile web` | `grails create-app NAME` |
| `create-app NAME --profile rest-api` | `grails create-restapi NAME` |
| `create-app NAME --profile plugin` / `web-plugin` / `rest-api-plugin` | `grails create-plugin NAME` / `create-web-plugin NAME` / `create-rest-api-plugin NAME` |
| `create-app --profile <own profile>` | `grails create-<type> NAME --preset <coordinates>`: the profile converted to a preset; a plugin with a feature pack for what only some applications need (`--features group:artifact:version`) |
| `create-app --inplace` (no name: named after the current directory) | unchanged, also for the other `create-` commands; refused (exit 2) in a directory whose name has a dot, which the shell split into package and name, or a character a class name cannot have, such as a space: give the name, `grails create-app --inplace com.example.shop` |
| `create-profile`, `create-feature`, `create-template`, `create-creator-command`, `create-generator-command`, `create-gradle-command` | removed with the profiles; a feature pack is written by hand |
| `grails create-script NAME` | `grails create-command NAME`: `create-script` is removed (exit code 2) and script commands are deprecated; the scripts of `src/main/scripts` still run, and a new one is written by hand |
| `list-profiles` | removed: `grails help` lists the `create-` commands |
| `profile-info` | removed: `grails create-app --list-features` (and the other `create-` commands) |
| `generate-all`, `generate-controller`, `generate-views` of the `rest-api` profile (JSON views) | no replacement for JSON views; the names are the scaffolding plugin's commands, for GSP views |
| `grails install-templates` (scaffolding templates) | `grails run-command install-templates`; `grails install-templates` now copies the templates of the `create-*` commands |
| `grails run <file>.groovy` outside a project | removed, no replacement; refused with exit code 2 and `grails run-script <file>.groovy`, which runs it in an application |
| `test-app` (ran `test` and `integrationTest`) | runs every `Test` task; `grails test-app --unit --integration` for the two |
| `shell` (only said to run `gradle shell -q`) | runs the Groovy shell of the application |
| `stop-app` | stops only what `grails run-app` of Grails 9 started: start with `run-app --detach` |
| `run-app <gradle arguments>` | `grails run-app -- <gradle arguments>` |

### The Grails Forge Command Line of Grails 8

| Grails 8 | Grails 9 |
|----------|----------|
| `grails-forge-cli CMD`, `grails -t forge CMD`, `grails --type forge CMD` | `grails CMD` (`-t`/`--type` are refused with exit code 2) |
| `grails -t shell CMD`, `grails-shell-cli CMD` | `grails CMD`, after translating `CMD` with the tables above |
| `GRAILS_PREFERRED_SHELL` | not read: remove it |
| `create-app`, `create-webapp`, `create-restapi`, `create-plugin`, `create-web-plugin` | unchanged; new: `create-rest-api-plugin` |
| `-f`/`--features`, `--list-features`, `-i`/`--inplace`, `--jdk`/`--java-version`, `-d`/`--data`/`-g`/`--gorm`, `-s`/`--servlet`, `-r`/`--reloading` | unchanged; new: `--exclude-features`, `--subproject`, `--features group:artifact:version`, and `--inplace` without a name, which was refused, names the application after the current directory |
| `--test`, `--build`, `--lang` | never were options; do not add them |
| `-h`, `-V`/`--version`, `-v`/`--verbose`, `-x`/`--stacktrace` | unchanged; new: `--plain-output`, `--non-interactive` |
| `create-controller`, `create-service`, `create-domain-class`, `create-taglib`, `create-interceptor`, `create-job`, `add-property` (`-f`/`--force`) | unchanged names; an existing file now fails the command (exit 1) without `--force` |
| `grails-forge-cli.yml` / `grails-cli.yml` | still read when there is no `.grails`; new applications have `.grails/project.yml` and `.grails/forge.lock.yml` |
| `update-cli-config` (named in a warning, never a command) | `grails project migrate` |
| no command: interactive console anywhere | interactive console in a project, help elsewhere |

### Commands and Scripts of Plugins

`NAME` below is run in one of three ways, called "the three ways": `grails NAME ARGS` (as in Grails 8; in a project the build has not compiled yet, the first such command has the build list the commands of the application and its plugins, which compiles the project, and then runs), `grails run-command NAME ARGS` (the same command; the form for an environment before `NAME`), and `./gradlew runCommand -Pargs="NAME ARGS"` or `./gradlew runCommand --arg=NAME --arg=ARG` (what the two others run). Their environment is `development`; for another one write `grails run-command -Dgrails.env=production NAME` (the option before `NAME`) or `./gradlew -Dgrails.env=production runCommand --arg=NAME`. `grails prod dbm-update` becomes `grails run-command -Dgrails.env=production dbm-update`.

| Grails 8: `grails NAME` | Grails 9 |
|-------------------------|----------|
| `dbm-*` (31 commands, `dbm-update`, `dbm-gorm-diff`, `dbm-status` and the rest; `dbm-create-changelog` and `dbm-changelog-to-groovy` were scripts) | the three ways; all are commands |
| `url-mappings-report`, `config-report`, `schema-export`, `generate-open-api` | the three ways |
| `generate-all`, `generate-controller`, `generate-views`, `generate-async-controller`, `generate-service`, `generate-scaffold-all`, `create-scaffold-controller`, `create-scaffold-service` (scaffolding) | the three ways; the scripts are removed |
| `install-templates` (scaffolding script) | `grails run-command install-templates` or `runCommand`, never `grails install-templates` |
| `s2-quickstart`, `s2-create-persistent-token`, `s2-create-role-hierarchy-entry` (Spring Security Core scripts) | the three ways; the scripts are removed |
| `s2-create-acl-domains` (ACL script) | the three ways |
| `init-oauth2` (OAuth2 script; through `runCommand` it was `init-o-auth2`) | the three ways, as `init-oauth2` |
| `s2ui-override`, `s2ui-create-challenge-questions` (Spring Security UI scripts) | the three ways |
| `create-job` (Quartz script) | `grails create-job`, built into the command line; needs the `grails-quartz` feature in the record |
| `create-functional-test` (Geb script) | `grails create-functional-test`, built into the command line; needs a Geb feature in the record |
| `query-tail` | not in Grails 8; in Grails 9 the three ways (earlier Grails 9 builds: `runScriptCommand --arg=query-tail`) |
| a command of the project in `grails-app/commands` | the three ways |
| a script of the project in `src/main/scripts` | `grails NAME`, or `./gradlew runScriptCommand --arg=NAME --arg=ARG`; deprecated, and no command creates a new one |
| a script of a third-party plugin (`META-INF/commands/NAME.groovy`) | `./gradlew runScriptCommand --arg=NAME --arg=ARG` |

Never run a script of the framework's plugins with `runScriptCommand` in Grails 9: none is left.

### How a Command Line Is Written

| Grails 8 | Grails 9 |
|----------|----------|
| `grails prod war`, `grails test run-app`, `grails dev run-app` | `grails war -Dgrails.env=production`, `grails run-app -Dgrails.env=test`, `grails run-app` |
| `grails -Dgrails.env=staging war` | `grails war -Dgrails.env=staging` (after the command) |
| an option in front of the command | after the command; in front only `--plain-output`, `--non-interactive`, `-x`/`--stacktrace`, `-v`/`--verbose`, `-h`, `-V` |
| `-option` or `--option` | `--option`; one dash only for `run-app -port/-host/-https/-debug-jvm`, `test-app -unit/-integration/-clean/-continuous/-debug-jvm`, `assemble -clean`, `console`/`shell -debug-jvm` |
| an unknown option is ignored | refused, exit code 2 |
| `grails -v`, `grails -version` | `grails -V` or `grails --version` (`-v` is `--verbose`) |
| Gradle arguments or tasks after a command, `--offline`, `--refresh-dependencies` | after `--`: `grails compile -- --offline`; or `grails gradle ...` |
| short forms (`cA`, `rA`, `tA`) | unchanged, but ambiguous ones are refused: `cC`, `s`, `co`; `cS` is `create-service`; write the full name in scripts |
| `grails ra`, `grails sa`, `grails cdc` (short forms in lower case) | no command in Grails 8; in Grails 9 `run-app`, `stop-app`, `create-domain-class`; `cc` fits two commands and is refused, `cs` is `create-service` |
| exit code 0 or 1 | 0 done, 2 mistake in the command line (nothing ran), 1 any other failure |
| arguments split and joined again | passed on unchanged; `@file` is expanded, `@@` is a literal `@` |
| `~/.grails/settings.groovy` | not read; proxies in `GRAILS_OPTS`/`JAVA_OPTS`, repositories in `GRAILS_REPO_URL` |
| `./grailsw CMD` | the same; the first run with `grailsVersion` on Grails 9 replaces `grailsw`, `grailsw.bat`, `grails-wrapper.jar` with those of Grails 9 (commit them) |
| `./grailsw update-wrapper` | downloads the command line again and replaces the three wrapper files; `grails update-wrapper` replaces them without the download; no arguments after it |

- `grails list-plugins [TEXT]` and `grails plugin-info NAME|group:artifact` exist in Grails 9 (they were shell commands). To find the coordinates and the dependency line of a plugin, run `grails plugin-info NAME --json` and read `plugin.dependency`, `plugin.version`, `plugin.managedBy` and `plugin.features` (a feature there is added with `grails feature add FEATURE`). Use `--offline` when there is no network: the plugins of the Grails release are still answered. Set `GRAILS_PLUGIN_INDEX=none` in CI to keep the commands from the network. Do not parse the text output.
- The `grails` command of Grails 9 runs project commands only in a project whose `gradle.properties` names Grails 9 or later; in an earlier one it runs `project migrate`, `--version` and the `create-` commands for another application, and exits 1 for the rest.

## Environment, Short Forms, Options and Exit Codes

- Replace an environment in front of the command with a system property after it: `grails prod war` becomes `grails war -Dgrails.env=production`, `grails test run-app` becomes `grails run-app -Dgrails.env=test`, `grails dev run-app` becomes `grails run-app`. `test`, `prod` and `dev` in that place are refused with exit code 2. Defaults: `development` for `run-app`, `console`, `shell`, `run-command`, `run-script`; `test` for `test-app`; `production` for `assemble`, `war`, `package`.
- `grails <application or plugin command> -Dgrails.env=X` does not select the environment: the command runs in development with the option as an argument, and a warning says so. Use `grails run-command -Dgrails.env=X <command>`. Use `grails help <command>` to describe it without running it; `grails <command> --help` runs it.
- The shell completion script is now `bin/grails_completion` (bash; zsh after `bashcompinit`), replacing `bin/grails_forge_cli_completion`. In a project, `grails feature list` shows the features it has and which `feature add` can add.
- Move every option of a command behind the name of the command. In front of it only `--plain-output`, `--non-interactive`, `-x`/`--stacktrace`, `-v`/`--verbose`, `-h` and `-V` are taken.
- Write options with two dashes and remove options a command does not have: the shell took one dash or two and ignored unknown options, and `grails` refuses both with exit code 2. Replace `grails -v` and `grails -version` with `grails --version`.
- Put a Gradle option that the command does not have behind `--`: `grails compile -- --offline`. Without the `--` it is refused.
- `--verbose` on a build command adds Gradle's `--info`; `--stacktrace` adds `--stacktrace` and `-Dgrails.full.stacktrace=true`; Gradle's debug log is `-- --debug`.
- Write out short forms in scripts. A short form that fits two commands is refused with exit code 2 where the shell ran the first: `cC` (`create-controller`, `create-command`), `s` (`stats`, `shell`), `co` (`compile`, `console`). `cS`, which the shell may have run as `create-script`, is `create-service`. The refusal names each command with the shortest form that fits it alone.
- Grails 9 also takes short forms in lower case: the first letters of the words (`ra` is `run-app`, `cdc` is `create-domain-class`) or the start of each word (`ccon` is `create-controller`). A form with an upper-case letter or a hyphen is read as the shell read it. `test` is never a short form: it is refused with the name to type. `run` and `stop` are still other names of `run-app` and `stop-app`, and nothing is short for them (`r` and `ru` are no command, `st` is `stats`). A command of the application or a plugin whose name is a short form of a `grails` command (such as `ra`) is run with `grails run-command NAME`.
- Exit codes: 0 done, 2 mistake in the command line (nothing ran), 1 any other failure. Make scripts check them.
- `gradle`, `run-command`, `run-script` and test filters pass each argument on unchanged. Remove quoting or escaping that worked around the shell splitting and joining arguments. A leading `@file` argument is expanded from the file; write `@@` for a literal `@`.
- In CI use `grails --plain-output --non-interactive <command>`.

## The Gradle Wrapper Is What the Commands Run

The shell ran builds through the Gradle Tooling API with a Gradle it chose. `grails` runs `./gradlew` of the project (in a multi-project build, the wrapper of the root) and no other Gradle.

- Check that `gradlew`, `gradlew.bat` and `gradle/wrapper/` are committed. Without them `compile`, `test-app`, `run-app` and the other build commands run nothing and exit 1.
- A command line that runs one command (`grails compile`, every CI job) starts `./gradlew` as a process: Gradle's own output, and on failure the line `Gradle did not succeed: it ended with exit code N`.
- Only the interactive console (`grails` without a command) runs its builds through the Tooling API, with the Gradle the wrapper names: plain text output, no input for the build, and `Gradle did not succeed: the build failed` on failure. `gradle`, `shell` and `run-app` start `./gradlew` there too.
- `GRAILS_GRADLE_RUNNER` overrides this: `process` starts `./gradlew` everywhere (use it when a build misbehaves in the console, and say so in the bug report), `tooling-api` uses the Tooling API for one-shot build commands as well, `auto` or unset is the rule above. To see which way `grails` ran Gradle for a command, and why, set `GRAILS_CLI_DIAGNOSTICS=true`; the `| Diagnostic` lines go to stderr.
- `grails test-app` now runs every `Test` task of the project, not only `test` and `integrationTest`, and `grails test-app <test>` looks for the test in every phase it runs (see the `test-app` entry below). Check the build for added `Test` tasks before leaving a job as it is.
- `grails run-app` takes no Gradle arguments after its name: use `grails run-app -- <gradle arguments>`. Options: `--port`, `--host`, `--https`, `--debug-jvm`, `--query-tail`, `--detach`, `--foreground`, `--ready-timeout`.
- `grails stop-app` only stops what `grails run-app` of Grails 9 started (record in `.grails/run`); it does not read `build/run-app.pid` and does not stop `./gradlew bootRun`. In jobs start the application with `grails run-app --detach` and stop it with `grails stop-app`.
- `grails shell` now runs the Groovy shell of the application; `grails assemble --clean` now cleans first.

## The Record of the Project for the `create-*` Commands

`grails` reads which code generation commands a project has, and its default package, from `.grails/project.yml` (applications Grails 9 generated) or `grails-forge-cli.yml`/`grails-cli.yml` (applications Grails Forge of Grails 7 or 8 generated). An application created with the Grails shell has neither: `create-controller` and the other `create-*` commands, `add-property` and `install-templates` are missing from `grails help` and exit 1 with `... is a command of a Grails project whose record is read` and name `grails project migrate`.

- Check for `.grails/project.yml`, `grails-forge-cli.yml` or `grails-cli.yml` at the root of each application. When one is there, leave it.
- When none is there, run `grails project migrate` in the directory of the application, before or after `grailsVersion` in `gradle.properties` names Grails 9. It and `grails project health` are the only project commands the Grails 9 `grails` runs in a project that still names Grails 7 or 8: every other one (`compile`, `test-app`, `run-app`, `gradle`, `create-*`, `feature add`, application commands) exits 1 with `This project is on Grails <version> ...` until the project is on Grails 9; use the project's own `./grailsw` for the old version meanwhile. It reads the project as text, writes only `.grails/` (`project.yml`, `forge.lock.yml`, `baseline/index.yml`), and prints the type, name, package, options and features with where each was found and which were assumed. Commit `.grails`.
- Read the output. Correct a wrong value by running it again with `--force` and the option: the name with its package (`grails project migrate com.example.myapp --force`), `--type web|rest-api|plugin|web-plugin|rest-api-plugin`, `--features a,b` for features it did not find (`grails-quartz` brings `create-job`), `--exclude-features x` for features the application does not have, `--jdk`, `--servlet`, `--data`, `--reloading`. `--force` works the record out anew, so repeat the options given before. `--dry-run` prints the record without writing. The servlet comes from the starter `build.gradle` declares (`spring-boot-starter-tomcat`, `-jetty`, `-undertow`); `servlet … (assumed)` means none was declared.
- It takes the type from `grails.profile` in `application.yml` or the `profile "org.apache.grails.profiles:<name>"` line of `build.gradle` when they are still there, and otherwise from the Grails Gradle plugins (`grails-web` with `grails-gsp`: web; `grails-web` alone: rest-api; `grails-plugin` with `grails-gsp`: web-plugin; `grails-plugin` alone: plugin, reported as assumed — pass `--type rest-api-plugin` for a REST API plugin). When it exits 1 saying the type or the package could not be worked out, pass `--type` or the name with its package.
- It reads `build.gradle`, or `build.gradle.kts` and `settings.gradle.kts` in a project without one, dependencies in string and in map notation, and nothing from comments. Read the lines that say what it did not read (`apply from` files, a version catalog, what is declared for other projects, the build above a project without `settings.gradle`) and add the features they hide with `--features`.
- It tells the two Geb features apart by the build: `geb-with-local-browsers` when the Selenium driver dependencies are declared, `geb-with-testcontainers` when `testFixtures('org.apache.grails:grails-geb')` is declared without them. Check the feature it reports against the base class of the Geb tests (`ContainerGebSpec` or `GebSpec`) and correct it with `--features` if they disagree.
- Exit code 2 is a mistake in the options (a type or feature that is none); exit code 1 with "Nothing was written" is a refusal: read the message.
- Keep `grails.codegen.defaultPackage` in `application.yml`.
- Do not write `.grails/project.yml` or `grails-forge-cli.yml` by hand. The record `project migrate` writes has `generationState: untracked`: `feature add` and `project regenerate` refuse such an application and change nothing. Add a feature's dependencies and configuration by hand, then run `grails project migrate --force` again.
- In an application Forge of Grails 7 or 8 generated, `grails-forge-cli.yml` keeps working and nothing has to be done; `grails project migrate` may be run there too, after which the commands go by `.grails` and warn when the file disagrees.
- In an application Grails 9 generated, keep `.grails/project.yml`, `.grails/forge.lock.yml` and `.grails/baseline` committed; `.grails/run`, `.grails/cache`, `.grails/transactions` and `.grails/history` stay ignored.
- Never merge or hand-edit files under `.grails/`; after a merge conflict there, restore one side's record (`git restore --source=HEAD --staged --worktree -- .grails`) and `feature add` the other side's `requestedFeatures`. After a manual `grailsVersion` bump to a later 9.x release, run `./grailsw update-wrapper` and then `./grailsw project migrate` and commit `.grails`: it moves the record (`project.yml`, `forge.lock.yml`, `baseline/`) to the new version and changes no other file; `feature add` and `project regenerate` refuse the record of an earlier version until then. Give a feature pack or preset the application has again with `--feature-pack-dir`.
- A `create-*` command now exits 1 and writes nothing when one of its files exists; add `--force` where a script relies on overwriting, or test for the file first. The shell skipped the file and exited 0.
- A name that is no class name (a path, a name with a space, a package with a hyphen, `Book.groovy`) is refused with exit code 2.
- `add-property` parses the class and only adds lines. It exits 1 and writes nothing for a class written on one line, a class whose last property is on the line of its closing brace, and, when a constraint is asked for, `constraints` that are not a block in the class (`static constraints = Shared.rules`): edit such a class by hand.
- Search `src/main/templates` for templates of the project. Token templates (`@artifact.name@`, `@artifact.package@`) work unchanged. Rewrite `src/main/templates/Job.groovy` and `src/main/templates/artifacts/Script.groovy` when they contain `<% %>` or expressions such as `${className.toLowerCase()}`: only plain names in braces (`${className}`, `${packageName}`, `${propertyName}`) are replaced, and anything else fails the command.

## Commands of Plugins and Script Commands

`grails` loads no class of the project. Application and plugin commands (`dbm-update`, `url-mappings-report`, `generate-all`, `s2-quickstart`, commands in `grails-app/commands`) are commands of `grails` by their names, as in Grails 8: the build lists them (Gradle task `grailsCommandCatalog`, run after `classes` by every build that compiles the project), and a name `grails` does not know yet in a project whose list is missing or out of date has the build list them once and is then run. The plugins of the framework have no script commands any more; script commands of other plugins are never listed.

- Keep `grails <application or plugin command> <args>` in scripts and CI: it works in a fresh checkout too (the first one compiles the project and lists the commands). Do not add `grails gradle grailsCommandCatalog` in front of it.
- A CI job that wants a mistyped name to fail at once without a build sets `GRAILS_LIST_COMMANDS=false`, and runs the commands after a build of the project, or as `grails run-command <command> <args>` or `./gradlew runCommand --arg=<command> --arg=<arg>`, which do not depend on the list.
- `grails help` lists the commands once a build has compiled the project; before that it says they are not listed yet, and starts no build. The list is `.grails/cache/command-catalog.json` in the project directory whatever the build directory is; `clean` does not remove it.
- A plugin command runs in the `development` environment. Replace `grails prod <command>` with `grails run-command -Dgrails.env=production <command>` (the option before the command name) or `./gradlew -Dgrails.env=production runCommand --arg=<command>`.
- The Spring Security scripts (`s2-quickstart`, `s2-create-persistent-token`, `s2-create-role-hierarchy-entry`, `s2-create-acl-domains`, `s2ui-override`, `s2ui-create-challenge-questions`, `init-oauth2`) are application commands now: `grails <script> <args>` runs the command of that name (or `grails run-command <script> <args>`, `./gradlew runCommand -Pargs="<script> <args>"`). They do not start the application (the project only has to compile). Do not use `runScriptCommand` for them.
- The Quartz `create-job` and Geb `create-functional-test` scripts are removed: `grails create-job` and `grails create-functional-test` are commands of the command line and need the feature (`grails-quartz`, `geb-with-testcontainers` or `geb-with-local-browsers`) in the record of the project, not the plugin on a class path. `create-functional-test BookSpec` writes `BookSpec.groovy` (the script wrote `BookSpecSpec.groovy`).
- `query-tail` is new in Grails 9 and is a command of Grails Data that runs without starting the application: `grails query-tail [--once|--stream|--statements|--help]`.
- A script of a third-party plugin (`META-INF/commands/<name>.groovy` in its jar) runs with `./gradlew runScriptCommand --arg=<script> --arg=<arg>`, one `--arg` for each argument.
- A command of the project that only works on files can implement `org.apache.grails.core.cli.StandaloneCommand` beside `ApplicationCommand` or `GrailsApplicationCommand`; `runCommand` then runs it without starting the application.
- `dbm-create-changelog` and `dbm-changelog-to-groovy` are application commands now: run them with `runCommand`, not `runScriptCommand`. Replace the classes `DbmCreateChangelog`, `DbmChangelogToGroovy` and `ScriptDatabaseMigrationCommand` with `DbmCreateChangelogCommand` and `DbmChangelogToGroovyCommand`.
- Scripts of the project in `src/main/scripts` are commands by their file names and run through `runScriptCommand`. In each script:
  - remove `import org.grails.cli...` lines and `completer` lines of a `description`: a script that imports a shell class does not compile;
  - remove `gradle.<task>()` calls and run the tasks before or after the script;
  - remove calls of commands that are no scripts (`testApp()`, `runApp()`, `generateController(...)`) and run those commands separately;
  - remove uses of `profile`, `spring`, `isServerAvailable()` and `waitForStartup()`;
  - do not rely on `-Dgrails.env=NAME` after the script name to set the environment: give it to the build (`./gradlew -Dgrails.env=NAME runScriptCommand ...`);
  - in a listener of `before`, `after` or `on('<name>Start')`/`on('<name>End')`, replace `context.console`, `context.config` and `context.navigateConfig(...)` with the script's own `console`, `config` and `navigateConfig(...)`: the listener is given an `org.apache.grails.core.cli.ExecutionContext` (`commandLine`, `baseDir`).
- Prefer rewriting a script as a class in `grails-app/commands` (`grails create-command <name>`): script commands are deprecated, `help` shows no description for them, and `grails create-script` is removed (exit code 2). Replace `grails create-script NAME` with `grails create-command NAME`; where a new script is wanted all the same, write `src/main/scripts/<name>.groovy` by hand.
- Compiled commands now also write `META-INF/grails-commands/<class>.json`. Rename a task of the build named `grailsCommandCatalog` or `runScriptCommand`: the Grails Gradle plugin registers tasks of these names. To keep the descriptor files out of an archive: `tasks.named('bootJar') { exclude 'META-INF/grails-commands/**' }`.
- A tool that reads `grails-forge-cli.yml` of a newly generated application reads `.grails/project.yml` and `.grails/forge.lock.yml` instead; newly generated applications have no `grails-forge-cli.yml` and no `profile` dependency.

## Spring Security Commands That Were Scripts

- **Check the user class for unhashed passwords.** If an application ran the `s2-quickstart` script with Grails 7 or 8, open the user class. When it declares `SpringSecurityService springSecurityService` and calls `encodePassword()` in `beforeInsert`/`beforeUpdate`, and no bean hashes the password (search for `PasswordEncoderListener`), the property is never injected and passwords are stored as given. Tell the user, add a listener as `s2-quickstart` of Grails 9 generates it (`<User>PasswordEncoderListener` in `src/main/groovy`, registered in `grails-app/conf/spring/resources.groovy`), remove the property and the methods, and have the stored passwords hashed or reset. Do not do the last step silently.
- `s2-quickstart` now creates the Requestmap class and its settings for a fourth argument, and takes `--uiOnly`, also through `runCommand` (both failed there in Grails 8). `--salt` is refused: remove it. Fewer than three or more than four arguments, an invalid package or class name, or an unknown option fail before anything is written.
- The three Spring Security Core commands append their settings to `grails-app/conf/application.groovy` when the project has that file, to `application.yml` (as a further YAML document) when it has only that, and to a new `application.groovy` otherwise. Look for the settings there. Grails 8's `runCommand` form of `s2-quickstart` wrote YAML with `authority.className`, the static rules and the filter chain map under `grails.plugin.authority`, where the plugin does not read them: in such an `application.yml` move them to `grails.plugin.springsecurity.authority.className`, `grails.plugin.springsecurity.controllerAnnotations.staticRules` and `grails.plugin.springsecurity.filterChain.chainMap`.
- `s2-quickstart` fails and writes nothing (no class, no listener, no setting) when `application.groovy` or `application.yml` already has a setting the run would write, such as `userLookup.userDomainClassName` or `controllerAnnotations.staticRules`, outside the environment blocks or in the block of any environment (a `staticRules` under `environments.production` counts, whichever environment the command runs in); the message names the files, the environments and the keys. The scripts appended a second block on every run, which replaced the settings that were there; in an application created with the `grails-spring-security` feature that drops the feature's `/user/**` to `ROLE_ADMIN` rule. Such an application already has its user, role and join classes: do not run `s2-quickstart` there. Where a build or script runs it again on purpose, add `--force` (it then writes as the scripts did) and merge the old values into the appended block; do not add `--force` just to get past the error. With `--force`, the appended YAML document replaces the settings of every environment too, so merge an environment's rules into an `environments` block of the appended document.
- Replace `init-o-auth2` (the only name the compiled command had in Grails 8) with `init-oauth2`.
- `s2ui-override` and `s2ui-create-challenge-questions` fail with a message and write nothing for wrong arguments (unknown type, missing package, a question count below 1). The challenge questions class now names the user class as given (`AppUser`, not `Appuser`).

## Interactive Mode

- `grails` without a command starts interactive mode only inside a project; elsewhere, and with `--non-interactive`, it prints the help.
- Remove `open` lines from input that is piped into `grails` (`exit` and `quit` both still end it). A `!` line there is run by the shell now, with an empty input: quote `*`, `$`, `|`, `;` and `>` that the program was given as they were. Better: one `grails --non-interactive <command>` call for each command.
- `run-app` at the prompt returns once the application answers (with the `grails-startup-progress` feature, a start that fails while the progress page has the port is reported at once) and stops it when interactive mode ends; use `run-app --detach` to leave it running.
- Gradle task names after `gradle` are not completed.

## `~/.grails/settings.groovy`, Repositories and Proxies

- `~/.grails/settings.groovy` is not read. Remove `grails.profiles.repositories` and profile defaults from provisioning scripts and developer setup documents.
- A repository for the feature pack of a plugin given by coordinates is named with `GRAILS_REPO_URL` or `--feature-pack-repository`; a feature catalog with `GRAILS_FORGE_CATALOG` or `--feature-catalog`. `~/.m2/settings.xml` is not read for them.
- An application created with an external feature pack or a preset (`--feature-pack-dir`, plugin coordinates, `--preset`, or a catalog name) needs that same pack for `grails feature add`, `grails feature list` and `grails project regenerate`: the jar is fetched again by the coordinates and SHA-256 in `.grails/forge.lock.yml` (`--feature-pack-repository`, `--offline` with a `file:` repository), and a directory pack is passed again with `--feature-pack-dir` and must match the recorded `contentSha256`. Keep pack templates at `eol=lf` in `.gitattributes`. With `--json` the refusal reason is `pack-missing` or `pack-differs`.
- `GRAILS_PLUGIN_INDEX` points every command that looks plugins up at another plugin index than the Grails one: one `https` address, one `file:` address, or `none` (no index, no network: use it in CI). It has the format of `grails-plugins-index.json` of apache/grails-plugins-metadata.
  - It is what `list-plugins` and `plugin-info` read (`--plugin-index` comes first), and the feature catalog of `create-app` and the other create commands when neither `--feature-catalog` nor `GRAILS_FORGE_CATALOG` names one; the Grails index is never a catalog unless named. So `--features NAME` takes the feature pack the index records for a plugin by that name, the one `plugin-info NAME` shows (`featurePacks` in `--json`).
  - Naming it trusts it as much as the plugins: it decides which jar a name means and the SHA-256 it is held to. To use an index for listing only, also set `GRAILS_FORGE_CATALOG=none`.
  - A company catalog: `export GRAILS_REPO_URL=https://repo.acme.example/maven` and `export GRAILS_PLUGIN_INDEX=https://catalog.acme.example/grails-plugins-index.json`, then `grails plugin-info com.acme:acme-audit-grails` and `grails create-app NAME --features acme-audit`.
  - To check a plugin's entry before it is published to the Grails index, point it at a local copy: `GRAILS_PLUGIN_INDEX=file:///path/to/grails-plugins-index.json grails plugin-info NAME --json`.
  - Run a create line `plugin-info NAME` prints for a pack exactly as printed: it already names the create command for the pack's type, the plugin (`group:artifact#name` when two plugins share a name), the catalog, the repository and `--refresh` where needed. `featurePacks[].applicationType` in `--json` is the type the version was chosen for. With `GRAILS_REPO_URL=mavenLocal()`, `plugin-info group:artifact` describes a plugin installed by `publishToMavenLocal`.
  - A catalog read from `https` is kept for 24 hours in `~/.grails/forge/catalogs` (`--refresh` reads it again); a `file:` index is read every time. An index entry of a later format than this command line reads is skipped, and a faulty version leaves out that version only, not the plugin.
- Proxy settings for the `grails` JVM go into `GRAILS_OPTS` or `JAVA_OPTS` (`-Dhttps.proxyHost=... -Dhttps.proxyPort=...`); the build takes them from Gradle.
- The Grails Gradle plugin gives the options of `GRAILS_OPTS`, or of `GRAILS_FORK_OPTS` in its place, to the application, its commands and the tests. They are separated at any run of white space now (two spaces in a row used to end the application with `Could not find or load main class`); quotes are not read. Move an option meant for the `grails` command alone, such as a small `-Xmx`, to `JAVA_OPTS`: an `-Xmx` below `768m` there keeps the application and the tests from starting.

## Projects Above the Current Directory, and Multi-Project Builds

- A project found above the current directory is used only when the user who runs `grails` owns it and the directory is not world-writable; otherwise the command exits 1 naming the path. In containers, CI jobs that run as another user, and under `sudo`, either `cd` to the project directory first or set `GRAILS_SAFE_DIRECTORY` to the absolute path of the project (a `PATH`-style list; add the directory of the Gradle wrapper when it is above the project).
- On Windows only the owner is checked (no access control list is read). Files owned by the Administrators group, as everything created in an elevated prompt is, count as the user's own only in an elevated process of an administrator; from a prompt that is not elevated, `cd` to the project, run elevated, or take ownership (`takeown /r /f <dir>`).
- In a multi-project Gradle build, run project commands in the directory of the application: `cd apps/shop && grails test-app`. At the root of the build such a command lists the Grails projects and exits 1; there is no option that names the project. `grails gradle <tasks>` works at the root.
- A project command in a multi-project build fails with "The Grails command line ran Gradle for the project in ... named its tasks by the path its directories give ...": the settings give the project directory another Gradle path (`projectDir` remapping). Run the task by its real path with `grails gradle <path>:<task>`, or include the project by its directory (`include 'apps:shop'`). Do not delete `.grails/run/project-path.gradle`; it is the check, rewritten when it differs.
- A settings callback (`gradle.settingsEvaluated { ... }`) that moves the directory of the project a command runs in is refused like a direct `projectDir` assignment; run that project's tasks by path with `grails gradle :path:task`.
- To add an application to an existing build use `grails create-app <name> --subproject` and add the `include` line it prints to the settings of the build.

## The Grails Wrapper and `GRAILS_REPO_URL`

- Repository URLs with credentials: percent-encode `/ # ? @` in passwords; a remote URL without a parseable host is refused before anything is downloaded, and an address that cannot be read with certainty is printed with everything up to its last `@` hidden.
- With `GRAILS_REPO_URL` (or `-Dgrails.repo.url`) naming a directory, `grailsw` now also reads a directory a build published to with `publish` (`maven-metadata.xml`), not only a local Maven repository (`maven-metadata-local.xml`).
- When a repository named this way does not have the command line, `grailsw` prints `...The Grails CLI was not found in the overridden repository [...]` and tries the next one. In a job that tests a command line through the override, treat that line as a failure. A release that the override lacks now gets this line too, in place of `There was an error downloading the wrapper jar from [...]` and a stack trace.
- `grailsw` installs a download only when it is whole: it matches the strongest checksum file the repository has beside the jar (`.sha512`, `.sha256`, `.sha1`, `.md5`) or, without one, every entry matches its CRC, and the jar has the class the wrapper starts (`org.apache.grails.cli.DelegatingShellApplication`). The checksum is from the same repository as the jar, so it finds a damaged download and says nothing of whether the repository is to be trusted. A repository of your own must not serve a stale checksum file beside the jar.
- A repository that fails before it gives the jar (unreadable `maven-metadata.xml`, unreachable, an HTTP error) is passed over with `The Grails CLI could not be taken from the repository [...]: ...` on standard error. When every repository missed, `grailsw` exits with 1 and `Could not find the Grails CLI in any repository:` followed by one line per repository with the reason, without a stack trace. Do not match the old texts `Could not find the Grails Repo in any repository` or `There was an error downloading the wrapper jar`.
- The first repository that gives the jar decides: a download that is not whole (checksum or CRC mismatch, no start class, a checksum file that cannot be read or holds no checksum, a transfer that broke off) ends `grailsw` with exit code 1 and `The Grails CLI that the repository [...] gave is not installed: ...`. Nothing is installed and no other repository is asked, for Maven Central and the ASF repository as for `GRAILS_REPO_URL`. Rerun the command; when the message names a repository of your own, fix the jar or its checksum file there or remove it from `GRAILS_REPO_URL`. Do not expect the next repository to stand in.
- `The installed Grails command line at [...] cannot be started (...). Downloading it again.` means a damaged jar was in the wrapper home; it is replaced once. Do not delete `~/.grails/wrapper` for it unless the wrapper then ends with `Delete the file and run the command again.`
- `The Grails wrapper cannot write to [...]` (exit code 1): make the directory writable or set `GRAILS_WRAPPER_HOME` to another one. The wrapper keeps every version it downloaded; nothing removes older ones.
- Outside a project and without `PREFERRED_GRAILS_VERSION`, `grailsw` starts the latest version in its home (`~/.grails/wrapper`); it started the oldest.

## IDEs and Tools That Started the Grails Shell

- A tool that resolves `org.apache.grails:grails-shell-cli` and starts `org.grails.cli.GrailsCli` cannot run Grails 9 commands. Use `org.apache.grails:grails-cli` with its dependencies and the class its jar's `Main-Class` names (`org.apache.grails.forge.cli.GrailsCli`), with `--plain-output --non-interactive`, and read the commands of a directory from `help --json` instead of a built-in list. A tool that calls `GrailsCli.execute` in its own JVM compiles against `org.apache.grails.forge:grails-forge-cli` (`grails-cli` has the class at run time only); both need `https://repo.gradle.org/gradle/libs-releases` for `org.gradle:gradle-tooling-api`.
- Until the Grails support of an IDE handles Grails 9, run the Gradle tasks (`bootRun`, `test`, `integrationTest`, `testApp`, `runCommand`, `runScriptCommand`) from the IDE.

## `feature add` and the Order of Generated YAML

Generated YAML configuration has a fixed key order (`info`, `dataSource`, `environments`, `hibernate`, `grails`, then alphabetical; alphabetical below the top level).

- Do not reorder an existing `application.yml` to match: `grails feature add` keeps the order a file has and only inserts the new keys.
- To switch a default feature, run `grails feature add <feature>` (for example `postgres` for the default `h2`); it removes the default's dependency and configuration and updates `.grails`.
- If the feature it would replace was named in `--features` at creation or added by name earlier, the command refuses with exit code 2 and names `--replace`: run `grails feature add <feature> --replace`. It still refuses when another selected feature requires the one to be replaced (for example `grails-spring-security` with `grails-spring-security-ui`), and it never changes `--data`, `--servlet`, `--reloading` or `--jdk`.
- If lines of the default were edited (for example the H2 URL), the command reports a conflict and writes nothing: restore the generated line, add the feature, then reapply the edit.
- `grails feature add A,B` with two features of one exclusive group (for example `h2,postgres`) is refused with exit code 2 even when the project already has one of them; name only the wanted feature, adding `--replace` if the other was asked for by name. For automation, use `feature add --dry-run --json` / `project regenerate --diff --json` and branch on `outcome`/`reason`, not on the exit code.
- In an application generated with `views-markup` by Grails Forge of Grails 8 or an earlier Grails 9 build, delete `grails-app/views/object/_object.gml` when it does not compile and write `this.` in front of `g`, `request`, `response`, `messageSource` and `locale` in the markup views. The `org.apache.grails.gradle.grails-markup` Gradle plugin can be applied in Grails 9 (it failed with `Cannot invoke method get() on null object`).
- An application generated with `cache-ehcache` has `org.grails.plugins:cache-ehcache:3.0.0`, which may not resolve: Grails 9 generates `5.0.0-RC1`.

## `test-app` Test Phases and Test Names, `project show`, and REST API Plugins

- **`test-app` runs every test phase**: `grails test-app` runs the Gradle task `testApp` of the Grails 9 Grails Gradle plugin, which runs every `Test` task of the project: `test`, `integrationTest`, and any added one (`testPhases { create('functionalTest') }`, a `jvm-test-suite` suite, a task of a build plugin). Grails 8 ran only `test` and `integrationTest`. Search the build for added `Test` tasks (`testPhases`, `testing { suites`, `tasks.register(..., Test)`); if there is one, a job that runs `grails test-app` now runs it too. To keep the job to what it ran: `grails test-app --unit --integration`, or `grails test-app -- -x functionalTest`.
- **Phase switches**: `--unit` = `test`, `--integration` = `integrationTest`, any other phase = its task name without `Test`, lower case with hyphens (`--functional` = `functionalTest`, `--end-to-end` = `endToEndTest`); a `Test` task not ending in `Test` uses its own name (`--smoke`). Several switches combine. A switch that is an option of the command (`--clean`, `--continuous`, `--debug-jvm`, `--tests`, `--help`, ...) is never a phase: run such a task with `grails gradle <task>`. A build that compiles the project (or `grails gradle grailsCommandCatalog`) lists the phases for `grails help test-app`; before that an unknown switch is taken for a phase and Gradle fails with `This project has no test phase X; its test phases are: ...`.
- **`test-app` with a test name**: `grails test-app <name>` gives the name to every phase it runs; a phase without a match passes and the build fails only when no phase ran a test. In a build's own scripts the same is `./gradlew testApp --tests X` (options: `--phase NAME`, `--tests`, `--rerun-tests`, `--debug-jvm`), or for tasks named one by one `./gradlew test --tests X integrationTest --tests X -Pgrails.test.filter.lenient=true`.
- **`test-app` filter and skipped phases**: a phase left out with `-- -x TASK` or skipped by `onlyIf`/`enabled = false` runs no test and its earlier results do not count; a named test that only such a phase has fails `testApp`.
- **Needs the Grails 9 Grails Gradle plugin**: if Gradle says there is no task `testApp`, the build still has an older Grails Gradle plugin; upgrade it. A project task of its own named `testApp` must be renamed.
- **Order**: the plugin now runs `test` before `integrationTest` before other `Test` tasks (`mustRunAfter`), also for `./gradlew check`. `--clean` is `testApp --rerun-tests` and no longer `cleanTest cleanIntegrationTest`.
- **`project show`**: prints every `create-` option with its recorded value and `default` / `differs from the current default` / `not recorded`; use it after an upgrade to see which recorded values are no longer defaults. JSON: `effectiveOptions`, `addedFeatures` (format 1).
- **REST API plugins**: `create-app NAME --profile rest-api-plugin` becomes `grails create-rest-api-plugin NAME` (application type `rest-api-plugin`: a plugin built with `org.apache.grails.gradle.grails-plugin` and `grails-gson`, with `views-json` by default and no GSP). As with the profile, its `UrlMappings` is empty and it has no controller and no views of its own.

## GORM for Neo4j Uses `grails-neo4j-bom`

- **An application with GORM for Neo4j imports `grails-neo4j-bom` in place of `grails-bom`.** Search the build for `org.apache.grails:grails-data-neo4j`. Where it is declared, replace `grails-bom` with `grails-neo4j-bom` in every `platform(...)` that imports it: in `dependencies`, on the `buildscript` classpath, and in `buildSrc/build.gradle`. With `grails-bom` the runtime class path does not resolve: `grails-data-neo4j` brings Neo4j driver 4.4.13 as a strict version from `grails-neo4j-bom`, and a strict version that comes in transitively cannot replace the 6.x driver `grails-bom` manages through Spring Boot. The application compiles, but `bootRun`, `bootJar` and the tests fail with `Could not resolve org.neo4j.driver:neo4j-java-driver:4.4.13` (`grails help` and `project health` say `runtimeClasspath cannot be resolved completely`). Grails Forge of Grails 8 and earlier Grails 9 snapshots generated `grails-bom` for these applications.
  - `grails-neo4j-bom` imports `grails-bom`, so GORM for Hibernate 5 next to GORM for Neo4j keeps its versions. The Grails Gradle plugin applies the declared BOM to the other configurations; a build that relies on the BOM the plugin applies sets `grails { bom = 'grails-neo4j-bom' }`. Never declare both: the plugin refuses two Grails BOMs.
  - No Grails BOM covers GORM for Neo4j with GORM for Hibernate 7 or with `grails-micronaut`: `create-app`, `feature add` and the hosted Forge refuse those combinations (`gorm-neo4j cannot be combined with ...`). Do not move an application with GORM for Neo4j to Hibernate 7 or Micronaut.
  - `grails create-app --data neo4j` (or `--features gorm-neo4j`) and `grails feature add gorm-neo4j` write `grails-neo4j-bom`; `project migrate` does not change the build file of an application an earlier version generated with `grails-bom`, and `project regenerate --diff` names `build.gradle` until it is replaced.

# After the Upgrade

## Verification Checklist

- [ ] `grails project health --json` (or `./grailsw project health --json`) in each application reports no finding of level `error` or `warning`, and `grails project health --strict` exits 0. Resolve each finding by its `fix`; the `id` (such as `removed-dependency.profile`) is stable and `upgradeNote` names the note of the upgrade guide. A `skipped` finding is a check that could not decide, as for a build that applies another build file or names dependencies by a version catalog: check what it names by hand. The command reads the files only (the version of Grails, the Gradle and Grails wrappers, the Java versions of the build, `.sdkmanrc`, removed and deprecated dependencies, the record under `.grails`, `.gitignore` and `.gitattributes`, the command catalog, `~/.grails/settings.groovy`, `grails.profile`, `GRAILS_PREFERRED_SHELL` and the upgrade skill); the items below cover the rest
- [ ] `gradle.properties` names the Grails 9 version and the wrapper is Gradle 9.7 or later
- [ ] Java: every setting of the build names Java 21 or later (`compileJava.options.release`, `options.release`, `targetCompatibility`, `sourceCompatibility`, the toolchain); Grails 8 generated `compileJava.options.release = 17` when asked for Java 17, and `project migrate` names each setting below 21 with its line (`project health`: `java-target.release`). `JAVA_HOME` is Java 21 or later: the `grails` and `grailsw` scripts refuse an earlier Java by name
- [ ] An application with a Grails wrapper has the `grailsw`, `grailsw.bat` and `grails-wrapper.jar` of Grails 9 committed (`./grailsw update-wrapper` wrote them and now says that nothing was changed), no `.new` file is left beside them, and `./grailsw --version` prints the Grails 9 version
- [ ] No script, CI job or run configuration passes `-t` or `--type` to `grails` or `grailsw`, calls `grails-shell-cli` or `grails-forge-cli`, or sets `GRAILS_PREFERRED_SHELL`
- [ ] A tool that calls `org.apache.grails.forge.cli.GrailsCli.execute` compiles against `org.apache.grails.forge:grails-forge-cli` (`grails-cli` has it at run time only) and adds `https://repo.gradle.org/gradle/libs-releases` for `org.gradle:gradle-tooling-api`
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
- [ ] No build file declares a `profile` dependency, depends on `grails-shell-cli` or `org.apache.grails.profiles`, applies `grails-profile` or `grails-publish-profile`, or declares `spring-boot-cli` without a version
- [ ] No client or test of an application with Redis or Grails cache token storage expects 200 from the REST logout endpoint for a token that is not stored
- [ ] No script, CI job, container file or document calls a removed or renamed command: `grails test`, `grails install`, `grails open`, `open` in piped input, `create-profile` and the other profile commands, `list-profiles`, `profile-info`, `create-app --profile`, `create-script` (`create-command`), `-t forge`, `grails-forge-cli`
- [ ] No call of `grails` or `grailsw` has an environment (`test`, `prod`, `dev`) or a command's option in front of the command, and Gradle options a command does not have follow `--`
- [ ] No script uses a short form that fits two commands (`cC`, `cc`, `s`, `co`), and scripts check the exit code of `grails` (2: nothing ran)
- [ ] The Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) is committed
- [ ] Gradle wrapper: `gradle-wrapper.properties` has `distributionSha256Sum`, the one Gradle publishes for the distribution `distributionUrl` names (`project health`: `gradle-wrapper.checksum` where it is missing, `gradle-wrapper.checksum-differs` where it is not that of the Gradle Grails generates applications with); to change the Gradle version run `./gradlew wrapper --gradle-version <v> --gradle-distribution-sha256-sum <sha256>` with the checksum from https://gradle.org/release-checksums/
- [ ] `grails help` in the application lists `create-controller`: the application has `.grails/project.yml` (generated by Grails 9, or written by `grails project migrate`), or a `grails-forge-cli.yml` of Grails 7 or 8
- [ ] Version control: `.grails/.gitignore` has `/cache/`, `/history`, `/run/` and `/transactions/` (this version's `project migrate` and `create-app` write them; add them to a `.grails/.gitignore` an earlier snapshot wrote), so committing `.grails` commits only the record (`project health`: `git-files.state-ignored`)
- [ ] Scripts and CI jobs run application and plugin commands, the Spring Security commands (`s2-quickstart` and the others) and `query-tail` among them, by name (`grails NAME`), with `grails run-command` where an environment goes before the name, or with `runCommand`; `runScriptCommand` is used only for scripts of the project and of third-party plugins
- [ ] The user class of an application that ran the `s2-quickstart` script hashes its passwords through a password encoder listener, not through a `springSecurityService` property
- [ ] No call of `grails` or `grailsw` uses an option with one dash that is not in the mapping, an option the command does not have, or `-v` for the version
- [ ] `grails install-templates` is `grails run-command install-templates` where the scaffolding templates are meant
- [ ] No script of `src/main/scripts` imports `org.grails.cli`, calls `gradle.<task>()` or a command that is no script, or uses `profile`, `spring`, `isServerAvailable()` or `waitForStartup()`
- [ ] Script listeners (`before`/`after`/`on('<name>Start')`) receive an `org.apache.grails.core.cli.ExecutionContext`: they use the script's own `console`, `config` and `navigateConfig(...)` instead of `context.console`, `context.config` and `context.navigateConfig(...)`
- [ ] `src/main/templates/Job.groovy`, where the project has it, contains no `<% %>` code and no expression other than a name in braces (`src/main/templates/artifacts/Script.groovy` is read by no command: `create-script` is removed)
- [ ] Jobs that run `grails test-app` in a project with added `Test` tasks either mean to run them or name the phases (`test-app --unit --integration`), and jobs that stop the application with `stop-app` started it with `run-app --detach`
- [ ] `run-app` waits on the `server.port` of `application.yml` as the application reads it (per document: environment block over the rest of the document, later documents over earlier ones; profile documents only with `-Dgrails.spring.profiles.active`); with `--https`, a key store given before or after `--` keeps the passwords of the configuration; leaving the console waits up to 30 s for each application's port to close
- [ ] Jobs that run `grails` as another user than the owner of the project, or below the project directory in a container, run it in the project directory or set `GRAILS_SAFE_DIRECTORY`; in a multi-project build they run project commands in the directory of the application
- [ ] A Gradle wrapper above the project whose `gradle` or `gradle/wrapper` is a symbolic link points into the wrapper's own directory, directly, through directories that are the user's and not world-writable; otherwise replace the link or set `GRAILS_SAFE_DIRECTORY`
- [ ] Nothing depends on `~/.grails/settings.groovy`
- [ ] Feature pack descriptors: a full name `group:artifact#name` in `requires`, `conflicts` or a preset's `features` matches only the pack of that plugin taken by coordinates or from a catalog, and a conflict by full name now refuses the application. `feature list` takes `--feature-pack-dir`, `--feature-pack-repository` and `--offline` as `feature add` does
- [ ] An interrupted `feature add` or `project migrate` that refuses with "has another mode than the change gave it": give the named file the mode the message names (or make it what it was before the change) and run the command again; a journal of an earlier Grails 9 build in `~/.grails/forge/transactions` is not read: restore the project from version control and remove that `journal`
- [ ] `project regenerate --diff [--json]` reports `unreadable-file` for a file it cannot read: make the file readable before comparing
- [ ] If `grails help` or `grails project health` says `runtimeClasspath cannot be resolved completely`, fix the dependency it names (`./gradlew dependencies --configuration runtimeClasspath`): the project compiles, but it does not run until then
- [ ] An application with GORM for Neo4j imports `grails-neo4j-bom` and no `grails-bom`, in `build.gradle` (`dependencies` and `buildscript`) and in `buildSrc/build.gradle`, and has neither Hibernate 7 nor Micronaut beside it
- [ ] The full test suite passes on Groovy 6

## Pitfalls to Avoid

- Do not add `io.reactivex:rxjava` back to make old imports compile; port the code instead. Two RxJava generations on one classpath hide the mismatch until a datastore or plugin hands the application the wrong `Observable`.
- Do not compare a blocking result with `null` to detect an empty result; RxJava 3 throws instead.
- Do not keep a `Subscription` field for an RxGORM subscription; the handle is the `Disposable` passed to `onSubscribe`.
- Do not pin Groovy, Spock or RxJava versions in the application build; the Grails BOM manages them.
- Do not copy the Grails wrapper files by hand where `./grailsw update-wrapper` can write them, and do not run it before `grailsVersion` names Grails 9: with the wrapper of Grails 7 or 8 and an earlier `grailsVersion` it only downloads that earlier command line again. Do not leave the replaced files uncommitted: until `grails-wrapper.jar` of Grails 9 is committed, every checkout replaces them again on its first `./grailsw` command.
- Do not translate a Grails 8 command by its name alone; run `grails help` in the project and `grails help <command>`. A name that is no command exits with 2 and runs no command (where the build had not listed the commands of the application and its plugins, it lists them first), which a script that ignores exit codes does not notice.
- Do not put the environment in front of the command (`grails prod war`); it is refused. Use `-Dgrails.env=` after the command.
- Do not add a `profile` dependency or `grails.profile` to get the `create-*` commands back in an application the Grails shell created, and do not write `grails-forge-cli.yml` or `.grails/project.yml` by hand; run `grails project migrate`. Do not expect `feature add` to work afterwards: the record it writes is `untracked`.
- Do not add `grails gradle grailsCommandCatalog` before `grails dbm-update`, `grails url-mappings-report` or another plugin command: every build that compiles the project lists the commands, and in a fresh checkout `grails NAME` has the build list them first. Set `GRAILS_LIST_COMMANDS=false` only where a mistyped name must fail without a build.
- Do not run `./gradlew runScriptCommand --arg=s2-quickstart` (or another script of the framework's plugins): the scripts are gone. Use `runCommand`.
- Do not put `-Dgrails.env=` after the name of a plugin command (`grails dbm-update -Dgrails.env=production`): everything after the name is an argument of the command. Use `grails run-command -Dgrails.env=production dbm-update`.
- Do not run `grails install-templates` to customize scaffolding; it copies the templates of the `create-*` commands. The scaffolding one is `grails run-command install-templates`.
- Do not rely on a `create-*` command skipping a file that exists; it now fails with exit code 1 and writes nothing unless `--force` is given.
- Do not set `GRAILS_SAFE_DIRECTORY=*` on a shared machine to silence the ownership check; name the project directory.
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
