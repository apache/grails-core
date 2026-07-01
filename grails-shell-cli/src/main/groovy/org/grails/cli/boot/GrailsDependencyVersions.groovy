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
package org.grails.cli.boot

import groovy.grape.Grape
import groovy.grape.GrapeEngine
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult

import grails.util.Environment
import org.grails.cli.compiler.dependencies.Dependency
import org.grails.cli.compiler.dependencies.DependencyManagement

/**
 * Introduces dependency management based on a published BOM file
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class GrailsDependencyVersions implements DependencyManagement {

    protected Map<String, Dependency> groupAndArtifactToDependency = [:]
    protected Map<String, String> artifactToGroupAndArtifact = [:]
    protected List<Dependency> dependencies = []
    protected Map<String, String> versionProperties = [:]
    private final Set<String> resolvedBoms = [] as Set<String>
    private GrapeEngine grapeEngine

    GrailsDependencyVersions() {
        this(getDefaultEngine())
    }

    GrailsDependencyVersions(Map<String, String> bomCoords) {
        this(getDefaultEngine(), bomCoords)
    }

    GrailsDependencyVersions(GrapeEngine grape) {
        this(grape, [group: 'org.apache.grails', module: 'grails-bom', version: Environment.grailsVersion, type: 'pom'])
    }

    GrailsDependencyVersions(GrapeEngine grape, Map bomCoords) {
        this.grapeEngine = grape
        if (!bomCoords.containsKey('transitive')) {
            bomCoords.put('transitive', false)
        }
        def results = grape.resolve(null, bomCoords)

        for (URI u in results) {
            def pom = new XmlSlurper().parseText(u.toURL().text)
            addDependencyManagement(pom)
        }
    }

    static GrapeEngine getDefaultEngine() {
        def grape = Grape.getInstance()

        // Use apache repository with SNAPSHOTS when grailsVersion is not set or it ends in SNAPSHOT
        // otherwise use only mavenCentral
        if (!Environment.grailsVersion || Environment.grailsVersion.endsWith('SNAPSHOT')) {
            grape.addResolver([name: 'apacheRepository', root: 'https://repository.apache.org/content/groups/public'] as Map<String, Object>)
        }

        grape.addResolver([name: 'grailsCentral', root: 'https://repo.grails.org/grails/restricted'] as Map<String, Object>)

        grape
    }

    @CompileDynamic
    void addDependencyManagement(GPathResult pom) {
        // Capture this POM's <properties> in a local map so that ${...} version references are
        // resolved against the POM that declared them, and so recursing into an imported BOM
        // cannot clobber the property table mid-iteration. Merge into the shared map with
        // first-writer-wins precedence so Grails' own versions win over imported BOM versions.
        Map<String, String> localProperties = pom.properties.'*'.collectEntries { [(it.name()): it.text()] }
        localProperties.each { String key, String value -> versionProperties.putIfAbsent(key, value) }

        List<Map<String, String>> grailsImports = []
        List<Map<String, String>> thirdPartyImports = []

        pom.dependencyManagement.dependencies.dependency.each { dep ->
            String groupId = dep.groupId.text()
            String artifactId = dep.artifactId.text()
            String version = versionLookup(dep.version.text(), localProperties)
            String scope = dep.scope.text()
            String type = dep.type.text()

            if (scope == 'import' && type == 'pom') {
                // Defer all imported BOMs (Grails BOMs such as grails-base-bom AND third-party BOMs
                // such as spring-boot-dependencies). They are resolved after this POM's direct
                // constraints so the importing BOM's versions take precedence.
                Map<String, String> coords = [group: groupId, module: artifactId, version: version]
                if (groupId == 'org.apache.grails') {
                    grailsImports << coords
                } else {
                    thirdPartyImports << coords
                }
            } else if (scope != 'import' && version && !version.startsWith('${')) {
                // Skip deps whose version property did not resolve: versionLookup returns null for
                // an unresolved ${...} reference, which is the meaningful guard here. The
                // startsWith('${') check is nearly dead - it only catches a malformed reference
                // with no closing brace, which versionLookup leaves untouched.
                addDependency(groupId, artifactId, version)
            }
        }

        // Resolve Grails BOM imports before third-party ones so that Grails-managed versions
        // (e.g. an intentionally pinned Groovy) win over the versions a third-party BOM declares.
        // Precedence among the third-party imports themselves is only POM declaration order and is
        // otherwise undefined; the conflict-prone artifacts (Groovy, Spock) stay correct solely
        // because they are declared as direct constraints above, which first-writer-wins applies
        // before any import is followed. Do not move such a pin into a BOM import without
        // restoring an equivalent direct constraint, or it may silently regress.
        for (Map<String, String> coords : (grailsImports + thirdPartyImports)) {
            resolveImportedBom(coords['group'], coords['module'], coords['version'])
        }
    }

    @CompileDynamic
    private void resolveImportedBom(String groupId, String artifactId, String version) {
        if (grapeEngine == null || !version) {
            return
        }
        // Cycle / duplicate-import protection: spring-boot-dependencies is imported by both
        // grails-bom and grails-base-bom, so the same BOM can be reached more than once.
        if (!resolvedBoms.add("$groupId:$artifactId:$version".toString())) {
            return
        }
        try {
            def results = grapeEngine.resolve(null, [group: groupId, module: artifactId, version: version, type: 'pom', transitive: false])
            for (URI u in results) {
                def importedPom = new XmlSlurper().parseText(u.toURL().text)
                addDependencyManagement(importedPom)
            }
        } catch (Exception e) {
            String coordinates = "${groupId}:${artifactId}:${version}".toString()
            throw new IllegalStateException("Failed to resolve imported BOM ${coordinates}".toString(), e)
        }
    }

    /**
     * Handles properties version lookup in grails-bom
     *
     *   <properties>
     *    <ant.version>1.10.15</ant.version>
     *   </properties>
     *
     *   <dependencyManagement>
     *    <dependencies>
     *     <dependency>
     *      <groupId>org.apache.ant</groupId>
     *      <artifactId>ant</artifactId>
     *      <version>${ant.version}</version>
     *     </dependency>
     *    </dependencies>
     *   </dependencyManagement>
     *
     * @param version
     *            either the version or the version to lookup
     *
     * @return the version with lookup from properties when required
     */
    String versionLookup(String version) {
        versionLookup(version, versionProperties)
    }

    String versionLookup(String version, Map<String, String> properties) {
        version?.startsWith('${') && version?.endsWith('}') ?
                properties[version[2..-2]] : version
    }

    protected void addDependency(String group, String artifactId, String version) {
        def groupAndArtifactId = "$group:$artifactId".toString()
        // First writer wins: a constraint declared by (or imported earlier into) the Grails BOM
        // must not be overwritten by a later third-party BOM (e.g. spring-boot-dependencies)
        // that manages the same artifact at a different version.
        if (groupAndArtifactToDependency.containsKey(groupAndArtifactId)) {
            return
        }
        artifactToGroupAndArtifact[artifactId] = groupAndArtifactId

        def dep = new Dependency(group, artifactId, version)
        dependencies.add(dep)
        groupAndArtifactToDependency[groupAndArtifactId] = dep
    }

    Dependency find(String groupId, String artifactId) {
        return groupAndArtifactToDependency["$groupId:$artifactId".toString()]
    }

    @Override
    List<Dependency> getDependencies() {
        return dependencies
    }

    Map<String, String> getVersionProperties() {
        return versionProperties
    }

    @Override
    String getSpringBootVersion() {
        return find('spring-boot').getVersion()
    }

    @Override
    Dependency find(String artifactId) {
        def groupAndArtifact = artifactToGroupAndArtifact[artifactId]
        if (groupAndArtifact)
            return groupAndArtifactToDependency[groupAndArtifact]
    }

    Iterator<Dependency> iterator() {
        return groupAndArtifactToDependency.values().iterator()
    }
}
