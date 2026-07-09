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
import groovy.transform.CompileStatic

import org.apache.maven.model.Dependency as MavenDependency
import org.apache.maven.model.Model
import org.apache.maven.model.Parent
import org.apache.maven.model.Repository
import org.apache.maven.model.building.DefaultModelBuilder
import org.apache.maven.model.building.DefaultModelBuilderFactory
import org.apache.maven.model.building.DefaultModelBuildingRequest
import org.apache.maven.model.building.ModelSource
import org.apache.maven.model.building.UrlModelSource
import org.apache.maven.model.resolution.InvalidRepositoryException
import org.apache.maven.model.resolution.ModelResolver
import org.apache.maven.model.resolution.UnresolvableModelException

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
        Map<String, Object> coordinates = new LinkedHashMap<>(bomCoords)
        if (!coordinates.containsKey('transitive')) {
            coordinates.put('transitive', false)
        }
        List<URI> results = resolveBom(coordinates)
        DefaultModelBuilder modelBuilder = new DefaultModelBuilderFactory().newInstance()
        GrapeModelResolver modelResolver = new GrapeModelResolver(grapeEngine)

        for (URI u in results) {
            addDependencyManagement(buildModel(modelBuilder, new UrlModelSource(u.toURL()), modelResolver, u.toString()))
            addImportedModelProperties(modelBuilder, modelResolver)
        }
    }

    private List<URI> resolveBom(Map<String, Object> coordinates) {
        try {
            List<URI> results = grapeEngine.resolve(null, coordinates) as List<URI>
            if (!results) {
                throw new IllegalStateException("Failed to resolve BOM ${formatCoordinates(coordinates)}".toString())
            }
            return results
        } catch (IllegalStateException e) {
            throw e
        } catch (Exception e) {
            throw new IllegalStateException("Failed to resolve BOM ${formatCoordinates(coordinates)}".toString(), e)
        }
    }

    private static String formatCoordinates(Map<String, Object> coordinates) {
        "${coordinates['group']}:${coordinates['module']}:${coordinates['version']}".toString()
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

    private Model buildModel(DefaultModelBuilder modelBuilder, ModelSource modelSource, GrapeModelResolver modelResolver, String description) {
        try {
            DefaultModelBuildingRequest request = new DefaultModelBuildingRequest()
            request.setModelResolver(modelResolver)
            request.setModelSource(modelSource)
            request.setSystemProperties(System.getProperties())
            return modelBuilder.build(request).effectiveModel
        } catch (Exception e) {
            String importResolutionMessage = findImportResolutionMessage(e)
            if (importResolutionMessage) {
                throw new IllegalStateException(importResolutionMessage, e)
            }
            throw new IllegalStateException("Failed to build model for '${description}'. Is it a valid Maven bom?".toString(), e)
        }
    }

    void addDependencyManagement(Model model) {
        addVersionProperties(model.properties)
        model.dependencyManagement?.dependencies?.each { MavenDependency dependency ->
            addDependency(dependency.groupId, dependency.artifactId, dependency.version)
        }
    }

    private void addImportedModelProperties(DefaultModelBuilder modelBuilder, GrapeModelResolver modelResolver) {
        int index = 0
        while (index < modelResolver.modelSourceEntries.size()) {
            Map.Entry<String, ModelSource> modelSourceEntry = modelResolver.modelSourceEntries[index]
            addVersionProperties(buildModel(modelBuilder, modelSourceEntry.value, modelResolver, modelSourceEntry.key).properties)
            index++
        }
    }

    private void addVersionProperties(Properties properties) {
        properties.each { key, value ->
            versionProperties.putIfAbsent(key.toString(), value.toString())
        }
    }

    private static String findImportResolutionMessage(Throwable failure) {
        Throwable current = failure
        while (current) {
            String message = extractImportResolutionMessage(current.message)
            if (message) {
                return message
            }
            current = current.cause
        }
        return null
    }

    private static String extractImportResolutionMessage(String message) {
        if (!message) {
            return null
        }
        String prefix = 'Failed to resolve imported BOM '
        int start = message.indexOf(prefix)
        if (start < 0) {
            return null
        }
        int end = message.indexOf(' @', start)
        message.substring(start, end < 0 ? message.length() : end).trim()
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

    private static class GrapeModelResolver implements ModelResolver {

        private final GrapeEngine grapeEngine
        private final Map<String, ModelSource> modelSources

        GrapeModelResolver(GrapeEngine grapeEngine) {
            this(grapeEngine, new LinkedHashMap<String, ModelSource>())
        }

        private GrapeModelResolver(GrapeEngine grapeEngine, Map<String, ModelSource> modelSources) {
            this.grapeEngine = grapeEngine
            this.modelSources = modelSources
        }

        @Override
        ModelSource resolveModel(Parent parent) throws UnresolvableModelException {
            return resolveModel(parent.groupId, parent.artifactId, parent.version)
        }

        @Override
        ModelSource resolveModel(MavenDependency dependency) throws UnresolvableModelException {
            return resolveModel(dependency.groupId, dependency.artifactId, dependency.version)
        }

        @Override
        ModelSource resolveModel(String groupId, String artifactId, String version) throws UnresolvableModelException {
            String coordinates = "${groupId}:${artifactId}:${version}".toString()
            ModelSource modelSource = modelSources[coordinates]
            if (modelSource) {
                return modelSource
            }
            Map<String, Object> dependency = [group: groupId, module: artifactId, version: version, type: 'pom', transitive: false]
            try {
                URI uri = grapeEngine.resolve(null, dependency)[0]
                modelSource = new UrlModelSource(uri.toURL())
                modelSources[coordinates] = modelSource
                return modelSource
            } catch (Exception e) {
                throw new IllegalStateException("Failed to resolve imported BOM ${coordinates}".toString(), e)
            }
        }

        @Override
        void addRepository(Repository repository) throws InvalidRepositoryException {
        }

        @Override
        void addRepository(Repository repository, boolean replace) throws InvalidRepositoryException {
        }

        @Override
        ModelResolver newCopy() {
            return new GrapeModelResolver(grapeEngine, modelSources)
        }

        List<Map.Entry<String, ModelSource>> getModelSourceEntries() {
            new ArrayList<Map.Entry<String, ModelSource>>(modelSources.entrySet())
        }

    }
}
