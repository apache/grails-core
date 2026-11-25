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

import java.nio.file.Path
import java.time.Duration
import groovy.transform.CompileStatic
import org.apache.grails.forge.core.bom.BomVersions
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.pack.PackArtifact
import org.apache.grails.forge.core.pack.PackDescriptor.PackDependency
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.util.VersionInfo
import picocli.CommandLine

/**
 * The feature packs that are not part of Grails which a command was asked for, and how they are
 * taken: a plugin that is named by its coordinates, {@code group:artifact:version}, whose jar is
 * fetched from a repository and held to the SHA-256 that was asked for; and a name, or a full name
 * {@code group:artifact#name}, that nothing here knows and a feature catalog has, which says the
 * plugin, its version, its SHA-256 and where it can be had. Every command that takes such a feature
 * takes it this way: the commands that create an application, and {@code feature add}, which adds
 * one to an application that is there. What they are asked with, and where what they need of the
 * environment comes from, each says as a {@link Host}.
 *
 * <p>Nothing is taken from a repository before every mistake in the command line was found, and
 * what is taken is said before it is asked for: which plugin a catalog made of a name, with which
 * digest, and which repositories are asked.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class PackSelection {

    /**
     * The command a selection is made for: what it was given, what it says, and what it reads of
     * the environment, which a specification gives otherwise.
     */
    static interface Host {

        /** @return the features that were asked for, as they were given: names, full names and coordinates */
        List<String> getAskedFeatures()

        /** @return how a message says where the features were asked for, as {@code asked for with --features} */
        String getAskedWith()

        /** @return what was given with {@code --feature-pack-repository} */
        List<String> getPackRepositories()

        /** @return what was given with {@code --feature-pack-sha256} */
        List<String> getPackDigests()

        /** @return what was given with {@code --feature-catalog} */
        List<String> getPackCatalogs()

        /** @return whether {@code --refresh} was given */
        boolean isCatalogRefresh()

        /** @return whether {@code --offline} was given */
        boolean isPackOffline()

        /** @return the type of the application, as a catalog names it */
        String getApplicationTypeName()

        /** @return the artifact of the BOM of Grails the application gets, as far as it is known */
        String getBomArtifact()

        /** @return whether a name is one the command knows without a pack that is taken: such a name is never looked for in a catalog */
        boolean isKnownFeature(String name)

        /**
         * @param pack a pack that was taken for a feature
         * @param given what it was asked for with
         * @return the pack
         * @throws RuntimeException when the pack is a preset, which is no feature that is asked for
         */
        Pack notAPreset(Pack pack, String given)

        /** @return what takes the jar of a plugin and a feature catalog from where they are, the same one each time */
        PackArtifacts getPackArtifacts()

        /** @return the feature catalogs of the environment ({@code GRAILS_FORGE_CATALOG}), or {@code null} */
        String getCatalogSetting()

        /** @return the plugin index of the environment ({@code GRAILS_PLUGIN_INDEX}), or {@code null} */
        String getPluginIndexSetting()

        /** @return whether the plugin index of Grails is read as a catalog when nothing names one */
        boolean isPluginIndexReadUnnamed()

        /** @return the home of the user, in which the copies of the feature catalogs are kept */
        Path getCatalogHome()

        /** @return the repositories of the build of an application ({@code GRAILS_REPO_URL}), or {@code null} */
        String getGrailsRepoUrl()

        /** @return how long the feature catalog nobody named can take */
        Duration getDefaultCatalogWithin()

        /** @return the versions a BOM of Grails manages, or {@code null} when this Forge has none for it */
        BomVersions bomVersionsOf(String artifact)

        /** @return the command, for a mistake in its command line */
        CommandLine.Model.CommandSpec getCommandSpec()

        /** Says a line. */
        void say(String line)

        /** Warns of something. */
        void warn(String line)
    }

    /**
     * The preset a command that creates an application is made from, which is taken with the
     * features: its digest is among those that were given, it is taken before the features of a
     * catalog are, since what it gives decides the BOM they are chosen for, and it is the first
     * feature of the application.
     */
    static interface Preset {

        /** @return the coordinates the preset was given by, or {@code null} when it was given otherwise */
        String getCoordinates()

        /** @return whether the preset was given by its name in a catalog */
        boolean isByName()

        /**
         * Takes the preset where it was given by its coordinates or by its name.
         *
         * @param repositories the repositories that are asked
         * @param digest the SHA-256 that was asked for of the artifact of its coordinates, or {@code null}
         * @param digests the digests that were asked for, by coordinates
         */
        void take(List<URI> repositories, String digest, Map<String, String> digests)

        /** @return the preset, once it was taken, or {@code null} when the command has none */
        Pack getPack()
    }

    /** What was asked for, once it was taken. */
    @CompileStatic
    static final class Taken {

        /** The packs: those that were given, and those that were taken from repositories */
        final List<Pack> packs

        /** The names of the features, with the name of its pack in the place of each plugin that was given by its coordinates or its full name */
        final List<String> features

        Taken(List<Pack> packs, List<String> features) {
            this.packs = packs
            this.features = features
        }
    }

    private final Host host

    // what is said after a refusal of a name that was looked for in a catalog and not found there
    private String notInCatalog = ''
    private String bomOfCatalogPacks

    PackSelection(Host host) {
        this.host = host
    }

    /**
     * @return what is to be said after the refusal of a name that is no feature: that it was not
     * found in a catalog either, or that a catalog was not read; empty when there is nothing to say
     */
    String getNotInCatalog() {
        return notInCatalog
    }

    /**
     * Takes what was asked for.
     *
     * @param given the packs that were given as directories and files
     * @param preset the preset of the command, or {@code null}
     * @throws CommandLine.ParameterException for a mistake in the command line
     * @throws PackArtifactException when a pack or a catalog could not be taken
     */
    Taken select(List<Pack> given, Preset preset) {
        List<Pack> packs = new ArrayList<Pack>(given)
        // every mistake in the command line is found before a repository is asked for anything
        Map<String, String> digests = new LinkedHashMap<>()
        Map<String, PackArtifacts.Coordinates> plugins = new LinkedHashMap<>()
        List<URI> repositories
        String presetDigest = null
        List<String> unused = []
        // what a catalog can be asked for: a name that is not known here, or the full name of a feature of a plugin
        List<String> sought = host.getAskedFeatures().findAll { String feature ->
            PackCatalogs.isId(feature) || (PackCatalogs.isName(feature) && !host.isKnownFeature(feature) &&
                    !packs.any { Pack pack -> pack.descriptor.name == feature })
        }.unique()
        try {
            if (host.getPackCatalogs()) {
                PackCatalogs.addresses(host.getPackCatalogs(), null, false)
            }
            if (host.isPackOffline() && host.isCatalogRefresh()) {
                throw new IllegalArgumentException('--refresh reads a feature catalog anew and --offline asks nothing of the network: give one of them')
            }
            host.getPackArtifacts().offline = host.isPackOffline()
            host.getPackArtifacts().refresh = host.isCatalogRefresh()
            for (String each : host.getPackDigests()) {
                Map.Entry<String, String> digest = PackArtifacts.digest(each)
                if (digests.containsKey(digest.key) && digests.get(digest.key) != digest.value) {
                    throw new IllegalArgumentException("--feature-pack-sha256 gives two different SHA-256 for ${digest.key}")
                }
                digests.put(digest.key, digest.value)
            }
            for (String feature : host.getAskedFeatures()) {
                if (PackArtifacts.isCoordinates(feature) && !PackCatalogs.isId(feature)) {
                    PackArtifacts.Coordinates coordinates = PackArtifacts.coordinates(feature)
                    plugins.put(coordinates.toString(), coordinates)
                }
            }
            // the SHA-256 of the artifact of a preset that is given by its coordinates is that of the preset, and of no feature
            presetDigest = preset?.getCoordinates() != null ? digests.remove(preset.getCoordinates()) : null
            for (String pinned : digests.keySet()) {
                // a catalog can still give these coordinates for a name, of a feature or of the preset: then this is said after it was read
                if (!plugins.containsKey(pinned) && sought.isEmpty() && !(preset != null && preset.isByName())) {
                    throw new IllegalArgumentException("--feature-pack-sha256 names ${pinned}, which is not among the features ${host.getAskedWith()}")
                }
            }
            // the environment is only read when there is something to look for
            repositories = PackArtifacts.repositories(host.getPackRepositories(),
                    plugins.isEmpty() && preset?.getCoordinates() == null ? null : host.getGrailsRepoUrl(), unused)
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(host.getCommandSpec().commandLine(), e.getMessage())
        }
        for (String each : unused) {
            host.warn(shown(each))
        }
        // the preset first: what it gives decides the BOM the features of a catalog are chosen for
        if (preset != null) {
            preset.take(repositories, presetDigest, digests)
        }
        Map<String, String> names = new HashMap<>()
        for (PackArtifacts.Coordinates coordinates : plugins.values()) {
            Pack pack = host.notAPreset(jarOf(coordinates, repositories, digests.get(coordinates.toString())), coordinates.toString())
            packs << pack
            names.put(coordinates.toString(), pack.descriptor.name)
        }
        // a name the pack of a plugin that was given by its coordinates has is not looked for anywhere
        sought.removeAll { String feature -> packs.any { Pack pack -> pack.descriptor.name == feature } }
        if (!sought.isEmpty() || digests.keySet().any { String pinned -> !plugins.containsKey(pinned) }) {
            fromCatalogs(sought, packs, names, digests, plugins.isEmpty() ? null : repositories)
        }
        // the preset is a feature of the application, applied as the feature of a pack is, and asks for its features as their requirements
        Pack presetPack = preset?.getPack()
        List<String> features = presetPack == null ? [] : [presetPack.descriptor.name]
        if (presetPack != null) {
            packs.add(0, presetPack)
        }
        Set<String> taken = new HashSet<>()
        for (String feature : host.getAskedFeatures()) {
            // the feature of a pack is one selection, however often and in whichever ways it was asked for:
            // by the coordinates of its plugin, by its full name and by its name
            String selected = PackArtifacts.isCoordinates(feature) && names.containsKey(feature) ? names.get(feature) : feature
            if (!names.containsValue(selected) || taken.add(selected)) {
                features << selected
            }
        }
        return new Taken(packs, features)
    }

    /**
     * Looks the names up that nothing here knows. A catalog says which plugin has the feature of a name
     * and which jar that is; the pack is then taken as the pack of a plugin that was given by its
     * coordinates is, with the digest of the catalog as the one the jar has to have. A name no catalog
     * has stays as it is, and is refused with the others that do not exist.
     */
    private void fromCatalogs(List<String> sought, List<Pack> packs, Map<String, String> names, Map<String, String> digests, List<URI> asked) {
        PackCatalogs catalogs = sought.isEmpty() ? null : catalogs()
        List<String> lookedFor = sought.collect { String feature -> FeatureSelectionException.shown(feature) }
        Set<String> pinned = new HashSet<>()
        List<String> notFound = []
        if (catalogs != null && !catalogs.isEmpty()) {
            try {
                catalogs.read()
            } catch (PackArtifactException e) {
                throw new PackArtifactException(PackCatalogs.bounded("${lookedFor.take(3).join(', ')}${lookedFor.size() > 3 ? " and ${lookedFor.size() - 3} more" : ''} " +
                        "${lookedFor.size() == 1 ? 'is' : 'are'} not of Grails or of a feature pack that was given, " +
                        "and ${lookedFor.size() == 1 ? 'was' : 'were'} looked for in a feature catalog. ${e.getMessage()}. A plugin can be given by its " +
                        'coordinates, group:artifact:version, and --feature-catalog names the catalog to read, or none'))
            }
            saidOf(catalogs)
            List<URI> repositories = asked
            // a full name first: the name alone is then the feature of the plugin that was named in full
            for (String feature : sought.findAll { String each -> PackCatalogs.isId(each) } + sought.findAll { String each -> !PackCatalogs.isId(each) }) {
                String name = PackCatalogs.nameOf(feature)
                String plugin = PackCatalogs.pluginOf(feature)
                // what was already taken for this command, by coordinates or by another way of naming it, is not taken again
                if (packs.any { Pack pack ->
                    pack.descriptor.name == name && (plugin == null || (pack.artifact != null && pack.artifact.coordinates.startsWith(plugin + ':')))
                }) {
                    names.put(feature, name)
                    continue
                }
                PackCatalogs.Choice choice
                try {
                    choice = catalogs.choose(feature, host.getApplicationTypeName(), VersionInfo.getGrailsVersion(), VersionInfo.getVersion(),
                            "${BomVersions.GROUP}:${host.getBomArtifact()}", this.&managedVersion)
                } catch (IllegalArgumentException e) {
                    throw new CommandLine.ParameterException(host.getCommandSpec().commandLine(), shown(e.getMessage()))
                }
                if (choice == null) {
                    notFound << feature
                    continue
                }
                if (repositories == null) {
                    List<String> unused = []
                    repositories = PackArtifacts.repositories(host.getPackRepositories(), host.getGrailsRepoUrl(), unused)
                    for (String each : unused) {
                        host.warn(shown(each))
                    }
                }
                PackArtifacts.Coordinates coordinates = choice.version.coordinates
                String from = choice.catalog.address
                // the artifact a catalog has for a version can be another than the plugin it is listed under, and one that was already taken
                if (packs.any { Pack pack -> pack.artifact != null && pack.artifact.coordinates == coordinates.toString() && pack.descriptor.name == name }) {
                    names.put(feature, name)
                    continue
                }
                String asPinned = digests.get(coordinates.toString())
                if (asPinned != null && asPinned != choice.version.sha256) {
                    throw new PackArtifactException("The feature catalog ${from} records the SHA-256 ${choice.version.sha256} for ${coordinates}, " +
                            "and ${asPinned} was asked for with --feature-pack-sha256")
                }
                pinned << coordinates.toString()
                List<URI> where = new ArrayList<>(repositories)
                // the repository the catalog names is asked last, when none of those the user has gave the jar, and not at all when repositories were given
                if (host.getPackRepositories().isEmpty() && choice.version.repository != null && !where.contains(choice.version.repository)) {
                    where << choice.version.repository
                }
                // said before anything of the plugin is asked for: which plugin a catalog made of the name, and where it is looked for
                boolean ofTheCatalog = host.getPackRepositories().isEmpty() && choice.version.repository != null && where.last() == choice.version.repository &&
                        !repositories.contains(choice.version.repository)
                List<URI> ofTheUser = ofTheCatalog ? where.take(where.size() - 1) : where
                boolean underOther = !coordinates.toString().startsWith(choice.plugin.coordinates + ':')
                host.say('@|blue ||@ ' + shown("The feature ${name} is taken as ${coordinates}, " +
                        (underOther ? "the coordinates the catalog gives for version ${choice.version.version} of the plugin ${choice.plugin.coordinates}, " : '') +
                        "which the feature catalog ${from} (sha256 ${choice.catalog.sha256}) " +
                        "names for it, with the SHA-256 ${choice.version.sha256}. Asking ${ofTheUser.take(4).collect { URI each -> PackArtifacts.shownAddress(each) }.join(', ')}" +
                        "${ofTheUser.size() > 4 ? " and ${ofTheUser.size() - 4} more" : ''}" +
                        (ofTheCatalog ? "${ofTheUser.isEmpty() ? '' : ', and then '}${PackArtifacts.shownAddress(choice.version.repository)}, the repository the catalog names" : '')))
                Pack pack = jarOf(coordinates, where, choice.version.sha256)
                if (pack.descriptor.name != choice.version.name) {
                    throw new PackArtifactException("The feature catalog ${from} says that ${coordinates} has the feature ${choice.version.name}, " +
                            "and the jar has the feature pack ${pack.descriptor.name}")
                }
                packs << pack.withArtifact(pack.artifact.withCatalog(choice.catalog.recordableAddress ?: choice.catalog.address, choice.catalog.sha256))
                bomOfCatalogPacks = host.getBomArtifact()
                names.put(feature, pack.descriptor.name)
            }
            // a name of Grails is the feature of Grails, whatever a catalog has under it
            for (String feature : host.getAskedFeatures().unique(false)) {
                if (PackCatalogs.isName(feature) && host.isKnownFeature(feature)) {
                    List<String> others = catalogs.pluginsWith(feature)
                    if (!others.isEmpty()) {
                        host.say("@|blue ||@ The feature ${feature} is that of Grails. The feature catalog has a feature of that name for " +
                                "${others.take(3).join(', ')}${others.size() > 3 ? " and ${others.size() - 3} more" : ''}, which is not used")
                    }
                }
            }
            notFound.sort { String feature -> sought.indexOf(feature) }
            if (catalogs.getUnread() != null) {
                // the catalog nobody named: a name that is not known is what it was without a catalog, and a line says what was not looked at
                notInCatalog = '\n' + shown(PackCatalogs.bounded("${catalogs.getUnread()}, so ${lookedFor.take(3).join(', ')}${lookedFor.size() > 3 ? " and ${lookedFor.size() - 3} more" : ''} " +
                        "${lookedFor.size() == 1 ? 'was' : 'were'} not looked for there"))
            } else if (!notFound.isEmpty()) {
                List<String> missed = notFound.collect { String feature -> FeatureSelectionException.shown(feature) }
                notInCatalog = shown(PackCatalogs.bounded(". ${missed.take(3).join(', ')}${missed.size() > 3 ? " and ${missed.size() - 3} more" : ''} " +
                        "${missed.size() == 1 ? 'was' : 'were'} not found in ${catalogs.searched()} either"))
            }
        }
        for (String each : digests.keySet()) {
            if (!pinned.contains(each) && !names.containsKey(each)) {
                throw new CommandLine.ParameterException(host.getCommandSpec().commandLine(),
                        "--feature-pack-sha256 names ${each}, which is not among the features ${host.getAskedWith()}")
            }
        }
    }

    /**
     * @return the pack of the jar of a plugin, from a repository or from the copy of the jar that is
     * kept on this computer; what keeping the copy warned of is said
     */
    Pack jarOf(PackArtifacts.Coordinates coordinates, List<URI> repositories, String sha256) {
        try {
            return host.getPackArtifacts().resolve(coordinates, repositories, sha256)
        } finally {
            for (String each : host.getPackArtifacts().takeWarnings()) {
                host.warn(shown(each))
            }
        }
    }

    /**
     * Says what a catalog that was read warned of.
     */
    void saidOf(PackCatalogs catalogs) {
        for (String each : catalogs.takeWarnings()) {
            host.warn(shown(each))
        }
    }

    /**
     * @return the feature catalogs of the command: those it names, else those of the environment
     * @throws CommandLine.ParameterException for an address that is none, and for {@code --refresh} with {@code --offline}
     */
    PackCatalogs catalogs() {
        try {
            if (host.isPackOffline() && host.isCatalogRefresh()) {
                throw new IllegalArgumentException('--refresh reads a feature catalog anew and --offline asks nothing of the network: give one of them')
            }
            host.getPackArtifacts().offline = host.isPackOffline()
            host.getPackArtifacts().refresh = host.isCatalogRefresh()
            String setting = host.getCatalogSetting()
            // the plugin index the environment names for the commands that list plugins is the catalog of these too
            String index = host.getPluginIndexSetting()
            boolean pluginIndex = host.isPluginIndexReadUnnamed()
            return new PackCatalogs(host.getPackArtifacts(), PackCatalogs.addresses(host.getPackCatalogs(), setting, index, pluginIndex), host.getCatalogHome(),
                    host.isCatalogRefresh(), PackCatalogs.isByDefault(host.getPackCatalogs(), setting, index, pluginIndex), host.getDefaultCatalogWithin())
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(host.getCommandSpec().commandLine(), e.getMessage())
        }
    }

    /**
     * @return the version the BOM the application gets manages an artifact at, or {@code null} for one
     * it does not manage
     * @throws IllegalArgumentException when this Forge does not have the versions of that BOM: a name
     * is then not made a version of, since the build of the application could have the plugin in another
     */
    String managedVersion(String artifact) {
        String bom = host.getBomArtifact()
        BomVersions versions = host.bomVersionsOf(bom)
        if (versions == null) {
            throw new IllegalArgumentException("A feature is not taken from a feature catalog for an application with ${BomVersions.GROUP}:${bom}: this Forge " +
                    "does not have the versions that BOM manages (it has those of ${shipped()}), " +
                    'and so cannot tell which version of the plugin the application would have. Give the plugin by its coordinates, group:artifact:version')
        }
        return versions.versionOf(artifact)
    }

    /**
     * @return the version the BOM the application gets manages an artifact at where that is known:
     * for a list, which says what would be taken and does not fail where it is not known
     */
    String managedIfKnown(String artifact) {
        return host.bomVersionsOf(host.getBomArtifact())?.versionOf(artifact)
    }

    // the BOMs this Forge has the versions of, as a sentence names them
    private static String shipped() {
        List<String> boms = BomVersions.SHIPPED.collect { String each -> "${BomVersions.GROUP}:${each}".toString() }
        return boms.size() < 2 ? boms.join('') : "${boms.take(boms.size() - 1).join(', ')} and ${boms.last()}"
    }

    /**
     * Holds the packs that a catalog gave for names to the BOM they were chosen for. Which version of
     * a plugin was taken depends on the BOM; the features of the application, once all are known, can
     * give it another BOM than the command said, and then the choice is not made again.
     *
     * @param actual the artifact of the BOM the application gets with all its features
     * @param how what is to be done about it, as one sentence without a full stop
     * @throws CommandLine.ParameterException when the packs were chosen for another BOM
     */
    void heldToItsBom(String actual, String how) {
        if (bomOfCatalogPacks != null && bomOfCatalogPacks != actual) {
            throw new CommandLine.ParameterException(host.getCommandSpec().commandLine(), 'The features that were found by their names in a feature catalog were chosen for an ' +
                    "application with ${BomVersions.GROUP}:${bomOfCatalogPacks}, and with all its features the application gets ${BomVersions.GROUP}:${actual}, " +
                    "which can manage their plugins at other versions. ${how}")
        }
    }

    /**
     * What is said of where a pack came from, and of one that was taken from a repository which jar it was.
     *
     * @param artifacts what took the jar, which says when it was the copy that is kept on this computer
     * @return the lines, the first one a headline
     */
    static List<String> origin(Pack pack, PackArtifacts artifacts) {
        PackArtifact artifact = pack.artifact
        List<String> lines = []
        lines << ("@|blue ||@ ${pack.descriptor.preset ? 'Preset' : 'Feature pack'} ${pack.descriptor.name} from ${shown(pack.origin)}" +
                (artifact == null ? '' : ", sha256 ${artifact.sha256}")).toString()
        if (artifact != null) {
            // the digest says which jar was read; only a checksum from elsewhere says it is the one that was published
            if (artifact.catalog != null) {
                String catalog = shown(artifact.catalog)
                lines << ("    the coordinates are those the feature catalog ${catalog.length() > 200 ? catalog.substring(0, 200) + '...' : catalog} " +
                        "(sha256 ${artifact.catalogSha256}) has for the ${pack.descriptor.preset ? 'preset' : 'feature'} ${pack.descriptor.name}").toString()
                // the catalog chose the jar and said what its digest is: that the two agree is no word of anyone else
                lines << '    the SHA-256 is the one that same catalog gives for the jar: the jar is the one the catalog means, which nobody else has checked'
            } else if (artifact.pinned) {
                lines << '    the jar has the SHA-256 that was asked for with --feature-pack-sha256'
            }
            String copy = artifacts.copyUsed(artifact.sha256)
            if (copy != null) {
                // no repository was asked: what a repository publishes for the jar was not looked at now
                lines << "    ${shown(copy)}".toString()
            } else {
                lines << (artifact.checkedAgainst == null ? '    the jar was not checked: the repository gave no checksum for it' :
                        "    the jar matches the ${artifact.checkedAgainst} checksum the repository publishes for it").toString()
            }
        }
        return lines
    }

    /**
     * A pack adds its plugin to the application only by declaring it. What it declares can be something
     * else than the plugin it was taken from: no dependency on it, or one on another version of it.
     *
     * @param generated what is said of the application: {@code generated}, or {@code changed} for one that is there
     * @return what to warn of, or {@code null} when the application gets the plugin the pack was taken
     * from, in that version or in the one a platform of the build gives it
     */
    static String unlikeItsPlugin(Pack pack, String generated = 'generated') {
        int at = pack.artifact.coordinates.lastIndexOf(':')
        String plugin = pack.artifact.coordinates.substring(0, at)
        String version = pack.artifact.coordinates.substring(at + 1)
        List<PackDependency> declared = pack.descriptor.dependencies.findAll { PackDependency dependency ->
            "${dependency.groupId}:${dependency.artifactId}".toString() == plugin
        }
        if (declared.isEmpty()) {
            return "The feature pack ${pack.descriptor.name} declares no dependency on ${plugin}, the plugin it was taken from: " +
                    "the application is ${generated} without that plugin"
        }
        if (declared.any { PackDependency dependency -> dependency.version == null || dependency.version == version }) {
            return null
        }
        return "The feature pack ${pack.descriptor.name} was taken from version ${version} of ${plugin} and declares that plugin in " +
                "version ${declared*.version.unique().join(' and ')}: the application is ${generated} with ${declared*.version.unique().join(' and ')}, " +
                "not with ${version}"
    }

    private static String shown(String text) {
        return text.replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |')
    }
}
