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

package org.apache.grails.buildsrc

import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.CompileStatic

import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader

/**
 * Reads POMs with Maven's own model, and resolves the {@code ${property}} references in their
 * coordinates and versions, which reading a single POM leaves unresolved.
 *
 * @since 8.0
 */
@CompileStatic
class PomVersions {

    private static final Pattern PROPERTY_REFERENCE = ~/\$\{([^}]+)}/
    private static final Pattern SINGLE_PROPERTY_REFERENCE = ~/^\$\{([^}]+)}$/
    private static final int MAX_INTERPOLATION_DEPTH = 10

    private PomVersions() {
    }

    static Model read(File pomFile) {
        (Model) pomFile.withInputStream { InputStream input -> new MavenXpp3Reader().read(input) }
    }

    /** The model's {@code <dependencyManagement>} entries. */
    static List<Dependency> managedDependencies(Model model) {
        model.dependencyManagement?.dependencies ?: Collections.<Dependency> emptyList()
    }

    /**
     * Returns the dependency's {@code group:artifact} with its {@code ${property}} references
     * expanded - some BOMs write their own group as {@code ${project.groupId}} - or {@code null}
     * when a reference cannot be resolved.
     */
    static String key(Dependency dependency, Map<String, String> properties) {
        String groupId = interpolate(dependency.groupId, properties)
        String artifactId = interpolate(dependency.artifactId, properties)
        groupId && artifactId ? "${groupId}:${artifactId}" as String : null
    }

    static boolean isImport(Dependency dependency) {
        dependency.scope == 'import'
    }

    /**
     * The properties the model's coordinates and versions may refer to: its own
     * {@code <properties>}, and the Maven built-ins, which are never declared there. A parent POM's
     * properties are not included, since reaching the parent means resolving it.
     */
    static Map<String, String> properties(Model model) {
        Map<String, String> properties = new LinkedHashMap<>()
        for (String name : model.properties.stringPropertyNames()) {
            properties.put(name, model.properties.getProperty(name).trim())
        }
        String groupId = model.groupId ?: model.parent?.groupId
        String version = model.version ?: model.parent?.version
        if (groupId) {
            properties.put('project.groupId', groupId)
        }
        if (version) {
            properties.put('project.version', version)
        }
        if (model.parent?.version) {
            properties.put('project.parent.version', model.parent.version)
        }
        properties
    }

    /**
     * Returns the property a version is written as, when the version is exactly one
     * {@code ${property}} reference to a property a consumer can set, or {@code null} otherwise.
     * Maven's {@code project.*} built-ins are not settable, so a version written as one counts as
     * a literal.
     */
    static String propertyReference(String version) {
        if (version == null) {
            return null
        }
        Matcher matcher = SINGLE_PROPERTY_REFERENCE.matcher(version.trim())
        if (!matcher.matches()) {
            return null
        }
        String name = matcher.group(1)
        name.startsWith('project.') ? null : name
    }

    /**
     * Expands every {@code ${property}} reference in {@code value}, or returns {@code null} when
     * a reference cannot be resolved.
     */
    static String interpolate(String value, Map<String, String> properties) {
        if (value == null) {
            return null
        }
        String result = value.trim()
        int remaining = MAX_INTERPOLATION_DEPTH
        while (result.contains('${') && remaining-- > 0) {
            Matcher matcher = PROPERTY_REFERENCE.matcher(result)
            if (!matcher.find()) {
                break
            }
            String resolved = properties.get(matcher.group(1))
            if (resolved == null) {
                return null
            }
            result = result.replace(matcher.group(0), resolved)
        }
        result.contains('${') ? null : result
    }
}
