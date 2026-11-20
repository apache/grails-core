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
package org.apache.grails.forge.cli.pack

import groovy.transform.CompileStatic
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.function.Consumer
import java.util.regex.Pattern
import org.apache.grails.forge.core.pack.PackDescriptor
import org.apache.grails.forge.core.pack.PackVersionRange
import org.grails.forge.feature.FeatureSelectionException

/**
 * A feature catalog as it was read: the plugins of the plugin index that have a feature pack, with the
 * versions the index records a pack for. A catalog says which plugin has the feature of a name, and in
 * which versions; it gives coordinates and the digest the jar has to have, and no pack is read from it.
 *
 * <p>The document is the JSON the plugin index publishes, with what this reads of it:</p>
 * <pre>
 * { "format": 1,
 *   "plugins": [
 *     { "coords": "com.acme:acme-audit-grails", "name": "Acme Audit", "desc": "...", "docs": "https://...",
 *       "maven-repo": "https://repo.acme.example/maven", "deprecated": false,
 *       "versions": [
 *         { "version": "2.3.0",
 *           "pack": { "format": 1, "kind": "feature", "name": "acme-audit", "templateApi": 1,
 *                     "applicationTypes": ["web"],
 *                     "compatibility": { "grails": "[9.0.0,10.0.0)", "forge": "[9.0.0,10.0.0)" },
 *                     "sha256": "...", "maven-repo": "https://repo.acme.example/maven" } } ] } ] }
 * </pre>
 * <p>{@code format} and {@code plugins} are required, and of a plugin {@code coords} and
 * {@code versions}; of a version {@code version}; of a {@code pack} its {@code format}, {@code kind}
 * ({@code feature} or {@code preset}), {@code name}, {@code templateApi}, both ranges of
 * {@code compatibility} and {@code sha256}, the SHA-256 of the main jar. Everything else can be left
 * out, and a field that is not named here is not read. The artifact of a pack is always the main jar
 * of the plugin it is listed under, in the version it is listed under: a catalog cannot give a pack
 * other coordinates. A document that is an array is the index as it was before it had a format: it
 * records no pack, and no feature is found in it.</p>
 *
 * <p>A catalog is read from elsewhere and is not trusted. What is wrong with the document as a whole
 * refuses it; a plugin with a pack that has something wrong with it is left out, with one line that
 * says what; of a plugin without a pack only its coordinates are kept. The limits are those of an index
 * many times the one that is published (0.64 MiB and 257 plugins in 2026): what is not read is checked
 * to be JSON and is not kept, so reading costs little more memory than the document has bytes.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class PackCatalog {

    /** The format of the catalog this reads */
    public static final int FORMAT = 1

    /** The most bytes a catalog can have */
    public static final int MAX_BYTES = 4 * 1024 * 1024

    /** How deep the objects and arrays of a catalog can be */
    public static final int MAX_DEPTH = 8

    /** The most values, of any kind, a catalog can have */
    public static final int MAX_VALUES = 200000

    /** The most characters of a text that is read; a longer one is no text of a catalog */
    public static final int MAX_TEXT = 4096

    /** The most characters of a number */
    public static final int MAX_NUMBER = 64

    /** The most plugins a catalog can have */
    public static final int MAX_PLUGINS = 5000

    /** The most versions a plugin of a catalog can have */
    public static final int MAX_VERSIONS = 200

    /** The most plugins that are left out of which it is kept what was wrong with them */
    public static final int MAX_PROBLEMS = 20

    /** What a plugin is left out for is one line of at most this many characters */
    public static final int MAX_PROBLEM = 300

    /** The most characters of the group and the artifact of a plugin, with the colon between them */
    public static final int MAX_PLUGIN = 120

    /** The most characters of a version */
    public static final int MAX_VERSION = 60

    private static final Pattern PART = Pattern.compile(/[A-Za-z0-9][A-Za-z0-9_.\-]*/)
    private static final Pattern NAME = Pattern.compile(/[a-z][a-z0-9]*(-[a-z0-9]+)*/)
    private static final Pattern SHA256 = Pattern.compile(/[0-9a-fA-F]{64}/)
    private static final Pattern RELEASE = Pattern.compile(/\d{1,9}(\.\d{1,9}){0,3}/)
    private static final Pattern UNREADABLE = Pattern.compile(/[\p{Cc}\p{Cf}\p{Cs}\p{Co}\p{Zl}\p{Zp}]/)
    private static final Pattern DOCS = Pattern.compile(/https:\/\/[A-Za-z0-9\-._~:\/?#!&()*+,;=%]+/)
    private static final Pattern WHOLE = Pattern.compile(/0|[1-9][0-9]{0,8}/)
    private static final Set<String> APPLICATION_TYPES = ['web', 'rest-api', 'web-plugin', 'plugin'] as Set<String>
    private static final int MAX_NAME = 100
    private static final int MAX_TITLE = 100
    private static final int MAX_DESCRIPTION = 200
    private static final int MAX_DOCS = 500
    private static final int MAX_RANGE = 200
    private static final int MAX_KEY = 200
    private static final String FEATURE = 'feature'
    private static final String PRESET = 'preset'

    // what the reader gives for a value that is read whatever it is, as long as it is no object
    private static final String ANY = 'any'
    // for a value that was not kept: one that is not read, or that is not of a kind that is
    private static final Object NOT_KEPT = new Object()
    // for a text that is longer than a text that is read
    private static final Object TOO_LONG = new Object()

    private static final Map<String, Object> OF_A_PACK = [format: ANY, kind: ANY, name: ANY, templateApi: ANY, applicationTypes: ANY,
                                                           compatibility: [grails: ANY, forge: ANY], sha256: ANY, 'maven-repo': ANY] as Map<String, Object>
    private static final Map<String, Object> OF_A_PLUGIN = [coords: ANY, name: ANY, desc: ANY, docs: ANY, 'maven-repo': ANY, deprecated: ANY,
                                                             versions: [[version: ANY, pack: OF_A_PACK]]] as Map<String, Object>

    /** The address the catalog was read from, as a message has it */
    final String address

    /** The address as it can be written into a project, or {@code null} for a file of this computer */
    final String recordableAddress

    /** The SHA-256 of the bytes that were read, in lowercase hexadecimal */
    final String sha256

    /** Whether the document is the index as it was before it recorded packs */
    final boolean withoutPacks

    /** The plugins that have a feature pack, in the order of the document */
    final List<Plugin> plugins

    /**
     * The {@code group:artifact} of the plugins the catalog lists and has no pack for that is read: one
     * without a pack, and one that is left out
     */
    final Set<String> withoutPack

    /** What was wrong with the first plugins that are left out, one line each */
    final List<String> problems

    /** How many plugins are left out */
    final int leftOut

    private PackCatalog(String address, String recordableAddress, String sha256, boolean withoutPacks, List<Plugin> plugins, Set<String> withoutPack,
                        List<String> problems, int leftOut) {
        this.address = address
        this.recordableAddress = recordableAddress
        this.sha256 = sha256
        this.withoutPacks = withoutPacks
        this.plugins = Collections.unmodifiableList(plugins)
        this.withoutPack = Collections.unmodifiableSet(withoutPack)
        this.problems = Collections.unmodifiableList(problems)
        this.leftOut = leftOut
    }

    /**
     * @param bytes the document
     * @param address where it was read from
     * @return the catalog
     * @throws PackArtifactException when the document as a whole is not a catalog, with what is wrong
     * with it and nothing else as its message. Nothing a document holds leaves this method in another
     * way: not an exception of whatever read it, and not a stack or a memory it was made to exhaust.
     */
    static PackCatalog parse(byte[] bytes, URI address) {
        try {
            return read(bytes, address)
        } catch (PackArtifactException e) {
            throw e
        } catch (OutOfMemoryError ignored) {
            // nothing is said of what the document is: it was not read to the end
            throw new PackArtifactException('it is too large for the memory this command has, and was not read')
        } catch (RuntimeException | StackOverflowError e) {
            throw new PackArtifactException("it is not a catalog: it could not be read (${e.class.simpleName})")
        }
    }

    private static PackCatalog read(byte[] bytes, URI address) {
        if (bytes.length > MAX_BYTES) {
            throw new PackArtifactException("it has more than the ${MAX_BYTES} bytes a catalog can have")
        }
        List<Plugin> plugins = []
        Set<String> withoutPack = new LinkedHashSet<>()
        List<String> problems = []
        Set<String> seen = new HashSet<>()
        int[] counts = new int[2]
        Json json
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            json = new Json(text, { Object entry ->
                int number = ++counts[0]
                String listed = listedAs(entry)
                if (!hasPack(entry)) {
                    // only so many versions are kept, and a pack of a version after them was not seen
                    if (hasMoreVersions(entry) && counts[1]++ < MAX_PROBLEMS) {
                        problems << ("plugin ${number}: ${listed ?: 'it'} has more than the ${MAX_VERSIONS} versions a plugin can have, " +
                                'and none of the first of them has a pack: whether a later one has was not read').toString()
                    }
                    if (listed != null) {
                        withoutPack << listed
                    }
                    return
                }
                try {
                    Plugin plugin = plugin((Map<String, Object>) entry)
                    if (!seen.add(plugin.coordinates)) {
                        throw new Problem("${plugin.coordinates} is in the catalog more than once")
                    }
                    plugins << plugin
                } catch (Problem e) {
                    if (listed != null && !seen.contains(listed)) {
                        withoutPack << listed
                    }
                    if (counts[1]++ < MAX_PROBLEMS) {
                        String line = "plugin ${number}: ${e.message}"
                        problems << (line.length() > MAX_PROBLEM ? line.substring(0, MAX_PROBLEM) + '...' : line)
                    }
                }
            } as Consumer<Object>)
            json.document()
        } catch (CharacterCodingException ignored) {
            throw new PackArtifactException('it is not written in UTF-8')
        } catch (Problem e) {
            throw new PackArtifactException("it is not a catalog: ${e.message}")
        }
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance('SHA-256').digest(bytes))
        String shown = PackArtifacts.shownAddress(address)
        String recordable = 'https'.equalsIgnoreCase(address.scheme) ? address.toString() : null
        if (json.array) {
            return new PackCatalog(shown, recordable, digest, true, [], new LinkedHashSet<String>(), [], 0)
        }
        if (!json.object) {
            throw new PackArtifactException('it is not a catalog: the document is neither an object nor an array')
        }
        if (!(json.format instanceof Long)) {
            throw new PackArtifactException('it is not a catalog: it has no format, which is a whole number')
        }
        if (json.format != (long) FORMAT) {
            throw new PackArtifactException("it is of format ${json.format}, and this Forge reads format ${FORMAT}")
        }
        if (!json.plugins) {
            throw new PackArtifactException('it is not a catalog: it has no plugins, which is an array')
        }
        withoutPack.removeAll(seen)
        return new PackCatalog(shown, recordable, digest, false, plugins, withoutPack, problems, counts[1])
    }

    /**
     * @param version a version
     * @return whether it is a release: numbers with dots between them, and nothing after them
     */
    static boolean isRelease(String version) {
        return RELEASE.matcher(version).matches()
    }

    /**
     * @return which of two releases is the later one, as a comparison gives it
     */
    static int compareReleases(String one, String other) {
        List<String> first = one.split(/\./).toList()
        List<String> second = other.split(/\./).toList()
        for (int index = 0; index < 4; index++) {
            int difference = Long.compare(index < first.size() ? Long.parseLong(first[index]) : 0L, index < second.size() ? Long.parseLong(second[index]) : 0L)
            if (difference != 0) {
                return difference
            }
        }
        return 0
    }

    // a text says why the plugin is deprecated; false written as a text says that it is not
    private static boolean isReason(String deprecated) {
        String text = deprecated.trim()
        return !text.isEmpty() && !'false'.equalsIgnoreCase(text)
    }

    // only a plugin that says it has a pack is read, and held to what a plugin with a pack has to say
    private static boolean hasPack(Object entry) {
        if (!(entry instanceof Map)) {
            return false
        }
        Object versions = ((Map) entry).get('versions')
        return versions instanceof List && ((List) versions).any { Object version -> version instanceof Map && ((Map) version).containsKey('pack') }
    }

    private static boolean hasMoreVersions(Object entry) {
        Object versions = entry instanceof Map ? ((Map) entry).get('versions') : null
        return versions instanceof List && ((List) versions).size() > MAX_VERSIONS
    }

    // the group and the artifact an entry is listed under, when it has such
    private static String listedAs(Object entry) {
        Object coordinates = entry instanceof Map ? ((Map) entry).get('coords') : null
        return coordinates instanceof String && isPlugin((String) coordinates) ? (String) coordinates : null
    }

    private static boolean isPlugin(String coordinates) {
        List<String> parts = coordinates.split(':', -1).toList()
        return coordinates.length() <= MAX_PLUGIN && parts.size() == 2 && parts.every { String part -> PART.matcher(part).matches() } &&
                !parts[0].contains('..') && !parts[0].endsWith('.')
    }

    private static Plugin plugin(Map<String, Object> entry) {
        String coordinates = text(entry, 'coords', true, MAX_TEXT)
        if (!isPlugin(coordinates)) {
            throw new Problem("coords is not group:artifact of at most ${MAX_PLUGIN} characters, but ${shown(coordinates)}")
        }
        Object deprecated = entry.get('deprecated')
        if (deprecated != null && !(deprecated instanceof Boolean) && !(deprecated instanceof String)) {
            throw new Problem("${coordinates}: deprecated is neither true, false nor a text")
        }
        try {
            String repository = text(entry, 'maven-repo', false, 2000)
            String docs = text(entry, 'docs', false, MAX_TEXT)
            List<Object> listed = (List<Object>) entry.get('versions')
            if (listed.size() > MAX_VERSIONS) {
                throw new Problem("has more than the ${MAX_VERSIONS} versions a plugin can have")
            }
            URI repositoryOfAll = repository == null ? null : repositoryOf(repository)
            List<Version> versions = []
            Set<String> seen = new HashSet<>()
            for (Object each : listed) {
                if (!(each instanceof Map)) {
                    throw new Problem('has a version that is not an object')
                }
                Map<String, Object> listedVersion = (Map<String, Object>) each
                if (!listedVersion.containsKey('pack')) {
                    continue
                }
                Version read = version(coordinates, listedVersion, repositoryOfAll)
                if (!seen.add(read.version)) {
                    throw new Problem("has the version ${read.version} more than once")
                }
                versions << read
            }
            return new Plugin(coordinates, line(said(entry, 'name'), MAX_TITLE), line(said(entry, 'desc'), MAX_DESCRIPTION),
                    docs != null && docs.length() <= MAX_DOCS && DOCS.matcher(docs).matches() ? docs : null,
                    deprecated == true || (deprecated instanceof String && isReason((String) deprecated)), versions)
        } catch (Problem e) {
            throw new Problem("${coordinates}: ${e.message}")
        }
    }

    private static Version version(String plugin, Map<String, Object> entry, URI repositoryOfAll) {
        String version = text(entry, 'version', true, MAX_TEXT)
        if (version.length() > MAX_VERSION) {
            throw new Problem("has a version of more than ${MAX_VERSION} characters")
        }
        PackArtifacts.Coordinates ofVersion
        try {
            ofVersion = PackArtifacts.coordinates("${plugin}:${version}")
        } catch (IllegalArgumentException ignored) {
            throw new Problem("version ${shown(version)} is not one version of a plugin")
        }
        Object value = entry.get('pack')
        if (!(value instanceof Map)) {
            throw new Problem("the pack of version ${version} is not an object")
        }
        Map<String, Object> pack = (Map<String, Object>) value
        try {
            String kind = text(pack, 'kind', true, MAX_NAME)
            if (kind != FEATURE && kind != PRESET) {
                throw new Problem("kind is neither ${FEATURE} nor ${PRESET}")
            }
            String name = text(pack, 'name', true, MAX_TEXT)
            if (name.length() > MAX_NAME || !NAME.matcher(name).matches()) {
                throw new Problem("name is not the name of a feature, but ${shown(name)}")
            }
            String sha256 = text(pack, 'sha256', true, MAX_TEXT)
            if (!SHA256.matcher(sha256).matches()) {
                throw new Problem('sha256 is not a SHA-256, which is 64 hexadecimal digits')
            }
            Object compatibility = pack.get('compatibility')
            if (!(compatibility instanceof Map)) {
                throw new Problem('compatibility is not an object')
            }
            Set<String> applicationTypes = null
            Object types = pack.get('applicationTypes')
            if (types != null) {
                if (!(types instanceof List) || ((List) types).isEmpty() || !((List) types).every { Object type -> APPLICATION_TYPES.contains(type) }) {
                    throw new Problem("applicationTypes is not an array of ${APPLICATION_TYPES.join(', ')}")
                }
                applicationTypes = new LinkedHashSet<String>((List<String>) types)
            }
            String repository = text(pack, 'maven-repo', false, 2000)
            return new Version(version, kind, name, whole(pack, 'format'), whole(pack, 'templateApi'), applicationTypes,
                    range((Map<String, Object>) compatibility, 'grails'), range((Map<String, Object>) compatibility, 'forge'),
                    sha256.toLowerCase(Locale.ROOT), ofVersion, repository == null ? repositoryOfAll : repositoryOf(repository))
        } catch (Problem e) {
            throw new Problem("the pack of version ${version}: ${e.message}")
        }
    }

    private static URI repositoryOf(String address) {
        try {
            return PackArtifacts.repositoryOfCatalog(address)
        } catch (IllegalArgumentException ignored) {
            // what an address is refused with is written for the command line, and names the address
            throw new Problem('maven-repo is not the https address of a repository that a catalog can name: without a user, a query or a fragment, ' +
                    'and not of this computer or of its network')
        }
    }

    private static PackVersionRange range(Map<String, Object> compatibility, String of) {
        String range = text(compatibility, of, true, MAX_RANGE)
        try {
            return PackVersionRange.parse(range)
        } catch (IllegalArgumentException ignored) {
            throw new Problem("compatibility.${of} is not a range of versions")
        }
    }

    private static int whole(Map<String, Object> map, String key) {
        Object value = map.get(key)
        if (!(value instanceof Long) || (Long) value < 1L || (Long) value > 1000000L) {
            throw new Problem("${key} is not a whole number from 1")
        }
        return ((Long) value).intValue()
    }

    private static String text(Map<String, Object> map, String key, boolean required, int limit) {
        Object value = map.get(key)
        if (value == null && !required) {
            return null
        }
        if (TOO_LONG.is(value) || (value instanceof String && ((String) value).length() > limit)) {
            throw new Problem("${key} has more than ${limit} characters")
        }
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw new Problem("${key} is not a text")
        }
        return (String) value
    }

    // what a catalog says of a plugin for people to read: one that is longer than a text is left unsaid, and the plugin is not left out for it
    private static String said(Map<String, Object> entry, String key) {
        return TOO_LONG.is(entry.get(key)) ? null : text(entry, key, false, MAX_TEXT)
    }

    // what a catalog says of a plugin for people to read, as one line that is no markup of the command
    private static String line(String text, int limit) {
        if (text == null) {
            return null
        }
        String cut = text.length() > limit * 2 ? text.substring(0, limit * 2) : text
        String safe = UNREADABLE.matcher(cut).replaceAll(' ').replace('@|', '@ |').replaceAll(/\s+/, ' ').trim()
        return safe.length() > limit ? safe.substring(0, safe.offsetByCodePoints(0, Math.min(limit, safe.codePointCount(0, safe.length())))) + '...' : safe
    }

    private static String shown(String text) {
        return FeatureSelectionException.shown(text)
    }

    /**
     * A plugin of the catalog that has a feature pack.
     */
    static final class Plugin {

        /** The group and the artifact of the plugin, as {@code group:artifact} */
        final String coordinates
        /** The name the plugin has for people, or {@code null} */
        final String title
        /** What the catalog says the plugin is for, or {@code null} */
        final String description
        /** The https address of its documentation, or {@code null} */
        final String docs
        final boolean deprecated
        /** The versions the catalog records a pack for */
        final List<Version> versions

        private Plugin(String coordinates, String title, String description, String docs, boolean deprecated, List<Version> versions) {
            this.coordinates = coordinates
            this.title = title
            this.description = description
            this.docs = docs
            this.deprecated = deprecated
            this.versions = Collections.unmodifiableList(versions)
        }
    }

    /**
     * The pack of one version of a plugin, as the catalog records it.
     */
    static final class Version {

        final String version
        final String kind
        /** The name of the feature of the pack */
        final String name
        final int format
        final int templateApi
        /** The types of application the pack is for, or {@code null} for all */
        final Set<String> applicationTypes
        final PackVersionRange grails
        final PackVersionRange forge
        /** The SHA-256 the main jar has to have, in lowercase hexadecimal */
        final String sha256
        /** The artifact the pack is in: the plugin it is listed under, in this version */
        final PackArtifacts.Coordinates coordinates
        /** The repository the catalog names for it, or {@code null} */
        final URI repository

        private Version(String version, String kind, String name, int format, int templateApi, Set<String> applicationTypes, PackVersionRange grails,
                        PackVersionRange forge, String sha256, PackArtifacts.Coordinates coordinates, URI repository) {
            this.version = version
            this.kind = kind
            this.name = name
            this.format = format
            this.templateApi = templateApi
            this.applicationTypes = applicationTypes
            this.grails = grails
            this.forge = forge
            this.sha256 = sha256
            this.coordinates = coordinates
            this.repository = repository
        }

        /**
         * @return whether the pack is one this Forge can read as a feature
         */
        boolean isReadable() {
            return kind == FEATURE && format == PackCatalog.FORMAT && templateApi == PackDescriptor.TEMPLATE_API
        }

        boolean isFeature() {
            return kind == FEATURE
        }
    }

    private static final class Problem extends RuntimeException {

        Problem(String message) {
            super(message, null, false, false)
        }
    }

    /**
     * Reads a JSON document as far as a catalog is read, and checks the rest of it to be JSON without
     * keeping it. What is read is named by a pattern: a map of the members of an object that are read,
     * a list with the pattern of the elements of an array, or {@link #ANY} for a value that is taken
     * as it is when it is a text, a whole number, true, false, or an array of such.
     *
     * <p>How deep the document goes is counted before it goes deeper, so the stack this uses does not
     * depend on the document. A number is checked against the grammar of JSON and is never made a
     * number of, unless it is a whole one of at most nine digits: no value of a catalog is another.</p>
     */
    private static final class Json {

        private final String text
        private final Consumer<Object> onPlugin
        private int at
        private int values
        boolean array
        boolean object
        boolean plugins
        Object format

        Json(String text, Consumer<Object> onPlugin) {
            this.text = text
            this.onPlugin = onPlugin
            // some tools write a mark before the document
            this.at = text.startsWith('﻿') ? 1 : 0
        }

        void document() {
            space()
            char first = peek()
            if (first == (char) '{') {
                object = true
                count()
                at++
                members(1, null, true)
            } else {
                array = first == (char) '['
                value(1, null)
            }
            space()
            if (at < text.length()) {
                throw new Problem('there is something after the document')
            }
        }

        private void count() {
            if (++values > MAX_VALUES) {
                throw new Problem("it has more than ${MAX_VALUES} values")
            }
        }

        private Object value(int depth, Object pattern) {
            count()
            space()
            char c = peek()
            if (c == (char) '{' || c == (char) '[') {
                if (depth > MAX_DEPTH) {
                    throw new Problem("it is nested deeper than ${MAX_DEPTH}")
                }
                at++
                if (c == (char) '{') {
                    return members(depth, pattern instanceof Map ? (Map<String, Object>) pattern : null, false)
                }
                return elements(depth, pattern instanceof List ? ((List) pattern).first() : null, pattern instanceof List || ANY.is(pattern))
            }
            if (c == (char) '"') {
                return string(pattern != null, MAX_TEXT)
            }
            for (Map.Entry<String, Object> word : [true: Boolean.TRUE, false: Boolean.FALSE, null: null].entrySet()) {
                if (text.startsWith(word.key, at)) {
                    at += word.key.length()
                    return word.value
                }
            }
            return number()
        }

        /**
         * @param read the members that are read, or {@code null} when none is
         * @param top whether this is the object the document is, of which the format and the plugins are taken
         */
        private Object members(int depth, Map<String, Object> read, boolean top) {
            Map<String, Object> map = read == null ? null : new LinkedHashMap<String, Object>()
            Set<String> keys = new HashSet<>()
            space()
            if (peek() == (char) '}') {
                at++
                return map == null ? NOT_KEPT : map
            }
            while (true) {
                space()
                if (peek() != (char) '"') {
                    throw new Problem('an object has a key that is not a text')
                }
                Object key = string(true, MAX_KEY)
                if (TOO_LONG.is(key)) {
                    throw new Problem("an object has a key of more than ${MAX_KEY} characters")
                }
                if (!keys.add((String) key)) {
                    throw new Problem('an object has one key twice')
                }
                space()
                if (peek() != (char) ':') {
                    throw new Problem('a key of an object has no colon after it')
                }
                at++
                if (top && key == 'format') {
                    format = value(depth + 1, ANY)
                } else if (top && key == 'plugins') {
                    space()
                    if (peek() == (char) '[') {
                        plugins = true
                        count()
                        at++
                        listed(depth + 1)
                    } else {
                        value(depth + 1, null)
                    }
                } else if (map != null && read.containsKey(key)) {
                    map.put((String) key, value(depth + 1, read.get(key)))
                } else {
                    value(depth + 1, null)
                }
                space()
                char next = peek()
                at++
                if (next == (char) '}') {
                    return map == null ? NOT_KEPT : map
                }
                if (next != (char) ',') {
                    throw new Problem('an object is not closed')
                }
            }
        }

        // the plugins, each given on as it is read and not kept here
        private void listed(int depth) {
            if (depth > MAX_DEPTH) {
                throw new Problem("it is nested deeper than ${MAX_DEPTH}")
            }
            space()
            if (peek() == (char) ']') {
                at++
                return
            }
            int number = 0
            while (true) {
                if (++number > MAX_PLUGINS) {
                    throw new Problem("it has more than the ${MAX_PLUGINS} plugins a catalog can have")
                }
                onPlugin.accept(value(depth + 1, OF_A_PLUGIN))
                space()
                char next = peek()
                at++
                if (next == (char) ']') {
                    return
                }
                if (next != (char) ',') {
                    throw new Problem('an array is not closed')
                }
            }
        }

        /**
         * @param pattern the pattern of an element
         * @param kept whether the elements are kept; of more than a plugin can have versions, one more
         * is kept, by which the array is known to be too long, and the rest is only checked
         */
        private Object elements(int depth, Object pattern, boolean kept) {
            List<Object> list = kept ? new ArrayList<Object>() : null
            space()
            if (peek() == (char) ']') {
                at++
                return kept ? list : NOT_KEPT
            }
            while (true) {
                boolean room = kept && list.size() <= MAX_VERSIONS
                // an element of an array that is taken as it is, is a value that is no array
                Object element = value(depth + 1, !room ? null : pattern == null ? NOT_KEPT : pattern)
                if (room) {
                    list << element
                }
                space()
                char next = peek()
                at++
                if (next == (char) ']') {
                    return kept ? list : NOT_KEPT
                }
                if (next != (char) ',') {
                    throw new Problem('an array is not closed')
                }
            }
        }

        /**
         * @return the text; {@link #TOO_LONG} for one of more characters than the limit, and
         * {@link #NOT_KEPT} for one that is not read, both of which are checked to their end
         */
        private Object string(boolean kept, int limit) {
            at++
            StringBuilder builder = kept ? new StringBuilder() : null
            boolean tooLong = false
            while (true) {
                if (at >= text.length()) {
                    throw new Problem('a text is not closed')
                }
                char c = text.charAt(at++)
                if (c == (char) '"') {
                    return tooLong ? TOO_LONG : builder == null ? NOT_KEPT : builder.toString()
                }
                if (c < (char) ' ') {
                    throw new Problem('a text has a control character in it')
                }
                if (c == (char) '\\') {
                    if (at >= text.length()) {
                        throw new Problem('a text is not closed')
                    }
                    char escaped = text.charAt(at++)
                    int plain = '"\\/bfnrt'.indexOf((int) escaped)
                    if (plain >= 0) {
                        c = '"\\/\b\f\n\r\t'.charAt(plain)
                    } else if (escaped == (char) 'u' && at + 4 <= text.length() && isHexadecimal(at)) {
                        c = (char) Integer.parseInt(text.substring(at, at + 4), 16)
                        at += 4
                    } else {
                        throw new Problem('a text has an escape that is none')
                    }
                }
                if (builder != null && !tooLong) {
                    if (builder.length() >= limit) {
                        tooLong = true
                        builder = null
                    } else {
                        builder.append(c)
                    }
                }
            }
        }

        private boolean isHexadecimal(int from) {
            for (int index = from; index < from + 4; index++) {
                if ('0123456789abcdefABCDEF'.indexOf((int) text.charAt(index)) < 0) {
                    return false
                }
            }
            return true
        }

        /**
         * @return the number when it is a whole one of at most nine digits, and {@link #NOT_KEPT} for
         * any other number of JSON, which is checked to be one and of which no number is made
         */
        private Object number() {
            int start = at
            if (at < text.length() && text.charAt(at) == (char) '-') {
                at++
            }
            int whole = digits()
            if (whole == 0 || (whole > 1 && text.charAt(at - whole) == (char) '0')) {
                throw new Problem('it is not JSON')
            }
            if (at < text.length() && text.charAt(at) == (char) '.') {
                at++
                if (digits() == 0) {
                    throw new Problem('it is not JSON')
                }
            }
            if (at < text.length() && (text.charAt(at) == (char) 'e' || text.charAt(at) == (char) 'E')) {
                at++
                if (at < text.length() && (text.charAt(at) == (char) '+' || text.charAt(at) == (char) '-')) {
                    at++
                }
                if (digits() == 0) {
                    throw new Problem('it is not JSON')
                }
            }
            if (at - start > MAX_NUMBER) {
                throw new Problem("a number has more than ${MAX_NUMBER} characters")
            }
            String number = text.substring(start, at)
            return WHOLE.matcher(number).matches() ? (Object) Long.valueOf(number) : NOT_KEPT
        }

        private int digits() {
            int start = at
            while (at < text.length() && text.charAt(at) >= (char) '0' && text.charAt(at) <= (char) '9') {
                at++
            }
            return at - start
        }

        private char peek() {
            if (at >= text.length()) {
                throw new Problem('the document ends before it is whole')
            }
            return text.charAt(at)
        }

        private void space() {
            while (at < text.length() && ' \t\n\r'.indexOf((int) text.charAt(at)) >= 0) {
                at++
            }
        }
    }
}
