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
package org.apache.grails.forge.core.pack

import groovy.transform.CompileStatic
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.apache.grails.forge.core.pack.PackDescriptor.PackBuildPlugin
import org.apache.grails.forge.core.pack.PackDescriptor.PackDependency
import org.apache.grails.forge.core.pack.PackDescriptor.PackFile

/**
 * A feature pack that was read: its descriptor, and the content of the files it adds to an application,
 * as it was when the pack was read.
 *
 * @since 9.0
 * @see PackLoader
 */
@CompileStatic
final class Pack {

    /** The name of the descriptor in a pack */
    public static final String DESCRIPTOR = 'feature.yml'

    /** The directory of a pack that holds the files its descriptor names */
    public static final String TEMPLATES = 'templates'

    /** Where a pack is in the jar of the plugin it belongs to */
    public static final String JAR_DIRECTORY = 'META-INF/grails-forge'

    /** Where the packs Forge has in itself are among its resources, each in a directory of its name */
    public static final String BUNDLED_DIRECTORY = 'META-INF/grails-forge-bundled'

    /** The resource that lists the names of the packs Forge has in itself, one on a line */
    public static final String BUNDLED_INDEX = 'META-INF/grails-forge-bundled/packs'

    /** The end of the name of a file of a pack that is a template and not a file written as it is */
    public static final String TEMPLATE_SUFFIX = '.gsp'

    /** The most things one line of {@link #summary()} names */
    public static final int MAX_SUMMARY_ITEMS = 20

    private static final int MAX_SUMMARY_ITEM = 120

    private final PackDescriptor descriptor
    private final String origin
    private final Map<String, byte[]> files
    private final boolean bundled
    private final PackArtifact artifact
    private final String contentSha256

    /**
     * @param descriptor the descriptor of the pack
     * @param origin where the pack came from, for messages
     * @param files the content of the file of each {@code from} of the descriptor
     */
    Pack(PackDescriptor descriptor, String origin, Map<String, byte[]> files) {
        this(descriptor, origin, files, false)
    }

    /**
     * @param descriptor the descriptor of the pack
     * @param origin where the pack came from, for messages
     * @param files the content of the file of each {@code from} of the descriptor
     * @param bundled whether the pack is one Forge has in itself
     */
    Pack(PackDescriptor descriptor, String origin, Map<String, byte[]> files, boolean bundled) {
        this(descriptor, origin, files, bundled, null, null)
    }

    /**
     * A pack that was read with the bytes of its descriptor, which has a {@link #getContentSha256()}.
     *
     * @param descriptorBytes the bytes of {@code feature.yml} as they were read
     */
    Pack(PackDescriptor descriptor, String origin, Map<String, byte[]> files, byte[] descriptorBytes) {
        this(descriptor, origin, files, false, null, contentSha256(descriptorBytes, files))
    }

    private Pack(PackDescriptor descriptor, String origin, Map<String, byte[]> files, boolean bundled, PackArtifact artifact, String contentSha256) {
        this.artifact = artifact
        this.bundled = bundled
        this.descriptor = descriptor
        this.origin = origin
        this.contentSha256 = contentSha256
        Map<String, byte[]> copies = new LinkedHashMap<>()
        files.each { String from, byte[] content -> copies.put(from, content.clone()) }
        this.files = Collections.unmodifiableMap(copies)
    }

    PackDescriptor getDescriptor() {
        return descriptor
    }

    /**
     * @return whether the pack is one Forge has in itself: a feature of the framework that is written
     * as a pack
     */
    boolean isBundled() {
        return bundled
    }

    /**
     * @return where the pack came from, for messages
     */
    String getOrigin() {
        return origin
    }

    /**
     * @return the published jar the pack was read from, or {@code null} for a pack that was not taken
     * from a repository
     */
    PackArtifact getArtifact() {
        return artifact
    }

    /**
     * What the pack is, wherever it was read from: the SHA-256, in lowercase hexadecimal, of its
     * {@code feature.yml} and of the files under {@code templates} the descriptor names, as the bytes
     * they are. A directory, the jar it is packaged into and a copy of either on another computer have
     * the same one when their bytes are the same; a line ending Git changed on the way is another byte.
     * Each file counts with its path in the pack and its length, the files in the order of their paths:
     * {@code feature.yml}, then {@code templates/<from>}.
     *
     * @return the digest, or {@code null} for a pack that was not read with the bytes of its descriptor
     */
    String getContentSha256() {
        return contentSha256
    }

    private static String contentSha256(byte[] descriptorBytes, Map<String, byte[]> files) {
        MessageDigest digest = MessageDigest.getInstance('SHA-256')
        entry(digest, DESCRIPTOR, descriptorBytes)
        for (String from : new TreeSet<String>(files.keySet())) {
            entry(digest, "${TEMPLATES}/${from}".toString(), files.get(from))
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    // a path, a byte that is in no path, the length in eight bytes and the content: no two packs give the same bytes
    private static void entry(MessageDigest digest, String path, byte[] content) {
        digest.update(path.getBytes(StandardCharsets.UTF_8))
        digest.update((byte) 0)
        long length = content.length
        for (int shift = 56; shift >= 0; shift -= 8) {
            digest.update((byte) (length >>> shift))
        }
        digest.update(content)
    }

    /**
     * @param artifact the published jar this pack was read from
     * @return this pack, with the jar it was read from
     */
    Pack withArtifact(PackArtifact artifact) {
        if (bundled) {
            throw new IllegalArgumentException("The feature pack ${origin} is one of Forge and comes from no repository")
        }
        return new Pack(descriptor, origin, files, false, Objects.requireNonNull(artifact), contentSha256)
    }

    /**
     * @param from the {@code from} of a file of the descriptor
     * @return a copy of the content of the file it names
     */
    byte[] content(String from) {
        byte[] content = files.get(from)
        if (content == null) {
            throw new IllegalArgumentException("The feature pack ${origin} has no file ${from}")
        }
        return content.clone()
    }

    /**
     * @param from the {@code from} of a file of the descriptor
     * @return whether the file is a template, which is rendered, and not a file that is written as it is
     */
    static boolean isTemplate(String from) {
        return from.toLowerCase(Locale.ROOT).endsWith(TEMPLATE_SUFFIX)
    }

    /**
     * What the pack adds to an application, for whoever is about to generate one with it: a line for
     * each kind of thing, and none for a kind the pack has nothing of. What runs as code, when the
     * application is generated or when it is built, comes first and is said to. A line names at most
     * {@link #MAX_SUMMARY_ITEMS} things and says how many more there are.
     *
     * @return the lines, which are text of the descriptor as the parser let it through
     */
    List<String> summary() {
        List<String> lines = []
        List<String> templates = descriptor.files.findAll { PackFile each -> isTemplate(each.from) }.collect { PackFile each -> each.from }.unique()
        if (templates) {
            lines << "templates, which are code that runs when the application is generated: ${some(templates)}".toString()
        }
        List<String> blocks = (descriptor.buildExtensions.keySet() + descriptor.ofForge.buildExtensionParts.keySet()).toList()
        if (blocks) {
            lines << "build file blocks, which are code of the build: ${some(blocks)}".toString()
        }
        if (descriptor.buildBlocks) {
            lines << "text it adds to the build file as it is, which is code of the build: ${some(descriptor.buildBlocks.keySet())}".toString()
        }
        List<PackDependency> ofTheBuild = descriptor.dependencies.findAll { PackDependency each -> each.scope == PackDescriptorParser.BUILDSCRIPT }
        if (ofTheBuild) {
            lines << "dependencies of the build itself, which are code of the build: ${some(ofTheBuild.collect { PackDependency each -> coordinates(each) })}".toString()
        }
        if (descriptor.files) {
            // a file outside the sources can be code of the build or of what builds it: none of those is left out
            List<String> sources = descriptor.files.collect { PackFile each -> each.to }.findAll { String to -> isSource(to) }
            List<String> others = descriptor.files.collect { PackFile each -> each.to }.findAll { String to -> !isSource(to) }
            if (others) {
                lines << "files it writes outside the sources: ${others.collect { String to -> item(to) }.join(', ')}".toString()
            }
            if (sources) {
                lines << "source files it writes: ${some(sources)}".toString()
            }
        }
        if (descriptor.buildPlugins) {
            lines << "build plugins: ${some(descriptor.buildPlugins.collect { PackBuildPlugin each -> each.version ? each.id + ':' + each.version : each.id })}".toString()
        }
        if (descriptor.buildProperties) {
            lines << "build properties: ${some(descriptor.buildProperties.collect { String name, String value -> name + '=' + value })}".toString()
        }
        if (descriptor.platforms) {
            lines << "platforms: ${some(descriptor.platforms.collect { PackDependency each -> coordinates(each) })}".toString()
        }
        List<PackDependency> ofTheApplication = descriptor.dependencies - ofTheBuild
        if (ofTheApplication) {
            lines << "dependencies: ${some(ofTheApplication.collect { PackDependency each -> each.scope + ' ' + coordinates(each) })}".toString()
        }
        int settings = leaves(descriptor.configuration)
        for (PackOfForge.Settings each : descriptor.ofForge.settings) {
            settings += leaves(each.values)
        }
        if (settings > 0) {
            lines << "configuration: ${settings} ${settings == 1 ? 'setting' : 'settings'}".toString()
        }
        if (descriptor.readme) {
            lines << 'a section of the readme'
        }
        if (descriptor.parameters) {
            lines << "options, given as --feature-option ${descriptor.name}.NAME=VALUE: ${some(descriptor.parameters.collect { PackParameter each -> each.usage })}".toString()
        }
        if (descriptor.requires) {
            lines << "requires the features ${some(descriptor.requires)}".toString()
        }
        if (descriptor.conflicts) {
            lines << "cannot be used with the features ${some(descriptor.conflicts)}".toString()
        }
        return lines
    }

    private static boolean isSource(String to) {
        return to.startsWith('src/main/') || to.startsWith('src/test/') || to.startsWith('src/integration-test/') ||
                (to.startsWith('grails-app/') && !to.startsWith('grails-app/conf/'))
    }

    private static String some(Collection<String> all) {
        List<String> shown = all.take(MAX_SUMMARY_ITEMS).collect { String each -> item(each) }
        String text = shown.join(', ')
        return all.size() > shown.size() ? "${text} and ${all.size() - shown.size()} more" : text
    }

    // one thing of a line: cut when long, and in quotes when it could be read as several
    private static String item(String each) {
        String text = each.length() > MAX_SUMMARY_ITEM ?
                each.substring(0, each.offsetByCodePoints(0, each.codePointCount(0, MAX_SUMMARY_ITEM))) + '...' : each
        return text.contains(',') ? "'${text}'".toString() : text
    }

    private static String coordinates(PackDependency dependency) {
        String coordinates = dependency.groupId + ':' + dependency.artifactId
        if (dependency.version) {
            coordinates += ':' + dependency.version
        }
        if (dependency.capabilities) {
            coordinates += " with ${dependency.capabilities.size() == 1 ? 'the capability' : 'the capabilities'} ${few(dependency.capabilities)}"
        }
        if (!dependency.excludes) {
            return coordinates
        }
        return "${coordinates} without ${few(dependency.excludes)}"
    }

    private static String few(List<String> all) {
        List<String> shown = all.take(3)
        String more = all.size() > shown.size() ? " and ${all.size() - shown.size()} more" : ''
        return "${shown.join(' and ')}${more}"
    }

    private static int leaves(Object value) {
        if (value instanceof Map) {
            int count = 0
            for (Object each : ((Map) value).values()) {
                count += leaves(each)
            }
            return count
        }
        return 1
    }

    @Override
    String toString() {
        return "Pack(${descriptor.name} from ${origin})"
    }
}
