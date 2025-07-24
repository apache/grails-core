/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.grails.gradle.tasks.bom

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlatformPlugin

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

    void addProjects(Collection<Project> projects) {
        for (Project project : projects) {
            if(project.plugins.hasPlugin(JavaPlatformPlugin) || !project.extensions.findByName('grailsPublish')) {
                continue
            }

            String artifactId = (project.findProperty('pomArtifactId') ?: project.name)
            String baseVersionName = artifactId.replaceAll('-', '.')
            String versionName = "${baseVersionName}.version" as String
            String coordinates = "${project.group}:${artifactId}:${project.version}" as String
            ExtractedDependencyConstraint constraint = new ExtractedDependencyConstraint(coordinates as String)
            constraint.versionPropertyReference = "\${${versionName}}" as String

            definitions.put(coordinates, constraint)
            keysToCoordinates.put(coordinates, baseVersionName)
            versions.put(versionName, project.version as String)
        }
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
