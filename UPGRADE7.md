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

# Common 7.0 Upgrade Gotchas - DRAFT -

Experienced while upgrading modules for Grails 7

- NOTE: Several items have been directly integrated into the upgrade guide. Please refer to it for the full list.
- The amount of boilerplate required in gradle files has been reduced:  
  - When applying the `org.apache.grails.gradle.grails-plugin`, previously `org.grails.grails-plugin`, gradle plugin, the bootJar task is disabled by default.  No more needing to explicitly set it to false!
  - We no longer have a `micronaut-bom` and a `spring-bom`.  We only have the `spring-bom` now, which allows `grails-bom` to inherit from it and be applied as part of the Spring Dependency Management plugin.  This means versions do not need included for any library in the bom.  Override bom versions via gradle properties.
  - The `grailsPublish` plugin returns and is no longer an internal only plugin.  It has been enhanced to work with some multi-project workflows.  Eliminate publishing boilerplate of the nexus-publish, maven-publish, & signing plugin by adopting it.
- When migrating a new project to Grails 7, it's advised to generate a stock 7.0 app from [start.grails.org](https://start.grails.org) and compare the project with a grails app generated from the same grails version that your application uses.  This helps catch the dependency clean up that has occurred.  Including the additions of new dependencies.  Note: due to an issue with project resolution the `grails-bom` will need explicitly imported in buildSrc or any project that does not apply the grails gradle plugins (grails-plugin, grails-web, or grails-gsp).  By default, the grails gradle plugins (grails-plugin, grails-web, grails-gsp) will apply the bom automatically.
- the gradle property `groovyVersion` is being replaced with the upstream spring property name `groovy.version`.  Please update your projects accordingly.
- hibernate-ehcache

    The `org.hibernate:hibernate-ehcache` library is no longer provided by the `org.apache.grails:grails-data-hibernate5`, previously `org.grails.plugins:hibernate5`,  plugin. If
    your application depends on `hibernate-ehcache`, you must now add it explicitly to your project dependencies.
    
    Since `hibernate-ehcache` brings in a conflicting `javax` version of `org.hibernate:hibernate-core`, it is
    recommended to exclude `hibernate-core` from the `hibernate-ehcache` dependency to avoid conflicts:

      dependencies {
          implementation 'org.hibernate:hibernate-ehcache:5.6.15.Final', {
              // exclude javax variant of hibernate-core
              exclude group: 'org.hibernate', module: 'hibernate-core'
          }
          runtimeOnly 'org.jboss.spec.javax.transaction:jboss-transaction-api_1.3_spec:2.0.0.Final', {
              // required for hibernate-ehcache to work with javax variant of hibernate-core excluded
          }
      }


## NOTE: This document is a draft and the explanations are only highlights and will be expanded further prior to release of 7.0.

### Cool New Features
- Hello Exterminator, Good by bugs! Lot's of things started working... and working well! For instance, use of controller namespaces now work seemlessly.
- Massive decoupling of dependencies and cleanup between modules.  SiteMesh dependencies are no longer compiled into controllers fused between numerous modules. SiteMesh isn't even required to use Grails!
- GSP can now be used OUTSIDE of Grails! see grails-boot
- Works with Spring Security 6 out of the box. No plugin needed!
