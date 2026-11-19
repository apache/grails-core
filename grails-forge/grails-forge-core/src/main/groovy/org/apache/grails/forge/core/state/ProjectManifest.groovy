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
package org.apache.grails.forge.core.state

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.grails.forge.application.ApplicationType
import org.grails.forge.util.NameUtils
import java.util.regex.Pattern

/**
 * The manifest of a project, {@code .grails/project.yml}: what was asked for when the application was
 * generated. What that came to is in the {@link ProjectLock}.
 *
 * @since 9.0
 */
@CompileStatic
final class ProjectManifest {

    /** The format of the manifest this version of Grails writes and reads */
    public static final int FORMAT = 1

    /** The baseline is what the generator wrote, so a feature can be added to the project later */
    public static final String TRACKED = 'tracked'

    /** There is no baseline the generator wrote: the project was made by an earlier version */
    public static final String UNTRACKED = 'untracked'

    @PackageScope
    static final int MAX_FEATURES = 1000

    @PackageScope
    static final int MAX_PARAMETERS = 200

    @PackageScope
    static final Pattern FEATURE_NAME = Pattern.compile(/[A-Za-z0-9][A-Za-z0-9._:#+-]{0,199}/)

    @PackageScope
    static final Pattern VERSION = Pattern.compile(/[A-Za-z0-9][A-Za-z0-9._+-]{0,99}/)

    @PackageScope
    static final Pattern COORDINATES = Pattern.compile(/[A-Za-z0-9_][A-Za-z0-9._-]{0,199}:[A-Za-z0-9_][A-Za-z0-9._-]{0,199}/)

    final String grailsVersion
    final ApplicationType applicationType
    final String name
    final String defaultPackage
    final GenerationOptions options
    final String generationState
    final List<RequestedFeature> requestedFeatures
    final List<RequestedFeature> excludedFeatures
    final Map<String, String> parameters

    ProjectManifest(String grailsVersion, ApplicationType applicationType, String name, String defaultPackage,
                    GenerationOptions options, String generationState, List<RequestedFeature> requestedFeatures,
                    List<RequestedFeature> excludedFeatures, Map<String, String> parameters) {
        this.grailsVersion = grailsVersion
        this.applicationType = applicationType
        this.name = name
        this.defaultPackage = defaultPackage
        this.options = options
        this.generationState = generationState
        this.requestedFeatures = Collections.unmodifiableList(new ArrayList<RequestedFeature>(requestedFeatures))
        this.excludedFeatures = Collections.unmodifiableList(new ArrayList<RequestedFeature>(excludedFeatures))
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<String, String>(parameters))
    }

    /**
     * @return whether the baseline of the project is what the generator wrote
     */
    boolean isTracked() {
        return generationState == TRACKED
    }

    @PackageScope
    byte[] toBytes() {
        Map<String, Object> map = new LinkedHashMap<>()
        map.put('format', FORMAT)
        map.put('grailsVersion', grailsVersion)
        map.put('applicationType', GenerationOptions.written(applicationType))
        map.put('name', name)
        map.put('defaultPackage', defaultPackage)
        map.put('options', options.toMap())
        map.put('generationState', generationState)
        map.put('requestedFeatures', requestedFeatures.collect { RequestedFeature each -> each.toMap() })
        map.put('excludedFeatures', excludedFeatures.collect { RequestedFeature each -> each.toMap() })
        map.put('parameters', new LinkedHashMap<String, Object>(parameters))
        return StateYaml.dump(map)
    }

    @PackageScope
    static ProjectManifest read(byte[] bytes) {
        StateSection root = new StateSection(ProjectState.MANIFEST, '', StateYaml.load(bytes, ProjectState.MANIFEST))
        format(root, FORMAT)
        String grailsVersion = root.matching('grailsVersion', VERSION, 'a version')
        ApplicationType applicationType = applicationType(root)
        String name = root.text('name')
        if (!NameUtils.isValidAppName(name)) {
            throw root.problem('name', 'is not the name of an application')
        }
        String defaultPackage = root.text('defaultPackage')
        if (!NameUtils.isValidJavaPackage(defaultPackage)) {
            throw root.problem('defaultPackage', 'is not the name of a package')
        }
        ProjectManifest manifest = new ProjectManifest(
                grailsVersion,
                applicationType,
                name,
                defaultPackage,
                GenerationOptions.read(root.section('options')),
                root.oneOf('generationState', [TRACKED, UNTRACKED]),
                root.sections('requestedFeatures', MAX_FEATURES).collect { StateSection each -> RequestedFeature.read(each) },
                root.sections('excludedFeatures', MAX_FEATURES).collect { StateSection each -> RequestedFeature.read(each) },
                root.texts('parameters', MAX_PARAMETERS))
        root.end()
        return manifest
    }

    @PackageScope
    static void format(StateSection root, int format) {
        int written = root.number('format', 0, Integer.MAX_VALUE)
        if (written != format) {
            throw root.problem('format', "is ${written}, and this version of Grails reads the format ${format}")
        }
    }

    @PackageScope
    static ApplicationType applicationType(StateSection root) {
        List<ApplicationType> types = ApplicationType.values() as List<ApplicationType>
        String written = root.oneOf('applicationType', types.collect { ApplicationType each -> GenerationOptions.written(each) })
        return types.find { ApplicationType each -> GenerationOptions.written(each) == written }
    }

    /**
     * A feature that was asked for by name, or one that was asked to be left out. One of a feature
     * pack that was found by its coordinates has those and its version.
     */
    @CompileStatic
    static final class RequestedFeature {

        final String name
        final String coordinates
        final String version

        RequestedFeature(String name, String coordinates = null, String version = null) {
            this.name = name
            this.coordinates = coordinates
            this.version = version
        }

        @PackageScope
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>()
            map.put('name', name)
            if (coordinates != null) {
                map.put('coordinates', coordinates)
            }
            if (version != null) {
                map.put('version', version)
            }
            return map
        }

        @PackageScope
        static RequestedFeature read(StateSection section) {
            RequestedFeature feature = new RequestedFeature(
                    section.matching('name', FEATURE_NAME, 'the name of a feature'),
                    section.optionalMatching('coordinates', COORDINATES, 'a group and an artifact, group:artifact'),
                    section.optionalMatching('version', VERSION, 'a version'))
            section.end()
            return feature
        }
    }
}
