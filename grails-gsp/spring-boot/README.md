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

GSP for Spring Boot
===================

Auto-configures Groovy Server Pages as the view technology of a Spring Boot application that is **not** a Grails
application: one that routes and binds with Spring MVC, has no `grails-app` directory, no plugins and no
`UrlMappings`. A Grails application gets GSP from `grails-gsp` instead and does not put this module on its class
path.

User documentation is in the Grails guide, under *The Web Layer → Groovy Server Pages → GSP in a Spring Boot
Application*. A worked example is `grails-test-examples/gsp-spring-boot`.

What it contributes
-------------------

| Bean | Notes |
|---|---|
| `groovyPagesTemplateEngine`, `groovyPageLocator`, `groovyPagesTemplateRenderer` | Render a page. The locator is given the registry of views compiled at build time, read from `classpath:gsp/views.properties`. |
| `gspViewResolver` | Resolves a view name to a page. Becomes the application's `viewResolver` unless `spring.gsp.replaceViewResolverBean` is `false`, and answers to `jspViewResolver` unless the application has a bean of its own under that name. |
| `renderTagLib`, `renderSitemeshTagLib`, `sitemesh3LayoutTagLib`, `gspTagLibraryLookup` | The tag libraries GSP ships and the lookup that finds them. The lookup also registers any tag library bean of the context, whether annotated `@grails.gsp.TagLib` or `@Artefact("TagLib")`. |
| `codecLookup` | Stands in for the one the codecs module contributes, where that is not on the class path. |
| `grailsApplication` | A standalone one, for the GSP beans that require it. It does not bring the Grails plugin runtime with it. |
| `grailsUrlMappingsHolder` | Empty, and only where nothing else declares one. A link generator requires mappings; an application routing with Spring MVC has none, and a link to a resource or to a path needs none. |
| SiteMesh 3 defaults | Applied to the environment of a context `SpringApplication` did not build. |

Compiled pages
--------------

The `org.apache.grails.gradle.grails-gsp` Gradle plugin compiles the templates and writes the registry the locator
reads. It needs `serverpath = '/'` here, because a Spring Boot application asks for `/form.gsp` rather than for a
path under a Grails view root. Where a template root is a `file:` URL the registry is left unread, so the templates
themselves render and an edit takes effect without a restart.

Building and running the example
--------------------------------

```shell
sdk env
./gradlew :grails-test-examples-gsp-spring-boot:bootRun
./gradlew :grails-test-examples-gsp-spring-boot:test
```
