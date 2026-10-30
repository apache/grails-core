---
name: grails-9-upgrade
description: Guide for upgrading Grails applications from Grails 8.x to Grails 9, covering the Groovy 6 baseline, the GPars deprecation in favour of groovy.concurrent, invokedynamic compilation, the separate grails-xml module, the opt-in settings for Spring JSON rendering, Jackson request binding, Spring MVC exception handlers and response bodies, the Jackson-backed JSON converter and domain class rendering, named JSON configurations, RFC 9457 validation problems, format resolution, web unit tests, generated GORM id and version properties, RxGORM on RxJava 3 (imports, blocking calls, subscribers, empty results), the deprecated RxJava 1 and RxJava 2 async and events modules, and the character encoding filter Spring Boot now registers
license: Apache-2.0
---

<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

## What I Do

- Guide upgrades of Grails applications from Grails 8.x to Grails 9.
- Turn the Grails 9 upgrade guide into a practical migration checklist.
- Identify the Groovy 6, GPars, invokedynamic, XML, JSON rendering and binding, REST error, content negotiation, unit test, GORM, RxGORM, RxJava, and character encoding changes that can break an application.
- Prepare an application for the defaults Grails 10 changes, by checking each Grails 9 opt-in setting while the Grails 8 behaviour is still the default.
- Keep migration work focused on public application behavior, not internal Grails framework implementation details.

## When to Use Me

Activate this skill when:

- Updating an application to Grails 9.
- Reviewing a Grails 9 upgrade branch or pull request.
- Fixing tests, build failures, runtime failures, or behavior changes after moving from Grails 8.x to Grails 9.
- Deciding whether an application keeps or excludes `grails-xml`.
- Turning on `grails.web.rendering.json.spring`, `grails.converters.json.legacy: false`, `grails.databinding.json.jackson`, `grails.exceptionresolver.springHandlers` or `grails.controllers.responseBody`, and checking what changes.
- Replacing `JSON.createNamedConfig(...)`, `JSON.use(...)` or marshallers that customize `respond` with Jackson serializers and the `NamedJsonConfigurationRegistry`.
- Fixing web unit tests (`ControllerUnitTest`, `TagLibUnitTest`, `UrlMappingsUnitTest`, `JsonViewUnitTest`) that fail after the upgrade.
- Porting code that uses RxGORM, `grails-async-rxjava`, `grails-async-rxjava2`, `grails-events-rxjava`, or `grails-events-rxjava2` to RxJava 3.
- Replacing GPars (`grails-async-gpars`, `grails-events-gpars`, or direct `GParsPool` use) with Groovy's `groovy.concurrent` toolkit.
- Fixing form input or request bodies decoded in the wrong charset, or moving `grails.filter.encoding` and `grails.filter.forceEncoding` to `spring.servlet.encoding`.

For an application still on Grails 7, apply the `grails-8-upgrade` skill first and move to Grails 8 before using this one.

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
| `https://github.com/ReactiveX/RxJava/wiki/What's-different-in-3.0` | RxJava 3 API differences, with the RxJava 1 to 2 differences it links to |

## Upgrade Baseline

Verify these before touching application code; `gradle.properties`, the Gradle wrapper, and the Grails BOM are the sources of truth:

- Java 21 remains the minimum to build and run.
- Groovy moves from 5.x to 6.0, and Spock to the `groovy-6.0` variant. Do not pin `groovy-all` or Spock yourself; the Grails BOM manages both.
- Spring Boot 4.1 and Spring Framework 7 are unchanged from Grails 8; a Grails 8 application's Boot configuration carries over.
- Gradle 9.8 or later for the wrapper.
- RxGORM (`grails-datamapping-rx`) depends on `io.reactivex.rxjava3:rxjava`. The RxJava 1 artifact `io.reactivex:rxjava` is no longer transitively available through it.
- Groovy source sets compile with invokedynamic by default; `grails { indy = false }` was the Grails 8 default and is now unset.
- XML support is the separate `org.apache.grails:grails-xml` module, which the Grails 9 web starter and `grails-web-boot` still bring.
- Every changed JSON, XML and Spring MVC path keeps its Grails 8 behaviour by default and has a setting that opts in, listed under [Opt-In Settings Ahead of Grails 10](#opt-in-settings-ahead-of-grails-10). Only a `ProblemDetail` given to `respond`, and the Spring MVC response types an action returns, change whatever the settings.

## Migration Workflow

1. Update `grailsVersion` in `gradle.properties` and refresh the Gradle wrapper; let the Grails BOM manage Groovy, Spock, and RxJava versions.
2. Run `./gradlew clean`, then build without tests (`./gradlew compileGroovy compileTestGroovy`) and fix compilation errors module by module. The clean build matters: classes Grails 8 compiled can keep generated `id` and `version` fields on a plain superclass.
3. Replace GPars with `groovy.concurrent`, following the sections below.
4. Port RxGORM and RxJava code, and replace the deprecated RxJava 1 and 2 async and events modules with their RxJava 3 counterparts.
5. Decide on XML: declare `grails-xml` explicitly if the application serves or reads XML, or exclude it if the application is JSON-only.
6. Run the test suite and fix the web unit tests, following [Web Unit Tests](#web-unit-tests).
7. Work through the opt-in settings one at a time: turn each on, check the responses and tests it affects, and set it explicitly in `application.yml` either way. Change the three JSON settings together.
8. Run the full test suite, then start the application and read its log for `[DEPRECATED]` and `deprecated` warnings from Grails, and for the warnings that name types `respond` still writes with the legacy converter.
9. Review the published upgrade guide section by section, confirming every item either does not apply or has been handled.

## Groovy 6

Groovy 6 is a major Groovy release. Read its release notes before assuming that a Groovy 5 construct behaves the same way. Compile the application statically where it was already static; a construct that compiles dynamically but fails under `@CompileStatic` is a sign of a Groovy change rather than a Grails one. Keep the Spock version the Grails BOM supplies, which is built for Groovy 6.

## GPars Deprecated in Favour of Groovy Concurrency

Groovy 6 brings the concurrency and parallel-processing patterns of GPars into the language as `groovy.concurrent`, so Grails no longer needs the separate GPars library. `grails-async-gpars` and `grails-events-gpars` still work in Grails 9, carry `@Deprecated(since = '9.0', forRemoval = true)`, log a warning when selected, and will be removed in a future release.

Replace the dependency, one for one:

| Grails 8 | Grails 9 |
|----------|----------|
| `org.apache.grails.async:grails-async-gpars` | `org.apache.grails.async:grails-async-groovy-concurrent` |
| `org.apache.grails.events:grails-events-gpars` | `org.apache.grails.events:grails-events-groovy-concurrent` |

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

  Without the module, `request.XML` throws an `IllegalStateException` that names the module, and a class compiled with `@CompileStatic` or `@GrailsCompileStatic` that reads it does not compile. An XML request body is not read, `respond` answers an XML client in the next type it accepts or with `406 Not Acceptable`, and `encodeAsXML` escapes an object other than text as its string. A markup view still renders XML for an action that has one.
- A build that lists `grails-web-boot` without the REST, controllers, data source, converters, data binding, domain class, MIME types and i18n modules now loads their plugins through `grails-xml`, among them the Grails locale resolver; excluding `grails-xml` leaves out those that nothing else brings.
- A plugin that reads `request.XML` in a statically compiled class, compiled against Grails 9, declares `grails-xml` as an `implementation` dependency. A library compiled against Grails 8 keeps working without being recompiled, but still needs the module at run time.
- New REST controllers, resources, and the `rest-api` profile's scaffolded controllers advertise JSON only. Add `xml` to an artefact's `responseFormats` or `formats` where that endpoint should negotiate XML.
- Legacy XML conversion is deprecated for removal in Grails 11: `grails.converters.XML`, `XmlRenderer`, `XmlCollectionRenderer`, the HAL XML, Atom and Vnd.Error XML renderers, and the XML validation errors marshaller. `grails.converters.XML` logs a warning when it first renders XML, and the HAL XML, Atom and Vnd.Error XML renderers when they are used. Plan the move to DTOs, explicit renderers, or Spring's XML converter.
- `grails.web.rendering.xml.spring: true`, with `runtimeOnly 'tools.jackson.dataformat:jackson-dataformat-xml'`, writes ordinary beans with Spring's `JacksonXmlHttpMessageConverter`. Adding the dependency alone changes nothing. `null`, domain objects, collections, arrays, maps, validation errors, `includes` and `excludes` projections, and renderers with a legacy `namedConfiguration` still use the Grails XML converter.
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

- Grails 10 changes the three JSON settings together, so that `respond`, `render ... as JSON`, `request.JSON` and data binding write and read JSON with the same mapper. Test them together. At the least, set `grails.converters.json.legacy: false` and `grails.databinding.json.jackson: true` together, or `request.JSON` and data binding accept different JSON.
- Grails 9 logs a warning while a legacy path is used: once when the legacy `respond` path renders, at startup for `JsonSlurper` request binding, and at startup while `grails.converters.json.legacy` is unset. Setting `grails.converters.json.legacy` to either value turns its warning off.
- Set every setting the application depends on explicitly, so that the Grails 10 upgrade changes nothing unannounced.

## `respond` Through Spring MVC's JSON Converters

With `grails.web.rendering.json.spring: true`, `respond` writes JSON with Spring MVC's message converters, backed by Boot's configured `JsonMapper`:

- The value is written as Spring MVC writes what a `@ResponseBody` handler method of the controller returns: the application's `ResponseBodyAdvice` beans apply, the action's `@JsonView` selects the properties, and the response is UTF-8 whatever `grails.converters.encoding` says.
- Jackson decides date formats, bean properties, circular references and validation errors, and writes an enum by its `@JsonValue` or its `toString()`, where the legacy converter writes its name. Compare the JSON of every `respond` endpoint before and after.
- Registering a marshaller does not change which path `respond` uses, and marshallers registered with `JSON.registerObjectMarshaller(...)` do not customize Spring's path. Move those representations to Jackson serializers, as [JSON Customization Moves to Jackson](#json-customization-moves-to-jackson) shows, before turning the setting on.
- `grails.converters.json.pretty.print` gives way to `spring.jackson.serialization.indent-output`, and `grails.converters.json.date` to Boot's `spring.jackson.*` date settings.
- `respond` still uses the legacy converter for a renderer with a legacy named configuration, for an `includes` or `excludes` projection when the selected converter is not Spring's standard Jackson converter, and for a type and media type no configured MVC converter writes. Grails logs a warning for each such type and reason, up to 100, so read the log to find responses the setting did not change.
- A value Spring's converter fails to write fails as in a Spring MVC controller. Spring MVC answers an `HttpMessageNotWritableException` with a 500 before the Grails exception resolver sees it, and the error page finds the exception in the `jakarta.servlet.error.exception` request attribute rather than in its model.
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
- `includes` and `excludes` on `render json:` and `respond` project the type written, or each element's type for a collection or array, by bean property name or JSON name. A value with no properties to project, such as a map or a string, throws `IllegalArgumentException` before anything is written.
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
| `JsonNode` | an object of its bean properties | the JSON it holds |
| `Optional` | `{"empty":false,"present":true}` | its value, or `null` |
| A `Map.Entry` | `{"key":"k","value":1}` | `{"k":1}` |
| A map with a `null` key | the entry left out | a `ConverterException` |
| Validation errors (`render errors as JSON`) | entries with `rejected-value` | entries with `codes` and no rejected value |
| A control character without a short escape | `"\u001b"` | `"\u001B"` |
| A value nested deeper than 500 levels | the JSON | a `ConverterException`; `spring.jackson.factory.constraints.write.max-nesting-depth` sets the limit |
| Pretty printed JSON | `"a": 1` | `"a" : 1`, Jackson's default pretty printer |

With `legacy: false`, map keys use the mapper's key serializers: a `Date` or `Calendar` key follows the mapper's date format and time zone, and a `Class` key is its name, without the `class ` prefix. A `NaN` or infinite `double` renders as `"NaN"`, and a single value, such as `render Role.HEAD as JSON`, renders as a JSON value, whatever the setting.

Two things deliberately differ from Spring Boot: enums, including one with a `@JsonValue`, render by `name()`, and strings are escaped for an HTML `<script>` element (`</` as `</`, U+2028 and U+2029 escaped).

Parsing changes with `legacy: false` too:

- `JSON.parse` and `request.JSON` parse with the application's Jackson mapper, so they accept strict JSON. Single quotes, comments, unquoted names and values, uppercase literals, trailing commas and `new Date(...)` are rejected. Re-enable the forms Jackson has a feature for with `spring.jackson.json.read.allow-single-quotes`, `allow-java-comments`, `allow-unquoted-property-names` or `allow-trailing-comma`. Text after the value is rejected unless `spring.jackson.deserialization.fail-on-trailing-tokens` is `false`.
- The returned `JSONObject` and `JSONArray` containers, and their number types, do not change. `new JSONObject(String)` and `new JSONArray(String)` always use Grails' own parser.
- `grails.converters.json.date: javascript` needs `legacy: true`, because its `new Date(...)` output is not JSON; an application that sets both fails to start with a message naming them. Remove `grails.converters.json.date` and configure dates with `spring.jackson.*`.

To keep one type's Grails 8 rendering under `legacy: false`, register a marshaller for it, for example in `BootStrap.init`; a registered marshaller takes precedence over the mapper:

```groovy
JSON.registerObjectMarshaller(Month) { Month month -> month.name() }
JSON.registerObjectMarshaller(Locale) { Locale locale -> locale.toString() }
```

With `legacy: false`, the deprecated `DateMarshaller`, `CalendarMarshaller`, `ToStringBeanMarshaller`, `ByteArrayMarshaller`, `InstantMarshaller`, `LocalDateMarshaller`, `LocalDateTimeMarshaller`, `OffsetDateTimeMarshaller` and `ZonedDateTimeMarshaller` are not registered, so a marshaller registered with an explicit negative priority may now come before or after different defaults.

Whatever the setting:

- A subclass of `org.grails.web.json.JSONWriter` needs review: it now writes through a Jackson `JsonGenerator`, its `array()`, `object()`, `key()` and `value` methods no longer call the protected `append` and `comma` methods, and its text reaches the `Writer` as the buffer fills, when the JSON is complete, or on `flush()`, rather than per value. Use the public methods instead of overriding `append` or writing to the `writer` field.
- `PrettyPrintJSONWriter` is deprecated in favour of `new JSONWriter(writer, true)`, and the JavaCC `JSONParser` in `org.grails.web.json.parser` in favour of `JSON.parse` or `new JSONObject(String)`.
- The `includes` and `excludes` a converter is given apply to the instance a proxy stands for, such as a lazy Hibernate association, where Grails 8 ignored them and rendered every property.
- The `PATH` circular reference behaviour renders an object with a `null` property, and writes the right path after a number.
- A value nested deeper than the thread's stack allows fails with a `ConverterException` instead of a `StackOverflowError`.

JSON views, the XML converter and the HAL renderer are not changed.

## Domain Classes Render as Jackson Beans of Their Persistent Properties

With `grails.converters.json.legacy: false`, `render ... as JSON` writes a domain class instance with Jackson's bean serializer, shaped to the domain class as `DomainClassMarshaller` shaped it:

- A property annotated `@JsonIgnore` or `@JsonProperty(access = WRITE_ONLY)`, or named by a class-level `@JsonIgnoreProperties`, is not rendered; `@JsonProperty`, `@JsonNaming` and `spring.jackson.property-naming-strategy` rename properties; `@JsonInclude` and `spring.jackson.default-property-inclusion` decide whether a null or empty property is written. A property that was rendered before may now be absent or renamed.
- Only persistent properties render, as before. The class name, id and version come first when they apply, then the properties in the order the domain class declares them, unless `@JsonPropertyOrder` says otherwise.
- The `tenantId` of a multi-tenant domain class and the `dbo` of a MongoDB domain class, which Grails 8 rendered, are no longer rendered.
- A to-many association whose value is a `Map` renders as an object of references, where it failed before.
- A domain class with its own Jackson serializer (`@JsonSerialize`, `@JsonValue`, `JacksonSerializable`, or a module) is written by it, also as the value of an association.
- Subclasses of `DomainClassMarshaller` and `DeepDomainClassMarshaller` still render as they did.

The application's `JsonMapper` is not changed, so a domain class instance a Spring MVC controller returns is still written as a plain Jackson bean. Set `grails.converters.json.domain.jackson.enabled: true` to have Grails register the `domainClassJacksonModule` bean, which Spring Boot adds to its `JsonMapper` and to every mapper built from its `JsonMapper.Builder`; inject it as a `tools.jackson.databind.JacksonModule` and pass it to `addModule` for a mapper built otherwise.

## Rendering Single Values as JSON

`render(Role.HEAD as JSON)`, which threw a `ConverterException` in Grails 8, renders `"HEAD"`. The converter accepts any scalar root: a string, a number, a boolean, `null` or an enum. Groovy's own casts do not support `value as JSON` for strings and numbers, so construct `new JSON(value)` for those. A non-null scalar given to `respond` renders the same way. Update a test that expected the exception.

## Opt Into Jackson JSON Request Binding

With `grails.databinding.json.jackson: true`, JSON, HAL JSON and JSON API request bodies are parsed with the mapper Spring MVC reads a `@RequestBody` map with, usually Boot's `JsonMapper`:

- An application `JsonSlurper` bean, such as one using `JsonParserType.LAX`, no longer applies. Configure parser features with a `JsonMapperBuilderCustomizer` or `spring.jackson.json.read.*`, and limits with `spring.jackson.factory.constraints.read.*` or a `JsonFactoryBuilderCustomizer`.
- Jackson rejects input the legacy parser accepted: trailing content after a document, trailing commas, leading-zero numbers, unescaped tabs inside strings, trailing comments, and input beyond its `StreamReadConstraints`. Review what clients send before turning it on.
- The body is read as plain maps, lists, strings, numbers and booleans, which the Grails data binder binds by name as before. Jackson annotations on command objects and domain classes, the naming strategy, the date format, module deserializers and default typing do not apply to binding.
- Numbers keep their types: a fractional value is a `BigDecimal`, and a whole one an `Integer`, `Long` or `BigInteger`.
- A malformed body is still a binding error, or a `DataBindingSourceCreationException` when bound to a collection with `bindData`.

## REST Validation Errors Use RFC 9457 Problems

With `grails.web.rendering.json.spring: true`, `respond` renders Spring `Errors` as `application/problem+json` instead of the legacy `{"errors": [...]}` body. Update the API clients and tests that read validation errors:

- The status is 422, the title `Validation failed`, and the detail counts the errors, such as `Request validation failed with 2 errors.`; the `grails.validation.problem.title` and `grails.validation.problem.detail` message codes translate them.
- An `errors` extension lists the object name, field, message and codes of each error. Rejected values are left out, because they can hold passwords or tokens. Validation errors written as XML are not problems and keep their rejected values.
- To build the problem explicitly, inject `ValidationProblemDetailFactory` and `respond validationProblemDetailFactory.create(book.errors)`.
- To include rejected values after reviewing the exposure, declare your own factory bean, `new ValidationProblemDetailFactory(true, messageSource)`. It replaces the default whatever its name; one named `validationProblemDetailFactory` needs bean definition overriding, which Grails allows unless `spring.main.allow-bean-definition-overriding` is `false`.

With JSON views, an errors template, `grails-app/views/errors/_errors.gson` or one in the controller's view directory, still renders validation errors. Applications generated with the JSON views feature or the `rest-api` profile have one; delete it to answer with the problem. Without an errors template, Grails 8 rendered validation errors with `_object.gson`, writing `null` with a 200. Whatever the settings, they are now written as without JSON views, with a 422.

Whatever the settings, `respond` writes a `ProblemDetail`, or a Spring `ErrorResponse` such as an `ErrorResponseException`, as Spring MVC writes a returned `ResponseEntity`, where Grails 8 wrote it as a bean with a 200:

- The response has the problem's status, and an absent `instance` is set to the request path. A `status` argument decides the status instead.
- The application's `ResponseBodyAdvice` and `ErrorResponse.Interceptor` beans apply, and an `ErrorResponse`'s message codes are resolved with the application's messages.
- Spring MVC's content negotiation picks the type, as `spring.mvc.contentnegotiation.*` configures it, not `grails.mime`. The problem is `application/problem+xml` only where the client asks for a problem in XML, the action responds in XML, and Spring MVC has an XML converter. Unless `respond` names a view, a client asking for `application/xml`, a browser, or a client accepting neither JSON nor XML is answered with `application/problem+json`, where Grails 8 wrote the problem as a bean or rendered the action's view, with a 200.
- A view renders a problem only when `respond` names it with the `view` argument.

## HAL Rendering

The Grails HAL JSON renderers remain available through explicit renderer registration. Adding `spring-boot-starter-hateoas` does not register a Grails renderer for Spring HATEOAS `EntityModel` values; return those models from a Spring MVC controller, or register a Grails renderer for the media type.

## Grails Format Resolution and Spring MVC Content Negotiation

`withFormat`, `respond` and `response.format` apply the `grails.mime.*` rules; Spring MVC handlers keep their own content negotiation. Check these changes in functional and unit tests:

- Media type names in `Accept` and `Content-Type` match ignoring case, as do their parameters: `Accept: Application/JSON` resolves to `json`, where Grails 8 resolved it to `all`, and a body sent as `Content-Type: Application/JSON` now binds, where Grails 8 ignored it. `text/html;Q=0.1` has the quality 0.1.
- The RFC 9457 types are the `json` and `xml` formats: `Accept: application/problem+json` selects `json`, so `respond` sends `406` for an action that does not respond in JSON, and `withFormat` runs no block without a `json` or `*` one. Listed ahead of another type, a problem type counts as its format, so `Accept: application/problem+json, application/hal+json` resolves to `json`, where Grails 8 resolved it to `hal`.
- `text/xml` and `application/xml` merge into one `xml` type with the larger quality and the place of the one listed first.
- A client that lists a format the action does not respond in ahead of one it does is answered, and labelled, in the one it does.
- A client that accepts any type, by `*/*`, no `Accept` header, or as a browser, is answered in the first format the action lists that a renderer can render the value in, where Grails 8 answered `406`, or `404` for validation errors.
- Each text entry of `grails.mime.disable.accept.header.userAgents` is its own regular expression, matched ignoring case; a string value is a comma-separated list; a blank entry is left out; an entry that is not a regular expression fails at startup, naming the setting.
- The `json`, `xml`, `hal`, `atom`, `rss`, `csv` and `text` formats are registered with Spring as format aliases, which Spring MVC uses only with `spring.mvc.contentnegotiation.favor-parameter: true`.
- A renderer or binding source creator registered for an interface of `java.lang.Class`, such as `Serializable`, no longer matches every class.
- The MIME types and the `grails.mime.use.accept.header` and `grails.mime.disable.accept.header.*` settings live in the application context instead of static fields. Remove test code that reset those fields or called `HttpServletResponseExtension.loadMimeTypeConfig`, and set the settings in a spec's `doWithConfig`, since they are read once for each application context. A subclass of `DefaultAcceptHeaderParser` that overrode `createMimeTypeAndAddToList` must take the requested `MimeType` now.

## Web Unit Tests

- `ControllerUnitTest`, `TagLibUnitTest` and the other web test traits always include the `restResponder` plugin, even when a spec overrides `getIncludePlugins()`. They also register JSON conversion beans with Boot's Jackson configuration, Spring MVC's message converters, the `mvcContentNegotiationManager` and `requestMappingHandlerAdapter` beans unless the spec declares beans of those names, and the `grails-xml` beans when that module is on the test classpath. Update specs that assert on the beans of their context.
- The content negotiation manager applies `spring.mvc.contentnegotiation.*` from `application.yml` or `doWithConfig`. A spec for an application that declares `@EnableWebMvc` declares the application's manager as its `mvcContentNegotiationManager` bean.
- Add a `format` parameter for `favor-parameter` with `request.addParameter`, not `params.format`.
- A test request resolves its format from its content type as a request to the application does: `application/json;charset=UTF-8` is `json`, where Grails 8 gave no format, and `text/xml` is read as `application/xml`. A format the test sets, directly or through `request.json` or `request.xml`, is kept.
- A URL mappings holder or link generator a spec declares, in `doWithSpring`, `beanRegistrar()` or a `@Bean` method of a nested `@Configuration` class, is kept, where the traits replaced it. The URL mappings plugin's own holder and link generator are still replaced, so a spec that includes the `controllers` or `urlMappings` plugin declares its holder in `doWithSpring` or `beanRegistrar()`.
- The test's locale resolver is kept, the i18n plugin's included, where the traits replaced it with a session resolver. A spec that changes a request's locale where the application configures a fixed or accept-header resolver sets `grails.i18n.localeResolver` to `session` in `doWithConfig`. `JsonViewUnitTest` keeps them the same way.
- The bean of a `@Bean` method that a nested `@Configuration` class inherits replaces the stand-in the test's context registers under that name, such as the `StaticMessageSource` named `messageSource`, in every unit test.
- A controller a test creates with `new` responds with the application's renderer registry, including its JSON and markup views.
- Without `grails-xml` on the test classpath, setting `request.xml` to an object rather than a `String` of XML, or reading `request.XML`, throws an `IllegalStateException` that names the module. `GrailsUnitTest` and `ServiceUnitTest` do not load XML or the web plugins because those modules are on the classpath.

## Opt Into Spring MVC Exception Handlers

With `grails.exceptionresolver.springHandlers: true`, the `@ExceptionHandler` methods of the controller and of the `@ControllerAdvice` beans that apply to it, Spring Boot's `spring.mvc.problemdetails.enabled` handler among them, resolve the exceptions actions throw before the Grails exception resolver. An exception none of them handles is resolved as before. Grails 10 makes it the default, so scope `@ControllerAdvice` beans meant only for Spring MVC controllers by `assignableTypes`, `basePackages` or `annotations` before then.

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

`Observable.toList()` returns a `Single<List<T>>` in RxJava 3. The RxGORM methods that emit a list, such as `list()`, `DetachedCriteria.toList()`, criteria `list()` and `toListObservable()` on a lazy association, still return `Observable<List<T>>`, so subscribers to them do not change.

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

A datastore implementation of RxGORM compiled for Grails 8 or earlier, such as the RxGORM for MongoDB plugin, links against RxJava 1 and fails at runtime with `NoClassDefFoundError: rx/Observable`. It must be rebuilt against this release. The `rx-mongodb` profile feature is removed, because the plugin it generated has had no release since GORM 6.1 and was never published to Maven Central. `RxEntity` has had no datastore implementation for several releases, so an application that implements it should move to `GormEntity` with the RxJava data services, which work on every GORM datastore.

## Deprecated RxJava 1 and RxJava 2 Modules

`grails-async-rxjava`, `grails-async-rxjava2`, `grails-events-rxjava` and `grails-events-rxjava2` are deprecated for removal. They still work and log a warning such as `The RxJava 1 promise factory (grails-async-rxjava) is deprecated` when the promise factory or event bus is created.

- Replace `org.apache.grails.async:grails-async-rxjava` or `grails-async-rxjava2` with `grails-async-rxjava3`.
- Replace `org.apache.grails.events:grails-events-rxjava` or `grails-events-rxjava2` with `grails-events-rxjava3`.
- The `Promise` and `EventBus` APIs are the same for every implementation, so an application that only uses the Grails APIs needs no code change. The RxJava 3 promise factory carries a `null` value as the RxJava 1 one does: `createPromise()` resolves to `null`, a task that returns `null` resolves to `null`, and a `null` keeps its place in the list `onComplete` and `waitAll` deliver. The RxJava 2 factory does not: it fails with a `NullPointerException` or resolves to the `java.lang.Void` class instead.
- Code that hands RxJava types to the Grails ones, such as the `rx.Scheduler` given to the `RxEventBus` constructor, moves to the RxJava 3 types as described above.
- Configure RxJava 3 scheduling through `io.reactivex.rxjava3.plugins.RxJavaPlugins` instead of RxJava 1 `RxJavaHooks` or RxJava 2 `RxJavaPlugins`.

## Spring Boot Registers the Character Encoding Filter

Grails no longer registers a character encoding filter of its own. Spring Boot's runs at `Ordered.HIGHEST_PRECEDENCE`, ahead of every other filter, and `spring.servlet.encoding` configures it. In Grails 8, Grails' filter made Spring Boot's back off, so `spring.servlet.encoding` had no effect.

- The request encoding is now forced by default, as in any Spring Boot application: a request that declares another charset is decoded as UTF-8 anyway, for its parameters, a body read through `getReader()`, `request.JSON` and `request.XML`, and a JSON or XML body bound to a command object or domain class. A PUT, PATCH or DELETE form is still decoded with the charset it declares, as in Grails 8, because `FormContentFilter` parses it. Set `spring.servlet.encoding.force-request: false` to keep the charset a request declares, as Grails 8 did.
- `grails.filter.encoding` and `grails.filter.forceEncoding` are deprecated. Grails translates them into `spring.servlet.encoding.charset` and `spring.servlet.encoding.force` with a warning, also from files named by `grails.config.locations`, and the application's own `spring.servlet.encoding` setting wins over them. Move to the Spring Boot properties.
- `GrailsFilters.CHARACTER_ENCODING_FILTER` is deprecated, and its order is now `Ordered.HIGHEST_PRECEDENCE`, the order of Spring Boot's encoding filter, where Grails 8.0 had -180, so a filter at `CHARACTER_ENCODING_FILTER.order + 1` runs right after the encoding filter, ahead of `FormContentFilter` and of Grails' security headers filter at `GrailsFilters.FIRST`: it no longer sees the parameters of a PUT, PATCH or DELETE form, and a response it sends without continuing the chain gets no security headers. Use `Ordered.HIGHEST_PRECEDENCE + 1` instead, or `GrailsFilters.ASSET_PIPELINE_FILTER.order + 1` to keep the place it had in Grails 8.0.
- `spring.servlet.encoding.enabled: false` turns the filter off.
- Grails 8.1 already registers its encoding filter at `Ordered.HIGHEST_PRECEDENCE`, so coming from 8.1 the encoding filter runs where it did. Coming from 8.0, filters ordered between `Ordered.HIGHEST_PRECEDENCE + 1` and -181 now run after the encoding filter, with the request encoding set. In a packaged application, asset-pipeline's filter then declares that charset on every asset response, for example `text/css;charset=UTF-8`.
- A filter that reads request parameters needs a later order than `Ordered.HIGHEST_PRECEDENCE`. The CAS plugin registers its single sign-out filter at `Ordered.HIGHEST_PRECEDENCE + 1`.

## Verification Checklist

- [ ] `gradle.properties` names the Grails 9 version and the wrapper is Gradle 9.8 or later
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
