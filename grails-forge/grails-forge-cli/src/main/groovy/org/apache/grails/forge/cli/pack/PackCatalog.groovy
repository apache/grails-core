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
 *                     "sha256": "...", "coords": "com.acme:acme-audit-grails:2.3.0",
 *                     "maven-repo": "https://repo.acme.example/maven" } } ] } ] }
 * </pre>
 * <p>{@code format} and {@code plugins} are required, and of a plugin {@code coords} and
 * {@code versions}; of a version {@code version}; of a {@code pack} its {@code format}, {@code kind}
 * ({@code feature} or {@code preset}), {@code name}, {@code templateApi}, both ranges of
 * {@code compatibility} and {@code sha256}, the SHA-256 of the main jar. Everything else can be left
 * out, and a field that is not named here is not read. A document that is an array is the index as it
 * was before it had a format: it records no pack, and no feature is found in it.</p>
 *
 * <p>A catalog is read from elsewhere and is not trusted. What is wrong with the document as a whole
 * refuses it; a plugin with a pack that has something wrong with it is left out, with one line that
 * says what, and a plugin without a pack is not looked at.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class PackCatalog {

    /** The format of the catalog this reads */
    public static final int FORMAT = 1

    /** The most bytes a catalog can have */
    public static final int MAX_BYTES = 16 * 1024 * 1024

    /** How deep the objects and arrays of a catalog can be */
    public static final int MAX_DEPTH = 8

    /** The most values, of any kind, a catalog can have */
    public static final int MAX_VALUES = 1000000

    /** The most characters of one text of a catalog */
    public static final int MAX_TEXT = 65536

    /** The most plugins a catalog can have */
    public static final int MAX_PLUGINS = 20000

    /** The most versions a plugin of a catalog can have */
    public static final int MAX_VERSIONS = 1000

    /** The most plugins that are left out of which it is kept what was wrong with them */
    public static final int MAX_PROBLEMS = 20

    /** What a plugin is left out for is one line of at most this many characters */
    public static final int MAX_PROBLEM = 300

    private static final Pattern PART = Pattern.compile(/[A-Za-z0-9][A-Za-z0-9_.\-]{0,199}/)
    private static final Pattern NAME = Pattern.compile(/[a-z][a-z0-9]*(-[a-z0-9]+)*/)
    private static final Pattern SHA256 = Pattern.compile(/[0-9a-fA-F]{64}/)
    private static final Pattern RELEASE = Pattern.compile(/\d{1,9}(\.\d{1,9}){0,3}/)
    private static final Pattern UNREADABLE = Pattern.compile(/[\p{Cc}\p{Cf}\p{Cs}\p{Co}\p{Zl}\p{Zp}]/)
    private static final Pattern DOCS = Pattern.compile(/https:\/\/[A-Za-z0-9\-._~:\/?#!&()*+,;=%]+/)
    private static final Set<String> APPLICATION_TYPES = ['web', 'rest-api', 'web-plugin', 'plugin'] as Set<String>
    private static final int MAX_NAME = 100
    private static final int MAX_TITLE = 100
    private static final int MAX_DESCRIPTION = 200
    private static final int MAX_DOCS = 500
    private static final int MAX_RANGE = 200
    private static final String FEATURE = 'feature'
    private static final String PRESET = 'preset'

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

    /** What was wrong with the first plugins that are left out, one line each */
    final List<String> problems

    /** How many plugins are left out */
    final int leftOut

    private PackCatalog(String address, String recordableAddress, String sha256, boolean withoutPacks, List<Plugin> plugins, List<String> problems, int leftOut) {
        this.address = address
        this.recordableAddress = recordableAddress
        this.sha256 = sha256
        this.withoutPacks = withoutPacks
        this.plugins = Collections.unmodifiableList(plugins)
        this.problems = Collections.unmodifiableList(problems)
        this.leftOut = leftOut
    }

    /**
     * @param bytes the document
     * @param address where it was read from
     * @return the catalog
     * @throws PackArtifactException when the document as a whole is not a catalog, with what is wrong
     * with it and nothing else as its message
     */
    static PackCatalog parse(byte[] bytes, URI address) {
        if (bytes.length > MAX_BYTES) {
            throw new PackArtifactException("it has more than the ${MAX_BYTES} bytes a catalog can have")
        }
        Object document
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            document = new Json(text).document()
        } catch (CharacterCodingException ignored) {
            throw new PackArtifactException('it is not written in UTF-8')
        } catch (Problem e) {
            throw new PackArtifactException("it is not a catalog: ${e.message}")
        }
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance('SHA-256').digest(bytes))
        String shown = PackArtifacts.shownAddress(address)
        String recordable = 'https'.equalsIgnoreCase(address.scheme) ? address.toString() : null
        if (document instanceof List) {
            return new PackCatalog(shown, recordable, digest, true, [], [], 0)
        }
        if (!(document instanceof Map)) {
            throw new PackArtifactException('it is not a catalog: the document is neither an object nor an array')
        }
        Map<String, Object> root = (Map<String, Object>) document
        Object format = root.get('format')
        if (!(format instanceof Long)) {
            throw new PackArtifactException('it is not a catalog: it has no format, which is a whole number')
        }
        if (format != (long) FORMAT) {
            throw new PackArtifactException("it is of format ${format}, and this Forge reads format ${FORMAT}")
        }
        Object entries = root.get('plugins')
        if (!(entries instanceof List)) {
            throw new PackArtifactException('it is not a catalog: it has no plugins, which is an array')
        }
        if (((List) entries).size() > MAX_PLUGINS) {
            throw new PackArtifactException("it has more than the ${MAX_PLUGINS} plugins a catalog can have")
        }
        List<Plugin> plugins = []
        List<String> problems = []
        Set<String> seen = new HashSet<>()
        int leftOut = 0
        int number = 0
        for (Object entry : (List) entries) {
            number++
            if (!hasPack(entry)) {
                continue
            }
            try {
                Plugin plugin = plugin((Map<String, Object>) entry)
                if (!seen.add(plugin.coordinates)) {
                    throw new Problem("${plugin.coordinates} is in the catalog more than once")
                }
                plugins << plugin
            } catch (Problem e) {
                if (leftOut++ < MAX_PROBLEMS) {
                    String line = "plugin ${number}: ${e.message}"
                    problems << (line.length() > MAX_PROBLEM ? line.substring(0, MAX_PROBLEM) + '...' : line)
                }
            }
        }
        return new PackCatalog(shown, recordable, digest, false, plugins, problems, leftOut)
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

    // only a plugin that says it has a pack is read, and held to what a plugin with a pack has to say
    private static boolean hasPack(Object entry) {
        if (!(entry instanceof Map)) {
            return false
        }
        Object versions = ((Map) entry).get('versions')
        return versions instanceof List && ((List) versions).any { Object version -> version instanceof Map && ((Map) version).containsKey('pack') }
    }

    private static Plugin plugin(Map<String, Object> entry) {
        String coordinates = text(entry, 'coords', true, 2 * MAX_NAME + 300)
        List<String> parts = coordinates.split(':', -1).toList()
        if (parts.size() != 2 || !parts.every { String part -> PART.matcher(part).matches() } || parts[0].contains('..') || parts[0].endsWith('.')) {
            throw new Problem("coords is not group:artifact, but ${shown(coordinates)}")
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
            return new Plugin(coordinates, line(text(entry, 'name', false, MAX_TEXT), MAX_TITLE), line(text(entry, 'desc', false, MAX_TEXT), MAX_DESCRIPTION),
                    docs != null && docs.length() <= MAX_DOCS && DOCS.matcher(docs).matches() ? docs : null,
                    deprecated == true || (deprecated instanceof String && !((String) deprecated).isEmpty()), versions)
        } catch (Problem e) {
            throw new Problem("${coordinates}: ${e.message}")
        }
    }

    private static Version version(String plugin, Map<String, Object> entry, URI repositoryOfAll) {
        String version = text(entry, 'version', true, MAX_NAME * 2)
        PackArtifacts.Coordinates ofVersion = coordinates("${plugin}:${version}", "version ${shown(version)}")
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
            String name = text(pack, 'name', true, MAX_NAME)
            if (!NAME.matcher(name).matches()) {
                throw new Problem("name is not the name of a feature, but ${shown(name)}")
            }
            String sha256 = text(pack, 'sha256', true, 64)
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
            String exact = text(pack, 'coords', false, 3 * MAX_NAME * 2 + 2)
            String repository = text(pack, 'maven-repo', false, 2000)
            return new Version(version, kind, name, whole(pack, 'format'), whole(pack, 'templateApi'), applicationTypes,
                    range((Map<String, Object>) compatibility, 'grails'), range((Map<String, Object>) compatibility, 'forge'),
                    sha256.toLowerCase(Locale.ROOT), exact == null ? ofVersion : coordinates(exact, 'coords'),
                    repository == null ? repositoryOfAll : repositoryOf(repository))
        } catch (Problem e) {
            throw new Problem("the pack of version ${version}: ${e.message}")
        }
    }

    private static PackArtifacts.Coordinates coordinates(String text, String what) {
        try {
            return PackArtifacts.coordinates(text)
        } catch (IllegalArgumentException ignored) {
            throw new Problem("${what} is not one version of a plugin")
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
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw new Problem("${key} is not a text")
        }
        if (((String) value).length() > limit) {
            throw new Problem("${key} has more than ${limit} characters")
        }
        return (String) value
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
        /** The artifact the pack is in */
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
     * Reads a JSON document into maps, lists, texts, numbers, booleans and nulls, and no further than
     * the limits of a catalog: how deep it goes is counted before it goes deeper, so the stack this
     * uses does not depend on the document.
     */
    private static final class Json {

        private final String text
        private int at
        private int values

        Json(String text) {
            // some tools write a mark before the document
            this.text = text
            this.at = text.startsWith('﻿') ? 1 : 0
        }

        Object document() {
            Object value = value(1)
            space()
            if (at < text.length()) {
                throw new Problem('there is something after the document')
            }
            return value
        }

        private Object value(int depth) {
            if (++values > MAX_VALUES) {
                throw new Problem("it has more than ${MAX_VALUES} values")
            }
            space()
            if (at >= text.length()) {
                throw new Problem('the document ends before it is whole')
            }
            char c = text.charAt(at)
            if (c == (char) '{' || c == (char) '[') {
                if (depth > MAX_DEPTH) {
                    throw new Problem("it is nested deeper than ${MAX_DEPTH}")
                }
                at++
                return c == (char) '{' ? object(depth) : array(depth)
            }
            if (c == (char) '"') {
                return string()
            }
            for (Map.Entry<String, Object> word : [true: Boolean.TRUE, false: Boolean.FALSE, null: null].entrySet()) {
                if (text.startsWith(word.key, at)) {
                    at += word.key.length()
                    return word.value
                }
            }
            return number()
        }

        private Map<String, Object> object(int depth) {
            Map<String, Object> map = new LinkedHashMap<>()
            space()
            if (peek() == (char) '}') {
                at++
                return map
            }
            while (true) {
                space()
                if (peek() != (char) '"') {
                    throw new Problem('an object has a key that is not a text')
                }
                String key = string()
                space()
                if (peek() != (char) ':') {
                    throw new Problem('a key of an object has no colon after it')
                }
                at++
                Object value = value(depth + 1)
                if (map.containsKey(key)) {
                    throw new Problem('an object has one key twice')
                }
                map.put(key, value)
                space()
                char next = peek()
                at++
                if (next == (char) '}') {
                    return map
                }
                if (next != (char) ',') {
                    throw new Problem('an object is not closed')
                }
            }
        }

        private List<Object> array(int depth) {
            List<Object> list = []
            space()
            if (peek() == (char) ']') {
                at++
                return list
            }
            while (true) {
                list << value(depth + 1)
                space()
                char next = peek()
                at++
                if (next == (char) ']') {
                    return list
                }
                if (next != (char) ',') {
                    throw new Problem('an array is not closed')
                }
            }
        }

        private String string() {
            at++
            StringBuilder builder = new StringBuilder()
            while (true) {
                if (at >= text.length()) {
                    throw new Problem('a text is not closed')
                }
                char c = text.charAt(at++)
                if (c == (char) '"') {
                    return builder.toString()
                }
                if (c < (char) ' ') {
                    throw new Problem('a text has a control character in it')
                }
                if (builder.length() >= MAX_TEXT) {
                    throw new Problem("a text has more than ${MAX_TEXT} characters")
                }
                if (c != (char) '\\') {
                    builder.append(c)
                    continue
                }
                if (at >= text.length()) {
                    throw new Problem('a text is not closed')
                }
                char escaped = text.charAt(at++)
                int plain = '"\\/bfnrt'.indexOf((int) escaped)
                if (plain >= 0) {
                    builder.append('"\\/\b\f\n\r\t'.charAt(plain))
                } else if (escaped == (char) 'u' && at + 4 <= text.length() && text.substring(at, at + 4) ==~ /[0-9a-fA-F]{4}/) {
                    builder.append((char) Integer.parseInt(text.substring(at, at + 4), 16))
                    at += 4
                } else {
                    throw new Problem('a text has an escape that is none')
                }
            }
        }

        private Object number() {
            int start = at
            while (at < text.length() && at - start <= 40 && '+-.eE0123456789'.indexOf((int) text.charAt(at)) >= 0) {
                at++
            }
            String number = text.substring(start, at)
            if (number ==~ /-?(0|[1-9][0-9]{0,17})/) {
                return Long.valueOf(number)
            }
            if (number ==~ /-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?/) {
                // no number of a catalog is other than whole: one that is, is kept as it is written
                return new BigDecimal(number.length() > 40 ? '0' : number)
            }
            throw new Problem('it is not JSON')
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
