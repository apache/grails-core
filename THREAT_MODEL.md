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

# Threat Model - Apache Grails

## §1 Header

- **Project**: Apache Grails (`apache/grails-core`)
- **Version binding**: 8.0.x branch. A report against version *N* is triaged against this document as it stood at *N*, not at HEAD.
- **Date**: 2026-01
- **Author**: Apache Grails PMC and contributors (initial draft).
- **Status**: **DRAFT** - not yet ratified by maintainers. Open questions in §14 must be resolved before this document is binding.
- **Reporting cross-reference**: findings that may violate a property claimed in §8 should be reported privately per [`SECURITY.md`](./SECURITY.md) (which routes to the [ASF Security Team](https://www.apache.org/security/)). Findings that fall under §3 (out of scope), §9 (disclaimed properties), or §11a (known non-findings) will be closed publicly citing the relevant section of this document.
- **Provenance legend**: every non-trivial claim is tagged.
  - *(documented)* - stated in the project's own docs ([`grails-doc/src/en/guide/security/`](./grails-doc/src/en/guide/security/), [`SECURITY.md`](./SECURITY.md), [`README.md`](./README.md), [`AGENTS.md`](./AGENTS.md), or the public Grails Guide).
  - *(maintainer)* - stated by a maintainer in response to a question from this drafting process.
  - *(inferred)* - reasoned from code structure, absence of a feature, or general domain knowledge. Each must have a matching entry in §14.
- **Draft confidence**: ~48 *(documented)* / 0 *(maintainer)* / ~64 *(inferred)*. This model is a draft-first artifact per the rubric §3.2 - the *(inferred)* count is expected to drop sharply once §14 is worked through with the PMC.

**Project description**: Apache Grails is an opinionated, full-stack web application framework for the JVM. It composes Apache Groovy, Spring Framework, Spring Boot, GORM (Hibernate / MongoDB / Neo4j data mapping), and GSP/JSON view rendering, and ships a CLI (`grails-shell-cli`, `grails-forge-cli`) plus an HTTP application generator (`grails-forge`). Grails is **embedded into a user-authored web application**; it is not deployed as a standalone network service by the project. The unit of trust modeled here is "an application using the Grails framework," not "a Grails server." *(documented: [README.md](./README.md))*

---

## §2 Scope and intended use

**Primary intended use**: building server-side web applications in Groovy/Java on the JVM, deployed as Spring Boot executable JARs or WARs running under an operator the application owner controls. *(documented: [README.md](./README.md))*

**Secondary intended use**: project scaffolding and code generation via the CLI tools and `grails-forge` HTTP API at <https://start.grails.org>. *(documented: [README.md](./README.md))*

**Caller roles** (the model distinguishes three; this is not a network-service split since the framework is in-process within the user's app):

| Role | Trust level | Description |
|---|---|---|
| **End user (HTTP client)** | **Untrusted** | Sends HTTP requests to a deployed Grails application. Source of all attacker-controllable input considered in this model. *(inferred)* |
| **Application developer / operator** | **Trusted** | Writes controllers, services, domain classes, URL mappings, GSP templates; configures `application.yml` / `application.groovy`; runs the CLI; chooses plugins. *(inferred)* |
| **Plugin / profile author** | **Trusted-by-association** | Author of a third-party Grails plugin or `grails-forge` profile. Code from a plugin runs with full application privileges. The framework does not isolate plugin code. *(inferred)* |

### Component-family table

The framework is large; not every module has the same threat profile. The model carves the framework into the following families:

| Family | Representative entry point(s) | Touches outside process? | In or out of model |
|---|---|---|---|
| HTTP request ingress | [`GrailsDispatcherServlet`](./grails-web-mvc/src/main/groovy/org/grails/web/servlet/mvc/GrailsDispatcherServlet.groovy), [`UrlMappingsHandlerMapping`](./grails-web-url-mappings/src/main/groovy/org/grails/web/mapping/mvc/UrlMappingsHandlerMapping.groovy) | Yes - network (via Spring Boot embedded container) | **In** |
| Interceptors / controllers | [`GrailsInterceptorHandlerInterceptorAdapter`](./grails-interceptors/src/main/groovy/org/grails/plugins/web/interceptors/GrailsInterceptorHandlerInterceptorAdapter.groovy), `grails.artefact.Controller` trait | Yes - via Spring MVC | **In** |
| Data binding | [`GrailsWebDataBinder`](./grails-web-databinding/src/main/groovy/grails/web/databinding/GrailsWebDataBinder.groovy), `SimpleDataBinder`, [`DefaultDataBindingSourceRegistry`](./grails-web-databinding/src/main/groovy/org/grails/web/databinding/bindingsource/DefaultDataBindingSourceRegistry.groovy) | Indirectly (consumes request body) | **In** |
| View rendering (GSP, JSON, Markup) | [`GroovyPageCompiler`](./grails-gsp/core/src/main/groovy/org/grails/gsp/compiler/GroovyPageCompiler.groovy), [`ResolvableGroovyTemplateEngine`](./grails-views-core/src/main/groovy/grails/views/ResolvableGroovyTemplateEngine.groovy), [`SmartViewResolver`](./grails-views-core/src/main/groovy/grails/views/mvc/SmartViewResolver.groovy) | Filesystem (template files) | **In** |
| Codecs / output encoding | [`grails-encoder`](./grails-encoder/), [`HTMLCodec`](./grails-encoder/src/main/groovy/org/grails/encoder/CodecFactory.java) and siblings | No | **In** |
| GORM mapping / validation integration | [`GormEntityTransformation`](./grails-datamapping-core/src/main/groovy/org/grails/compiler/gorm/GormEntityTransformation.groovy), [`PersistentEntityValidator`](./grails-datamapping-validation/src/main/groovy/grails/gorm/validation/PersistentEntityValidator.groovy), [`HibernateMappingBuilder`](./grails-data-hibernate5/core/src/main/groovy/org/grails/orm/hibernate/cfg/HibernateMappingBuilder.groovy) | Yes - JDBC / DB driver | **In** |
| Configuration loading | [`ExternalConfigRunListener`](./grails-core/src/main/groovy/grails/config/external/ExternalConfigRunListener.groovy), [`GroovyConfigPropertySourceLoader`](./grails-core/src/main/groovy/org/grails/core/cfg/GroovyConfigPropertySourceLoader.groovy), [`GrailsApplicationPostProcessor`](./grails-core/src/main/groovy/grails/boot/config/GrailsApplicationPostProcessor.groovy) | Yes - filesystem, env, system properties | **In** |
| Plugin / artefact discovery | [`PluginManagerLoader`](./grails-core/src/main/groovy/grails/plugins/PluginManagerLoader.groovy), [`ClassPathScanner`](./grails-core/src/main/groovy/grails/boot/config/tools/ClassPathScanner.groovy), [`GrailsFactoriesLoader`](./grails-core/src/main/groovy/org/grails/core/io/support/GrailsFactoriesLoader.groovy) | Filesystem (classpath JARs) | **In** |
| Compile-time AST transforms | [`GlobalGrailsClassInjectorTransformation`](./grails-core/src/main/groovy/org/grails/compiler/injection/GlobalGrailsClassInjectorTransformation.groovy), [`ResourceTransform`](./grails-rest-transforms/src/main/groovy/org/grails/plugins/web/rest/transform/ResourceTransform.groovy), [`ViewsTransform`](./grails-views-core/src/main/groovy/grails/views/compiler/ViewsTransform.groovy) | No (build only) | **In** (developer-trusted inputs only - see §6) |
| CLI / shell (`grails-shell-cli`) | [`GrailsCli`](./grails-shell-cli/src/main/groovy/org/grails/cli/GrailsCli.groovy), [`MavenProfileRepository`](./grails-shell-cli/src/main/groovy/org/grails/cli/profile/repository/MavenProfileRepository.groovy) | Yes - network (Maven), filesystem | **In** (with caveats - see §3 and §7) |
| Interactive consoles (`grails-console`) | [`GrailsShell`](./grails-console/src/main/groovy/grails/ui/shell/GrailsShell.groovy), `GroovyConsoleApplicationContext` | Local I/O | **Out** - developer-only tool, see §3 |
| `grails-forge` HTTP API (start.grails.org) | [`ZipCreateController`](./grails-forge/grails-forge-api/src/main/java/org/grails/forge/api/create/zip/ZipCreateController.java), [`GitHubCreateController`](./grails-forge/grails-forge-api/src/main/java/org/grails/forge/api/create/github/GitHubCreateController.java) | Yes - network | **Out** - separate Micronaut-based service deployed independently from any application built with Grails; modeled separately by the operators of start.grails.org. *(inferred)* |
| Generated application scaffolding | `grails-profiles/`, files emitted by `create-app` | n/a | **Out** - separately authored; the framework's contract does not extend to scaffolded code once it is in the user's repository. See §3. |
| `grails-test-examples/`, demo apps | n/a | n/a | **Out** - test/demo code, not shipped. See §3. |

---

## §3 Out of scope (explicit non-goals)

The framework **does not** attempt to defend against, and **does not** model, the following. Triagers may close findings citing this section.

- **Application-level authentication and authorization.** The framework ships no built-in user store, login flow, or session-based auth. The user is expected to integrate with the Spring Security plugin, Apache Shiro, or an equivalent. The `grails-shell-cli` `SpringSecurityCompilerAutoConfiguration` is a compile-time hook for the plugin, not an auth implementation. *(documented: [grails-doc/src/en/guide/security/authentication.adoc](./grails-doc/src/en/guide/security/authentication.adoc), [grails-doc/src/en/guide/security/securityPlugins.adoc](./grails-doc/src/en/guide/security/securityPlugins.adoc))*
- **Transport security (TLS).** Provided by the Spring Boot embedded container (Tomcat / Jetty / Undertow / Netty) or by a reverse proxy in front of the application. Out of layer. *(inferred)*
- **Database engine, JDBC driver, and JVM vulnerabilities.** The framework is a consumer; bugs in these layers are upstream. *(inferred)*
- **Spring Framework, Spring Boot, Hibernate, GORM datastore implementations.** Triaged in their own projects; the framework re-exposes their public APIs but does not own their threat models. *(inferred)*
- **Third-party Grails plugins** (anything not in this repository). Plugins run with full application privileges; their threat models are the responsibility of their authors. *(inferred)*
- **Generated application scaffolding output** (files emitted by `create-app` or `grails-forge`). Once written to the user's filesystem, the output is the user's code. The generator does not promise the generated code remains free of advisories as Grails evolves. *(inferred)*
- **`grails-test-examples/`, demo and reproducer modules**, and any `examples/` or scaffold output checked into the repository. Not shipped in framework distributions; threat-model separately if used as a starting point. *(inferred)*
- **Interactive consoles (`grails-console` module: `GrailsShell`, `GroovyConsole`).** These provide arbitrary Groovy code execution with full application-context access **by design**. They are developer tools. Reachability of these consoles from a deployed application is a deployment-configuration finding against the operator, not a framework vulnerability. *(inferred)*
- **`grails-forge` HTTP service at start.grails.org**, including the `ZipCreateController` / `GitHubCreateController` endpoints. The Micronaut-based service is deployed independently, has its own operator, and is modeled separately. The Grails framework neither runs this service inside user applications nor inherits its risk. *(inferred)*
- **Build-time supply chain** (Gradle plugin portal, Maven Central, signing, reproducible builds, GitHub Action pinning). Important, but not threat-model content per the rubric §1. *(inferred)*
- **Side-channel attacks** (timing, cache, power, micro-architectural). No constant-time guarantees are made anywhere in the framework. *(inferred)*

---

## §4 Trust boundaries and data flow

The principal trust boundary modeled here is the **HTTP request boundary**: data crossing from an end user (untrusted) into a Grails application's controller layer. A secondary boundary is the **filesystem / environment boundary** at application startup, where configuration is loaded.

### Primary data flow (HTTP request → response)

```
[Untrusted end user]
        |
        v  (HTTP bytes; transport security is the operator's responsibility - §3)
[Embedded servlet container]                                  <-- boundary: not framework
        |
        v
[GrailsDispatcherServlet]                                     <-- HTTP request enters framework
        |
        v
[UrlMappingsHandlerMapping]  (regex/wildcard match against developer-authored UrlMappings DSL)
        |
        v
[GrailsInterceptorHandlerInterceptorAdapter]  (developer-authored interceptors)
        |
        v
[Controller action]  (developer-authored; trusted-code, untrusted-data)
        |     |
        |     +--> [GrailsWebDataBinder]  (request params/body -> domain object)  <-- HIGHEST-RISK SURFACE
        |
        v
[Domain services, GORM]  (developer-authored business logic; GORM escapes parameterized queries)
        |
        v
[View rendering: GSP / JSON / Markup]  (output encoding via codecs - §8)
        |
        v
[Embedded servlet container]
        |
        v
[End user]
```

The trust transition occurs at `GrailsDispatcherServlet`. Within the framework, **request parameters, headers, cookies, and request bodies are treated as attacker-controlled**. The output codec layer is the matching transition on the response side - data leaving the framework into a view template is escaped per the view's content type. *(documented: [grails-doc/src/en/guide/security/xssPrevention.adoc](./grails-doc/src/en/guide/security/xssPrevention.adoc), [grails-doc/src/en/guide/security/codecs.adoc](./grails-doc/src/en/guide/security/codecs.adoc))*

### Reachability preconditions per component family

A triager applies these tests before deciding a finding is in-model:

| Component family | Reachability precondition for a finding to be in-model |
|---|---|
| HTTP request ingress | Reachable from an HTTP request with no developer-authored guard preceding it. *(inferred)* |
| Data binding | Reachable via a controller action that calls `bindData()`, uses command objects, or accepts a domain class parameter, AND the bound fields are not declared `bindable: false`. *(inferred)* |
| View rendering | Reachable when developer-supplied model data flows into a GSP/JSON view AND output encoding is either disabled or bypassed via `${raw()}`. *(inferred)* |
| Codecs | Reachable from any call to an encoder API; the codec subsystem itself is a control, not a sink. *(inferred)* |
| GORM mapping integration | Reachable from a query path that accepts attacker-controlled values. HQL string concatenation, dynamic finder property names sourced from `params`, and `where`-DSL closures dynamically built from input are the canonical risk paths. *(documented: [securingAgainstAttacks.adoc](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc) "SQL injection")* |
| Configuration loading | Reachable only if the attacker controls `grails.config.locations`, `GRAILS_CONFIG_LOCATIONS`, or the application classpath. **All three require local privilege at or above the application process** - therefore out of model in the default web-app scenario. *(inferred)* |
| Plugin / artefact discovery | Reachable only via a malicious JAR on the classpath. Same precondition as above - **classpath compromise is out of model**. *(inferred)* |
| Compile-time AST transforms | Reachable only at build time, with developer-controlled source files. Not reachable from an end user. *(inferred)* |
| CLI / shell (`grails-shell-cli`) | Reachable by the developer running the CLI on their local machine. End users do not invoke the CLI; if they can, the host is already compromised. *(inferred)* |

The reachability column for **configuration loading** and **plugin discovery** is the load-bearing claim of this section: the framework's most powerful capabilities (Groovy DSL evaluation, classloader injection) are guarded by "code on the classpath is trusted." A report against these surfaces that does not first demonstrate untrusted control of the classpath or `grails.config.locations` is `OUT-OF-MODEL: trusted-input`. See §13.

---

## §5 Assumptions about the environment

**Runtime**:
- JDK 21 or higher. *(documented: [AGENTS.md](./AGENTS.md))*
- Apache Groovy 4.0.x. *(documented: [AGENTS.md](./AGENTS.md))*
- Spring Boot 4.0.x / Spring Framework 7.0.x. *(documented: [AGENTS.md](./AGENTS.md))*
- Jakarta EE 10 servlet API (`jakarta.*`, not `javax.*`). *(documented: [AGENTS.md](./AGENTS.md))*

**Operator-controlled environment**:
- The application is deployed by a trusted operator on hardware and OS the operator controls. *(inferred)*
- The embedded servlet container is fronted by, or itself provides, transport security (TLS). *(inferred)*
- The application classpath contains only artifacts the operator/developer chose - no untrusted JAR is loaded at runtime. *(inferred)*
- Environment variables and system properties consulted at startup (`GRAILS_ENV`, `grails.env`, `grails.config.locations`, and the standard Spring Boot set) reflect operator intent. *(inferred)*

**Concurrency**:
- The framework assumes a thread-per-request servlet model. Reactive (`WebFlux`) deployment is supported by Spring Boot but is not the modeled default. *(inferred)*
- `GrailsWebRequest.lookup()` provides thread-local access to the active request; the framework assumes one logical request per thread. *(documented: [AGENTS.md](./AGENTS.md))*

### What the framework does NOT do to its host

These are **negative claims** about the framework's behavior. By the rubric they are the lowest-confidence claims in the model and the highest-priority targets for maintainer confirmation (see §14).

- The framework does not bind sockets directly. All network listening is delegated to the Spring Boot embedded container, configured by the operator. *(inferred)*
- The framework does not spawn child processes from the runtime layer. The CLI (`grails-shell-cli`) does spawn build tools (Gradle) - but only when invoked by a developer at the terminal. *(inferred)*
- The framework does not install JVM signal handlers. *(inferred)*
- The framework does not read environment variables beyond `GRAILS_ENV` / `grails.env` / `grails.config.locations` and the Spring Boot standard set. *(inferred)*
- The framework does not mutate global JVM state (default `Locale`, default `TimeZone`, system properties) at runtime. It does mutate `Locale` and `TimeZone` for the duration of a request via `GrailsWebRequest` for i18n - this is request-scoped, not process-wide. *(inferred)*
- The framework does not write to stdout or stderr at runtime beyond SLF4J-routed logging. *(inferred)*
- The framework does not load classes from network locations at runtime. (The CLI does, via `MavenProfileRepository`, but that is the developer surface - see §6 and §7.) *(inferred)*

---

## §5a Build-time and configuration variants

The framework exposes a small number of configuration knobs whose value affects which security properties hold. Defaults are listed; "Maintainer stance" is a §14 target.

| Knob | Default | Effect on the model | Maintainer stance |
|---|---|---|---|
| `grails.databinding.autoGrowCollectionLimit` | 256 *(documented: [`SimpleDataBinder.groovy`](./grails-databinding-core/src/main/groovy/grails/databinding/SimpleDataBinder.groovy))* | Caps automatic collection growth during data binding - hard limit on memory amplification from an attacker submitting deeply indexed parameters (`list[1000000]=x`). Raising removes the cap. | **§14 wave 2** - is the documented default the supported production posture, or is the operator expected to lower it? |
| `grails.databinding.dateFormats` / `dateParsingLenient` | RFC-3339 + locale defaults; lenient parsing on | Affects how strict date binding is. Loose parsing has historically been a source of validation-bypass findings in other frameworks. | **§14 wave 2** |
| `grails.views.default.codec` and codec defaults (`grails.views.gsp.codecs.expression`, `scriptlet`, `taglib`, `staticparts`) | `html` for expression / scriptlet contexts (XSS protection on by default) | Setting any of these to `none` **disables automatic output encoding** for that context - immediate `OUT-OF-MODEL: non-default-build` for XSS reports under non-default settings. *(documented: [xssPrevention.adoc](./grails-doc/src/en/guide/security/xssPrevention.adoc), [codecs.adoc](./grails-doc/src/en/guide/security/codecs.adoc))* | **§14 wave 1** - confirm the `html` default is the supported production posture. |
| `grails.controllers.upload.maxFileSize` / `maxRequestSize` | **128000 bytes (~125 KB) each**, set by `ControllersAutoConfiguration` (overrides Spring Boot's `MultipartProperties` defaults). *(documented: [`ControllersAutoConfiguration.java`](./grails-controllers/src/main/groovy/org/grails/plugins/web/controllers/ControllersAutoConfiguration.java))* | Multipart upload size cap. Operators who raise these past their application's actual need expose themselves to DoS via large multipart bodies. | **§14 wave 2** |
| `grails.allowedMethods` (per-controller) | None (developer opt-in) | Restricts HTTP methods accepted by each action. Absence is **not** a finding; the model treats per-action method gating as a developer responsibility. *(inferred)* | **§14 wave 1** |
| `grails.config.locations` (env var, system property, or config) | Empty | Adds external config file paths. **A non-empty value sourced from an untrusted location is a `BY-DESIGN: property-disclaimed` triage outcome** - see §9. | **§14 wave 1** - confirm this disposition. |
| `GRAILS_ENV` / `grails.env` | `development` from CLI, `production` for assembled bootJars | Selects the active environment block in `application.yml` / `application.groovy`. Operators who deploy with `GRAILS_ENV=development` inherit the looser dev defaults (e.g., stack traces in responses). | **§14 wave 1** - is deploying with `development` a `non-default-build` posture? |
| `grails.serverURL` | Operator-set; required for absolute link generation outside a request | Used by `createLink` and similar tag helpers when no request scope exists. Misconfiguration is a phishing/open-redirect adjacent issue but is a deployment-config concern, not a framework bug. *(inferred)* | **§14 wave 2** |

There is **no compile-time `-D` define or build flag** that voids a §8 property; the model is invariant under build configuration. *(inferred)* (§14 wave 3)

---

## §6 Assumptions about inputs

The framework's public input boundary is the HTTP request. Per-parameter trust is summarized below. The table is intentionally framework-level; per-application controllers, services, and domain classes are out of scope (developer-authored).

### Per-parameter trust table

| Entry point / surface | Parameter | Attacker-controllable? | Caller (developer) must enforce |
|---|---|---|---|
| `Controller.params` | All values | **Yes** - direct request parameter map | Type coercion correctness; never concatenate into HQL/SQL/JPQL/Groovy strings; never use as redirect target without an allow-list. *(documented: [securingAgainstAttacks.adoc](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc) "XSS", "HTML/URL injection")* |
| `Controller.request.headers` | All values | **Yes** - including `X-Forwarded-*`, `Host`, `User-Agent`, `Referer`, custom auth tokens | Treat presence as evidence of nothing; auth headers must be verified against the configured auth subsystem (Spring Security or equivalent). *(inferred)* |
| `Controller.request.cookies` | All values | **Yes** | Treat as attacker-supplied; if used for auth, integrity-protect via Spring Security or signed cookies. *(inferred)* |
| `Controller.request.JSON` / `XML` | Full body | **Yes** | Parser inputs are bounded by `maxRequestSize`; nested-depth limits are the parser's responsibility (Jackson, JAXP). *(inferred)* |
| `bindData(target, source)` | `source` (any `Map` or request) | **Yes** for the source; **No** for the target type (developer-controlled) | Use `bindable`/`include`/`exclude` to whitelist fields. The framework will bind every settable property of `target` from matching keys in `source` unless told otherwise. *(documented: [GORM data binding guide](https://grails.apache.org/docs/latest/guide/single.html#dataBinding))* |
| Command-object binding (auto-bound controller action parameter) | Field values | **Yes** | Annotate command-object fields with `bindable=false` for fields that must not be set from the request. *(inferred)* |
| Domain-class binding (`new Book(params)`, `book.properties = params`) | Field values | **Yes** | **Mass-assignment risk.** Use command objects or explicit allow-lists rather than binding the request to a domain class. *(inferred)* |
| `MultipartFile` upload | `bytes`, `originalFilename`, `contentType` | **Yes** | Never use `originalFilename` as a filesystem path without sanitization; verify `contentType` server-side against the actual byte content. *(inferred)* |
| URL mapping path variables | Pattern captures (`/$id`, `/$controller/$action/$id`) | **Yes** | Validate type and range; `params.id` is an arbitrary string until validated. *(documented: [securingAgainstAttacks.adoc](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc) "Guessable IDs")* |
| GSP expression `${...}` (default mode) | Model value | **Yes** | Default codec encodes for HTML; explicit `${raw(x)}` opt-outs are a developer assertion that `x` is trusted. *(documented: [xssPrevention.adoc](./grails-doc/src/en/guide/security/xssPrevention.adoc))* |
| `grails.config.locations` (env / system property) | File path | **No - trusted operator input** | Operators must not source this value from end-user input. See §9 false-friend on Groovy config evaluation. *(inferred)* |
| `application.groovy` / `application.yml` contents | All keys | **No - trusted operator input** | `application.groovy` is evaluated as Groovy; treat it like source code, not configuration. *(inferred)* |
| Domain `mapping {}` / `constraints {}` closure | Closure body | **No - trusted developer input** | Evaluated at startup via `HibernateMappingBuilder.evaluate()` and `ConstraintsEvaluator`. Developer-authored code. *(inferred)* |
| AST transform inputs (`@Resource`, `@Validateable`, etc.) | Annotated source | **No - trusted developer input** | Run at compile time on developer source. *(inferred)* |
| `MavenProfileRepository` (CLI) | Profile coordinates | **Trusted developer input by default; trusted-third-party if a custom repo is configured** | The CLI downloads profile JARs and executes their `.groovy` command scripts (`GroovyScriptCommand`). Treat profile sources with the same trust as any other build-time dependency. See §11. *(inferred)* |

### Size, shape, rate assumptions

- Request body size: bounded by the embedded container / Spring Boot multipart settings, not by the framework itself. *(inferred)*
- Number of bound collection entries during data binding: bounded by `grails.databinding.autoGrowCollectionLimit` (default 256). *(documented: [`SimpleDataBinder.groovy`](./grails-databinding-core/src/main/groovy/grails/databinding/SimpleDataBinder.groovy))*
- URL mapping pattern complexity: developer-authored; the framework does not enforce a complexity ceiling on regex / wildcard patterns. ReDoS risk is therefore left to the developer. See §11. *(inferred)*
- Request rate: no built-in rate limiting; operators must add it at the proxy or via Spring Security / bucket4j. *(inferred)*

---

## §7 Adversary model

### In-scope adversary: the HTTP end user

The principal adversary is a remote, unauthenticated HTTP client. Capabilities:

- Crafts arbitrary HTTP requests against any URL pattern the application exposes.
- Sends arbitrary headers, cookies, query parameters, form bodies, JSON bodies, XML bodies, and multipart uploads up to operator-configured size limits.
- May replay or modify requests sourced from authenticated sessions in a CSRF context. *(documented: [securingAgainstAttacks.adoc](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc) "Cross-site request forgery")*
- May enumerate URL spaces, including guessable `id` values. *(documented: [securingAgainstAttacks.adoc](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc) "Guessable IDs")*

What this adversary does **not** have:

- Read or write access to the application filesystem, classpath, environment variables, or system properties.
- Ability to inject classes into the classpath or modify `application.groovy` / `application.yml`.
- Co-location on the same JVM as the application.
- Side-channel observation (timing, cache, power).
- Ability to compromise the JDK, Spring, Hibernate, or any third-party plugin.

**Adversary goal**: cause the framework to violate one of the properties in §8 - typically by injecting code (XSS, HQL injection, Groovy injection), exfiltrating data the developer did not authorize disclosure of, or exhausting resources (DoS).

### Documented adversary-model statements

Two load-bearing trust statements. The first is the framework's stated position on end-user trust. The second is the model's stated position on classpath trust - it discharges the largest single class of inbound reports.

> "You must assume that every unprotected URL is publicly accessible one way or another." *(documented: [securingAgainstAttacks.adoc](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc))*

> **Plugin code, `application.groovy`, plugin `doWithSpring` closures, AST transforms, and every JAR on the application classpath are fully trusted and execute with the privileges of the application process. The framework provides no sandbox, no isolation, and no verification of provenance beyond what Maven Resolver gives the CLI.** *(inferred)* (§14 wave 1)

A report that depends on hostile control of any of those surfaces is `OUT-OF-MODEL: trusted-input` (§13). A report that depends on a hostile HTTP client is in-model.

### Distributed-system adversary

Not applicable - Apache Grails is a single-process web framework with no consensus protocol, no replication, and no honest-fraction threshold. *(inferred)*

### Out-of-scope adversaries

- **Local attacker with shell access on the application host.** Such an attacker can modify config, JARs, env vars, and the JVM itself. The framework cannot defend against them and does not try. *(inferred)*
- **Compromised plugin / JAR on the application classpath.** Plugins run with full privileges by design. *(inferred)*
- **Compromised build environment.** Out of model per §3.
- **Co-tenant attacker on the same JVM (e.g., another application in a shared servlet container).** The framework does not assume process-level isolation between deployed applications. The model assumes one application per JVM. *(inferred)*
- **Attacker who controls a Grails plugin or `grails` profile downloaded by a developer running the CLI.** This is the supply-chain surface around `MavenProfileRepository`; see §11. The framework does not verify profile JAR signatures beyond what Maven Resolver provides. *(inferred)*
- **Attacker with the ability to make a developer execute the `GrailsShell` or `GroovyConsole`.** Out of scope - these are unrestricted code-execution tools by design. *(inferred)*
- **Side-channel observers** (timing, cache, micro-architectural). *(inferred)*

---

## §8 Security properties the project provides

Each property is stated with its conditions, the symptom of a violation, a severity tier, and a provenance tag.

| # | Property | CWE | Conditions | Violation symptom | Severity | Provenance |
|---|---|---|---|---|---|---|
| P1 | **Parameterized GORM queries are SQL/HQL-injection safe.** | [CWE-89](https://cwe.mitre.org/data/definitions/89.html) | Caller uses named or positional parameters (or criteria DSL / `where` DSL with bound values), not string concatenation into HQL/SQL. | Reflected data appears unescaped in the executed SQL; attacker-controlled boolean predicates evaluate. | **Security-critical (CVE-eligible)** | *(documented: [securingAgainstAttacks.adoc](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc) "SQL injection", [grails-doc/src/en/guide/security.adoc](./grails-doc/src/en/guide/security.adoc))* |
| P2 | **Default GSP `${...}` expression output is HTML-encoded.** | [CWE-79](https://cwe.mitre.org/data/definitions/79.html) | View runs with the default codec configuration (`expression=html`); developer has not explicitly opted into `raw()` or set the codec to `none`. | Reflected XSS via a value rendered through `${userInput}` without `raw()`. | **Security-critical (CVE-eligible)** | *(documented: [xssPrevention.adoc](./grails-doc/src/en/guide/security/xssPrevention.adoc), [grails-doc/src/en/guide/security.adoc](./grails-doc/src/en/guide/security.adoc))* |
| P3 | **Grails-supplied link / form / `createLink` / `createLinkTo` tag helpers URL-encode their parameter values.** | [CWE-601](https://cwe.mitre.org/data/definitions/601.html) | Caller uses the supplied tag library rather than hand-built strings. | Reflected URL/header injection via a value passed to `<g:link params="...">` or equivalent that is not encoded. | **Security-critical (CVE-eligible)** | *(documented: [grails-doc/src/en/guide/security.adoc](./grails-doc/src/en/guide/security.adoc), [securingAgainstAttacks.adoc](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc) "HTML/URL injection")* |
| P4 | **`HTMLCodec`, `URLCodec`, `JavaScriptCodec`, `Base64Codec`, and related codecs encode for their stated context.** | [CWE-116](https://cwe.mitre.org/data/definitions/116.html) | Codec is invoked at the boundary between trusted source and the named output context. | Output does not match the documented encoding for the context (e.g., `encodeAsHTML` fails to escape `<`). | **Security-critical (CVE-eligible)** | *(documented: [codecs.adoc](./grails-doc/src/en/guide/security/codecs.adoc))* |
| P5 | **`useToken` (synchronizer token) on a form blocks naive CSRF and double-submit.** | [CWE-352](https://cwe.mitre.org/data/definitions/352.html) | Form is rendered with `useToken="true"` and the receiving action uses `withForm { }`. | Token validation accepts a missing, reused, or attacker-supplied token. | **Security-critical (CVE-eligible)** | *(documented: [securingAgainstAttacks.adoc](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc) "Cross-site request forgery")* |
| P6 | **Data binding respects `bindable=false` and explicit `include`/`exclude` lists.** | [CWE-915](https://cwe.mitre.org/data/definitions/915.html) | Field is annotated or the binding call explicitly lists allowed/forbidden fields. | A field marked unbindable is set from request input. | **Security-critical (CVE-eligible)** | *(inferred)* |
| P7 | **Compile-time AST transforms (`@Resource`, `@Validateable`, etc.) only act on developer-authored source.** | [CWE-94](https://cwe.mitre.org/data/definitions/94.html) | Build runs on developer-controlled source. | A transform fires on or is influenced by attacker-supplied input. | **Correctness** (security-critical only if reachable from a non-build attacker) | *(inferred)* |
| P8 | **Configuration loading does not evaluate `application.groovy` from a path the framework itself chose at runtime - paths come from build-time classpath and operator-supplied environment/system properties.** | [CWE-94](https://cwe.mitre.org/data/definitions/94.html) | Operator has not pointed `grails.config.locations` at attacker-writable storage. | A user request causes evaluation of a Groovy file the operator did not authorize. | **Security-critical (CVE-eligible)** if violated. | *(inferred)* (§14 wave 1) |
| P9 | **`maxFileSize` / `maxRequestSize` / `autoGrowCollectionLimit` provide bounded data-binding memory.** | [CWE-770](https://cwe.mitre.org/data/definitions/770.html) | Operator does not raise the limits past application needs. | Memory growth proportional to attacker-controlled input regardless of limit. | **Resource bug** | *(inferred)* (§14 wave 2) |

### Resource consumption line

The model takes the position that:

- Hangs or unbounded memory growth driven by **bounded** request input (i.e., input that fits under the operator-configured size limits) **is a bug**. *(inferred)* (§14 wave 2)
- Super-linear CPU in **unbounded** developer-authored regex (URL mapping patterns, constraint regexes) **is not a framework bug** - ReDoS surfaced through developer-supplied patterns is a developer responsibility. *(inferred)*
- "Slow but completes" is not a framework bug at any input size. *(inferred)* (§14 wave 2)

---

## §9 Security properties the project does NOT provide

These properties are **disclaimed by design**. A report that depends on one of them is a `BY-DESIGN: property-disclaimed` triage outcome (see §13).

- **Authentication, authorization, session management, password storage.** The framework ships no built-in user store. Delegated to Spring Security plugin or equivalent. *(documented: [authentication.adoc](./grails-doc/src/en/guide/security/authentication.adoc), [securityPlugins.adoc](./grails-doc/src/en/guide/security/securityPlugins.adoc))*
- **CSRF protection on requests not protected by `useToken`.** `useToken` is opt-in per form. Actions that mutate state without a synchronizer token are not CSRF-protected by the framework. *(documented: [securingAgainstAttacks.adoc](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc))*
- **XSS protection for templates rendered with the codec disabled (`grails.views.default.codec=none`) or values explicitly passed through `raw()`.** Opt-out is the developer's stated intent. *(documented: [xssPrevention.adoc](./grails-doc/src/en/guide/security/xssPrevention.adoc), [codecs.adoc](./grails-doc/src/en/guide/security/codecs.adoc))*
- **SQL/HQL injection protection for queries built by string concatenation.** Only parameterized GORM forms are safe; `Book.find("from Book where title = '${params.title}'")` is documented as unsafe. *(documented: [securingAgainstAttacks.adoc](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc))*
- **Authorization for individual domain-object access ("guessable IDs").** The framework does not check that the requesting user is permitted to view the object identified by `params.id`. The developer must enforce this. *(documented: [securingAgainstAttacks.adoc](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc) "Guessable IDs")*
- **Rate limiting / DoS protection against request volume.** No built-in throttling. *(documented: [securingAgainstAttacks.adoc](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc) "Denial of service")*
- **Constant-time cryptographic operations.** No constant-time comparison or secret-handling primitives are provided. *(inferred)*
- **Sandboxing of Groovy code in `application.groovy`, plugin `doWithSpring` blocks, or AST transforms.** Groovy source on the classpath is trusted, full stop. *(inferred)*
- **Verification of plugin or `grails-forge` profile JAR provenance.** The CLI uses standard Maven resolution; trust in the configured repository is operator-supplied. *(inferred)*
- **Cross-process / cross-tenant isolation.** One application per JVM is assumed. Sharing a JVM across mutually-untrusting apps is unsupported. *(inferred)*
- **Transport security.** Provided by Spring Boot / the operator's proxy. *(inferred)*

### False-friend properties (the highest-value section for integrators)

Features that **look like** a security property but are not one. Reports that confuse a false friend for the real thing are `KNOWN-NON-FINDING` (§11a) when documented and `BY-DESIGN: property-disclaimed` (§13) when not.

- **`@Secured` annotations are not a built-in framework guarantee.** `@Secured` is integration-shaped sugar for the Spring Security plugin. Without that plugin installed and correctly configured, the annotation is inert. *(inferred)*
- **`bindable=false` is neither an access-control control nor a validation constraint.** It prevents a field from being set from a request-binding source (`bindData`, command-object binding, domain-class constructor binding, `properties=` assignment). It does not prevent the field from being set programmatically, by GORM, or by a service layer; it does not prevent the field from being **read** and serialized into a response; and it does not validate the value once set. *(inferred)*
- **`useToken` is a synchronizer-token CSRF defense, not a transaction-integrity guarantee.** It blocks naive cross-site form submissions and accidental double-submits. It is not a replay-protection mechanism for an authenticated user against a future-token attacker. *(inferred)*
- **Domain-class `constraints { }` are input-validation rules, not security boundaries.** `nullable: false` and `matches: /…/` are correctness checks. A constraint failure is a validation error, not an attack signal. *(inferred)*
- **GSP comments (`<%-- … --%>`) strip from output; HTML comments (`<!-- … -->`) do not.** Putting secrets in HTML comments leaks them to clients. The framework does not strip HTML comments. *(inferred)*
- **`grails.serverURL` is for link generation, not an authoritative declaration of the deployment URL for security purposes.** Setting it does not bind the application to that origin; the embedded container still serves whatever the operator binds it to. *(inferred)*
- **`GRAILS_ENV=development` is not a security boundary.** Stack traces, verbose error pages, and dev-tool endpoints surfaced in `development` mode are a deployment-configuration symptom, not a framework vulnerability. A report that requires `GRAILS_ENV=development` to reproduce is `OUT-OF-MODEL: non-default-build` (§13), not `VALID`. *(inferred)* (§14 wave 1)
- **`grails.config.locations` is a Groovy code-execution path, not a configuration-file path.** Any file the application process can read AND an attacker can write to is equivalent to classpath compromise: the `.groovy` form is evaluated via `ConfigSlurper`. A path that looks like "just config" but lives in attacker-writable storage (e.g. an S3 bucket without write controls, a world-writable `/tmp` derivative, a CI artifact directory) is `BY-DESIGN: property-disclaimed` (§13). *(inferred)* (§14 wave 1)

### Well-known attack classes against this category of project that the framework does not defend against

One sentence per class.

- **Mass assignment.** Binding the request map directly to a domain class without `bindable`/allow-lists. *(inferred)*
- **Open redirect.** Using a request parameter as a `redirect(url: params.next)` target. *(documented: [securingAgainstAttacks.adoc](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc) "XSS - cross-site scripting injection" mentions this in the `successURL` example)*
- **Server-Side Request Forgery (SSRF).** No built-in URL-fetch allow-list. *(inferred)*
- **XXE in XML data binding.** XML parsing is delegated to the underlying parser; the framework does not impose a parser configuration. *(inferred)*
- **ReDoS in developer-authored URL mappings and constraint regexes.** No complexity ceiling. *(inferred)*
- **Zip-bomb / archive expansion** in multipart and `grails-forge` ZIP generation. Bounded only by container size limits. *(inferred)*
- **Path traversal** through `MultipartFile.originalFilename` if used as a filesystem path. *(inferred)*
- **JSON / XML DoS** (deeply nested or large primitives) - the parser's responsibility, not the framework's. *(inferred)*
- **Session fixation, replay, sidejacking.** Session security is the auth plugin's responsibility. *(inferred)*
- **Cache-key / cache-poisoning attacks** in Spring Boot caching layers. *(inferred)*

---

## §10 Downstream responsibilities

For the assumptions in §5-§7 to hold, the **application developer / operator** must:

1. Use a real authentication and authorization layer (Spring Security plugin or equivalent). The framework provides no replacement. *(documented: [authentication.adoc](./grails-doc/src/en/guide/security/authentication.adoc))*
2. Place TLS termination in front of the application (operator) or configure the embedded container's HTTPS connector. *(inferred)*
3. Never concatenate request parameters into HQL/SQL strings, Groovy `Eval`/`GroovyShell` calls, redirect targets, or shell commands. Use parameterized GORM queries, `withForm { }`, allow-lists. *(documented: [securingAgainstAttacks.adoc](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc))*
4. Bind request data through command objects or explicit allow-lists - not by passing `params` directly into a domain-class constructor or property setter. *(inferred)*
5. Authorize every domain-object access by ID. Never rely on guessable IDs as an access control. *(documented: [securingAgainstAttacks.adoc](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc) "Guessable IDs")*
6. Apply `useToken` (or equivalent CSRF protection from the auth plugin) on every state-mutating form. *(documented: [securingAgainstAttacks.adoc](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc))*
7. Keep `grails.views.default.codec` and the GSP context codecs at their HTML-encoding defaults. *(documented: [xssPrevention.adoc](./grails-doc/src/en/guide/security/xssPrevention.adoc))*
8. Sanitize and bound any request parameter used to size a query result (`max`, `offset`, `firstResult`). *(documented: [securingAgainstAttacks.adoc](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc) "Denial of service")*
9. Cap multipart upload size at the application boundary; cap request rate at the proxy. *(inferred)*
10. Treat `application.groovy`, plugin `doWithSpring` blocks, and any code on the classpath as part of the application's TCB. Do not source `grails.config.locations` from user input or attacker-writable storage. *(inferred)*
11. Validate `MultipartFile.originalFilename` and `contentType` before using them; never use the original filename as a filesystem path. *(inferred)*
12. Deploy with `GRAILS_ENV=production` (or equivalent) - the development environment loosens stack-trace exposure and dev-tools behavior. *(inferred)* (§14 wave 1)

---

## §11 Known misuse patterns

In-the-wild patterns the API permits but that violate the assumptions in §5-§7.

- **Mass-assignment via `new DomainClass(params)` or `domainInstance.properties = params`.** Looks ergonomic. Lets the request set fields the developer never intended - including `id`, `version`, role/permission booleans, and association references. *Fix*: use command objects, or annotate sensitive fields `bindable=false`, or pass an explicit allow-list to `bindData`. *(inferred)*
- **HQL/SQL string concatenation in `Book.find("…${params.x}…")` or `executeQuery("… ${userInput}")`.** Looks like ordinary GString interpolation. Lets the attacker change the query's boolean logic, exfiltrate unrelated rows, fingerprint the schema, or in some dialects DROP tables. *Fix*: named or positional parameters - `Book.find("from Book b where b.title = :title", [title: params.title])`. *(documented: [securingAgainstAttacks.adoc](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc) "SQL injection")*
- **Using a request parameter as a redirect target (`redirect(url: params.next)` or `successURL`-style flows).** Documented in the XSS section as the open-redirect / phishing pivot pattern. *Fix*: allow-list of permitted redirect targets. *(documented: [securingAgainstAttacks.adoc](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc))*
- **`${raw(userInput)}` in a GSP** to "fix" double-encoding when the developer is actually disabling XSS protection on attacker-controlled data. *Fix*: keep the codec enabled and stop double-encoding upstream. *(inferred)*
- **Disabling the default codec globally** (`grails.views.default.codec=none`) to make legacy templates render unchanged. *Fix*: keep the default and migrate templates individually. *(documented: [xssPrevention.adoc](./grails-doc/src/en/guide/security/xssPrevention.adoc), [codecs.adoc](./grails-doc/src/en/guide/security/codecs.adoc))*
- **Using `grails.config.locations` to load a writable file** (e.g., `/var/tmp/app-config.groovy`, an S3-backed user-config blob). The file is **evaluated as Groovy**, which means whoever can write it can run arbitrary code in the application process. *Fix*: source `grails.config.locations` only from a trusted operator-controlled path; prefer `application.yml` over `application.groovy` when the values are user-influenced. *(inferred)*
- **Exposing `GrailsShell`, `GroovyConsole`, or any Spring Boot Actuator endpoint that evaluates Groovy** on a network-reachable interface. These modules provide unrestricted code execution by design (§3). Reaching them from an HTTP listener is the operator's mistake, but the application's the one that loses. *Fix*: bind those endpoints to localhost or disable them in production; do not configure the embedded container to expose `grails-console` artefacts.
- **Treating constraint validation as authorization.** A constraint passing does not mean the user is permitted to perform the operation. A `nullable: false` field will still accept an attacker-supplied value if the attacker is authenticated and the action allows them to submit it. *Fix*: keep authz separate from validation; run authz checks at the action entry, validation at the binding boundary, and never confuse "validates" with "authorized". *(inferred)*
- **Liquibase Groovy change-log files loaded from a path the developer does not fully control.** `GroovyChangeLogParser` in `grails-data-hibernate5/dbmigration` evaluates change-log Groovy via `GroovyShell.parse(...).run()`. If the change-log location is influenced by a CI/CD parameter, an artifact registry, or any externally-writable path, this is full Groovy code execution at application startup. *Fix*: keep migration files in the application's own source tree under version control; never source change-log paths from runtime input. *(inferred)*
- **Putting secrets in HTML comments inside GSP templates.** They reach the client. *Fix*: use GSP comments (`<%-- … --%>`) or omit. *(inferred)*
- **Using `params.id` directly in a GORM `get(id)` call without an authorization check.** The "guessable IDs" pattern. Returns objects the requesting user has no business viewing or mutating. *Fix*: authorize the loaded object against the authenticated principal before returning, rendering, or mutating it - via the Spring Security plugin's ACL support, a per-action interceptor, or an explicit `if (book.owner != currentUser) { notFound(); return }`. *(documented: [securingAgainstAttacks.adoc](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc) "Guessable IDs")*
- **Trusting `MultipartFile.originalFilename`** as a destination filename. The value is attacker-controlled and may include `../`, NUL bytes, or shell metacharacters. *Fix*: generate server-side filenames; never let user data join a filesystem path. *(inferred)*


---

## §11a Known non-findings (recurring false positives)

The mirror of §11: patterns that scanners, fuzzers, AI analyzers, or human reviewers repeatedly flag against this project that **are not bugs given the model**. Feed this section to suppression configurations.

- **"Dynamic class loading in `ClassPathScanner.classLoader.loadClass(name)`"** - SAST flags reflective class loading on apparently-attacker-controlled data. Discharged by the §6 trust assumption on classpath input ("application classpath contains only artifacts the operator/developer chose"). `name` originates from the build classpath, not from an HTTP request. → `OUT-OF-MODEL: trusted-input`.
- **"Reflective `Class.forName` / `loadClass` in `ApplicationClassInjector` and AST infrastructure, and in `DatabaseMigrationCommand` (`passwordEncryptionCodec`) / `ConfigSupport` (`source`) / `ExecuteStep` (profile YAML `className`)"** - SAST flags the same reflective pattern across multiple modules. Discharged by the §6 trust assumption on `application.yml` / `application.groovy` / profile-YAML inputs ("trusted operator input"). Class names come from configuration files, not request data. → `OUT-OF-MODEL: trusted-input`.
- **"Groovy `evaluate()` / `ConfigSlurper` in `GroovyConfigPropertySourceLoader`, `ExternalConfigRunListener`, `HibernateMappingBuilder`, and `GroovyChangeLogParser`"** - SAST flags a code-injection sink. Discharged by the §6 trust assumption on `application.groovy`, domain `mapping {}` closures, external config locations, and migration files (all trusted developer/operator inputs). See the §9 Groovy-sandboxing disclaimer and §11 misuse patterns when the assumption does not hold. → `OUT-OF-MODEL: trusted-input` (or `BY-DESIGN: property-disclaimed` if the assumption is violated).
- **"`GroovyShell` instantiation in `grails-console` (`GrailsShell`, `GroovyConsoleApplicationContext`) and `GrailsApplicationScriptRunner`"** - SAST flags arbitrary code execution. These are developer-only tools that take a `File` argument from the CLI, by design (§3). → `OUT-OF-MODEL: unsupported-component`.
- **"Every `@GroovyASTTransformation` implementation in framework JARs"** - SAST flags AST-rewriting code as code-injection surface. Discharged by §8 P7 ("AST transforms only act on developer-authored source") and the §6 trust assumption on annotated source. The set on 8.0.x at the time of this revision is the union of: `Cache{Put,Evict,able}Transformation`, `TransactionalTransform`, `RollbackTransform`, `OrderedGormTransformation`, `TenantTransform`, `ServiceTransformation`, `GormEntityTransformation`, `JpaGormEntityTransformation`, `GlobalJpaEntityTransform`, `DirtyCheckTransformation`, `SubscriberTransform`, `PublisherTransform`, `MarkupViewsTransform`, `JsonViewsTransform`, `ViewsTransform`, `ResourceTransform`, `LinkableTransform`, `GlobalGrailsClassInjectorTransformation`, `GlobalImportTransformation`, `EnhancesTraitTransformation`, `HibernateEntityTransformation`, `RxScheduleIOTransformation`, `GroovyScriptCommandTransform`. Future additions inherit the same disposition unless they read attacker-controllable input. → `OUT-OF-MODEL: trusted-input`.
- **"Mass-assignment warning on domain-class binding"** when the developer has applied `bindable=false` or used a command object - the SAST heuristic does not see the allow-list. Discharged by §8 P6 ("data binding respects `bindable=false`"). Verify the allow-list exists; if it does, suppress. → `KNOWN-NON-FINDING`.
- **"GSP `${raw(x)}` is unsafe"** when `x` is a trusted server-side value (e.g. a model attribute populated from a query, never touched by request input). Discharged by §6 (the source of `x` is not a request parameter). Verify by trace; if confirmed trusted, suppress. → `KNOWN-NON-FINDING`.
- **"`SimpleDataBinder` uses reflection on user-class field types"** - SAST flags type-confusion risk. Discharged by §6 (the target class is developer-authored; reflection runs on the target, not on attacker-supplied class metadata). → `OUT-OF-MODEL: trusted-input`.
- **"`SpelExpressionParser` in `GroovyEclipseCompilationHelper`"** - SAST flags SpEL injection. The only SpEL usage in the framework is inside the Eclipse IDE compiler helper; the expression is partially-developer-controlled (Eclipse project settings) and never reachable from an HTTP request. → `OUT-OF-MODEL: unsupported-component` (compile-time, not runtime).
- **"`BeforeValidateHelper` implements `Serializable` with a custom `readObject` method"** ([`BeforeValidateHelper.java`](./grails-datamapping-validation/src/main/groovy/org/grails/datastore/gorm/support/BeforeValidateHelper.java)) - SAST flags a Java-deserialization gadget candidate. Discharged by §6 (the framework never deserializes an `ObjectInputStream` from an attacker-reachable channel). **In-model only if** the application stores `BeforeValidateHelper` instances in an attacker-reachable serialized container (HTTP session, distributed cache, untrusted message bus); that is the application's responsibility. Spot-check confirms no other custom `readObject` lives in the runtime tree, and no production code path calls `ObjectInputStream.readObject(...)` directly. → `OUT-OF-MODEL: trusted-input` for the framework itself; the application's session-store choice may pull it back in. (§14 wave 3 to confirm.)
- **"`SynchronizerTokensHolder` is `Serializable`"** - SAST flags the CSRF token-holder as session-store gadget. Discharged by §6 (the session store is operator-trusted infrastructure; framework does not hand out raw `ObjectInputStream` access). If the application uses a serializing session backend (e.g. Redis with JDK serialization) the integrity of `SynchronizerTokensHolder` reduces to the integrity of that store, which is the operator's responsibility. → `OUT-OF-MODEL: trusted-input`.
- **"`@Secured` annotation present but no authz logic in the annotated method body"** - false positive when the Spring Security plugin is installed (it wires the check externally to the method). Discharged by §9 (auth is delegated to Spring Security). → `KNOWN-NON-FINDING`.
- **"Framework-level logging reflects user input"** (e.g. `log.info "request: ${request}"`) - SAST flags log-injection / Log4Shell-adjacent patterns. Discharged by §6: the framework's own logging never serializes request data into a log line without explicit developer code. Application code that does so is an application-layer bug. → `OUT-OF-MODEL: unsupported-component`.
- **"`new File(...)` with multipart-derived names"** - SAST flags path traversal. No production path-traversal sink was found in framework runtime code; the risk lives in user controller code calling `multipartFile.transferTo(new File(params.filename))`. Framework-internal `new File(...)` usages either use server-derived paths or are test infrastructure. Discharged by §6 (no in-framework sink reaches a request-influenced filename). → `OUT-OF-MODEL: unsupported-component` for framework code; the application's own controller code is the surface.
- **"Production `@RestController` / `@Controller` beans shipped in framework JARs"** - SAST flags the framework as exposing HTTP endpoints of its own. The framework ships **no** `@RestController` or `@Controller` beans in production JARs; every HTTP entry point is user-defined and either extends `RestfulController` or mixes in the `Controller` trait. Discharged by §2 (HTTP request ingress component family) and §6 (controllers are developer-authored). → `KNOWN-NON-FINDING`.

---

## §12 Conditions that would change this model

Revise this document on:

- A new HTTP entry-point family (e.g., introducing first-party WebSocket or gRPC handling into the framework core).
- A change in default for any §5a knob - particularly any reduction in the default codec set.
- Introduction of a built-in authentication, session, or CSRF subsystem (this would move items from §9 into §8).
- A change in how `application.groovy` is loaded (e.g., switching `ConfigSlurper` for a non-evaluating parser would invalidate §11's "config-as-code" item and the `BY-DESIGN` disposition for §9 Groovy-config disclaimers).
- Promotion of `grails-console` or any GroovyShell-backed endpoint into a default-enabled production surface (would move §3's "interactive consoles are out of scope" claim).
- A new minimum JDK or Groovy baseline (might unlock new constant-time / safer APIs that change §9 disclaimers).
- A vulnerability report that **cannot** be cleanly routed to one of the §13 dispositions - that is a `MODEL-GAP` and indicates this document is incomplete. Revise rather than ad-hoc the call.

---

## §13 Triage dispositions

Every report against the framework receives **exactly one** of the following dispositions. Each cites the section that licenses it. A finding that does not fit is `MODEL-GAP` and triggers a §12 revision, not an ad-hoc judgment.

| Disposition | Meaning | Licensed by |
|---|---|---|
| `VALID` | Violates a property the framework claims, via an in-scope adversary and input. | §8, §6, §7 |
| `VALID-HARDENING` | No §8 property is violated, but the API makes a §11 misuse easy enough that the project elects to harden it. Reported privately per [SECURITY.md](./SECURITY.md); fixed at maintainer discretion; typically no CVE. | §11 |
| `OUT-OF-MODEL: trusted-input` | Requires attacker control of a parameter the model marks trusted (classpath, `application.groovy`, `grails.config.locations`, domain `mapping {}` closures, AST transform inputs). | §6 |
| `OUT-OF-MODEL: adversary-not-in-scope` | Requires an attacker capability the model excludes (local shell, JVM co-tenant, side channel, compromised plugin). | §7 |
| `OUT-OF-MODEL: unsupported-component` | Lands in `grails-test-examples/`, scaffold output, `grails-console`, `grails-forge` HTTP service, or third-party plugins. | §3 |
| `OUT-OF-MODEL: non-default-build` | Only manifests under a discouraged or non-default §5a configuration (e.g., codec disabled, dev environment deployed to production, raised collection-growth limit). | §5a |
| `BY-DESIGN: property-disclaimed` | Concerns a property the framework explicitly does not provide (auth, CSRF without `useToken`, rate limiting, constant-time crypto, sandboxing of Groovy on classpath). | §9 |
| `KNOWN-NON-FINDING` | Matches a documented recurring false positive. | §11a |
| `MODEL-GAP` | Cannot be cleanly routed to any of the above. | triggers §12 |

---

## §14 Open questions for the maintainers

The model is **draft-first**. The questions below are grouped in waves of 3-7 per the rubric §3.2. Each is framed as a proposed answer for the PMC to confirm, correct, or strike. Once answered, promote the matching *(inferred)* tags to *(maintainer)* and delete the question.

### Wave 1 - scope and intended use (most-load-bearing answers; §2-§3 depend on these)

1. **Caller-role split.** *Proposed*: the three roles in §2 (untrusted end user, trusted developer/operator, trusted-by-association plugin author) are the correct primitives. Correct or extend? *Lands in §2.*
2. **Spring Security as the only intended auth path.** *Proposed*: the framework explicitly defers all authentication, authorization, and session management to Spring Security plugin (or equivalent third-party). There is no in-framework auth roadmap. Confirm or correct. *Lands in §3 and §9.*
3. **`grails-forge` HTTP service is out of model.** *Proposed*: start.grails.org is operated separately and its own operators carry its threat model. Confirm. *Lands in §3.*
4. **`grails-console` (`GrailsShell` / `GroovyConsole`) is a developer-only tool, never enabled by default in production.** *Proposed*: these modules are not autoconfigured into a production `bootJar`. Confirm. *Lands in §3 and §11a.*
5. **`grails.views.default.codec=html` is the supported production posture.** *Proposed*: a deployment with codec disabled is `OUT-OF-MODEL: non-default-build`, not a `VALID` XSS finding. Confirm. *Lands in §5a and §13.*
6. **`grails.config.locations` is operator-trusted input.** *Proposed*: a report demonstrating Groovy execution via a value the operator pointed `grails.config.locations` at is `BY-DESIGN: property-disclaimed`. Confirm. *Lands in §6, §9, §11.*
7. **Deploying with `GRAILS_ENV=development`** is a `non-default-build` posture for triage purposes. *Proposed*: information disclosure that occurs only in `development` mode is closed as such, not as `VALID`. Confirm. *Lands in §5a and §13.*

### Wave 2 - trust boundaries and resource limits (§4-§6, §9)

8. **`autoGrowCollectionLimit` default of 256 is the supported posture.** *Proposed*: a DoS report that requires raising this past the default is `OUT-OF-MODEL: non-default-build`. Below the default, super-linear memory in input size is a bug. Confirm.
9. **Resource-consumption line.** *Proposed*: super-linear CPU or memory in bounded input is a bug; super-linear in unbounded developer-authored regex (URL mappings, constraint regexes) is a developer-responsibility issue. Confirm.
10. **`SimpleDataBinder` mass-assignment.** *Proposed*: binding `new Book(params)` without an allow-list is `VALID-HARDENING` (the framework should warn, not block, but documents should call this out more loudly). Or is it `BY-DESIGN: property-disclaimed`? Choose.
11. **Multipart limits.** *Proposed*: the framework does not impose multipart caps beyond Spring Boot's defaults; this is operator responsibility. Confirm.
12. **`bindable=false` semantics.** *Proposed*: `bindable=false` is enforced for all binding paths (`bindData`, command-object binding, domain-class constructor binding, `properties=`). Confirm coverage - is there any binding path that ignores it?
13. **XML data binding parser configuration.** *Proposed*: the framework does not impose XXE-hardening configuration on the XML parser; XXE in `XmlDataBindingSourceCreator` is the parser's threat model, not the framework's. Confirm.

### Wave 3 - misuse, false friends, and §11a curation

14. **`BeforeValidateHelper` serialization (§11a).** *Proposed*: this class is never stored in HTTP sessions or distributed caches by the framework itself. If an application does so, the framework does not own that risk. Confirm.
15. **`MavenProfileRepository` supply chain.** *Proposed*: the CLI relies on Maven Resolver's default signature/checksum behavior; the framework does not add an additional verification layer. A typosquat or repository compromise is operator/developer responsibility. Confirm.
16. **GSP `${raw()}` audit.** *Proposed*: the framework's own GSP files never call `raw()` on attacker-controllable data; SAST hits inside framework GSPs are `KNOWN-NON-FINDING`. Confirm with a spot-check.
17. **`Groovysh`-backed actuator endpoints.** *Proposed*: there is no Spring Boot actuator endpoint shipped by the framework that evaluates Groovy. Confirm there is no `/groovy` or similar.
18. **Open redirect via `redirect()`.** *Proposed*: `redirect(url: params.next)` is documented misuse in §11; the framework will not add an allow-list to `redirect()` itself. Confirm.
19. **The §13 disposition table is closed and complete.** *Proposed*: no additional Grails-specific disposition is needed beyond the rubric set. Confirm.

### Meta

20. **Document ownership.** *Proposed*: this file lives at the repo root, maintained by the PMC, revised per the §12 triggers. The next release branch should fork this document with its own version binding.
21. **Coexistence with SECURITY.md.** *Proposed*: `SECURITY.md` remains the disclosure-process artifact; this file is the model. `SECURITY.md` should add a single line cross-referencing this document.
22. **Coexistence with `grails-doc/src/en/guide/security/`.** *Proposed*: the user guide remains end-user-facing prose; this document is triage-facing. Where they overlap, this document cites the guide as the *(documented)* source rather than re-stating prose.

---

## §15 Machine-readable companion

A YAML sidecar at [`threat-model.yaml`](./threat-model.yaml) carries the triage-relevant facts in structured form, regenerated whenever this prose document changes. The prose document is canonical; the YAML is a derived index for automated triage tooling.

---

## Appendix A - back-map: existing documentation → threat-model section

This back-map proves §3.1a coverage. Every threat-model-shaped claim already in the repository's own documentation is reflected somewhere in this document.

| Existing claim (file:line) | Original wording (paraphrase) | This document |
|---|---|---|
| [`grails-doc/.../security.adoc`](./grails-doc/src/en/guide/security.adoc) | "Grails is no more or less secure than Java Servlets." | §5 (runtime assumptions), §3 (JDK/JVM out of scope) |
| [`grails-doc/.../security.adoc`](./grails-doc/src/en/guide/security.adoc) | GORM SQL escaping, GSP HTML escaping, link-tag URL escaping, codecs. | §8 P1, P2, P3, P4 |
| [`grails-doc/.../securingAgainstAttacks.adoc`](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc) "SQL injection" | HQL string concatenation is unsafe; use parameterized queries. | §8 P1, §9 disclaimer, §10 #3, §11 |
| [`grails-doc/.../securingAgainstAttacks.adoc`](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc) "XSS" | Default GSP encoding; avoid using request data as redirect target. | §8 P2, §9 false friend, §10 #7, §11 |
| [`grails-doc/.../securingAgainstAttacks.adoc`](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc) "CSRF" | Use `useToken` and `withForm { }` on state-mutating forms. | §8 P5, §9 disclaimer (without `useToken`), §10 #6 |
| [`grails-doc/.../securingAgainstAttacks.adoc`](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc) "Denial of service" | Sanitize `params.max` against query result sizes. | §9 disclaimer, §10 #8 |
| [`grails-doc/.../securingAgainstAttacks.adoc`](./grails-doc/src/en/guide/security/securingAgainstAttacks.adoc) "Guessable IDs" | Authorize each domain-object access; "every unprotected URL is publicly accessible." | §7 adversary statement, §9 disclaimer, §10 #5 |
| [`grails-doc/.../xssPrevention.adoc`](./grails-doc/src/en/guide/security/xssPrevention.adoc) | Default codecs; `${raw()}` is opt-out. | §8 P2, §5a codec knob, §9 false friend, §11 misuse |
| [`grails-doc/.../codecs.adoc`](./grails-doc/src/en/guide/security/codecs.adoc) | `HTMLCodec`, `URLCodec`, `JavaScriptCodec`, etc. | §8 P4, §5a codec defaults |
| [`grails-doc/.../authentication.adoc`](./grails-doc/src/en/guide/security/authentication.adoc) | Interceptor-based auth example; defers to Spring Security/Shiro. | §3 (auth out of scope), §9 disclaimer, §10 #1 |
| [`grails-doc/.../securityPlugins.adoc`](./grails-doc/src/en/guide/security/securityPlugins.adoc) | Use the Spring Security Core plugin for authorization/roles. | §3, §9, §10 #1 |
| [`SECURITY.md`](./SECURITY.md) | Disclosure routes through the ASF Security Team. | §1 reporting cross-reference |
| [`AGENTS.md`](./AGENTS.md) | JDK 21, Groovy 4.0.x, Spring Boot 4.0.x, Jakarta EE 10, Spock 2.3. | §5 runtime assumptions |
| [`README.md`](./README.md) | The framework is embedded in a user web application; not a service. | §1 description, §2 deployment context |

No claim in the existing documentation is dropped, weakened, or contradicted by this document. Where the existing documentation and this document would conflict, the documentation wins; raise a §14 question rather than silently editing.
