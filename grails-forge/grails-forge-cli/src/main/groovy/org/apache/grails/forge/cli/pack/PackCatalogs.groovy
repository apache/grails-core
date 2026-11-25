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
import java.nio.file.DirectoryStream
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
 * <p>Of the catalogs that are read, the first that lists a plugin at all, by its
 * {@code group:artifact}, is the one that speaks for it: what a later catalog says of that plugin is
 * not looked at, whether or not the first has a pack or the name that is asked for.</p>
 *
 * <p>A catalog of an https address is kept as a copy in {@code .grails/forge/catalogs} of the home of
 * the user, so that it is not asked for with every command: the bytes as they were read, in a file
 * named by the SHA-256 of the address. A copy is used for a day; an older one is used when the catalog
 * cannot be read, with a warning. A copy is read as the catalog itself is, as something that is not
 * trusted, and only from directories that are the user's own: each of the three has to be a directory
 * and no link, to belong to the user and to be writable by nobody else, or no copy is read or
 * written. A catalog of a file is read every time.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class PackCatalogs {

    /** The plugin index of Grails */
    public static final String PLUGIN_INDEX = 'https://raw.githubusercontent.com/apache/grails-plugins-metadata/main/grails-plugins-index.json'

    /**
     * Whether the plugin index of Grails is read when no catalog is named. It is not: a catalog says
     * which artifact a name is, where it is fetched from and which digest it is held to, so a name is
     * looked up only in a catalog that the command or the environment names.
     */
    public static final boolean PLUGIN_INDEX_UNNAMED = false

    /** How long the catalog nobody named can take: a name that is mistyped does not wait for it as for a jar */
    public static final Duration DEFAULT_WITHIN = Duration.ofSeconds(5)

    /** What is given in the place of an address for no catalog at all */
    public static final String NONE = 'none'

    /** How long a copy of a catalog is used without the catalog being asked for */
    public static final Duration TIME_TO_LIVE = Duration.ofHours(24)

    /** The most catalogs a command reads */
    public static final int MAX_CATALOGS = 10

    /** The most characters of a message that is made of what catalogs say */
    public static final int MAX_MESSAGE = 1000

    private static final Pattern ID = Pattern.compile(/([A-Za-z0-9][A-Za-z0-9_.\-]{0,199}:[A-Za-z0-9][A-Za-z0-9_.\-]{0,199})#([a-z][a-z0-9]*(-[a-z0-9]+)*)/)
    private static final Pattern NAME = Pattern.compile(/[a-z][a-z0-9]*(-[a-z0-9]+)*/)
    private static final int MAX_NAME = 100
    private static final int MAX_REASONS = 3
    private static final int MAX_SWEPT = 100
    private static final Duration LEFT_BEHIND = Duration.ofHours(1)
    private static final String FILE = 'file'

    private final PackArtifacts artifacts
    private final List<URI> addresses
    private final Path home
    private final boolean refresh
    private final boolean byDefault
    private final Duration defaultWithin
    private final List<String> warnings = []
    private List<PackCatalog> catalogs
    private Map<String, Listed> listed
    private String unread
    private boolean copiesLookedAt
    private String copiesRefused
    private Path copies

    /**
     * @param artifacts what reads a file of an address, with its rules
     * @param addresses the addresses of the catalogs, as {@link #addresses} gives them
     * @param home the home of the user, under which the copies are kept, or {@code null} for no copies
     * @param refresh whether every catalog is asked for, whatever copy there is
     * @param byDefault whether the catalogs are those nobody named: one that cannot be read is then no
     * failure of the command, is given less time, and is not asked again for a day
     * @param defaultWithin how long such a catalog can take
     */
    PackCatalogs(PackArtifacts artifacts, List<URI> addresses, Path home, boolean refresh, boolean byDefault, Duration defaultWithin) {
        this.byDefault = byDefault
        this.defaultWithin = defaultWithin
        this.artifacts = artifacts
        this.addresses = addresses
        this.home = home
        this.refresh = refresh
    }

    /**
     * @param given the addresses that were given for this command, each an address or {@link #NONE}
     * @param environment what the environment names in their place, separated by semicolons, or {@code null}
     * @param pluginIndex whether the plugin index of Grails is read when neither names a catalog
     * @return the addresses of the catalogs to read: those that were given, else those of the
     * environment, else the plugin index or none; none when {@link #NONE} is what is said
     * @throws IllegalArgumentException when one is not the address of a catalog
     */
    static List<URI> addresses(List<String> given, String environment, boolean pluginIndex) {
        return addresses(given, environment, null, pluginIndex)
    }

    /**
     * @param indexVariable the plugin index the environment names for every command that reads one
     * ({@code GRAILS_PLUGIN_INDEX}): one address or {@link #NONE}, or {@code null}. It is the catalog
     * when neither the command nor the variable of the catalogs names one
     * @return the addresses of the catalogs to read: those that were given, else those of the
     * environment, else the plugin index the environment names, else the plugin index of Grails or
     * none; none when {@link #NONE} is what is said
     * @see #addresses(List, String, boolean)
     */
    static List<URI> addresses(List<String> given, String environment, String indexVariable, boolean pluginIndex) {
        List<String> named = given ?: (environment ?: '').split(';')*.trim().findAll { String each -> !each.isEmpty() }
        if (named.isEmpty() && indexVariable != null && !indexVariable.trim().isEmpty()) {
            // one address, as the commands that list the plugins read it: a semicolon in it is part of it
            named = [indexVariable.trim()]
        }
        if (named.isEmpty()) {
            return pluginIndex ? [PackArtifacts.catalog(PLUGIN_INDEX)] : Collections.<URI>emptyList()
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
    static boolean isByDefault(List<String> given, String environment, boolean pluginIndex) {
        return isByDefault(given, environment, null, pluginIndex)
    }

    /**
     * @return whether {@link #addresses(List, String, String, boolean)} gives the catalog nobody named for these
     */
    static boolean isByDefault(List<String> given, String environment, String indexVariable, boolean pluginIndex) {
        return pluginIndex && !given && (environment ?: '').split(';')*.trim().every { String each -> each.isEmpty() } &&
                (indexVariable == null || indexVariable.trim().isEmpty())
    }

    /**
     * @param feature one of the features that were asked for
     * @return whether it is the feature of a plugin by its full name, {@code group:artifact#name}
     */
    static boolean isId(String feature) {
        return feature.length() <= PackCatalog.MAX_PLUGIN + MAX_NAME + 1 && ID.matcher(feature).matches()
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

    /**
     * @param feature a name, or a full name as {@link #isId} knows it
     * @return the {@code group:artifact} of a full name, and {@code null} of a name
     */
    static String pluginOf(String feature) {
        return feature.contains('#') ? feature.substring(0, feature.indexOf('#')) : null
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
     * @param bom the BOM the application gets, as {@code group:artifact}, for a message
     * @param managed gives the version that BOM manages an artifact at, by its {@code group:artifact},
     * or {@code null} for one it does not manage. Of such an artifact that version is the one that is
     * taken, release or not, and no other: a pack of another version would be read for a build that
     * has the plugin in the version of the BOM. It can throw an {@link IllegalArgumentException} when
     * the versions of the BOM are not known, which is then what this throws
     * @return what was chosen, or {@code null} when no catalog has a pack of that name
     * @throws IllegalArgumentException when plugins have it and none of them, or more than one, can be
     * chosen: one line of at most {@link #MAX_MESSAGE} characters
     * @throws PackArtifactException when a catalog cannot be read
     */
    Choice choose(String feature, String applicationType, String grails, String forge, String bom, Function<String, String> managed) {
        String name = nameOf(feature)
        String only = pluginOf(feature)
        Map<String, Choice> chosen = new LinkedHashMap<>()
        Map<String, String> refused = new LinkedHashMap<>()
        for (Listed each : listed().values()) {
            Plugin plugin = each.plugin
            if (plugin == null || (only != null && plugin.coordinates != only)) {
                continue
            }
            List<Version> withIt = plugin.versions.findAll { Version version -> version.name == name && version.feature }
            if (withIt.isEmpty()) {
                continue
            }
            Object result = of(plugin, withIt, applicationType, grails, forge, bom, managed)
            if (result instanceof Version) {
                chosen.put(plugin.coordinates, new Choice(each.catalog, plugin, (Version) result))
            } else {
                refused.put(plugin.coordinates, (String) result)
            }
        }
        if (chosen.size() == 1) {
            return chosen.values().first()
        }
        if (chosen.size() > 1) {
            throw new IllegalArgumentException(bounded("More than one plugin of the feature catalog has a feature ${name}: name the one that is meant as " +
                    chosen.keySet().take(MAX_REASONS).collect { String plugin -> "${plugin}#${name}".toString() }.join(' or ') +
                    (chosen.size() > MAX_REASONS ? " (and ${chosen.size() - MAX_REASONS} more)" : '')))
        }
        // a plugin a later catalog has the name for, and of which an earlier one speaks
        for (PackCatalog catalog : read()) {
            for (Plugin plugin : catalog.plugins) {
                Listed first = listed().get(plugin.coordinates)
                if (!first.plugin.is(plugin) && (only == null || plugin.coordinates == only) && !refused.containsKey(plugin.coordinates) &&
                        plugin.versions.any { Version version -> version.name == name && version.feature }) {
                    refused.put(plugin.coordinates, ("the feature catalog ${first.catalog.address} is the first that lists ${plugin.coordinates} and has no feature " +
                            "${name} for it, and what a later catalog says of that plugin is not looked at").toString())
                }
            }
        }
        if (refused.isEmpty()) {
            return null
        }
        throw new IllegalArgumentException(bounded("The feature ${name} is not taken from the feature catalog: " +
                refused.values().take(MAX_REASONS).join('; ') + (refused.size() > MAX_REASONS ? "; and ${refused.size() - MAX_REASONS} more plugins" : '')))
    }

    /**
     * Chooses the plugin and the version that has a preset, as {@link #choose} chooses the one that has a
     * feature, from the packs whose kind is {@code preset}: of the plugins that have a preset of that
     * name, the one that is not deprecated; of its versions, the one the BOM of the application manages,
     * or else the latest release whose preset this Forge reads and that is for this Grails and this
     * Forge, and that is for the type of application where the catalog records the type.
     *
     * @param preset the name of the preset, or its full name, {@code group:artifact#name}
     * @return what was chosen, or {@code null} when no catalog has a preset of that name
     * @throws IllegalArgumentException when plugins have it and none of them, or more than one, can be
     * chosen: one line of at most {@link #MAX_MESSAGE} characters
     * @throws PackArtifactException when a catalog cannot be read
     */
    Choice choosePreset(String preset, String applicationType, String grails, String forge, String bom, Function<String, String> managed) {
        String name = nameOf(preset)
        String only = pluginOf(preset)
        Map<String, Choice> chosen = new LinkedHashMap<>()
        Map<String, String> refused = new LinkedHashMap<>()
        for (Listed each : listed().values()) {
            Plugin plugin = each.plugin
            if (plugin == null || (only != null && plugin.coordinates != only)) {
                continue
            }
            List<Version> withIt = plugin.versions.findAll { Version version -> version.name == name && version.preset }
            if (withIt.isEmpty()) {
                continue
            }
            Object result = of(plugin, withIt, applicationType, grails, forge, bom, managed)
            if (result instanceof Version) {
                chosen.put(plugin.coordinates, new Choice(each.catalog, plugin, (Version) result))
            } else {
                refused.put(plugin.coordinates, (String) result)
            }
        }
        if (chosen.size() == 1) {
            return chosen.values().first()
        }
        if (chosen.size() > 1) {
            throw new IllegalArgumentException(bounded("More than one plugin of the feature catalog has a preset ${name}: name the one that is meant as " +
                    chosen.keySet().take(MAX_REASONS).collect { String plugin -> "${plugin}#${name}".toString() }.join(' or ') +
                    (chosen.size() > MAX_REASONS ? " (and ${chosen.size() - MAX_REASONS} more)" : '')))
        }
        if (refused.isEmpty()) {
            return null
        }
        throw new IllegalArgumentException(bounded("The preset ${name} is not taken from the feature catalog: " +
                refused.values().take(MAX_REASONS).join('; ') + (refused.size() > MAX_REASONS ? "; and ${refused.size() - MAX_REASONS} more plugins" : '')))
    }

    /**
     * What a plugin of a catalog offers for the name of a feature, by the rule {@link #choose} has.
     * The commands that list and describe plugins say it with this, so that what they say a plugin
     * offers is what an application is created with.
     *
     * @param applicationType the type of the application, or {@code null} for whichever type
     * @return the version that is chosen, or why none is, as one line
     */
    static Object offered(Plugin plugin, String name, String applicationType, String grails, String forge, String bom, Function<String, String> managed) {
        List<Version> withIt = plugin.versions.findAll { Version version -> version.name == name && version.feature }
        if (withIt.isEmpty()) {
            return "${plugin.coordinates} has no feature ${name}".toString()
        }
        return of(plugin, withIt, applicationType, grails, forge, bom, managed)
    }

    /**
     * @param name the name of a feature
     * @return the plugins the catalogs have a pack of that name for, as {@code group:artifact}
     */
    List<String> pluginsWith(String name) {
        return listed().values().findAll { Listed each -> each.plugin != null && each.plugin.versions.any { Version version -> version.feature && version.name == name } }
                .collect { Listed each -> each.plugin.coordinates }
    }

    /**
     * What the catalogs offer, for a list: each catalog with its digest, the features that can be
     * selected by their name with the plugin and the version that would be taken, and how many are
     * left out and why. A plugin is listed under the catalog that speaks for it, as it is chosen.
     *
     * @param ofFramework whether a name is that of a feature of the framework, which no plugin has
     * @return the lines of the list, none of which is markup of the command
     */
    List<String> listing(String applicationType, String grails, String forge, Predicate<String> ofFramework, String bom, Function<String, String> managed) {
        List<String> lines = []
        for (PackCatalog catalog : read()) {
            lines << "Feature catalog ${catalog.address}, sha256 ${catalog.sha256}".toString()
            if (catalog.withoutPacks) {
                lines << '  the catalog records no feature pack'
            }
            List<List<String>> rows = []
            int notOffered = 0
            int shadowed = 0
            int ofAnEarlier = 0
            for (Plugin plugin : catalog.plugins) {
                if (!listed().get(plugin.coordinates).plugin.is(plugin)) {
                    ofAnEarlier++
                    continue
                }
                for (String name : plugin.versions.findAll { Version version -> version.feature }*.name.unique()) {
                    Object result = of(plugin, plugin.versions.findAll { Version version -> version.name == name && version.feature }, applicationType, grails, forge, bom, managed)
                    if (ofFramework.test(name)) {
                        shadowed++
                    } else if (result instanceof Version) {
                        // a version that was published under other coordinates is listed as what would be fetched, and of which plugin it is
                        Version taken = (Version) result
                        rows << [name, taken.coordinates.toString() + (artifactOf(taken) == plugin.coordinates ? '' : " of ${plugin.coordinates}"),
                                 plugin.description ?: plugin.title ?: '']
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
                lines << ("  ${notOffered} more ${notOffered == 1 ? 'is' : 'are'} not offered: of a deprecated plugin, without a release, not in the version " +
                        "${bom} manages it at, or not for this application, Grails ${grails} and Forge ${forge}").toString()
            }
            if (shadowed > 0) {
                lines << ("  ${shadowed} more ${shadowed == 1 ? 'has' : 'have'} the name of a feature of the framework, which is the one that is " +
                        'selected by that name').toString()
            }
            if (ofAnEarlier > 0) {
                lines << ("  ${ofAnEarlier} ${ofAnEarlier == 1 ? 'plugin is' : 'plugins are'} not looked at: a catalog that is named before this one lists " +
                        "${ofAnEarlier == 1 ? 'it' : 'them'}").toString()
            }
            if (catalog.versionsOfOtherFormats > 0) {
                lines << ("  ${otherFormats(catalog.versionsOfOtherFormats)}").toString()
            }
            if (catalog.leftOut + catalog.versionsLeftOut > 0) {
                lines << "  ${leftOutAs(catalog.leftOut, catalog.versionsLeftOut, true)}:".toString()
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
        int versionsLeftOut = (all*.versionsLeftOut.sum() ?: 0) as int
        int ofOtherFormats = (all*.versionsOfOtherFormats.sum() ?: 0) as int
        return bounded("the feature ${all.size() == 1 ? 'catalog' : 'catalogs'} ${all*.address.take(MAX_REASONS).join(', ')}" +
                (all.size() > MAX_REASONS ? " and ${all.size() - MAX_REASONS} more" : '') +
                (all.every { PackCatalog catalog -> catalog.withoutPacks } ? ' (which records no feature pack)' : '') +
                (ofOtherFormats > 0 ? " (${otherFormats(ofOtherFormats).uncapitalize()})" : '') +
                (leftOut + versionsLeftOut > 0 ? " (${leftOutAs(leftOut, versionsLeftOut, false)}; " +
                        "the first: ${all.collectMany { PackCatalog catalog -> catalog.problems }.first()})" : ''))
    }

    // what is said of the versions whose packs a later Forge reads: whose names are not known here
    private static String otherFormats(int versions) {
        return "${versions} ${versions == 1 ? 'version has' : 'versions have'} a feature pack of a format this Forge does not read, which a later Forge can " +
                "read; what ${versions == 1 ? 'it has' : 'they have'} is not looked at"
    }

    private static String leftOutAs(int plugins, int versions, boolean sentence) {
        if (versions == 0) {
            return "${plugins} ${plugins == 1 ? (sentence ? 'plugin is' : 'plugin') : (sentence ? 'plugins are' : 'plugins')} left out for what the catalog says of ${plugins == 1 ? 'it' : 'them'}"
        }
        String of = plugins == 0 ? '' : "${plugins} ${plugins == 1 ? 'plugin' : 'plugins'} and "
        return "${of}${versions} ${versions == 1 ? 'version' : 'versions'} ${!sentence ? '' : plugins + versions == 1 ? 'is ' : 'are '}left out for what the catalog says of ${plugins + versions == 1 ? 'it' : 'them'}"
    }

    private static String artifactOf(Version version) {
        return "${version.coordinates.group}:${version.coordinates.artifact}".toString()
    }

    /**
     * @param message a message that is made of what catalogs say
     * @return the message, and no more of it than a message has
     */
    static String bounded(String message) {
        return message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE) + '...' : message
    }

    // the one rule for which catalog speaks for a plugin: the first that lists it, with a pack or without
    private Map<String, Listed> listed() {
        if (listed == null) {
            Map<String, Listed> all = new LinkedHashMap<>()
            for (PackCatalog catalog : read()) {
                for (Plugin plugin : catalog.plugins) {
                    all.putIfAbsent(plugin.coordinates, new Listed(catalog, plugin))
                }
                for (String coordinates : catalog.withoutPack) {
                    all.putIfAbsent(coordinates, new Listed(catalog, null))
                }
            }
            listed = all
        }
        return listed
    }

    /**
     * @return the version of a plugin that is chosen of those that have the feature, or why none is
     */
    private static Object of(Plugin plugin, List<Version> withIt, String applicationType, String grails, String forge, String bom,
                             Function<String, String> versionOfForge) {
        List<Version> releases = withIt.findAll { Version version -> PackCatalog.isRelease(version.version) }
        Version latest = latestOf(releases)
        if (plugin.deprecated != null) {
            return ("${plugin.coordinates} is deprecated${plugin.deprecated.isEmpty() ? '' : " (${plugin.deprecated})"}, and is used only by its coordinates, " +
                    "such as ${(latest ?: withIt.first()).coordinates}").toString()
        }
        // the version a BOM manages is that of an artifact: of the one that would be fetched, whichever plugin the catalog lists it under
        String ofForgeArtifact = ([plugin.coordinates] + withIt.collect { Version version -> artifactOf(version) }).unique()
                .find { String artifact -> versionOfForge.apply(artifact) != null }
        if (ofForgeArtifact != null) {
            String managed = versionOfForge.apply(ofForgeArtifact)
            Version ofForge = withIt.find { Version version -> version.version == managed && artifactOf(version) == ofForgeArtifact }
            if (ofForge == null) {
                return ("${bom}, which the application gets, manages ${ofForgeArtifact} at ${managed}, and the catalog has no pack for ${managed} (it has one for " +
                        "${withIt*.version.take(MAX_REASONS).join(', ')}${withIt.size() > MAX_REASONS ? " and ${withIt.size() - MAX_REASONS} more" : ''}); another version " +
                        "is used only by its coordinates, such as ${(latest ?: withIt.first()).coordinates}").toString()
            }
            if (!ofForge.readable || !isFor(ofForge, applicationType) ||
                    !ofForge.isForGrails(grails) || !ofForge.forge.contains(forge)) {
                return ("${bom}, which the application gets, manages ${ofForgeArtifact} at ${managed}, and the pack of ${managed} is not one this Forge reads for this " +
                        "application, Grails ${grails} and Forge ${forge}").toString()
            }
            return ofForge
        }
        if (latest == null) {
            return "${plugin.coordinates} has it in no release, and a version that is none is used only by its coordinates, such as ${withIt.first().coordinates}".toString()
        }
        List<Version> usable = releases.findAll { Version version ->
            version.readable && isFor(version, applicationType) &&
                    version.isForGrails(grails) && version.forge.contains(forge)
        }
        if (!usable.isEmpty()) {
            return latestOf(usable)
        }
        if (!latest.readable) {
            return "${plugin.coordinates} has it in no version this Forge reads the pack of (${latest.version} needs another Forge)".toString()
        }
        if (!isFor(latest, applicationType)) {
            return "${plugin.coordinates} has it for applications of type ${latest.applicationTypes.join(', ')}, and this is ${applicationType}".toString()
        }
        return ("${plugin.coordinates} has it in no version for Grails ${grails} and Forge ${forge} (${latest.version} is for Grails ${latest.grails}" +
                "${latest.grailsVersion == null ? '' : ", which the descriptor of the plugin gives as ${latest.grailsVersion}"} and Forge ${latest.forge})").toString()
    }

    // a type of application that is not given is any
    private static boolean isFor(Version version, String applicationType) {
        return version.applicationTypes == null || applicationType == null || version.applicationTypes.contains(applicationType)
    }

    private static Version latestOf(List<Version> releases) {
        Version latest = null
        for (Version each : releases) {
            // Equal numeric releases, such as 2.3 and 2.3.0, are distinct artifact coordinates.
            // Break the tie by their spelling rather than the order of the catalog's entries.
            if (latest == null || (PackCatalog.compareReleases(each.version, latest.version) ?: each.version.compareTo(latest.version)) > 0) {
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
        Path directory = copies()
        Path copy = directory?.resolve(file + '.json')
        // says that the catalog nobody named could not be read, and since when
        Path marker = byDefault ? directory?.resolve(file + '.unread') : null
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
            if (artifacts.offline) {
                warnings << "The feature catalog ${shown} is read from its copy of ${time(keptAt)}, ${old(keptAt)}: --offline asks nothing of the network".toString()
                return kept
            }
            if (!age.isNegative() && age < TIME_TO_LIVE) {
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
                        (!artifacts.offline ? '' : copiesRefused == null ? ', and there is no copy of the catalog on this computer' :
                                ", and no copy of a catalog is read on this computer, whether there is one or not: ${copiesRefused}"))
            }
            warnings << ("The feature catalog ${shown} could not be read (${e.message}): the copy of it that was read on " +
                    "${time(keptAt)}, ${old(keptAt)}, is used").toString()
            return kept
        }
    }

    private static String time(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.SECONDS).atOffset(ZoneOffset.UTC))
    }

    // how old a copy is, in words
    private static String old(Instant instant) {
        Duration age = Duration.between(instant, Instant.now())
        if (age.isNegative()) {
            return 'which is a time to come'
        }
        long days = age.toDays()
        long hours = age.toHours()
        return days > 0 ? "${days} ${days == 1 ? 'day' : 'days'} old".toString() : hours > 0 ? "${hours} ${hours == 1 ? 'hour' : 'hours'} old".toString() : 'less than an hour old'
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

    /**
     * The directory of the copies, when it and the two above it, as far as they are there, are the
     * user's own. What is in a directory someone else can write to, or that is reached through a link,
     * is not a copy this command made, and is not read as one.
     *
     * @return the directory, or {@code null} when no copy is read or written
     */
    private Path copies() {
        if (!copiesLookedAt) {
            copiesLookedAt = true
            if (home != null) {
                Path directory = home.resolve('.grails').resolve('forge').resolve('catalogs')
                Path notOurs = OwnDirectory.notOurs(directory)
                if (notOurs == null) {
                    copies = directory
                } else {
                    copiesRefused = OwnDirectory.refusal(notOurs)
                    warnings << "No copy of a feature catalog is read or kept: ${copiesRefused}".toString()
                }
            }
        }
        return copies
    }

    // a copy is written whole or not at all: beside its place first, and then moved into it
    private void keep(Path copy, byte[] bytes) {
        if (copy == null) {
            return
        }
        Path written = null
        try {
            boolean posix = FileSystems.default.supportedFileAttributeViews().contains('posix')
            FileAttribute<?>[] ofDirectory = posix ? [PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString('rwx------'))] as FileAttribute<?>[] : new FileAttribute<?>[0]
            FileAttribute<?>[] ofFile = posix ? [PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString('rw-------'))] as FileAttribute<?>[] : new FileAttribute<?>[0]
            Files.createDirectories(copy.parent, ofDirectory)
            // what was made just now is looked at as what was there before
            if (OwnDirectory.notOurs(copy.parent) != null) {
                return
            }
            sweep(copy.parent)
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

    // what a command that was ended while it wrote a copy left behind
    private static void sweep(Path directory) {
        Instant before = Instant.now() - LEFT_BEHIND
        List<Path> leftBehind = []
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, 'catalog*.tmp')) {
            for (Path file : files) {
                if (leftBehind.size() < MAX_SWEPT && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) &&
                        Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toInstant() < before) {
                    leftBehind << file
                }
            }
        }
        for (Path file : leftBehind) {
            discard(file)
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

    // a plugin as the first catalog that lists it has it; without a pack, the plugin is none
    private static final class Listed {

        final PackCatalog catalog
        final Plugin plugin

        Listed(PackCatalog catalog, Plugin plugin) {
            this.catalog = catalog
            this.plugin = plugin
        }
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
