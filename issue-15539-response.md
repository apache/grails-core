Thank you for the detailed bug report.

## Root Cause

Grails publishes **two separate BOMs** that serve different purposes:

| BOM | Groovy Coordinates | Groovy Version | Purpose |
|---|---|---|---|
| `grails-bom` | `org.apache.groovy:groovy-bom` | 4.0.30 | Your application code (runs on Groovy 4) |
| `grails-gradle-bom` | `org.codehaus.groovy:groovy-bom` | 3.0.25 | Grails Gradle plugins (run inside Gradle 8's JVM, which embeds Groovy 3) |

This is by design - Gradle 8 ships with Groovy 3.x (`org.codehaus.groovy`), so the Gradle plugin BOM correctly aligns to Gradle's embedded Groovy version.

The problem is that `grails-gradle-bom` leaks into your application's dependency graph through this transitive chain:

```
your-app
  → grails-bom:7.0.9 (constraint for grails-gradle-model)
    → grails-bootstrap
      → grails-gradle-model:7.0.9
        → grails-gradle-bom:7.0.9
          → org.codehaus.groovy:groovy-bom:3.0.25   ← Groovy 3 reference leaks here
```

Without the group redirection strategy, this resolves fine because `org.codehaus.groovy:groovy-bom:3.0.25` exists on Maven Central and just adds version constraints (it doesn't pull in Groovy 3 jars).

However, your `eachDependency` redirect rewrites it to `org.apache.groovy:groovy-bom:3.0.25`, which doesn't exist - Apache Groovy coordinates only start at version 4.0.

## Workaround

Guard the redirect with a version check so it only applies to Groovy 4.x coordinates:

```groovy
allprojects {
    configurations.all {
        resolutionStrategy {
            eachDependency { details ->
                if (details.requested.group == 'org.codehaus.groovy'
                        && !details.requested.version.startsWith('3.')) {
                    details.useTarget(
                        group: 'org.apache.groovy',
                        name: details.requested.name,
                        version: '4.0.30'
                    )
                }
            }
        }
    }
}
```

This skips the redirect for Groovy 3.x dependencies (which come from the Gradle plugin BOM) while still redirecting any Groovy 4.x references that use the old coordinates.
