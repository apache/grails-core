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
package org.apache.grails.forge.cli.plugin

import java.time.Duration
import java.util.concurrent.Callable
import java.util.function.Function
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.GradleRunner
import org.apache.grails.forge.cli.pack.PackArtifactException
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.cli.pack.PackCatalog
import org.apache.grails.forge.cli.pack.PackCatalogs
import org.apache.grails.forge.core.bom.BomVersions
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.ForgeContext
import org.grails.forge.cli.command.BaseCommand
import org.grails.forge.cli.command.CreateCommand
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureRegistry
import org.grails.forge.util.VersionInfo
import picocli.CommandLine

/**
 * What {@code list-plugins} and {@code plugin-info} share: the plugins they know, and where they know
 * them from. There are two lists. The plugins of this release of Grails are in the command line
 * itself ({@link BundledPlugins}), with the versions the Grails BOM manages them at, and are always
 * there. The plugins of everyone else are in the plugin index of Grails ({@link PluginIndex}), which
 * is read from its address when one of the two commands runs; when it cannot be read the command
 * says so and answers with the first list. The feature packs the index records for a plugin are read
 * from it as the create commands read a feature catalog ({@link PackCatalog}), so that what these
 * commands say a plugin offers is what an application is created with; the plugin index that
 * {@code GRAILS_PLUGIN_INDEX} names is the feature catalog of those commands too.
 *
 * <p>A command line that is said to create an application with the pack of a plugin does so when it
 * is typed as it is said, in the same environment: it names the create command of a type of
 * application the pack is for, and the plugin, the feature catalog and the repository the pack is
 * taken from wherever the create command would otherwise take another ({@link #commandsOf}).</p>
 *
 * @since 9.0
 */
@CompileStatic
abstract class PluginsCommand extends BaseCommand implements Callable<Integer> {

    /** The environment variable that names the plugin index */
    public static final String INDEX_VARIABLE = 'GRAILS_PLUGIN_INDEX'

    /** What is given in the place of an address for no plugin index */
    public static final String NONE = PackCatalogs.NONE

    /** How long the plugin index can take, from the request to its last byte */
    public static final Duration INDEX_WITHIN = Duration.ofSeconds(20)

    /** How long one file of a Maven repository can take */
    public static final Duration FILE_WITHIN = Duration.ofSeconds(15)

    /** The format of the documents {@code --json} writes */
    public static final int FORMAT = 1

    /** What stands for the name of the application in a command line that creates one */
    public static final String APPLICATION = 'NAME'

    private static final String CATALOG_VARIABLE = 'GRAILS_FORGE_CATALOG'
    private static final String HTTPS = 'https'

    @CommandLine.Option(names = ['--plugin-index'], paramLabel = 'URL',
            description = 'The plugin index to read in the place of that of Grails. The address is https, or file: for a file; none is for no ' +
                    'plugin index, which leaves the plugins of this release of Grails. Without the option the GRAILS_PLUGIN_INDEX environment ' +
                    'variable names the index, and without that the plugin index of Grails is read.')
    String pluginIndex

    @CommandLine.Option(names = ['--offline'],
            description = 'Ask nothing of the network: only the plugins of this release of Grails are known, and a plugin index or a repository ' +
                    'that is a file or a directory.')
    boolean offline

    @CommandLine.Option(names = ['--json'], description = 'Write what the command says as one JSON document for a tool to read.')
    boolean json

    /** What there is to say besides the answer: what was not read, and why. Each is one line of text. */
    protected final List<String> notes = []

    private final ForgeContext context
    private PackArtifacts packArtifacts
    private String indexAddress
    private URI indexUri
    private String indexProblem
    private List<BomVersions> boms
    // the plugins the index records a feature pack for: a name more than one of them has is given with its plugin
    private Collection<PackCatalog.Plugin> withPacks = Collections.<PackCatalog.Plugin>emptyList()
    private Set<String> ofGrails

    /**
     * @param context the Forge application of the command line, which is started only to tell the
     * feature of a pack of the plugin index from a feature of Grails of the same name
     */
    protected PluginsCommand(ForgeContext context) {
        this.context = context
    }

    @Override
    Integer call() throws Exception {
        try {
            return run()
        } catch (IllegalArgumentException e) {
            // an address that was given and is none
            throw new CommandLine.ParameterException(spec.commandLine(), e.message)
        }
    }

    protected abstract int run()

    /**
     * @return the plugins that are known, those of this release of Grails first and then those of the
     * plugin index that are not among them. Of a plugin both lists have, the index adds what the
     * first does not say: its versions, documentation and whether it is deprecated.
     * @throws IllegalArgumentException when the address that is given for the plugin index is none
     */
    protected List<PluginRecord> known() {
        Map<String, PluginRecord> plugins = new LinkedHashMap<>()
        for (PluginRecord each : BundledPlugins.all()) {
            plugins.put(each.coordinates, each)
        }
        Map<String, PackCatalog.Plugin> packs = new HashMap<>()
        PluginIndex index = index(packs)
        if (index != null) {
            for (PluginRecord each : index.plugins) {
                PluginRecord ofGrails = plugins.get(each.coordinates)
                if (ofGrails == null) {
                    each.packs = packs.get(each.coordinates)
                    plugins.put(each.coordinates, each)
                } else {
                    ofGrails.versions = each.versions
                    ofGrails.documentation = ofGrails.documentation ?: each.documentation
                    ofGrails.deprecated = each.deprecated
                    ofGrails.repository = each.repository
                    ofGrails.packs = packs.get(each.coordinates)
                }
            }
        }
        return new ArrayList<PluginRecord>(plugins.values())
    }

    /**
     * @param packs given the plugins the index records a feature pack for, by their {@code group:artifact}
     */
    private PluginIndex index(Map<String, PackCatalog.Plugin> packs) {
        String option = pluginIndex
        String named = option ?: (pluginIndexSetting() ?: '').trim()
        if (named == NONE) {
            return null
        }
        URI address = PackArtifacts.pluginIndex(named ?: PackCatalogs.PLUGIN_INDEX)
        indexAddress = PackArtifacts.shownAddress(address)
        indexUri = address
        byte[] bytes
        PluginIndex read
        try {
            bytes = artifacts().file(address, PluginIndex.MAX_BYTES, 'the plugin index', indexWithin())
            read = PluginIndex.parse(bytes)
        } catch (PackArtifactException e) {
            indexProblem = GradleRunner.line(e.message)
            notes << ("The plugin index ${indexAddress} was not read (${indexProblem}), so the plugins of the community are not known to this command: " +
                    'only the plugins of this release of Grails are').toString()
            return null
        }
        // the packs as a create command reads them from the same bytes, by the one reading there is of them
        try {
            for (PackCatalog.Plugin each : PackCatalog.parse(bytes, address).plugins) {
                packs.put(each.coordinates, each)
            }
            withPacks = packs.values()
        } catch (PackArtifactException e) {
            notes << "The feature packs of the plugin index ${indexAddress} were not read (${GradleRunner.line(e.message)}): no feature of a plugin of it is named".toString()
        }
        return read
    }

    /**
     * What a plugin of the plugin index offers to the create commands: for each feature its packs
     * have, the type of application and the version an application of this Grails and this Forge is
     * created with, as those commands choose it, or why none is chosen. The type is the first the pack
     * is for in the order of {@link ApplicationType}, a web application before a REST API and an
     * application before a plugin, and the version is the one the create command of that type chooses.
     * A feature with the name of a feature of Grails is offered for none: the create commands select
     * the feature of Grails by that name, and refuse a pack that has it.
     */
    protected List<Offer> offers(PluginRecord plugin) {
        if (plugin.packs == null) {
            return Collections.<Offer>emptyList()
        }
        List<Offer> offers = []
        String bom = managed(plugin.coordinates)?.key ?: "${BomVersions.GROUP}:${BomVersions.SHIPPED.first()}".toString()
        Function<String, String> managedAt = { String artifact -> managed(artifact)?.value } as Function<String, String>
        for (String name : plugin.packs.versions.findAll { PackCatalog.Version each -> each.feature }*.name.unique()) {
            if (isOfGrails(name)) {
                offers << new Offer(name, null, null, 'the name is that of a feature of Grails, which is the one that is selected by it, and a feature pack of that name is refused')
                continue
            }
            Offer offer = null
            for (ApplicationType type : ApplicationType.values()) {
                Object chosen = PackCatalogs.offered(plugin.packs, name, typeName(type), grailsVersion(), forgeVersion(), bom, managedAt)
                if (chosen instanceof PackCatalog.Version) {
                    offer = new Offer(name, type, (PackCatalog.Version) chosen, null)
                    break
                }
            }
            if (offer == null) {
                Object forAnyType = PackCatalogs.offered(plugin.packs, name, null, grailsVersion(), forgeVersion(), bom, managedAt)
                // a version that is chosen when the type is not looked at is for types of application a later Forge knows
                offer = new Offer(name, null, null, forAnyType instanceof PackCatalog.Version ?
                        "${plugin.coordinates} has it for applications of type ${((PackCatalog.Version) forAnyType).applicationTypes?.join(', ')}, which this Forge does not create".toString() :
                        GradleRunner.line((String) forAnyType))
            }
            offers << offer
        }
        return offers
    }

    /**
     * @return one entry for each offer, as a JSON document has it: the {@code name} of the feature,
     * whether it is {@code offered}, and the {@code version}, {@code coordinates},
     * {@code applicationTypes} and {@code applicationType} of the pack that is, or the {@code reason}
     * none is
     */
    protected static List<Map<String, Object>> featurePacks(List<Offer> offers) {
        List<Map<String, Object>> entries = []
        for (Offer offer : offers) {
            Map<String, Object> entry = new LinkedHashMap<>()
            entry.put('name', offer.name)
            entry.put('offered', offer.version != null)
            if (offer.version != null) {
                entry.put('version', offer.version.version)
                entry.put('coordinates', offer.version.coordinates.toString())
                entry.put('applicationTypes', offer.version.applicationTypes == null ? null : new ArrayList<String>(offer.version.applicationTypes))
                entry.put('applicationType', typeName(offer.applicationType))
            } else {
                entry.put('reason', offer.reason)
            }
            entries << entry
        }
        return entries
    }

    /**
     * The command lines that create an application with the pack of an offer, each as the words that
     * follow the name of the program, the name of the application being {@link #APPLICATION}. Each is
     * the create command of the type the version was chosen for, and selects the pack in one way:
     * <ul>
     * <li>by the coordinates of the version, and with the repository the plugin index names for it
     * when the create command would not ask that repository: a plugin given by its coordinates is
     * looked for in the repositories of {@code GRAILS_REPO_URL} and Maven Central only, and in the
     * place of those in the repositories {@code --feature-pack-repository} names;</li>
     * <li>by the name of the feature, with the plugin index as the feature catalog: by its full name,
     * {@code group:artifact#name}, when another plugin of the index has a feature of that name; with
     * {@code --feature-catalog} unless the environment makes the index the one catalog of the create
     * commands; and with {@code --refresh} for an index of the network, which the create commands
     * would otherwise read from the copy they keep of it for a day.</li>
     * </ul>
     *
     * @param repositories the repositories a create command asks for a plugin that is given by its
     * coordinates, when the command line names none
     */
    protected List<List<String>> commandsOf(PluginRecord plugin, Offer offer, List<URI> repositories) {
        String create = CreateCommand.CREATE_COMMANDS.get(offer.applicationType)
        List<String> byCoordinates = [create, APPLICATION, '--features', offer.version.coordinates.toString()]
        URI repository = offer.version.repository
        if (repository != null && !repositories.contains(repository)) {
            byCoordinates.addAll([PackArtifacts.REPOSITORY_OPTION, repository.toString()])
        }
        boolean ofAnother = withPacks.any { PackCatalog.Plugin each ->
            each.coordinates != plugin.coordinates && each.versions.any { PackCatalog.Version version -> version.feature && version.name == offer.name }
        }
        List<String> byName = [create, APPLICATION, '--features', ofAnother ? "${plugin.coordinates}#${offer.name}".toString() : offer.name]
        if (!isCatalogOfTheEnvironment()) {
            byName.addAll(['--feature-catalog', indexUri.toString()])
        }
        if (HTTPS.equalsIgnoreCase(indexUri.scheme)) {
            byName << '--refresh'
        }
        return [byCoordinates, byName]
    }

    /**
     * @return whether a create command reads the plugin index, and no other catalog, without being
     * told: when the catalogs of the environment are the index alone, as {@code GRAILS_FORGE_CATALOG}
     * names them, or else {@code GRAILS_PLUGIN_INDEX}
     */
    private boolean isCatalogOfTheEnvironment() {
        try {
            return PackCatalogs.addresses(Collections.<String>emptyList(), featureCatalogSetting(), pluginIndexSetting(),
                    PackCatalogs.PLUGIN_INDEX_UNNAMED) == [indexUri]
        } catch (IllegalArgumentException ignored) {
            // what the environment names is no catalog, which a catalog that is named in the command takes the place of
            return false
        }
    }

    /**
     * @return whether a name is that of a feature of Grails, of whichever type of application. The
     * Forge application is started to know, the first time a name is asked about; when it cannot be,
     * which keeps the create commands from creating anything too, a note says so
     */
    protected boolean isOfGrails(String name) {
        if (ofGrails == null) {
            ofGrails = new HashSet<String>()
            try {
                for (Feature feature : context.get().getBean(FeatureRegistry).all()) {
                    ofGrails << feature.name
                }
            } catch (ForgeContext.NotStartedException e) {
                notes << GradleRunner.line(e.message)
            }
        }
        return ofGrails.contains(name)
    }

    private static String typeName(ApplicationType type) {
        return type.getName().replace('_', '-')
    }

    /**
     * @return the version of this Forge, which a pack has to be for
     */
    protected String forgeVersion() {
        return VersionInfo.getVersion()
    }

    /**
     * @return what is said of the plugin index in a JSON document: its address, whether it was read,
     * and why not; {@code null} when none was asked for
     */
    protected Map<String, Object> indexSaid() {
        if (indexAddress == null) {
            return null
        }
        Map<String, Object> said = new LinkedHashMap<>()
        said.put('address', indexAddress)
        said.put('read', indexProblem == null)
        if (indexProblem != null) {
            said.put('problem', indexProblem)
        }
        return said
    }

    protected boolean isIndexRead() {
        return indexAddress != null && indexProblem == null
    }

    /**
     * @return the BOM of Grails that manages an artifact and the version it manages it at, or
     * {@code null} for an artifact no BOM this command line has the versions of manages
     */
    protected Map.Entry<String, String> managed(String coordinates) {
        if (boms == null) {
            boms = BomVersions.SHIPPED.collect { String bom -> BomVersions.of(bom) }.findAll { BomVersions each -> each != null }
        }
        for (BomVersions each : boms) {
            String version = each.versionOf(coordinates)
            if (version != null) {
                return new AbstractMap.SimpleImmutableEntry<String, String>(each.coordinates, version)
            }
        }
        return null
    }

    /**
     * @return the version of a plugin an application of this Grails gets: the one a Grails BOM manages
     * it at, else the latest the plugin index has for this Grails; {@code null} when neither is known
     */
    protected String versionOf(PluginRecord plugin) {
        return managed(plugin.coordinates)?.value ?: plugin.latestFor(grailsVersion())?.version
    }

    /** Says the notes as warnings, where the errors are; a JSON document has them in itself. */
    protected void sayNotes() {
        if (!json) {
            for (String note : notes) {
                warned(GradleRunner.line(note))
            }
        }
    }

    protected PackArtifacts artifacts() {
        if (packArtifacts == null) {
            packArtifacts = createPackArtifacts()
            packArtifacts.offline = offline
        }
        return packArtifacts
    }

    /**
     * @return what reads the plugin index and a file of a Maven repository from where they are
     */
    protected PackArtifacts createPackArtifacts() {
        return new PackArtifacts()
    }

    /**
     * @return the plugin index that is read when the command names none: an address, {@code none} for
     * no index, or {@code null} when the environment names none
     */
    protected String pluginIndexSetting() {
        return System.getenv(INDEX_VARIABLE)
    }

    /**
     * @return the feature catalogs the create commands read in the place of the plugin index of the
     * environment ({@code GRAILS_FORGE_CATALOG}): addresses separated by semicolons, {@code none}, or
     * {@code null} when the environment names none
     */
    protected String featureCatalogSetting() {
        return System.getenv(CATALOG_VARIABLE)
    }

    /**
     * @return how long the plugin index can take
     */
    protected Duration indexWithin() {
        return INDEX_WITHIN
    }

    /**
     * @return the version of Grails this command line belongs to
     */
    protected String grailsVersion() {
        return VersionInfo.getGrailsVersion()
    }

    /**
     * What a plugin of the plugin index offers to the create commands for one feature of its packs.
     */
    @CompileStatic
    static final class Offer {

        /** The name of the feature */
        final String name
        /** The type of application the version is chosen for, or {@code null} when none is offered */
        final ApplicationType applicationType
        /** The version an application of that type is created with, or {@code null} */
        final PackCatalog.Version version
        /** Why no version is offered, as one line, or {@code null} when one is */
        final String reason

        Offer(String name, ApplicationType applicationType, PackCatalog.Version version, String reason) {
            this.name = name
            this.applicationType = applicationType
            this.version = version
            this.reason = reason
        }
    }
}
