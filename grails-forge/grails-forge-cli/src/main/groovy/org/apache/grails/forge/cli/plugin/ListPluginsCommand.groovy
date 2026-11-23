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
import picocli.CommandLine

/**
 * {@code list-plugins}: the Grails plugins there are, each with its name, its coordinates, the version
 * an application of this Grails gets and what it is for. See {@link PluginsCommand} for where the
 * plugins are known from.
 *
 * @since 9.0
 */
@CommandLine.Command(name = ListPluginsCommand.NAME, description = [
        'Lists the Grails plugins: those of this release of Grails, and those of the plugin index of Grails, which is read from the network.',
        'Each has its name, its coordinates, the latest version for this Grails and what it is for; @|bold plugin-info|@ says more of one.'])
@CompileStatic
class ListPluginsCommand extends PluginsCommand {

    public static final String NAME = 'list-plugins'

    private static final String NO_VERSION = '-'
    private static final int MAX_FILTER = 100

    @CommandLine.Parameters(arity = '0..1', paramLabel = 'FILTER',
            description = 'Lists only the plugins that have this text in their name, their coordinates or their description, in whichever case.')
    String filter

    @CommandLine.Option(names = ['--deprecated'], description = 'Lists the plugins the plugin index says are deprecated as well.')
    boolean deprecated

    @Override
    protected int run() {
        if (filter != null && (filter.length() > MAX_FILTER || filter != PluginRecord.line(filter, MAX_FILTER))) {
            throw new CommandLine.ParameterException(spec.commandLine(), "The text to list plugins by is one line of at most ${MAX_FILTER} characters")
        }
        List<PluginRecord> all = known()
        String sought = filter?.toLowerCase(Locale.ROOT)
        List<PluginRecord> matching = all.findAll { PluginRecord plugin ->
            sought == null || [plugin.name, plugin.coordinates, plugin.description].any { String text -> text != null && text.toLowerCase(Locale.ROOT).contains(sought) }
        }
        int notListed = deprecated ? 0 : matching.count { PluginRecord plugin -> plugin.deprecated != null } as int
        List<PluginRecord> listed = matching.findAll { PluginRecord plugin -> deprecated || plugin.deprecated == null }
                .sort(false) { PluginRecord one, PluginRecord other ->
                    one.name.toLowerCase(Locale.ROOT) <=> other.name.toLowerCase(Locale.ROOT) ?: one.coordinates <=> other.coordinates
                }
        if (json) {
            Map<String, Object> document = new LinkedHashMap<>()
            document.put('format', FORMAT)
            document.put('grailsVersion', grailsVersion())
            document.put('pluginIndex', indexSaid())
            document.put('notes', notes)
            document.put('deprecatedNotListed', notListed)
            document.put('plugins', listed.collect { PluginRecord plugin -> described(plugin) })
            spec.commandLine().getOut().println(JsonOutput.toJson(document))
            return 0
        }
        sayNotes()
        if (listed.isEmpty()) {
            out(filter == null ? 'No plugin is known' : "No plugin has ${filter} in its name, its coordinates or its description")
        } else {
            List<List<String>> rows = []
            rows << ['Name', 'Coordinates', 'Version', 'Description']
            for (PluginRecord plugin : listed) {
                rows << [plugin.name + (plugin.deprecated == null ? '' : ' [DEPRECATED]'), plugin.coordinates, versionOf(plugin) ?: NO_VERSION, plugin.description ?: '']
            }
            List<Integer> widths = (0..2).collect { int column -> rows.collect { List<String> row -> row[column].length() }.max() + 2 }
            out("Grails plugins for Grails ${grailsVersion()}")
            for (List<String> row : rows) {
                out(('  ' + row[0].padRight(widths[0]) + row[1].padRight(widths[1]) + row[2].padRight(widths[2]) + row[3]).stripTrailing())
            }
        }
        int ofGrails = listed.count { PluginRecord plugin -> plugin.ofGrails } as int
        int withPacks = listed.count { PluginRecord plugin -> offeredPacks(plugin) } as int
        out("${listed.size()} ${listed.size() == 1 ? 'plugin' : 'plugins'}: ${ofGrails} of this release of Grails, with the version the Grails BOM manages" +
                (indexRead ? ", and ${listed.size() - ofGrails} of the plugin index, with the latest version that says it is for this Grails (${NO_VERSION} where none does)" : ''))
        if (withPacks > 0) {
            out("${withPacks} of the plugin index ${withPacks == 1 ? 'has' : 'have'} a feature pack an application of this Grails is created with, " +
                    "which ${PluginInfoCommand.NAME} names")
        }
        if (notListed > 0) {
            out("${notListed} deprecated ${notListed == 1 ? 'plugin is' : 'plugins are'} not listed: --deprecated lists ${notListed == 1 ? 'it' : 'them'}")
        }
        if (!listed.isEmpty()) {
            out("More of one plugin: ${UsageHelp.toType(spec, "${PluginInfoCommand.NAME} NAME")}")
        }
        return 0
    }

    private Map<String, Object> described(PluginRecord plugin) {
        Map<String, Object> described = new LinkedHashMap<>()
        described.put('name', plugin.name)
        described.put('coordinates', plugin.coordinates)
        described.put('version', versionOf(plugin))
        described.put('description', plugin.description)
        described.put('ofGrails', plugin.ofGrails)
        described.put('deprecated', plugin.deprecated != null)
        described.put('featurePacks', offeredPacks(plugin))
        return described
    }

    // the names of the features of the packs of the plugin an application of this Grails is created with
    private List<String> offeredPacks(PluginRecord plugin) {
        return featurePacks(plugin).findAll { Map<String, Object> pack -> pack.get('offered') }.collect { Map<String, Object> pack -> (String) pack.get('name') }
    }
}
