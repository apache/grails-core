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
import org.grails.forge.application.OperatingSystem
import java.util.regex.Pattern

/**
 * The lock of a project, {@code .grails/forge.lock.yml}: what the {@link ProjectManifest} came to when
 * the application was generated. It names the manifest and the baseline it was written with by their
 * digests, so that a change to one of the three files without the others is seen.
 *
 * @since 9.0
 */
@CompileStatic
final class ProjectLock {

    /** The format of the lock this version of Grails writes and reads */
    public static final int FORMAT = 1

    /** The name of the generator in a lock Forge writes */
    public static final String GENERATOR = 'grails-forge'

    /** The files are written with the line endings their templates have, on every operating system */
    public static final String LINE_ENDINGS_OF_TEMPLATES = 'preserve'

    @PackageScope
    static final Pattern SHA256 = Pattern.compile(/[0-9a-f]{64}/)

    final String manifestSha256
    final String generatorName
    final String generatorVersion
    final String grailsVersion
    final int templateApi
    final ApplicationType applicationType
    final GenerationOptions options
    final OperatingSystem operatingSystem
    final String lineEndings
    final List<LockedFeature> features
    final Map<String, String> parameters
    final String baselineIndexSha256

    ProjectLock(String manifestSha256, String generatorName, String generatorVersion, String grailsVersion, int templateApi,
                ApplicationType applicationType, GenerationOptions options, OperatingSystem operatingSystem, String lineEndings,
                List<LockedFeature> features, Map<String, String> parameters, String baselineIndexSha256) {
        this.manifestSha256 = manifestSha256
        this.generatorName = generatorName
        this.generatorVersion = generatorVersion
        this.grailsVersion = grailsVersion
        this.templateApi = templateApi
        this.applicationType = applicationType
        this.options = options
        this.operatingSystem = operatingSystem
        this.lineEndings = lineEndings
        this.features = Collections.unmodifiableList(new ArrayList<LockedFeature>(features))
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<String, String>(parameters))
        this.baselineIndexSha256 = baselineIndexSha256
    }

    /**
     * @return the names of the features of the application, in the order they were applied in
     */
    List<String> getFeatureNames() {
        return features.collect { LockedFeature each -> each.name }
    }

    @PackageScope
    byte[] toBytes() {
        Map<String, Object> map = new LinkedHashMap<>()
        map.put('format', FORMAT)
        map.put('manifest', [sha256: manifestSha256] as Map<String, Object>)
        map.put('generator', [name: generatorName, version: generatorVersion] as Map<String, Object>)
        map.put('grailsVersion', grailsVersion)
        map.put('templateApi', templateApi)
        map.put('applicationType', GenerationOptions.written(applicationType))
        map.put('options', options.toMap())
        if (operatingSystem != null) {
            map.put('operatingSystem', GenerationOptions.written(operatingSystem))
        }
        map.put('lineEndings', lineEndings)
        map.put('features', features.collect { LockedFeature each -> each.toMap() })
        map.put('parameters', new LinkedHashMap<String, Object>(parameters))
        map.put('baselineIndex', [sha256: baselineIndexSha256] as Map<String, Object>)
        return StateYaml.dump(map)
    }

    @PackageScope
    static ProjectLock read(byte[] bytes) {
        StateSection root = new StateSection(ProjectState.LOCK, '', StateYaml.load(bytes, ProjectState.LOCK))
        ProjectManifest.format(root, FORMAT)
        StateSection manifest = root.section('manifest')
        String manifestSha256 = manifest.matching('sha256', SHA256, 'a SHA-256 digest in hexadecimal')
        manifest.end()
        StateSection generator = root.section('generator')
        String generatorName = generator.matching('name', ProjectManifest.FEATURE_NAME, 'the name of a generator')
        String generatorVersion = generator.matching('version', ProjectManifest.VERSION, 'a version')
        generator.end()
        String grailsVersion = root.matching('grailsVersion', ProjectManifest.VERSION, 'a version')
        int templateApi = root.number('templateApi', 1, 9999)
        ApplicationType applicationType = ProjectManifest.applicationType(root)
        GenerationOptions options = GenerationOptions.read(root.section('options'))
        OperatingSystem operatingSystem = null
        if (root.has('operatingSystem')) {
            List<OperatingSystem> systems = OperatingSystem.values() as List<OperatingSystem>
            String written = root.oneOf('operatingSystem', systems.collect { OperatingSystem each -> GenerationOptions.written(each) })
            operatingSystem = systems.find { OperatingSystem each -> GenerationOptions.written(each) == written }
        }
        String lineEndings = root.oneOf('lineEndings', [LINE_ENDINGS_OF_TEMPLATES])
        List<LockedFeature> features = root.sections('features', ProjectManifest.MAX_FEATURES).collect { StateSection each -> LockedFeature.read(each) }
        Set<String> names = new HashSet<>()
        for (LockedFeature feature : features) {
            if (!names.add(feature.name)) {
                throw root.problem('features', "has the feature ${feature.name} more than once")
            }
        }
        Map<String, String> parameters = root.texts('parameters', ProjectManifest.MAX_PARAMETERS)
        StateSection baseline = root.section('baselineIndex')
        String baselineIndexSha256 = baseline.matching('sha256', SHA256, 'a SHA-256 digest in hexadecimal')
        baseline.end()
        root.end()
        return new ProjectLock(manifestSha256, generatorName, generatorVersion, grailsVersion, templateApi, applicationType, options,
                operatingSystem, lineEndings, features, parameters, baselineIndexSha256)
    }

    /**
     * A feature of the application and where it came from. A feature of the framework has only its
     * name. A feature of a feature pack that is not part of Forge has, as far as they are known, where
     * the pack was read from, the coordinates and the version of the artifact it is in, and the digest
     * of that artifact.
     */
    @CompileStatic
    static final class LockedFeature {

        /** A feature of the framework that is written as a class */
        public static final String FRAMEWORK = 'framework'

        /** A feature of the framework that is written as a feature pack inside Forge */
        public static final String BUNDLED_PACK = 'bundled-pack'

        /** A feature of a feature pack that is not part of Forge */
        public static final String PACK = 'pack'

        final String name
        final String source
        final String origin
        final String coordinates
        final String version
        final String sha256

        LockedFeature(String name, String source, String origin = null, String coordinates = null, String version = null, String sha256 = null) {
            this.name = name
            this.source = source
            this.origin = origin
            this.coordinates = coordinates
            this.version = version
            this.sha256 = sha256
        }

        @PackageScope
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>()
            map.put('name', name)
            map.put('source', source)
            if (origin != null) {
                map.put('origin', origin)
            }
            if (coordinates != null) {
                map.put('coordinates', coordinates)
            }
            if (version != null) {
                map.put('version', version)
            }
            if (sha256 != null) {
                map.put('sha256', sha256)
            }
            return map
        }

        @PackageScope
        static LockedFeature read(StateSection section) {
            String name = section.matching('name', ProjectManifest.FEATURE_NAME, 'the name of a feature')
            String source = section.oneOf('source', [FRAMEWORK, BUNDLED_PACK, PACK])
            LockedFeature feature = new LockedFeature(
                    name,
                    source,
                    section.optionalText('origin'),
                    section.optionalMatching('coordinates', ProjectManifest.COORDINATES, 'a group and an artifact, group:artifact'),
                    section.optionalMatching('version', ProjectManifest.VERSION, 'a version'),
                    section.optionalMatching('sha256', SHA256, 'a SHA-256 digest in hexadecimal'))
            if (source != PACK && (feature.origin != null || feature.coordinates != null || feature.version != null || feature.sha256 != null)) {
                throw section.problem('source', "is ${source}, and only a feature of a feature pack that is not part of Forge has an origin, coordinates, a version or a digest")
            }
            section.end()
            return feature
        }
    }
}
