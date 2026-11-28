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
 * A feature catalog as it was read: the plugins of a plugin index that have a feature pack, with the
 * versions the index records a pack for. A catalog says which plugin has the feature of a name, and in
 * which versions; it gives coordinates and the digest the jar has to have, and no pack is read from it.
 *
 * <p>The document is the plugin index of Grails as {@code apache/grails-plugins-metadata} publishes it,
 * {@code grails-plugins-index.json}: a JSON array of plugins, in which a version of a plugin can have a
 * {@code pack} member. What this reads of it:</p>
 * <pre>
 * [ { "coords": "org.grails.plugins:grails-mail", "name": "Mail", "desc": "...", "docs": "https://...",
 *     "maven-repo": "https://repo1.maven.org/maven2", "deprecated": "why it is",
 *     "versions": [
 *       { "version": "5.0.3", "grailsVersion": "7.0.0 > *",
 *         "pack": { "format": 1, "kind": "feature", "name": "mail", "templateApi": 1,
 *                   "applicationTypes": ["web"],
 *                   "compatibility": { "grails": "[9.0.0,10.0.0)", "forge": "[9.0.0,10.0.0)" },
 *                   "sha256": "..." } },
 *       { "version": "4.0.2", "grailsVersion": "6.0.0 > *", "coords": "org.grails.plugins:mail",
 *         "maven-repo": "https://repo.grails.org/plugins", "artifactAvailable": false } ] } ]
 * </pre>
 * <p>Of a plugin {@code coords} and {@code versions} are required, and of a version {@code version}.
 * A version can have {@code coords} and {@code maven-repo} of its own, which the index has for a
 * plugin that was published under other coordinates or to another repository over time: the artifact
 * of a version is its own {@code group:artifact}, or else that of the plugin, in that version, and
 * the repository named for it likewise. A version with {@code artifactAvailable: false} is not
 * offered. {@code grailsVersion} is the range the descriptor of the plugin gives, {@code lower > upper}
 * with {@code *} for any. A {@code pack} is what the descriptor of the feature pack in the jar says,
 * and the SHA-256 of the jar: {@code format}, {@code kind} ({@code feature} or {@code preset}),
 * {@code name}, {@code templateApi}, both ranges of {@code compatibility} and {@code sha256} are
 * required, {@code applicationTypes} is not. A pack is for a Grails that both {@code grailsVersion}
 * and its own range have: neither is taken over the other. A field that is not named here is not
 * read. No order of the versions is relied on: which is the latest is found by comparing them.</p>
 *
 * <p>The {@code format} of a pack says how the rest of it is written, and is read first: a pack of
 * another format than {@link #FORMAT} is one a later Forge reads, and nothing else of it is read
 * here; the version is passed over and counted, and the other versions of the plugin are read as
 * they are. Of a pack of this format a {@code kind} or a type of application this Forge does not know
 * is no fault: a pack of another kind is never selected by a name, and one for types of application
 * this Forge does not know is selected for none of them.</p>
 *
 * <p>A catalog is read from elsewhere and is not trusted. What is wrong with the document as a whole
 * refuses it; a plugin with a pack that has something wrong with it is left out, with one line that
 * says what, and so is one version whose {@code grailsVersion} is not such a range; of a plugin without
 * a pack only its coordinates are kept. The limits are those of an index several times the one that
 * is published (0.64 MiB, 257 plugins, 5,081 versions, 303 of one plugin, 26,167 values and texts of
 * at most 678 characters in November 2026): what is not read is checked to be JSON and is not kept,
 * so reading costs little more memory than the document has bytes.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class PackCatalog {

    /** The format of the {@code pack} member this reads */
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
    public static final int MAX_VERSIONS = 1000

    /** The most plugins and versions that are left out of which it is kept what was wrong with them */
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
    private static final Set<String> APPLICATION_TYPES = ['web', 'rest-api', 'web-plugin', 'plugin', 'rest-api-plugin'] as Set<String>
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
                                                           compatibility: [grails: ANY, forge: ANY], sha256: ANY] as Map<String, Object>
    private static final Map<String, Object> OF_A_PLUGIN = [coords: ANY, name: ANY, desc: ANY, docs: ANY, 'maven-repo': ANY, deprecated: ANY,
                                                             versions: [[version: ANY, coords: ANY, 'maven-repo': ANY, grailsVersion: ANY,
                                                                         artifactAvailable: ANY, pack: OF_A_PACK]]] as Map<String, Object>
    // the range of the descriptor of a plugin: a version, > or >=, and a version or * for any
    private static final String OF_A_RANGE = /(\d{1,9}|\*)(?:\.(\d{1,9}|\*))?(?:\.(\d{1,9}|\*))?(?:\.(\d{1,9}|\*))?(?:[.\-][A-Za-z][A-Za-z0-9\-]*)?/
    private static final Pattern GRAILS_VERSION = Pattern.compile('\\s*' + OF_A_RANGE + '\\s*>=?\\s*(?:(\\*)|' + OF_A_RANGE + ')\\s*')
    private static final Pattern LEADS_TO = Pattern.compile(/(\d{1,9})(?:\.(\d{1,9}))?(?:\.(\d{1,9}))?(?:\.(\d{1,9}))?(?:[-+].*|\.[^0-9].*)?/)

    /** The address the catalog was read from, as a message has it */
    final String address

    /** The address as it can be written into a project, or {@code null} for a file of this computer */
    final String recordableAddress

    /** The SHA-256 of the bytes that were read, in lowercase hexadecimal */
    final String sha256

    /** Whether the catalog records no feature pack that is read, as the plugin index did before it recorded packs */
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

    /** How many versions of plugins that are not left out are left out */
    final int versionsLeftOut

    /** How many versions have a pack of another format than {@link #FORMAT}, which a later Forge reads and of which nothing else is read */
    final int versionsOfOtherFormats

    private PackCatalog(String address, String recordableAddress, String sha256, boolean withoutPacks, List<Plugin> plugins, Set<String> withoutPack,
                        List<String> problems, int leftOut, int versionsLeftOut, int versionsOfOtherFormats) {
        this.versionsLeftOut = versionsLeftOut
        this.versionsOfOtherFormats = versionsOfOtherFormats
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
        int[] counts = new int[4]
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
                    Plugin plugin = plugin((Map<String, Object>) entry, { String line ->
                        if (counts[1] + counts[2]++ < MAX_PROBLEMS) {
                            problems << bounded("plugin ${number}: ${line}")
                        }
                    } as Consumer<String>, { counts[3]++ } as Runnable)
                    if (!seen.add(plugin.coordinates)) {
                        throw new Problem("${plugin.coordinates} is in the catalog more than once")
                    }
                    plugins << plugin
                } catch (Problem e) {
                    if (listed != null && !seen.contains(listed)) {
                        withoutPack << listed
                    }
                    if (counts[1]++ + counts[2] < MAX_PROBLEMS) {
                        problems << bounded("plugin ${number}: ${e.message}")
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
        if (!json.array) {
            throw new PackArtifactException('it is not a catalog: the document is not an array of plugins, as the plugin index is')
        }
        withoutPack.removeAll(seen)
        return new PackCatalog(shown, recordable, digest, plugins.isEmpty() && counts[3] == 0, plugins, withoutPack, problems, counts[1], counts[2], counts[3])
    }

    private static String bounded(String line) {
        return line.length() > MAX_PROBLEM ? line.substring(0, MAX_PROBLEM) + '...' : line
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

    /**
     * @param versionLeftOut told what is wrong with each version that is left out. What a version has
     * leaves out that version, and the other versions of the plugin are read as they are
     * @param ofOtherFormat told of each version whose pack has a format this Forge does not read
     */
    private static Plugin plugin(Map<String, Object> entry, Consumer<String> versionLeftOut, Runnable ofOtherFormat) {
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
                Object available = listedVersion.get('artifactAvailable')
                if (!listedVersion.containsKey('pack') || available == false) {
                    continue
                }
                Version read
                try {
                    if (available != null && available != true) {
                        throw new Problem('has a version whose artifactAvailable is neither true nor false')
                    }
                    read = version(coordinates, listedVersion, repositoryOfAll)
                } catch (OfOtherFormat ignored) {
                    ofOtherFormat.run()
                    continue
                } catch (Problem e) {
                    // what one version says leaves out that version: the others of the plugin, older ones a Forge reads among them, are still offered
                    versionLeftOut.accept("${coordinates}: ${e.message}".toString())
                    continue
                }
                if (!seen.add(read.version)) {
                    throw new Problem("has the version ${read.version} more than once")
                }
                versions << read
            }
            return new Plugin(coordinates, line(said(entry, 'name'), MAX_TITLE), line(said(entry, 'desc'), MAX_DESCRIPTION),
                    docs != null && docs.length() <= MAX_DOCS && DOCS.matcher(docs).matches() ? docs : null,
                    deprecated == true ? '' : deprecated instanceof String && isReason((String) deprecated) ? line((String) deprecated, MAX_TITLE) : null, versions)
        } catch (Problem e) {
            throw new Problem("${coordinates}: ${e.message}")
        }
    }

    private static Version version(String plugin, Map<String, Object> entry, URI repositoryOfAll) {
        String version = text(entry, 'version', true, MAX_TEXT)
        if (version.length() > MAX_VERSION) {
            throw new Problem("has a version of more than ${MAX_VERSION} characters")
        }
        Object value = entry.get('pack')
        if (!(value instanceof Map)) {
            throw new Problem("the pack of version ${shown(version)} is not an object")
        }
        Map<String, Object> pack = (Map<String, Object>) value
        // the format says how the rest of the pack is written: of another one nothing more is read, by the rules of this one or any other
        int format
        try {
            format = whole(pack, 'format')
        } catch (Problem e) {
            throw new Problem("the pack of version ${shown(version)}: ${e.message}")
        }
        if (format != FORMAT) {
            throw new OfOtherFormat()
        }
        // a version can have been published under other coordinates than the plugin has now
        String own = text(entry, 'coords', false, MAX_TEXT)
        if (own != null && !isPlugin(own)) {
            throw new Problem("the coords of version ${shown(version)} is not group:artifact of at most ${MAX_PLUGIN} characters, but ${shown(own)}")
        }
        PackArtifacts.Coordinates ofVersion
        try {
            ofVersion = PackArtifacts.coordinates("${own ?: plugin}:${version}")
        } catch (IllegalArgumentException ignored) {
            throw new Problem("version ${shown(version)} is not one version of a plugin")
        }
        String ownRepository = text(entry, 'maven-repo', false, 2000)
        URI repository
        try {
            repository = ownRepository == null ? repositoryOfAll : repositoryOf(ownRepository)
        } catch (Problem e) {
            throw new Problem("version ${version}: ${e.message}")
        }
        long[] grailsVersion = null
        Object ofDescriptor = entry.get('grailsVersion')
        if (ofDescriptor != null) {
            grailsVersion = ofDescriptor instanceof String ? rangeOfDescriptor((String) ofDescriptor) : null
            if (grailsVersion == null) {
                // what the descriptor of a plugin says is copied into the index as it is: one version is left out for it, not the plugin
                throw new OfOneVersion("version ${version} is left out: its grailsVersion is not a range of the form 7.0.0 > *, but " +
                        "${ofDescriptor instanceof String ? shown((String) ofDescriptor) : 'no text'}")
            }
        }
        try {
            // a kind of pack this Forge does not know is one it never selects by a name
            String kind = text(pack, 'kind', true, MAX_TEXT)
            if (kind.length() > MAX_NAME || !NAME.matcher(kind).matches()) {
                throw new Problem("kind is not the name of a kind of pack, such as ${FEATURE} or ${PRESET}, but ${shown(kind)}")
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
                // a type this Forge does not know is kept, and is the type of no application it creates
                if (!(types instanceof List) || ((List) types).isEmpty() || !((List) types).every { Object type ->
                    type instanceof String && ((String) type).length() <= MAX_NAME && NAME.matcher((String) type).matches()
                }) {
                    throw new Problem("applicationTypes is not an array of names of types of application, such as ${APPLICATION_TYPES.join(', ')}")
                }
                applicationTypes = new LinkedHashSet<String>((List<String>) types)
            }
            return new Version(version, kind, name, format, whole(pack, 'templateApi'), applicationTypes,
                    range((Map<String, Object>) compatibility, 'grails'), range((Map<String, Object>) compatibility, 'forge'),
                    sha256.toLowerCase(Locale.ROOT), ofVersion, repository, grailsVersion,
                    ofDescriptor == null ? null : ((String) ofDescriptor).trim().replaceAll(/\s+/, ' '))
        } catch (Problem e) {
            throw new Problem("the pack of version ${version}: ${e.message}")
        }
    }

    /**
     * The range the descriptor of a plugin gives for Grails, as the index has it: {@code 7.0.0 > *},
     * also with {@code >=}, with more than one space, with a qualifier after the lower version, with
     * {@code *} for a part of a version, and with a version in the place of the last {@code *}.
     *
     * @return the eight numbers of the lowest and the highest version, the highest being the most a
     * number can be where any is allowed; {@code null} when the text is not such a range
     */
    private static long[] rangeOfDescriptor(String text) {
        if (text.length() > MAX_RANGE) {
            return null
        }
        java.util.regex.Matcher matcher = GRAILS_VERSION.matcher(text)
        if (!matcher.matches()) {
            return null
        }
        long[] range = new long[8]
        for (int part = 0; part < 4; part++) {
            String lower = matcher.group(part + 1)
            range[part] = lower == null || lower == '*' ? 0L : Long.parseLong(lower)
            String upper = matcher.group(5) != null ? '*' : matcher.group(part + 6)
            range[part + 4] = upper == '*' ? Long.MAX_VALUE : upper == null ? 0L : Long.parseLong(upper)
        }
        return range
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
        // the index has plugins with an empty description
        return TOO_LONG.is(entry.get(key)) || entry.get(key) == '' ? null : text(entry, key, false, MAX_TEXT)
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
        /** Why the plugin is deprecated, as one line, which is empty when the index gives no reason; {@code null} for a plugin that is not */
        final String deprecated
        /** The versions the catalog records a pack for */
        final List<Version> versions

        private Plugin(String coordinates, String title, String description, String docs, String deprecated, List<Version> versions) {
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
        /** The artifact the pack is in: this version of the coordinates the version has, or else of those of the plugin */
        final PackArtifacts.Coordinates coordinates
        /** The repository the catalog names for the version, or else for the plugin, or {@code null} */
        final URI repository
        /** The range of the descriptor of the plugin as the index has it, or {@code null} when it has none */
        final String grailsVersion
        private final long[] grailsVersionRange

        private Version(String version, String kind, String name, int format, int templateApi, Set<String> applicationTypes, PackVersionRange grails,
                        PackVersionRange forge, String sha256, PackArtifacts.Coordinates coordinates, URI repository, long[] grailsVersionRange,
                        String grailsVersion) {
            this.grailsVersionRange = grailsVersionRange
            this.grailsVersion = grailsVersion
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
         * @return whether the pack is one this Forge can read, as a feature or as a preset, whichever its kind is
         */
        boolean isReadable() {
            return (kind == FEATURE || kind == PRESET) && format == PackCatalog.FORMAT && templateApi == PackDescriptor.TEMPLATE_API
        }

        boolean isFeature() {
            return kind == FEATURE
        }

        /**
         * @return whether the pack is a preset, which is given to a create command with {@code --preset}
         */
        boolean isPreset() {
            return kind == PRESET
        }

        /**
         * @param version a version of Grails, which can be one that leads to a release, such as a snapshot
         * @return whether the pack is for it: both the range of the pack and that of the descriptor
         * of the plugin, when the index has one, have it
         */
        boolean isForGrails(String version) {
            if (!grails.contains(version)) {
                return false
            }
            if (grailsVersionRange == null) {
                return true
            }
            java.util.regex.Matcher release = LEADS_TO.matcher(version == null ? '' : version.trim())
            if (!release.matches()) {
                return false
            }
            int low = 0
            int high = 0
            for (int part = 0; part < 4 && (low == 0 || high == 0); part++) {
                long number = release.group(part + 1) == null ? 0L : Long.parseLong(release.group(part + 1))
                low = low != 0 ? low : Long.compare(number, grailsVersionRange[part])
                high = high != 0 ? high : Long.compare(number, grailsVersionRange[part + 4])
            }
            return low >= 0 && high <= 0
        }
    }

    private static class Problem extends RuntimeException {

        Problem(String message) {
            super(message, null, false, false)
        }
    }

    // what is wrong with one version of a plugin, and leaves that version out and not the plugin
    private static final class OfOneVersion extends Problem {

        OfOneVersion(String message) {
            super(message)
        }
    }

    // a version whose pack has a format this Forge does not read: passed over, and not wrong
    private static final class OfOtherFormat extends Problem {

        OfOtherFormat() {
            super('of another format')
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

        Json(String text, Consumer<Object> onPlugin) {
            this.text = text
            this.onPlugin = onPlugin
            // some tools write a mark before the document
            this.at = text.startsWith('﻿') ? 1 : 0
        }

        void document() {
            space()
            if (peek() == (char) '[') {
                array = true
                count()
                at++
                listed(1)
            } else {
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
                    return members(depth, pattern instanceof Map ? (Map<String, Object>) pattern : null)
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
         */
        private Object members(int depth, Map<String, Object> read) {
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
                if (map != null && read.containsKey(key)) {
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
