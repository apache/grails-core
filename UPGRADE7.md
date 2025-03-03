# Common 7.0 Upgrade Gotchas - DRAFT -

Experienced while upgrading modules for Grails 7

- h2 2.x is stricter about reserved words
  - https://github.com/grails/gorm-hibernate5/pull/910/commits/c8de45df204966ccc228b46b94beeb2142ae0f59 
- [GROOVY-10621](https://issues.apache.org/jira/browse/GROOVY-10621) 
  - Primitive booleans will no longer generate the form of isProperty & getProperty.  They will only generate isProperty()
- The amount of boilerplate required in gradle files has been reduced:  
  - When `org.grails.grails-plugin` gradle plugin is applied, the bootJar task is disabled by default.  No more needing to explicitly set it to false!
  - We no longer have a `micronaut-bom` and a `spring-bom`.  We only have the `spring-bom` now, which allows `grails-bom` to inherit from it and be applied as part of the Spring Dependency Management plugin.  This means versions do not need included for any library in the bom.  Override bom versions via gradle properties.
  - The `grailsPublish` plugin returns and is no longer an internal only plugin.  It has been enhanced to work with some multi-project workflows.  Eliminate publishing boilerplate of the nexus-publish, maven-publish, & signing plugin by adopting it.
- Jar artifacts produced by Grails Plugins will no longer have the suffix `-plain`
  - https://github.com/grails/grails-gradle-plugin/pull/347
- [GROOVY-5169](https://issues.apache.org/jira/browse/GROOVY-5169)  [GROOVY-10449](https://issues.apache.org/jira/browse/GROOVY-10449)
  - Fields with a public modifier were not returned with MetaClassImpl#getProperties() in groovy 3, but are now.
- Some older libraries may include an older version of groovy, but still be compatible with Groovy 4.  One example is `GPars`.  In your gradle file, you can force a dependency upgrade via this code:

        configurations.configureEach {
            resolutionStrategy.eachDependency { DependencyResolveDetails details ->
                if (details.requested.group == 'org.codehaus.groovy') {
                    details.useTarget(group: 'org.apache.groovy', name: details.requested.name, version: '4.0.25')
                }
            }
        }
- The `jakarta` package switch means that older libraries using `javax` will need to be updated to use the correct namespace.  The side effect of this change is most grails plugins will likely need updated to be compatible with Grails 7.
- When migrating a new project to Grails 7, it's advised to generate a stock 7.0 app from [start.grails.org](https://start.grails.org) and compare the project with a grails app generated from the same grails version that your application uses.  This helps catch the dependency clean up that has occurred.  Including the additions of new dependencies.  Note: due to an issue with project resolution the `grails-bom` will need explicitly imported in buildSrc or any project that does not apply the grails gradle plugins (grails-plugin, grails-web, or grails-gsp).  By default, the grails gradle plugins (grails-plugin, grails-web, grails-gsp) will apply the bom automatically.
- the gradle property `groovyVersion` is being replaced with the upstream spring property name `groovy.version`.  Please update your projects accordingly.
- hibernate-ehcache

    The `org.hibernate:hibernate-ehcache` library is no longer provided by the `org.grails.plugins:hibernate5` plugin. If
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

## New Features 
- [grails-gsp #551](https://github.com/grails/grails-gsp/issues/551) adopts a `formActionSubmit` tag to replace `actionSubmit`.  Dispatching actions via a parameter name on a form submit will be removed in a future version of grails.

### Cool New Features
- You can now @Scaffold Controllers and Services and virtually eliminate any boiler plate code.
- Hello Exterminator, Good by bugs! Lot's of things started working... and working well! For instance, use of controller namespaces now work seemlessly.
- Bootstrap 5.3.3 support. Saffolding and Fields tags now optionally support boostrap classes.
- Priortization of AutoConfiguration over bean overriding.
- Lightweight, Removal of numerous dependencies.
- grails-bom overhaul for keeping depedencies up to date and in sync.
- g:form now automatically provides csrf protection when Spring Security CSRF is enabled.
- Massive decoupling of dependencies and cleanup between modules.  SiteMesh dependencies are no longer compiled into controllers fused between numerous modules. SiteMesh isn't even required to use Grails!
- SiteMesh ahs been upgrade to SiteMesh 3!
- Completely up to date modern stack that has been optimized for easier future transitions.
- GSP can now be used OUTSIDE of Grails! see grails-boot
- Works with Spring Security 6 out of the box. No plugin needed!
- Tested and works with Java 17-23 [grails-core](https://github.com/grails/grails-core/blob/7.0.x/.github/workflows/gradle.yml#L18) and [grails-functional-tests](https://github.com/grails/grails-functional-tests/blob/7.0.x/.github/workflows/gradle.yml#L21)
