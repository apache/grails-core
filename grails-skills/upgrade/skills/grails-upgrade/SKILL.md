---
name: grails-upgrade
description: Guide for upgrading Grails applications from Grails 9.x to Grails 10 and from one Grails 10 release to a later one, covering the changes of each Grails 10 release, Grails formats resolved with Spring MVC content negotiation, and the defaults that Grails 9 announced for Grails 10 for Spring JSON rendering, the JSON converter, Jackson request binding, Spring MVC exception handlers, response bodies, and the grails-xml module
license: Apache-2.0
---

<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

## What I Do

- Guide upgrades of Grails applications from Grails 9.x to Grails 10.
- Guide upgrades from one Grails 10 release to a later one, with the changes of each release listed under that release.
- Turn the Grails 10 upgrade guide into a practical migration checklist.
- Check an application against the defaults that Grails 9 announced for Grails 10, before the upgrade changes them.
- Keep migration work focused on public application behavior, not internal Grails framework implementation details.

## When to Use Me

Activate this skill when:

- Updating an application to Grails 10.
- Updating a Grails 10 application to a later Grails 10 release.
- Reviewing a Grails 10 upgrade branch or pull request.
- Fixing tests, build failures, runtime failures, or behavior changes after moving from Grails 9.x to Grails 10.
- Fixing a request answered `406 Not Acceptable`, or in another format than on Grails 9, now that `grails.mime.negotiation.spring` resolves Grails formats with Spring MVC's content negotiation by default.
- Setting `grails.web.rendering.json.spring`, `grails.converters.json.legacy`, `grails.databinding.json.jackson`, `grails.exceptionresolver.springHandlers` or `grails.controllers.responseBody` explicitly ahead of the upgrade.
- Deciding whether an application declares `grails-xml` itself.

## Which Parts Apply

Find the Grails version the application is on, the `grailsVersion` in its `gradle.properties`, and the version it is going to.

- From Grails 9.x: apply everything from [Upgrade Baseline](#upgrade-baseline) on, then every section of [Changes Within Grails 10 Releases](#changes-within-grails-10-releases) up to the target version.
- From an earlier Grails 10 release: apply the sections of [Changes Within Grails 10 Releases](#changes-within-grails-10-releases) for every release after the current version, up to and including the target version. The rest of this skill has already been applied.
- From Grails 8 or earlier: upgrade to the last Grails 9 release first, with the `grails-upgrade` skill of that release.

## Changes Within Grails 10 Releases

Each change is listed under the release that first carried it. A change that adds a configuration setting names the setting and its default. Where there is a way to keep the previous result, the entry gives it.

No Grails 10 release has changed anything after 10.0.0 yet.

## Primary Sources

Use the published Grails documentation as the source of truth before changing an application. The URLs below use `<version>`, which stands for the Grails 10 version the application is upgrading to, such as the `grailsVersion` in its `gradle.properties`; use `snapshot` for a snapshot version.

| URL | Use for |
|-----|---------|
| `https://grails.apache.org/docs/<version>/guide/upgrading.html#upgrading100x` | Main Grails 9 to Grails 10 upgrade guide |
| `https://grails.apache.org/docs/<version>/guide/upgrading.html#upgrading90x` | Grails 8 to Grails 9 upgrade guide, which announces the defaults Grails 10 changes |
| `https://grails.apache.org/docs/<version>/guide/introduction.html#whatsNew` | Feature and platform overview |
| `https://grails.apache.org/docs/<version>/guide/introduction.html#dependencyUpgrades` | Platform dependency baseline |
| `https://grails.apache.org/docs/<version>/ref/Versions/Grails%20BOM.html` | Dependency versions of the Grails BOM |

Do not assume `https://grails.apache.org/docs/latest/` points at Grails 10. Check the version printed on the documentation.

## Upgrade Baseline

Grails 10 is in development. Read the upgrade guide of the target version, since each change an application has to act on is added to it, and to this skill, with the pull request that makes it.

Verify these before touching application code:

- The application is on the last Grails 9 release, builds, and passes its tests there.
- The application starts on Grails 9 without `[DEPRECATED]` or `deprecated` warnings from Grails that it has not looked at.
- Every setting under [Defaults Grails 9 Announced for Grails 10](#defaults-grails-9-announced-for-grails-10) that the application depends on is set explicitly.

## Migration Workflow

1. Set the announced defaults explicitly on Grails 9, one at a time, and run the tests after each. Change the three JSON settings together.
2. Update `grailsVersion` in `gradle.properties`; let the Grails BOM manage dependency versions.
3. Run `./gradlew clean`, then compile and run the test suite.
4. Review the published upgrade guide section by section, confirming every item either does not apply or has been handled.

## Defaults Grails 9 Announced for Grails 10

The Grails 9 upgrade guide announces these defaults for Grails 10. The Grails 10 guide of the target version says which of them have changed; until it lists a setting, its default is the Grails 9 one. `grails.mime.negotiation.spring` has changed: see [Grails Formats Resolved with Spring MVC Content Negotiation](#grails-formats-resolved-with-spring-mvc-content-negotiation).

| Setting | What it governs | Grails 9 default | Announced for Grails 10 |
|---------|-----------------|------------------|-------------------------|
| `grails.web.rendering.json.spring` | JSON that `respond` writes, including validation errors as problems | `false` | `true` |
| `grails.converters.json.legacy` | `grails.converters.JSON`: `render ... as JSON`, `JSON.parse`, `request.JSON`, and `respond` while `grails.web.rendering.json.spring` is `false` | `true` | `false` |
| `grails.databinding.json.jackson` | JSON request bodies bound to command objects and domain classes | `false` | `true` |
| `grails.exceptionresolver.springHandlers` | Exceptions actions throw, resolved by Spring MVC `@ExceptionHandler` methods | `false` | `true` |
| `grails.controllers.responseBody` | The values of actions annotated `@ResponseBody`, written by Spring MVC as the body | `false` | `true` |
| `grails.mime.negotiation.spring` | `request.format`, `response.format`, `withFormat` and the format `respond` writes in, resolved with Spring MVC's content negotiation manager | `false` | `true`, changed in Grails 10 |

- Search `application.yml` and `application.groovy` for each setting. One that is set keeps its value through the upgrade. One that is missing takes the new default once Grails 10 changes it.
- The three JSON settings change together, so that `respond`, `render ... as JSON`, `request.JSON` and data binding write and read JSON with the same mapper. Test them together.
- To keep the Grails 9 result, set the Grails 9 default explicitly.
- The Grails 9 guide also announces that the web starter and `grails-web-boot` stop bringing `org.apache.grails:grails-xml`, and that `@Resource` then defaults to JSON only. An application that serves XML, or uses `grails.converters.XML`, XML marshallers, XML data binding, `request.XML`, or the XML and Atom renderers, declares the module itself:

  ```groovy
  dependencies {
      implementation 'org.apache.grails:grails-xml'
  }
  ```

The `grails-upgrade` skill of the last Grails 9 release describes what each setting changes and how to check an application with it.

## Grails Formats Resolved with Spring MVC Content Negotiation

`grails.mime.negotiation.spring` is `true` by default from Grails 10. `request.format`, `response.format`, `withFormat` and the format `respond` writes in are resolved with Spring MVC's content negotiation manager, so the `spring.mvc.contentnegotiation.*` settings apply to them, and the `format` parameter, URL extensions and the `grails.mime` settings of the `Accept` header decide what Spring MVC writes for an action too, such as a `ResponseEntity`. A Spring MVC endpoint, and Spring Security's filters before a URL mapping has matched a request, negotiate as before.

- Search `application.yml` and `application.groovy` for `grails.mime.negotiation.spring`. An application that set it to `true` on Grails 9 answers as it did; remove the setting. One that set it to `false` keeps the Grails rules.
- Where it is not set, check these answers, which change:
  - `Accept: */*, application/json` is the `json` format, where it was `all`: Spring MVC takes a more specific type ahead of a less specific one of equal quality, so `withFormat` runs the `json` block rather than its first.
  - A request that accepts no configured type, such as `Accept: image/png`, or whose `Accept` header Spring cannot read, such as `application/json;q=abc`, has no format: `response.format` and `response.mimeType` are `null`, and `respond` answers `406`. Code that reads either must allow for `null`.
  - A `format` parameter or URL extension no type is configured for is not acceptable, where Grails 9 answered in the first configured format.
  - A type with the `+json` or `+xml` suffix no configured type names, such as `application/merge-patch+json`, is the `json` or `xml` format, in `Accept` and in `Content-Type`, so data binding reads such a body as JSON.
  - The URL extension decides what Spring MVC writes for an action: `/book/show/1.json` of an action that returns a `ResponseEntity` is JSON whatever the `Accept` header asks for, and `/book/show/1.html` of one that returns a map is `406`.
- To keep the Grails 9 answers, set the setting to `false`:

  ```yaml
  grails:
      mime:
          negotiation:
              spring: false
  ```

The section "Opt Into Spring MVC Content Negotiation for Grails Formats" of the Grails 9 upgrade guide lists every kind of answer that changes.

## Verification Checklist

- [ ] The application was on the last Grails 9 release, with its tests passing, before the upgrade.
- [ ] Every announced setting the application depends on is set explicitly.
- [ ] `grails-xml` is declared if the application serves or reads XML.
- [ ] Requests answered `406` or in another format than on Grails 9 have been checked against Spring MVC content negotiation, or `grails.mime.negotiation.spring: false` keeps the Grails rules.
- [ ] The test suite passes on the target Grails 10 version.
- [ ] The upgrade guide of the target version has been read section by section.

## Pitfalls to Avoid

- Do not skip a major version. An application on Grails 8 upgrades to the last Grails 9 release first, with the skill of that release.
- Do not leave an announced setting unset on the assumption that its default has not changed; check the upgrade guide of the target version.
- Do not use `javax.*` imports. Grails 10 continues the Jakarta baseline.
