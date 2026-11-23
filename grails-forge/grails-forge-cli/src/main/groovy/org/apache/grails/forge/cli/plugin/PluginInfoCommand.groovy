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

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.UsageHelp
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.cli.plugin.PluginRecord.Release
import org.grails.forge.cli.command.FeatureCommand
import org.grails.forge.feature.FeatureSelectionException
import picocli.CommandLine

/**
 * {@code plugin-info}: what is known of one Grails plugin, which is named as {@code list-plugins}
 * names it or by its coordinates: its versions, what it is for, its license, where its documentation,
 * issues and sources are, the line that adds it to the build of an application, and the feature of
 * the command line that adds it, where there is one.
 *
 * <p>Besides the two lists of {@link PluginsCommand}, the Maven repositories are asked for the
 * versions of the plugin and for what the plugin says of itself ({@link PluginArtifacts}): those of
 * {@code GRAILS_REPO_URL}, then Maven Central, then the repository the plugin index names for the
 * plugin. A plugin neither list has is looked for in the repositories when it is given by its
 * coordinates.</p>
 *
 * @since 9.0
 */
@CommandLine.Command(name = PluginInfoCommand.NAME, description = [
        'Says what is known of one Grails plugin: its versions, what it is for, its license, where its documentation, issues and sources are,',
        'and the line that adds it to the build of an application. @|bold list-plugins|@ lists the plugins.'])
@CompileStatic
class PluginInfoCommand extends PluginsCommand {

    public static final String NAME = 'plugin-info'

    private static final int MAX_NAMED = 5
    private static final String GRAILS_PREFIX = 'grails-'
    private static final String SNAPSHOT = '-SNAPSHOT'

    @CommandLine.Parameters(arity = '1', paramLabel = 'PLUGIN', completionCandidates = BundledPluginCandidates,
            description = 'The plugin: its name or its artifact, as list-plugins has them, or its coordinates as group:artifact.')
    String plugin

    @CommandLine.Option(names = ['--snapshots'], description = 'Lists the snapshot versions among the versions as well.')
    boolean snapshots

    @Override
    protected int run() {
        List<PluginRecord> all = known()
        boolean byCoordinates = plugin.contains(':')
        if (byCoordinates && !PluginIndex.isCoordinates(plugin)) {
            throw new CommandLine.ParameterException(spec.commandLine(), "'${FeatureSelectionException.shown(plugin)}' is not the coordinates of a plugin: " +
                    'they are group:artifact, without a version')
        }
        List<PluginRecord> found = byCoordinates ? all.findAll { PluginRecord each -> each.coordinates == plugin } : named(all)
        if (found.size() > 1) {
            return failed("More than one plugin is called ${FeatureSelectionException.shown(plugin)}: name the one that is meant by its coordinates, " +
                    found.take(MAX_NAMED)*.coordinates.join(' or ') + (found.size() > MAX_NAMED ? " (and ${found.size() - MAX_NAMED} more)" : ''))
        }
        PluginRecord record = found ? found.first() : byCoordinates ? new PluginRecord(coordinates: plugin, name: plugin.substring(plugin.indexOf(':') + 1)) : null
        if (record == null) {
            return failed(unknown(all))
        }
        PluginArtifacts repositories = new PluginArtifacts(artifacts(), repositories(record), FILE_WITHIN)
        PluginArtifacts.Versions published = repositories.versions(record.coordinates)
        if (found.isEmpty() && published == null) {
            notes.addAll(repositories.notes)
            return failed("No plugin ${record.coordinates} is known: it is not among the plugins of this release of Grails" +
                    (indexRead ? ' or of the plugin index' : '') + ', and no repository that was asked has it')
        }
        Map.Entry<String, String> managed = managed(record.coordinates)
        List<String> versions = (published != null ? published.versions : record.versions*.version)
                .findAll { String version -> snapshots || !version.endsWith(SNAPSHOT) }
        String latest = latest(record, published, versions)
        String forGrails = managed?.value ?: record.latestFor(grailsVersion())?.version
        // what the plugin says of itself, in the version an application would get, or else in its latest
        String described = [forGrails, latest].find { String version -> version != null && !version.endsWith(SNAPSHOT) }
        Map<String, String> said = described == null ? null : repositories.description(record.coordinates, described)
        notes.addAll(repositories.notes)

        Map<String, Object> info = new LinkedHashMap<>()
        info.put('name', record.name)
        info.put('coordinates', record.coordinates)
        info.put('ofGrails', record.ofGrails)
        info.put('title', said?.get('title'))
        info.put('description', said?.get('description') ?: record.description)
        info.put('grailsVersion', grailsVersion())
        info.put('version', forGrails)
        info.put('managedBy', managed?.key)
        info.put('latestVersion', latest)
        info.put('versions', versions)
        info.put('versionsFrom', published != null ? published.from : versions ? 'the plugin index' : null)
        info.put('requiresGrails', said?.get('grailsVersion') ?: record.versions.find { Release each -> each.version == (forGrails ?: latest) }?.grailsVersion)
        info.put('license', said?.get('license') ?: record.license)
        info.put('documentation', said?.get('documentation') ?: record.documentation)
        info.put('issueTracker', said?.get('issues') ?: record.issues)
        info.put('source', said?.get('source') ?: record.source)
        info.put('deprecated', record.deprecated)
        info.put('dependency', dependency(record, managed, forGrails ?: latest))
        info.put('features', record.features)
        info.put('featurePacks', featurePacks(record))
        if (json) {
            Map<String, Object> document = new LinkedHashMap<>()
            document.put('format', FORMAT)
            document.put('pluginIndex', indexSaid())
            document.put('notes', notes)
            document.put('plugin', info)
            spec.commandLine().getOut().println(JsonOutput.toJson(document))
            return 0
        }
        sayNotes()
        print(info, record, managed)
        return 0
    }

    private void print(Map<String, Object> info, PluginRecord record, Map.Entry<String, String> managed) {
        out("Plugin: ${record.name} (${record.coordinates})")
        if (info.get('title') && info.get('title') != record.name) {
            out("Title: ${info.get('title')}")
        }
        if (info.get('description')) {
            out('')
            out(info.get('description') as String)
        }
        out('')
        if (record.deprecated != null) {
            out("* Deprecated${record.deprecated.isEmpty() ? '' : ": ${record.deprecated}"}")
        }
        String version = info.get('version') as String
        out("* Version for Grails ${grailsVersion()}: " + (version == null ? 'none is known' : managed != null ? "${version}, which ${managed.key} manages" :
                "${version}, the latest the plugin index has for this Grails"))
        out("* Latest Version: ${info.get('latestVersion') ?: 'not known'}")
        List<String> versions = (List<String>) info.get('versions')
        if (versions) {
            out("* All Versions (${info.get('versionsFrom')}): ${versions.join(', ')}")
        } else {
            out('* All Versions: not known' + (snapshots ? '' : ', snapshots left out'))
        }
        if (info.get('requiresGrails')) {
            out("* Grails Version: ${info.get('requiresGrails')}")
        }
        out("* License: ${info.get('license') ?: 'not known'}")
        if (info.get('documentation')) {
            out("* Documentation: ${info.get('documentation')}")
        }
        if (info.get('issueTracker')) {
            out("* Issue Tracker: ${info.get('issueTracker')}")
        }
        if (info.get('source')) {
            out("* Source: ${info.get('source')}")
        }
        if (record.features) {
            out("* ${record.features.size() == 1 ? 'Feature' : 'Features'}: ${record.features.join(', ')}. In the directory of an application: " +
                    "${UsageHelp.toType(spec, "${FeatureCommand.NAME} add ${record.features.first()}")}")
        }
        for (Map<String, Object> pack : (List<Map<String, Object>>) info.get('featurePacks')) {
            if (pack.get('offered')) {
                List<String> types = (List<String>) pack.get('applicationTypes')
                out("* Feature pack: ${pack.get('name')}, of version ${pack.get('version')}${types ? ", for applications of type ${types.join(', ')}" : ''}. " +
                        'An application is created with it by:')
                out("    ${UsageHelp.toType(spec, "create-app NAME --features ${pack.get('coordinates')}")}")
                String named = byName((String) pack.get('name'))
                if (named != null) {
                    out("    ${UsageHelp.toType(spec, "create-app NAME ${named}")}")
                }
            } else {
                out("* Feature pack: ${pack.get('name')}, which an application of Grails ${grailsVersion()} is not created with: ${pack.get('reason')}")
            }
        }
        out('* Definition:')
        out('')
        out('dependencies {')
        out("    ${info.get('dependency')}")
        out('}')
    }

    /**
     * The line that adds the plugin to the build of an application: without a version when a BOM of
     * Grails manages it, and with the version otherwise.
     */
    private static String dependency(PluginRecord record, Map.Entry<String, String> managed, String version) {
        return managed != null ? "implementation '${record.coordinates}'".toString() :
                "implementation '${record.coordinates}:${version ?: 'VERSION'}'".toString()
    }

    // the release the repository names, or with snapshots its latest; else the latest release of the versions that are known
    private String latest(PluginRecord record, PluginArtifacts.Versions published, List<String> versions) {
        if (published != null) {
            String named = snapshots ? (published.latest ?: published.release) : published.release
            if (named != null && (snapshots || !named.endsWith(SNAPSHOT))) {
                return named
            }
        }
        String latest = null
        for (String version : versions) {
            if (new Release(version, null).release && (latest == null || PluginRecord.compare(version, latest) > 0)) {
                latest = version
            }
        }
        return latest ?: (published != null ? (versions ? versions.last() : null) : (versions ? versions.first() : null))
    }

    // by the name people know it by, by its artifact, or by its artifact without the grails- most have
    private List<PluginRecord> named(List<PluginRecord> all) {
        String sought = plugin.toLowerCase(Locale.ROOT)
        List<PluginRecord> found = all.findAll { PluginRecord each ->
            String artifact = each.artifact.toLowerCase(Locale.ROOT)
            return artifact == sought || artifact == GRAILS_PREFIX + sought || each.name.toLowerCase(Locale.ROOT) == sought
        }
        // a plugin of this release of Grails is the one that is meant by a name another plugin has too
        List<PluginRecord> ofGrails = found.findAll { PluginRecord each -> each.ofGrails }
        if (ofGrails.size() == 1) {
            return ofGrails
        }
        // and of two plugins of the index, the one whose artifact it is
        List<PluginRecord> byArtifact = found.findAll { PluginRecord each -> each.artifact.toLowerCase(Locale.ROOT) == sought }
        return byArtifact.size() == 1 ? byArtifact : found
    }

    private String unknown(List<PluginRecord> all) {
        String sought = plugin.toLowerCase(Locale.ROOT)
        List<String> close = sought.length() < 3 ? Collections.<String>emptyList() : all.findAll { PluginRecord each ->
            String artifact = each.artifact.toLowerCase(Locale.ROOT)
            String bare = artifact.startsWith(GRAILS_PREFIX) ? artifact.substring(GRAILS_PREFIX.length()) : artifact
            return artifact.contains(sought) || each.name.toLowerCase(Locale.ROOT).contains(sought) || (bare.length() >= 3 && sought.contains(bare))
        }.sort(false) { PluginRecord each -> each.artifact }*.artifact.unique()
        return "No plugin ${FeatureSelectionException.shown(plugin)} is known among the plugins of this release of Grails" +
                (indexRead ? ' and of the plugin index' : '') + '.' +
                (close ? " Did you mean: ${close.take(MAX_NAMED).join(', ')}${close.size() > MAX_NAMED ? " (and ${close.size() - MAX_NAMED} more)" : ''}?" : '') +
                " ${UsageHelp.toType(spec, ListPluginsCommand.NAME)} lists the plugins, and a plugin that is in neither list is looked for in the repositories " +
                'when it is given by its coordinates, as group:artifact'
    }

    private int failed(String message) {
        if (json) {
            // a tool is told in the document what a person is told in the warnings
            Map<String, Object> document = new LinkedHashMap<>()
            document.put('format', FORMAT)
            document.put('pluginIndex', indexSaid())
            document.put('notes', notes)
            document.put('plugin', null)
            document.put('error', message)
            spec.commandLine().getOut().println(JsonOutput.toJson(document))
        } else {
            sayNotes()
        }
        err(message)
        return 1
    }

    /**
     * The repositories that are asked about a plugin: those {@link #repositories()} gives, and after
     * them the one the plugin index names for the plugin.
     */
    private List<URI> repositories(PluginRecord record) {
        Set<URI> all = new LinkedHashSet<>(repositories())
        if (record.repository != null) {
            all << record.repository
        }
        return all.toList()
    }

    /**
     * @return the repositories that are asked about a plugin: those of {@code GRAILS_REPO_URL}, in its
     * order, and then Maven Central, as for the feature pack of a plugin
     */
    protected List<URI> repositories() {
        List<String> warnings = []
        List<URI> repositories = PackArtifacts.repositories(Collections.<String>emptyList(), grailsRepoUrl(), warnings)
        // the warnings are written for a feature pack: here an entry that is not asked is only not asked
        if (warnings) {
            notes << 'Some entries of GRAILS_REPO_URL are not repositories this command asks: a path that is not absolute, an address with a user, or one that is not https'
        }
        return repositories
    }

    /**
     * @return the repositories of the build of a generated application, which are asked about a plugin
     */
    protected String grailsRepoUrl() {
        return System.getenv('GRAILS_REPO_URL')
    }
}
