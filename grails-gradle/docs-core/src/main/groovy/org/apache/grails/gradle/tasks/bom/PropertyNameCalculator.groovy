package org.apache.grails.gradle.tasks.bom

import org.gradle.api.GradleException

/**
 * Decides on the property name for a dependency
 */
class PropertyNameCalculator {
    final Map<String, String> keysToPlatformCoordinates = [:]
    final Map<String, ExtractedDependencyConstraint> platformDefinitions = [:]

    final Map<String, String> keysToCoordinates = [:]
    final Map<String, ExtractedDependencyConstraint> definitions = [:]
    final Map<String, String> versions = [:]

    PropertyNameCalculator(Map<String, String> platformDefinitions, Map<String, String> definitions, Map<String, String> definitionVersions) {
        this.platformDefinitions.putAll(populate(platformDefinitions, keysToPlatformCoordinates))
        this.definitions.putAll(populate(definitions, keysToCoordinates))
        this.versions.putAll(definitionVersions)
    }

    private static Map<String, ExtractedDependencyConstraint> populate(Map<String, String> definitions, Map<String, String> keyMappings) {
        definitions.collectEntries { Map.Entry<String, String> entry ->
            ExtractedDependencyConstraint constraint = new ExtractedDependencyConstraint(entry.value)
            if (!constraint.version) {
                throw new GradleException("Version is required for dependency: ${entry.value}")
            }

            keyMappings.put(constraint.coordinates, entry.key)

            [constraint.coordinates, constraint]
        }
    }

    ExtractedDependencyConstraint calculate(String groupId, String artifactId, String version, boolean isPlatform) {
        Map<String, ExtractedDependencyConstraint> toSearch = isPlatform ? platformDefinitions : definitions as Map<String, ExtractedDependencyConstraint>
        Map<String, String> coordinateMapping = isPlatform ? keysToPlatformCoordinates : keysToCoordinates

        ExtractedDependencyConstraint found = toSearch.get("$groupId:$artifactId:$version" as String)
        if (!found) {
            return null
        }

        if(found.versionPropertyReference) {
            return found
        }

        String propertyName = determinePossibleKey(found, coordinateMapping)
        if (propertyName) {
            found.versionPropertyReference = propertyName ? "\${${propertyName}}" : '' as String
            return found
        }

        throw new GradleException("Could not determine artifact property key for ${found.coordinates}")
    }

    String determinePossibleKey(ExtractedDependencyConstraint found, Map<String, String> keyMappings) {
        String possibleKey = keyMappings[found.coordinates]
        while (possibleKey) {
            String propertyName = "${possibleKey}.version" as String
            if (versions.containsKey(propertyName)) {
                return propertyName
            }

            int lastIndex = possibleKey.lastIndexOf('-')
            possibleKey = lastIndex > 0 ? possibleKey.substring(0, lastIndex) : null
        }

        null
    }
}