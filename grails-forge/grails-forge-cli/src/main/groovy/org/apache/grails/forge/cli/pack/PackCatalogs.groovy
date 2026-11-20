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
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.function.Function
import java.util.function.Predicate
import java.util.regex.Pattern
import org.apache.grails.forge.cli.pack.PackCatalog.Plugin
import org.apache.grails.forge.cli.pack.PackCatalog.Version

/**
 * The feature catalogs a command reads, and the choice of the plugin and the version that has the
 * feature of a name. Nothing is read until a name is looked for, and each catalog is read once.
 *
 * <p>A catalog of an https address is kept as a copy in a directory of the user, so that it is not
 * asked for with every command: the bytes as they were read, in a file named by the SHA-256 of the
 * address, which only the user can read where the file system has such permissions. A copy is used
 * for a day; an older one is used when the catalog cannot be read, with a warning. A copy is read as
 * the catalog itself is, as something that is not trusted. A catalog of a file is read every time.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class PackCatalogs {

    /** The plugin index of Grails */
    public static final String DEFAULT = 'https://raw.githubusercontent.com/apache/grails-plugins-metadata/main/grails-plugins-index.json'

    /**
     * Whether the plugin index of Grails is read when no catalog is named. With {@code false}, a name
     * is looked up only in a catalog the command or the environment names.
     */
    public static final boolean DEFAULT_ON = true

    /** How long the catalog nobody named can take: a name that is mistyped does not wait for it as for a jar */
    public static final Duration DEFAULT_WITHIN = Duration.ofSeconds(5)

    /** What is given in the place of an address for no catalog at all */
    public static final String NONE = 'none'

    /** How long a copy of a catalog is used without the catalog being asked for */
    public static final Duration TIME_TO_LIVE = Duration.ofHours(24)

    /** The most catalogs a command reads */
    public static final int MAX_CATALOGS = 10

    private static final Pattern ID = Pattern.compile(/([A-Za-z0-9][A-Za-z0-9_.\-]{0,199}:[A-Za-z0-9][A-Za-z0-9_.\-]{0,199})#([a-z][a-z0-9]*(-[a-z0-9]+)*)/)
    private static final Pattern NAME = Pattern.compile(/[a-z][a-z0-9]*(-[a-z0-9]+)*/)
    private static final int MAX_NAME = 100
    private static final int MAX_REASONS = 5
    private static final String FILE = 'file'

    private final PackArtifacts artifacts
    private final List<URI> addresses
    private final Path copies
    private final boolean refresh
    private final boolean byDefault
    private final Duration defaultWithin
    private final List<String> warnings = []
    private List<PackCatalog> catalogs
    private String unread

    /**
     * @param artifacts what reads a file of an address, with its rules
     * @param addresses the addresses of the catalogs, as {@link #addresses} gives them
     * @param copies the directory the copies are kept in, or {@code null} for none
     * @param refresh whether every catalog is asked for, whatever copy there is
     */
    PackCatalogs(PackArtifacts artifacts, List<URI> addresses, Path copies, boolean refresh) {
        this(artifacts, addresses, copies, refresh, false, DEFAULT_WITHIN)
    }

    /**
     * @param byDefault whether the catalogs are those nobody named: one that cannot be read is then no
     * failure of the command, is given less time, and is not asked again for a day
     * @param defaultWithin how long such a catalog can take
     */
    PackCatalogs(PackArtifacts artifacts, List<URI> addresses, Path copies, boolean refresh, boolean byDefault, Duration defaultWithin) {
        this.byDefault = byDefault
        this.defaultWithin = defaultWithin
        this.artifacts = artifacts
        this.addresses = addresses
        this.copies = copies
        this.refresh = refresh
    }

    /**
     * @param given the addresses that were given for this command, each an address or {@link #NONE}
     * @param environment what the environment names in their place, separated by semicolons, or {@code null}
     * @return the addresses of the catalogs to read: those that were given, else those of the
     * environment, else the plugin index of Grails; none when {@link #NONE} is what is said
     * @throws IllegalArgumentException when one is not the address of a catalog
     */
    static List<URI> addresses(List<String> given, String environment) {
        List<String> named = given ?: (environment ?: '').split(';')*.trim().findAll { String each -> !each.isEmpty() }
        if (named.isEmpty()) {
            return DEFAULT_ON ? [PackArtifacts.catalog(DEFAULT)] : Collections.<URI>emptyList()
        }
        if (named.contains(NONE)) {
            if (named.size() > 1) {
                throw new IllegalArgumentException("${NONE} is given for no feature catalog, and cannot be given with the address of one")
            }
            return []
        }
        if (named.size() > MAX_CATALOGS) {
            throw new IllegalArgumentException("More than ${MAX_CATALOGS} feature catalogs are named")
        }
        Set<URI> addresses = new LinkedHashSet<>()
        for (String each : named) {
            addresses << PackArtifacts.catalog(each)
        }
        return addresses.toList()
    }

    /**
     * @return whether {@link #addresses} gives the catalog nobody named for these
     */
    static boolean isByDefault(List<String> given, String environment) {
        return DEFAULT_ON && !given && (environment ?: '').split(';')*.trim().every { String each -> each.isEmpty() }
    }

    /**
     * @param feature one of the features that were asked for
     * @return whether it is the feature of a plugin by its full name, {@code group:artifact#name}
     */
    static boolean isId(String feature) {
        return feature.length() <= 2 * 200 + MAX_NAME + 2 && ID.matcher(feature).matches()
    }

    /**
     * @param feature one of the features that were asked for
     * @return whether it could be the name of the feature of a pack
     */
    static boolean isName(String feature) {
        return feature.length() <= MAX_NAME && NAME.matcher(feature).matches()
    }

    /**
     * @param feature a name, or a full name as {@link #isId} knows it
     * @return the name of the feature
     */
    static String nameOf(String feature) {
        return feature.substring(feature.indexOf('#') + 1)
    }

    boolean isEmpty() {
        return addresses.isEmpty()
    }

    /**
     * @return what there is to say about how the catalogs were read, each once
     */
    List<String> takeWarnings() {
        List<String> taken = new ArrayList<>(warnings)
        warnings.clear()
        return taken
    }

    /**
     * @return the catalogs, read now when they were not yet
     * @throws PackArtifactException when one of them cannot be read or is no catalog: one line that
     * names it and says why
     */
    List<PackCatalog> read() {
        if (catalogs == null) {
            List<PackCatalog> all = []
            try {
                for (URI address : addresses) {
                    all << one(address)
                }
            } catch (PackArtifactException e) {
                if (!byDefault) {
                    throw e
                }
                // nobody asked for this catalog: without it, a name is what it was before there were catalogs
                unread = e.message
                all = []
            }
            catalogs = all
        }
        return catalogs
    }

    /**
     * @return why the catalog nobody named was not read, as one line that names it, or {@code null}
     * when it was read, or when the catalogs are ones that were named, of which {@link #read()} throws
     */
    String getUnread() {
        read()
        return unread
    }

    /**
     * Chooses the plugin and the version that has a feature: of the plugins that have a pack of that
     * name, the one that is not deprecated; of its versions, the latest release whose pack this Forge
     * reads and that is for the application, this Grails and this Forge. A snapshot, a milestone and a
     * deprecated plugin are not chosen for a name: they are used by their coordinates.
     *
     * @param feature the name of the feature, or its full name, {@code group:artifact#name}
     * @param applicationType the type of the application, as a descriptor names it
     * @param grails the version of Grails the application is for
     * @param forge the version of this Forge
     * @param managed gives the version Forge has for a plugin in the applications it generates, by its
     * {@code group:artifact}, or {@code null} for a plugin it has none for. Of such a plugin that
     * version is the one that is taken, release or not, and no other: a pack of another version would
     * be read for a build that has the plugin in the version of Forge
     * @return what was chosen, or {@code null} when no catalog has a pack of that name
     * @throws IllegalArgumentException when plugins have it and none of them, or more than one, can be chosen
     * @throws PackArtifactException when a catalog cannot be read
     */
    Choice choose(String feature, String applicationType, String grails, String forge, Function<String, String> managed) {
        String name = nameOf(feature)
        String only = feature.contains('#') ? feature.substring(0, feature.indexOf('#')) : null
        Map<String, Choice> chosen = new LinkedHashMap<>()
        Map<String, String> refused = new LinkedHashMap<>()
        for (PackCatalog catalog : read()) {
            for (Plugin plugin : catalog.plugins) {
                // the catalog that is named first is the one that speaks for a plugin
                if ((only != null && plugin.coordinates != only) || chosen.containsKey(plugin.coordinates) || refused.containsKey(plugin.coordinates)) {
                    continue
                }
                List<Version> withIt = plugin.versions.findAll { Version version -> version.name == name && version.feature }
                if (withIt.isEmpty()) {
                    continue
                }
                Object result = of(plugin, withIt, applicationType, grails, forge, managed.apply(plugin.coordinates))
                if (result instanceof Version) {
                    chosen.put(plugin.coordinates, new Choice(catalog, plugin, (Version) result))
                } else {
                    refused.put(plugin.coordinates, (String) result)
                }
            }
        }
        if (chosen.size() == 1) {
            return chosen.values().first()
        }
        if (chosen.size() > 1) {
            throw new IllegalArgumentException("More than one plugin of the feature catalog has a feature ${name}: name the one that is meant as " +
                    chosen.keySet().take(MAX_REASONS).collect { String plugin -> "${plugin}#${name}".toString() }.join(' or ') +
                    (chosen.size() > MAX_REASONS ? " (and ${chosen.size() - MAX_REASONS} more)" : ''))
        }
        if (refused.isEmpty()) {
            return null
        }
        throw new IllegalArgumentException("The feature ${name} is not taken from the feature catalog: " +
                refused.values().take(MAX_REASONS).join('; ') + (refused.size() > MAX_REASONS ? "; and ${refused.size() - MAX_REASONS} more plugins" : ''))
    }

    /**
     * @param name the name of a feature
     * @return the plugins of the catalogs that have a pack of that name, as {@code group:artifact}
     */
    List<String> pluginsWith(String name) {
        List<String> plugins = []
        for (PackCatalog catalog : read()) {
            for (Plugin plugin : catalog.plugins) {
                if (plugin.versions.any { Version version -> version.feature && version.name == name } && !plugins.contains(plugin.coordinates)) {
                    plugins << plugin.coordinates
                }
            }
        }
        return plugins
    }

    /**
     * What the catalogs offer, for a list: each catalog with its digest, the features that can be
     * selected by their name with the version that would be taken, and how many are left out and why.
     *
     * @param ofFramework whether a name is that of a feature of the framework, which no plugin has
     * @return the lines of the list, none of which is markup of the command
     */
    List<String> listing(String applicationType, String grails, String forge, Predicate<String> ofFramework, Function<String, String> managed) {
        List<String> lines = []
        Set<String> spoken = new HashSet<>()
        for (PackCatalog catalog : read()) {
            lines << "Feature catalog ${catalog.address}, sha256 ${catalog.sha256}".toString()
            if (catalog.withoutPacks) {
                lines << '  the catalog is the plugin index without a format, which records no feature pack'
            }
            List<List<String>> rows = []
            int notOffered = 0
            int shadowed = 0
            for (Plugin plugin : catalog.plugins) {
                if (!spoken.add(plugin.coordinates)) {
                    continue
                }
                for (String name : plugin.versions.findAll { Version version -> version.feature }*.name.unique()) {
                    Object result = of(plugin, plugin.versions.findAll { Version version -> version.name == name && version.feature }, applicationType, grails, forge,
                            managed.apply(plugin.coordinates))
                    if (ofFramework.test(name)) {
                        shadowed++
                    } else if (result instanceof Version) {
                        rows << [name, ((Version) result).coordinates.toString(), plugin.description ?: plugin.title ?: '']
                    } else {
                        notOffered++
                    }
                }
            }
            rows.sort { List<String> row -> row[0] + ' ' + row[1] }
            int width = (rows.collect { List<String> row -> row[0].length() }.max() ?: 0) + 2
            int coordinates = (rows.collect { List<String> row -> row[1].length() }.max() ?: 0) + 2
            for (List<String> row : rows) {
                lines << ('  ' + row[0].padRight(width) + row[1].padRight(coordinates) + row[2]).stripTrailing()
            }
            if (rows.isEmpty()) {
                lines << '  no feature of this catalog can be selected by its name'
            }
            if (notOffered > 0) {
                lines << ("  ${notOffered} more ${notOffered == 1 ? 'is' : 'are'} not offered: of a deprecated plugin, without a release, not in the version Forge has for it, or not for " +
                        "this application, Grails ${grails} and Forge ${forge}").toString()
            }
            if (shadowed > 0) {
                lines << ("  ${shadowed} more ${shadowed == 1 ? 'has' : 'have'} the name of a feature of the framework, which is the one that is " +
                        'selected by that name').toString()
            }
            if (catalog.leftOut > 0) {
                lines << "  ${catalog.leftOut} ${catalog.leftOut == 1 ? 'plugin is' : 'plugins are'} left out for what the catalog says of ${catalog.leftOut == 1 ? 'it' : 'them'}:".toString()
                for (String problem : catalog.problems) {
                    lines << "    ${problem}".toString()
                }
            }
        }
        return lines
    }

    /**
     * @return what is said of the catalogs after a name that none of them has: where it was looked
     * for, and that plugins were left out when there are such
     */
    String searched() {
        List<PackCatalog> all = read()
        int leftOut = (all*.leftOut.sum() ?: 0) as int
        return "the feature ${all.size() == 1 ? 'catalog' : 'catalogs'} ${all*.address.join(', ')}" +
                (all.any { PackCatalog catalog -> catalog.withoutPacks } ? ' (the plugin index without a format, which records no feature pack)' : '') +
                (leftOut > 0 ? " (${leftOut} ${leftOut == 1 ? 'plugin' : 'plugins'} left out for what the catalog says of ${leftOut == 1 ? 'it' : 'them'}; " +
                        "the first: ${all.collectMany { PackCatalog catalog -> catalog.problems }.first()})" : '')
    }

    /**
     * @return the version of a plugin that is chosen of those that have the feature, or why none is
     */
    private static Object of(Plugin plugin, List<Version> withIt, String applicationType, String grails, String forge, String managed) {
        List<Version> releases = withIt.findAll { Version version -> PackCatalog.isRelease(version.coordinates.version) }
        Version latest = latestOf(releases)
        if (plugin.deprecated) {
            return "${plugin.coordinates} is deprecated, and is used only by its coordinates, such as ${(latest ?: withIt.first()).coordinates}".toString()
        }
        if (managed != null) {
            Version ofTheBom = withIt.find { Version version -> version.coordinates.version == managed && version.coordinates.toString() == "${plugin.coordinates}:${managed}".toString() }
            if (ofTheBom == null) {
                List<String> has = withIt*.version.take(MAX_REASONS)
                return ("Forge has ${plugin.coordinates} in version ${managed} for the applications it generates, and the catalog has no pack for ${managed} (it has one for " +
                        "${has.join(', ')}${withIt.size() > MAX_REASONS ? ' and others' : ''}); another version is used only by its coordinates, such as " +
                        "${(latest ?: withIt.first()).coordinates}").toString()
            }
            if (!ofTheBom.readable || (ofTheBom.applicationTypes != null && !ofTheBom.applicationTypes.contains(applicationType)) ||
                    !ofTheBom.grails.contains(grails) || !ofTheBom.forge.contains(forge)) {
                return ("Forge has ${plugin.coordinates} in version ${managed} for the applications it generates, and the pack of ${managed} is not one this Forge reads for this " +
                        "application, Grails ${grails} and Forge ${forge}").toString()
            }
            return ofTheBom
        }
        if (latest == null) {
            return "${plugin.coordinates} has it in no release, and a version that is none is used only by its coordinates, such as ${withIt.first().coordinates}".toString()
        }
        List<Version> usable = releases.findAll { Version version ->
            version.readable && (version.applicationTypes == null || version.applicationTypes.contains(applicationType)) &&
                    version.grails.contains(grails) && version.forge.contains(forge)
        }
        if (!usable.isEmpty()) {
            return latestOf(usable)
        }
        if (!latest.readable) {
            return "${plugin.coordinates} has it in no version this Forge reads the pack of (${latest.version} needs another Forge)".toString()
        }
        if (latest.applicationTypes != null && !latest.applicationTypes.contains(applicationType)) {
            return "${plugin.coordinates} has it for applications of type ${latest.applicationTypes.join(', ')}, and this is ${applicationType}".toString()
        }
        return "${plugin.coordinates} has it in no version for Grails ${grails} and Forge ${forge} (${latest.version} is for Grails ${latest.grails} and Forge ${latest.forge})".toString()
    }

    private static Version latestOf(List<Version> releases) {
        Version latest = null
        for (Version each : releases) {
            if (latest == null || PackCatalog.compareReleases(each.coordinates.version, latest.coordinates.version) > 0) {
                latest = each
            }
        }
        return latest
    }

    private PackCatalog one(URI address) {
        String shown = PackArtifacts.shownAddress(address)
        if (FILE.equalsIgnoreCase(address.scheme)) {
            try {
                return PackCatalog.parse(artifacts.file(address, PackCatalog.MAX_BYTES, 'the catalog'), address)
            } catch (PackArtifactException e) {
                throw new PackArtifactException("The feature catalog ${shown} could not be read: ${e.message}")
            }
        }
        String file = digest(address.toString().getBytes(StandardCharsets.UTF_8))
        Path copy = copies?.resolve(file + '.json')
        // says that the catalog nobody named could not be read, and since when
        Path marker = byDefault ? copies?.resolve(file + '.unread') : null
        PackCatalog kept = null
        Instant keptAt = null
        if (copy != null) {
            try {
                if (Files.isRegularFile(copy, LinkOption.NOFOLLOW_LINKS) && Files.size(copy) <= PackCatalog.MAX_BYTES) {
                    keptAt = Files.getLastModifiedTime(copy, LinkOption.NOFOLLOW_LINKS).toInstant()
                    kept = PackCatalog.parse(Files.readAllBytes(copy), address)
                }
            } catch (IOException | RuntimeException ignored) {
                // a copy that cannot be read, or is no catalog, is no copy
                kept = null
            }
        }
        if (kept != null && !refresh) {
            Duration age = Duration.between(keptAt, Instant.now())
            if (artifacts.offline || (!age.isNegative() && age < TIME_TO_LIVE)) {
                return kept
            }
        }
        try {
            Instant failedAt = refresh || artifacts.offline ? null : markedAt(marker)
            if (failedAt != null) {
                throw new PackArtifactException("the last attempt, at ${time(failedAt)}, failed, and it is not asked for again within a day; --refresh asks for it")
            }
            byte[] bytes
            PackCatalog catalog
            try {
                bytes = artifacts.file(address, PackCatalog.MAX_BYTES, 'the catalog', byDefault ? defaultWithin : null)
                catalog = PackCatalog.parse(bytes, address)
            } catch (PackArtifactException e) {
                if (marker != null && !artifacts.offline) {
                    keep(marker, new byte[0])
                }
                throw e
            }
            keep(copy, bytes)
            discard(marker)
            return catalog
        } catch (PackArtifactException e) {
            // what was asked for anew is not answered with what there was
            if (kept == null || refresh) {
                throw new PackArtifactException("The feature catalog ${shown} could not be read: ${e.message}" +
                        (artifacts.offline ? ', and there is no copy of the catalog on this computer' : ''))
            }
            warnings << ("The feature catalog ${shown} could not be read (${e.message}): the copy of it that was read on " +
                    "${time(keptAt)} is used").toString()
            return kept
        }
    }

    private static String time(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.SECONDS).atOffset(ZoneOffset.UTC))
    }

    /**
     * @return when the catalog could not be read, if that was within the time a copy is used for
     */
    private static Instant markedAt(Path marker) {
        if (marker == null) {
            return null
        }
        try {
            if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
                return null
            }
            Instant at = Files.getLastModifiedTime(marker, LinkOption.NOFOLLOW_LINKS).toInstant()
            Duration age = Duration.between(at, Instant.now())
            return !age.isNegative() && age < TIME_TO_LIVE ? at : null
        } catch (IOException | RuntimeException ignored) {
            return null
        }
    }

    // a copy is written whole or not at all: beside its place first, and then moved into it
    private static void keep(Path copy, byte[] bytes) {
        if (copy == null) {
            return
        }
        Path written = null
        try {
            boolean posix = FileSystems.default.supportedFileAttributeViews().contains('posix')
            FileAttribute<?>[] ofDirectory = posix ? [PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString('rwx------'))] as FileAttribute<?>[] : new FileAttribute<?>[0]
            FileAttribute<?>[] ofFile = posix ? [PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString('rw-------'))] as FileAttribute<?>[] : new FileAttribute<?>[0]
            Files.createDirectories(copy.parent, ofDirectory)
            written = Files.createTempFile(copy.parent, 'catalog', '.tmp', ofFile)
            Files.write(written, bytes)
            try {
                Files.move(written, copy, StandardCopyOption.ATOMIC_MOVE)
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(written, copy, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (IOException | RuntimeException ignored) {
            // the catalog was read; that no copy of it could be kept changes nothing of this command
            discard(written)
        }
    }

    private static void discard(Path written) {
        if (written != null) {
            try {
                Files.deleteIfExists(written)
            } catch (IOException | RuntimeException ignored) {
                // nothing more to do about it
            }
        }
    }

    private static String digest(byte[] bytes) {
        return HexFormat.of().formatHex(MessageDigest.getInstance('SHA-256').digest(bytes))
    }

    /**
     * The plugin and the version of it that were chosen for a name, and the catalog that has them.
     */
    static final class Choice {

        final PackCatalog catalog
        final Plugin plugin
        final Version version

        private Choice(PackCatalog catalog, Plugin plugin, Version version) {
            this.catalog = catalog
            this.plugin = plugin
            this.version = version
        }
    }
}
