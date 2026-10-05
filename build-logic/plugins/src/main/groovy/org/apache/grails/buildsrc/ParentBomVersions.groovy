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

import java.util.function.Function

import groovy.transform.CompileStatic

import org.gradle.api.GradleException

import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.apache.maven.model.Parent

/**
 * Everything a set of parent BOMs manages, including what they manage through the BOMs they
 * import, together with the parent-level property that controls each version.
 *
 * <p>A version a parent manages through an imported BOM is attributed to the property the parent
 * imports that BOM with - {@code jackson-2-bom.version} for everything
 * {@code com.fasterxml.jackson:jackson-bom} manages, not the property that BOM uses internally -
 * because that is the property a consumer sets to move the whole family. A version the parent
 * writes literally has no property, and is recorded with an empty one.</p>
 *
 * <p>When two entries manage the same module, the first one wins, with a BOM's own entries ahead
 * of the entries it imports, matching Maven's {@code <dependencyManagement>} resolution.</p>
 *
 * <p>Grails applications walk BOMs the same way through {@code BomManagedVersions} in
 * {@code grails-gradle-plugins}, which build-logic cannot depend on: a fix to how either one reads
 * a BOM belongs in the other too.</p>
 *
 * @since 8.0
 */
@CompileStatic
class ParentBomVersions {

    private static final int MAX_PARENT_DEPTH = 10

    /** {@code group:artifact} to the version the parents manage it at. */
    final Map<String, String> versions = new LinkedHashMap<>()

    /** {@code group:artifact} to the parent-level property controlling its version, or empty. */
    final Map<String, String> properties = new LinkedHashMap<>()

    private final Function<String, File> pomResolver
    private final Set<String> visited = new HashSet<>()
    private final Map<String, Map<String, String>> propertiesByPom = [:]

    private ParentBomVersions(Function<String, File> pomResolver) {
        this.pomResolver = pomResolver
    }

    /**
     * @param parentBoms the parent BOMs as {@code group:artifact:version}, in precedence order
     * @param pomResolver returns the POM file for a {@code group:artifact:version}
     */
    static ParentBomVersions resolve(Collection<String> parentBoms, Function<String, File> pomResolver) {
        ParentBomVersions result = new ParentBomVersions(pomResolver)
        for (String parentBom : parentBoms) {
            result.collect(parentBom, null)
        }
        result
    }

    /**
     * @param controllingProperty the parent-level property the BOM was imported with, empty when it
     * was imported at a literal version, or {@code null} for a parent BOM itself
     */
    private void collect(String coordinates, String controllingProperty) {
        if (!visited.add(coordinates)) {
            return
        }
        Model bom = PomVersions.read(pomResolver.apply(coordinates))
        Map<String, String> bomProperties = effectiveProperties(bom, 0)

        List<Dependency> imports = []
        for (Dependency dependency : PomVersions.managedDependencies(bom)) {
            if (PomVersions.isImport(dependency)) {
                imports.add(dependency)
                continue
            }
            record(dependency, bomProperties, controllingProperty, coordinates)
        }

        for (Dependency imported : imports) {
            collect(record(imported, bomProperties, controllingProperty, coordinates), controllingPropertyOf(imported, controllingProperty))
        }
    }

    /**
     * Records the entry, unless an earlier one already manages the module, and returns its resolved
     * {@code group:artifact:version}. An entry that cannot be resolved fails the build, since it
     * would otherwise go unchecked without a trace.
     */
    private String record(Dependency dependency, Map<String, String> bomProperties, String controllingProperty, String bomCoordinates) {
        String key = PomVersions.key(dependency, bomProperties)
        String version = PomVersions.interpolate(dependency.version, bomProperties)
        if (!key || !version) {
            throw new GradleException("Cannot resolve ${dependency.groupId}:${dependency.artifactId}:${dependency.version}, " +
                    "managed by ${bomCoordinates}, from that POM's properties, its parent POMs' properties and the Maven built-ins.")
        }
        if (!versions.containsKey(key)) {
            versions.put(key, version)
            properties.put(key, controllingPropertyOf(dependency, controllingProperty))
        }
        "${key}:${version}"
    }

    private static String controllingPropertyOf(Dependency dependency, String controllingProperty) {
        controllingProperty != null ? controllingProperty : (PomVersions.propertyReference(dependency.version) ?: '')
    }

    /**
     * A POM's properties with its parent chain's underneath, so the POM's own values win.
     */
    private Map<String, String> effectiveProperties(Model bom, int depth) {
        Map<String, String> merged = new LinkedHashMap<>()
        Parent parent = bom.parent
        if (parent != null && depth < MAX_PARENT_DEPTH) {
            merged.putAll(parentProperties("${parent.groupId}:${parent.artifactId}:${parent.version}" as String, depth + 1))
        }
        merged.putAll(PomVersions.properties(bom))
        merged
    }

    private Map<String, String> parentProperties(String coordinates, int depth) {
        Map<String, String> cached = propertiesByPom.get(coordinates)
        if (cached == null) {
            cached = effectiveProperties(PomVersions.read(pomResolver.apply(coordinates)), depth)
            propertiesByPom.put(coordinates, cached)
        }
        cached
    }
}
