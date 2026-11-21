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
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.GradleRunner
import org.apache.grails.forge.cli.pack.PackArtifactException
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.cli.pack.PackCatalogs
import org.apache.grails.forge.core.bom.BomVersions
import org.grails.forge.cli.command.BaseCommand
import org.grails.forge.util.VersionInfo
import picocli.CommandLine

/**
 * What {@code list-plugins} and {@code plugin-info} share: the plugins they know, and where they know
 * them from. There are two lists. The plugins of this release of Grails are in the command line
 * itself ({@link BundledPlugins}), with the versions the Grails BOM manages them at, and are always
 * there. The plugins of everyone else are in the plugin index of Grails ({@link PluginIndex}), which
 * is read from its address when one of the two commands runs, and by no other command; when it
 * cannot be read the command says so and answers with the first list.
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

    private PackArtifacts packArtifacts
    private String indexAddress
    private String indexProblem
    private List<BomVersions> boms

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
        PluginIndex index = index()
        if (index != null) {
            for (PluginRecord each : index.plugins) {
                PluginRecord ofGrails = plugins.get(each.coordinates)
                if (ofGrails == null) {
                    plugins.put(each.coordinates, each)
                } else {
                    ofGrails.versions = each.versions
                    ofGrails.documentation = ofGrails.documentation ?: each.documentation
                    ofGrails.deprecated = each.deprecated
                    ofGrails.repository = each.repository
                }
            }
        }
        return new ArrayList<PluginRecord>(plugins.values())
    }

    private PluginIndex index() {
        String named = pluginIndex ?: (pluginIndexSetting() ?: '').trim()
        if (named == NONE) {
            return null
        }
        URI address = PackArtifacts.pluginIndex(named ?: PackCatalogs.PLUGIN_INDEX)
        indexAddress = PackArtifacts.shownAddress(address)
        try {
            return PluginIndex.parse(artifacts().file(address, PluginIndex.MAX_BYTES, 'the plugin index', indexWithin()))
        } catch (PackArtifactException e) {
            indexProblem = GradleRunner.line(e.message)
            notes << ("The plugin index ${indexAddress} was not read (${indexProblem}), so the plugins of the community are not known to this command: " +
                    'only the plugins of this release of Grails are').toString()
            return null
        }
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
}
