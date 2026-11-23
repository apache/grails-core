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
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * The lock of a project, {@code .grails/forge.lock.yml}: what the {@link ProjectManifest} came to when
 * the application was generated. It names the manifest and the index of the baseline it was written
 * with by their digests, and ends with the digest of everything of itself that comes before, its seal.
 * A change to one of the three files that is made without the others is therefore seen, whichever
 * file it is made to. The digests are no signature: whoever changes all three and writes the digests
 * again has a record that reads.
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

    private static final String SEAL = 'seal:\n  sha256: '

    // the generator as a lock names it, with the identity of its build after its version
    private static final Pattern WITH_BUILD = Pattern.compile(/(?m)^(generator:\n  name: [^\n]*\n  version: [^\n]*\n)  sha256: [0-9a-f]{64}\n/)

    final String manifestSha256
    final String generatorName
    final String generatorVersion

    /**
     * The identity of the build of the generator that wrote the lock: the digest of the sources,
     * templates and feature packs it generates from, which tells two builds of one version apart, as
     * two snapshots are. {@code null} in a lock that was written without it.
     */
    final String generatorSha256
    final String grailsVersion
    final int templateApi
    final ApplicationType applicationType
    final GenerationOptions options
    final String lineEndings
    final List<LockedRepository> repositories
    final int repositoriesNotNamed
    final List<LockedFeature> features
    final Map<String, String> parameters
    final String baselineIndexSha256

    ProjectLock(String manifestSha256, String generatorName, String generatorVersion, String grailsVersion, int templateApi,
                ApplicationType applicationType, GenerationOptions options, String lineEndings, List<LockedRepository> repositories,
                List<LockedFeature> features, Map<String, String> parameters, String baselineIndexSha256) {
        this(manifestSha256, generatorName, generatorVersion, grailsVersion, templateApi, applicationType, options, lineEndings, repositories, 0,
                features, parameters, baselineIndexSha256)
    }

    /**
     * @param repositoriesNotNamed how many repositories were given beyond those that are named
     */
    ProjectLock(String manifestSha256, String generatorName, String generatorVersion, String grailsVersion, int templateApi,
                ApplicationType applicationType, GenerationOptions options, String lineEndings, List<LockedRepository> repositories,
                int repositoriesNotNamed, List<LockedFeature> features, Map<String, String> parameters, String baselineIndexSha256) {
        this(manifestSha256, generatorName, generatorVersion, null, grailsVersion, templateApi, applicationType, options, lineEndings, repositories,
                repositoriesNotNamed, features, parameters, baselineIndexSha256)
    }

    /**
     * @param generatorSha256 the identity of the build of the generator, see {@link #generatorSha256}, or
     * {@code null} when it is not known
     * @param repositoriesNotNamed how many repositories were given beyond those that are named
     * @since 9.0
     */
    ProjectLock(String manifestSha256, String generatorName, String generatorVersion, String generatorSha256, String grailsVersion, int templateApi,
                ApplicationType applicationType, GenerationOptions options, String lineEndings, List<LockedRepository> repositories,
                int repositoriesNotNamed, List<LockedFeature> features, Map<String, String> parameters, String baselineIndexSha256) {
        this.repositoriesNotNamed = repositoriesNotNamed
        this.manifestSha256 = manifestSha256
        this.generatorName = generatorName
        this.generatorVersion = generatorVersion
        this.generatorSha256 = generatorSha256
        this.grailsVersion = grailsVersion
        this.templateApi = templateApi
        this.applicationType = applicationType
        this.options = options
        this.lineEndings = lineEndings
        this.repositories = Collections.unmodifiableList(new ArrayList<LockedRepository>(repositories))
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
        Map<String, Object> generator = [name: generatorName, version: generatorVersion] as Map<String, Object>
        if (generatorSha256 != null) {
            generator.put('sha256', generatorSha256)
        }
        map.put('generator', generator)
        map.put('grailsVersion', grailsVersion)
        map.put('templateApi', templateApi)
        map.put('applicationType', GenerationOptions.written(applicationType))
        map.put('options', options.toMap())
        map.put('lineEndings', lineEndings)
        if (repositories) {
            map.put('repositories', repositories.collect { LockedRepository each -> each.toMap() })
        }
        if (repositoriesNotNamed > 0) {
            map.put('repositoriesNotNamed', repositoriesNotNamed)
        }
        map.put('features', features.collect { LockedFeature each -> each.toMap() })
        map.put('parameters', new LinkedHashMap<String, Object>(parameters))
        map.put('baselineIndex', [sha256: baselineIndexSha256] as Map<String, Object>)
        byte[] sealed = StateYaml.dump(map)
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        bytes.write(sealed)
        bytes.write("${SEAL}${StateYaml.sha256(sealed)}\n".toString().getBytes(StandardCharsets.UTF_8))
        return bytes.toByteArray()
    }

    /**
     * Whether two locks say the same but for the build of the generator that wrote them, as two builds
     * of one version that generate the same files from the same choices write them. The seal is left
     * out with the identity, since it is the digest of the rest.
     *
     * @param one the bytes of a lock
     * @param other the bytes of another
     * @since 9.0
     */
    static boolean sameButForTheBuild(byte[] one, byte[] other) {
        return withoutTheBuild(one) == withoutTheBuild(other)
    }

    /**
     * @param lock the bytes of a lock
     * @return its text up to its seal, without the identity of the build of the generator
     * @since 9.0
     */
    static String withoutTheBuild(byte[] lock) {
        String text = new String(lock, StandardCharsets.ISO_8859_1)
        int seal = text.lastIndexOf('\n' + SEAL)
        return (seal < 0 ? text : text.substring(0, seal + 1)).replaceFirst(WITH_BUILD, '$1')
    }

    @PackageScope
    static ProjectLock read(byte[] bytes) {
        return read(bytes, false)
    }

    @PackageScope
    static ProjectLock read(byte[] bytes, boolean missingOptionsAsDefaults) {
        StateSection root = new StateSection(ProjectState.LOCK, '', StateYaml.load(bytes, ProjectState.LOCK, StateLimits.LOCK_LINES))
        ProjectManifest.format(root, FORMAT)
        StateSection manifest = root.section('manifest')
        String manifestSha256 = manifest.matching('sha256', SHA256, 'a SHA-256 digest in hexadecimal')
        manifest.end()
        StateSection generator = root.section('generator')
        String generatorName = generator.matching('name', ProjectManifest.FEATURE_NAME, 'the name of a generator')
        String generatorVersion = generator.matching('version', ProjectManifest.VERSION, 'a version')
        // a lock that a build before the identity was recorded wrote has none
        String generatorSha256 = generator.optionalMatching('sha256', SHA256, 'a SHA-256 digest in hexadecimal')
        generator.end()
        String grailsVersion = root.matching('grailsVersion', ProjectManifest.VERSION, 'a version')
        int templateApi = root.number('templateApi', 1, 9999)
        ApplicationType applicationType = ProjectManifest.applicationType(root)
        GenerationOptions options = GenerationOptions.read(root.section('options'), missingOptionsAsDefaults)
        String lineEndings = root.oneOf('lineEndings', [LINE_ENDINGS_OF_TEMPLATES])
        List<LockedRepository> repositories = !root.has('repositories') ? [] as List<LockedRepository> :
                root.sections('repositories', StateLimits.MAX_REPOSITORIES).collect { StateSection each -> LockedRepository.read(each) }
        int repositoriesNotNamed = root.has('repositoriesNotNamed') ? root.number('repositoriesNotNamed', 1, 1_000_000) : 0
        if (repositoriesNotNamed > 0 && repositories.size() != StateLimits.MAX_REPOSITORIES) {
            throw root.problem('repositoriesNotNamed', "is there although fewer than ${StateLimits.MAX_REPOSITORIES} repositories are named")
        }
        List<LockedFeature> features = root.sections('features', StateLimits.MAX_FEATURES).collect { StateSection each -> LockedFeature.read(each) }
        Set<String> names = new HashSet<>()
        for (LockedFeature feature : features) {
            if (!names.add(feature.name)) {
                throw root.problem('features', "has the feature ${feature.name} more than once")
            }
        }
        Map<String, String> parameters = root.texts('parameters', StateLimits.MAX_PARAMETERS)
        StateSection baseline = root.section('baselineIndex')
        String baselineIndexSha256 = baseline.matching('sha256', SHA256, 'a SHA-256 digest in hexadecimal')
        baseline.end()
        StateSection seal = root.section('seal')
        String sealed = seal.matching('sha256', SHA256, 'a SHA-256 digest in hexadecimal')
        seal.end()
        root.end()
        // the seal is the end of the file and is of the bytes before it, as they are
        byte[] end = "${SEAL}${sealed}\n".toString().getBytes(StandardCharsets.UTF_8)
        int before = bytes.length - end.length
        if (before < 0 || !Arrays.equals(Arrays.copyOfRange(bytes, before, bytes.length), end) ||
                StateYaml.sha256(Arrays.copyOfRange(bytes, 0, before)) != sealed) {
            throw new ProjectStateException("${ProjectState.LOCK} was changed after it was written: it does not have the digest it ends with")
        }
        return new ProjectLock(manifestSha256, generatorName, generatorVersion, generatorSha256, grailsVersion, templateApi, applicationType, options,
                lineEndings, repositories, repositoriesNotNamed, features, parameters, baselineIndexSha256)
    }

    /**
     * A repository the build of the application was generated with in place of the ones Grails gives
     * it, which is done with {@code GRAILS_REPO_URL}. What is of the machine the application was
     * generated on, a directory or the address of a repository with a name and a password in it, is
     * not said: only that there was one.
     */
    @CompileStatic
    static final class LockedRepository {

        /** A repository at an address */
        public static final String REMOTE = 'remote'

        /** A directory of the machine the application was generated on */
        public static final String LOCAL = 'local'

        /** The local Maven repository of whoever builds the application */
        public static final String MAVEN_LOCAL = 'maven-local'

        /** Maven Central, placed among the others */
        public static final String MAVEN_CENTRAL = 'maven-central'

        final String kind
        final String url

        LockedRepository(String kind, String url = null) {
            this.kind = kind
            this.url = url
        }

        @PackageScope
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>()
            map.put('kind', kind)
            if (url != null) {
                map.put('url', url)
            }
            return map
        }

        @PackageScope
        static LockedRepository read(StateSection section) {
            String kind = section.oneOf('kind', [REMOTE, LOCAL, MAVEN_LOCAL, MAVEN_CENTRAL])
            String url = section.has('url') ? section.text('url', StateLimits.MAX_ADDRESS) : null
            if (url != null && (kind != REMOTE || !isPublicAddress(url))) {
                throw section.problem('url', 'must be the https address of a repository, without a name, a password, a query or a fragment, and only a remote repository has one')
            }
            section.end()
            return new LockedRepository(kind, url)
        }
    }

    /**
     * @param address the address of a repository
     * @return whether the address can be committed with a project: it is an {@code https} address,
     * in whatever case that is written, of a length the state can hold and without a name, a
     * password, a query or a fragment in it: see {@link #recordableAddress(String)}.
     */
    static boolean isPublicAddress(String address) {
        return address != null && address == recordableAddress(address)
    }

    /**
     * The one rule for what of the address of a repository is written into the state of a project,
     * which is committed: the scheme, the host, the port and the path of an {@code https} address.
     * A query and a fragment are left out, because a token is given to a repository in them; an
     * address with a name or a password in it, an address that is not {@code https}, a directory and
     * an address longer than the state takes are not written at all.
     *
     * @param address the address of a repository, as it was given
     * @return what of it can be committed, or {@code null}
     */
    static String recordableAddress(String address) {
        if (address == null) {
            return null
        }
        try {
            URI uri = new URI(address)
            if (!'https'.equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null) {
                return null
            }
            String recorded = "${uri.getScheme()}://${uri.getHost()}${uri.getPort() >= 0 ? ':' + uri.getPort() : ''}${uri.getRawPath() ?: ''}".toString()
            return StateYaml.isLine(recorded, StateLimits.MAX_ADDRESS) ? recorded : null
        } catch (URISyntaxException ignored) {
            return null
        }
    }

    /**
     * A feature of the application and where it came from. A feature of the framework has only its
     * name. A feature of a feature pack that is not part of Forge has, as far as they are known, the
     * address of the repository the pack was taken from, the coordinates and the version of the
     * artifact it is in, and the digest of that artifact; and, when the coordinates were those a
     * catalog gave for the name of the feature, the address of that catalog and the digest of the
     * catalog as it was read. A pack that was given as a directory or a file has the digest of its
     * content in their place ({@code contentSha256}, see {@code Pack.getContentSha256()}): where it was
     * is a path of the computer it was read on, and what it was is the same on every computer.
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
        final String catalog
        final String catalogSha256
        /** The digest of the content of a pack that was given as a directory or a file, or {@code null} */
        final String contentSha256

        LockedFeature(String name, String source, String origin = null, String coordinates = null, String version = null, String sha256 = null,
                      String catalog = null, String catalogSha256 = null, String contentSha256 = null) {
            this.contentSha256 = contentSha256
            this.catalog = catalog
            this.catalogSha256 = catalogSha256
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
            if (contentSha256 != null) {
                map.put('contentSha256', contentSha256)
            }
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
            if (catalog != null) {
                map.put('catalog', catalog)
            }
            if (catalogSha256 != null) {
                map.put('catalogSha256', catalogSha256)
            }
            return map
        }

        @PackageScope
        static LockedFeature read(StateSection section) {
            String name = section.matching('name', ProjectManifest.FEATURE_NAME, 'the name of a feature')
            String source = section.oneOf('source', [FRAMEWORK, BUNDLED_PACK, PACK])
            String contentSha256 = section.optionalMatching('contentSha256', SHA256, 'a SHA-256 digest in hexadecimal')
            LockedFeature feature = new LockedFeature(
                    name,
                    source,
                    section.has('origin') ? section.text('origin', StateLimits.MAX_ADDRESS) : null,
                    section.optionalMatching('coordinates', ProjectManifest.COORDINATES, 'a group and an artifact, group:artifact', StateLimits.MAX_COORDINATES),
                    section.optionalMatching('version', ProjectManifest.VERSION, 'a version'),
                    section.optionalMatching('sha256', SHA256, 'a SHA-256 digest in hexadecimal'),
                    section.has('catalog') ? section.text('catalog', StateLimits.MAX_ADDRESS) : null,
                    section.optionalMatching('catalogSha256', SHA256, 'a SHA-256 digest in hexadecimal'),
                    contentSha256)
            // the content of a pack of a directory or a file stands for what it was; a jar of a repository has its own digest
            if (contentSha256 != null && (source != PACK || feature.coordinates != null || feature.sha256 != null)) {
                throw section.problem('contentSha256', 'is the digest of the content of a feature pack that was given as a directory or a file, ' +
                        'and only such a feature has one')
            }
            if (feature.catalog != null && !isPublicAddress(feature.catalog)) {
                throw section.problem('catalog', 'must be the https address of a catalog, without a name, a password, a query or a fragment')
            }
            // what a catalog gives is the coordinates of an artifact and its digest
            if ((feature.catalog != null && feature.catalogSha256 == null) ||
                    (feature.catalogSha256 != null && (feature.coordinates == null || feature.version == null || feature.sha256 == null))) {
                throw section.problem('catalogSha256', 'is the digest of the catalog that gave the coordinates, the version and the digest of the artifact, and goes with them')
            }
            if (source != PACK && (feature.origin != null || feature.coordinates != null || feature.version != null || feature.sha256 != null)) {
                throw section.problem('source', "is ${source}, and only a feature of a feature pack that is not part of Forge has an origin, coordinates, a version or a digest")
            }
            if (feature.origin != null && !isPublicAddress(feature.origin)) {
                throw section.problem('origin', 'must be the https address of a repository, without a name, a password, a query or a fragment')
            }
            section.end()
            return feature
        }
    }
}
