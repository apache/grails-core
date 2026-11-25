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
import org.apache.grails.forge.core.pack.PackDescriptorParser
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.Project
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

    /**
     * There is no baseline the generator wrote: the application was there before it had a record, and
     * the record says what it is taken to be, see {@link AdoptedProjectRecord}
     */
    public static final String UNTRACKED = 'untracked'

    /**
     * The application was there before it had a record, and the baseline is what this generator
     * writes for what the application is taken to be: the type, the options and the features that
     * {@code project migrate} found in its files or was given. A feature is added to the project
     * against that baseline, and where the application is not what the generator writes for those
     * choices, it is a change of the application, as it is in a project that was generated. The state
     * stays as it is when a feature is added and when the record is moved to another version
     *
     * @since 9.0
     */
    public static final String ADOPTED = 'adopted'

    @PackageScope
    static final Pattern FEATURE_NAME = Pattern.compile(/[A-Za-z0-9][A-Za-z0-9._:#+-]{0,199}/)

    // the group, the artifact and the version of a feature pack are each as long as its coordinates let them be
    @PackageScope
    static final Pattern VERSION = Pattern.compile(/[A-Za-z0-9][A-Za-z0-9._+-]{0,199}/)

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
    /**
     * The name of the preset the application was made from, which is one of the {@link #requestedFeatures}
     * and has its coordinates and version there when it was taken by them; or {@code null}
     */
    final String preset

    ProjectManifest(String grailsVersion, ApplicationType applicationType, String name, String defaultPackage,
                    GenerationOptions options, String generationState, List<RequestedFeature> requestedFeatures,
                    List<RequestedFeature> excludedFeatures, Map<String, String> parameters) {
        this(grailsVersion, applicationType, name, defaultPackage, options, generationState, requestedFeatures, excludedFeatures, parameters, null)
    }

    /**
     * @param preset the name of the preset the application was made from, which has to be among the
     * requested features, or {@code null}
     */
    ProjectManifest(String grailsVersion, ApplicationType applicationType, String name, String defaultPackage,
                    GenerationOptions options, String generationState, List<RequestedFeature> requestedFeatures,
                    List<RequestedFeature> excludedFeatures, Map<String, String> parameters, String preset) {
        this.preset = preset
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

    /**
     * @return whether the baseline of the project was written for an application that was there, from
     * what the application is taken to be, see {@link #ADOPTED}
     * @since 9.0
     */
    boolean isAdopted() {
        return generationState == ADOPTED
    }

    /**
     * @return whether the project has a baseline of the generator, which a feature is added against:
     * the one the application was generated with, or the one that was adopted for it
     * @since 9.0
     */
    boolean hasBaseline() {
        return generationState == TRACKED || generationState == ADOPTED
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
        // only of an application that was made from a preset, so that the manifest of any other is as it was
        if (preset != null) {
            map.put('preset', preset)
        }
        map.put('requestedFeatures', requestedFeatures.collect { RequestedFeature each -> each.toMap() })
        map.put('excludedFeatures', excludedFeatures.collect { RequestedFeature each -> each.toMap() })
        map.put('parameters', new LinkedHashMap<String, Object>(parameters))
        return StateYaml.dump(map)
    }

    @PackageScope
    static ProjectManifest read(byte[] bytes) {
        return read(bytes, false)
    }

    @PackageScope
    static ProjectManifest read(byte[] bytes, boolean missingOptionsAsDefaults) {
        StateSection root = new StateSection(ProjectState.MANIFEST, '', StateYaml.load(bytes, ProjectState.MANIFEST, StateLimits.MANIFEST_LINES))
        format(root, FORMAT)
        String grailsVersion = root.matching('grailsVersion', VERSION, 'a version')
        ApplicationType applicationType = applicationType(root)
        String name = root.text('name')
        String defaultPackage = root.text('defaultPackage')
        // the two are what an application can be created with, and nothing else: the name is one step
        // of a path and the package is a package, as the create commands take them
        if (name.contains('/') || !PackDescriptorParser.isPathBelowRoot(name)) {
            throw root.problem('name', 'is not a name an application can be created with')
        }
        if (!created(defaultPackage + '.' + name, name, defaultPackage)) {
            throw root.problem('defaultPackage', 'is not a package an application can be created with')
        }
        ProjectManifest manifest = new ProjectManifest(
                grailsVersion,
                applicationType,
                name,
                defaultPackage,
                GenerationOptions.read(root.section('options'), missingOptionsAsDefaults),
                root.oneOf('generationState', [TRACKED, UNTRACKED, ADOPTED]),
                root.sections('requestedFeatures', StateLimits.MAX_FEATURES).collect { StateSection each -> RequestedFeature.read(each) },
                root.sections('excludedFeatures', StateLimits.MAX_FEATURES).collect { StateSection each -> RequestedFeature.read(each) },
                root.texts('parameters', StateLimits.MAX_PARAMETERS),
                root.has('preset') ? root.matching('preset', FEATURE_NAME, 'the name of a preset') : null)
        if (manifest.preset != null && !manifest.requestedFeatures.any { RequestedFeature each -> each.name == manifest.preset }) {
            throw root.problem('preset', "is ${manifest.preset}, which is not among the requestedFeatures: the preset an application is made from is asked for with it")
        }
        root.end()
        return manifest
    }

    private static boolean created(String qualified, String name, String defaultPackage) {
        try {
            Project project = NameUtils.parse(qualified)
            return project.getName() == name && project.getPackageName() == defaultPackage
        } catch (IllegalArgumentException ignored) {
            return false
        }
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
     * pack that was taken by the coordinates of its plugin has those and its version.
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
                    section.optionalMatching('coordinates', COORDINATES, 'a group and an artifact, group:artifact', StateLimits.MAX_COORDINATES),
                    section.optionalMatching('version', VERSION, 'a version'))
            section.end()
            return feature
        }
    }
}
