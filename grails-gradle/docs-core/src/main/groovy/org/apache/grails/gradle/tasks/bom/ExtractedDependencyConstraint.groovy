package org.apache.grails.gradle.tasks.bom

import groovy.transform.CompileStatic
import groovy.transform.MapConstructor
import groovy.transform.ToString

import java.util.regex.Matcher
import java.util.regex.Pattern

@CompileStatic
@MapConstructor(includes = ['groupId', 'artifactId', 'version', 'versionPropertyReference', 'source'], includeSuperProperties = true)
@ToString(includes = ['groupId', 'artifactId', 'version', 'versionPropertyReference', 'source'], includeSuperProperties = true)
class ExtractedDependencyConstraint extends CoordinateVersionHolder {
    String versionPropertyReference
    String source

    ExtractedDependencyConstraint(String coordinates) {
        coordinates.split(':').with { String[] parts ->
            groupId = parts[0]
            artifactId = parts[1]
            version = parts[2]
        }
    }

    String getVersionPropertyReference() {
        versionPropertyReference == '${project.version}' ? '' : versionPropertyReference
    }

    String getVersionPropertyName() {
        String property = getVersionPropertyReference()
        if(!property) {
            return null
        }

        Pattern dynamicPattern = ~/\$\{([^}]+)\}/

        Matcher matcher = property =~ dynamicPattern
        if(!matcher.find()) {
            throw new IllegalStateException("Invalid Verison Property: ${property}")
        }

        matcher.group(1)
    }
}