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

import java.nio.charset.StandardCharsets
import groovy.transform.CompileStatic

/**
 * The Grails plugins of the release of Grails this command line belongs to: the plugins the build of
 * the framework publishes, as the resource {@link #RESOURCE} lists them. They are known without the
 * network, and their versions are those the Grails BOM of this release manages, which the list
 * therefore does not have.
 *
 * @since 9.0
 */
@CompileStatic
final class BundledPlugins {

    /** Where the list is among the resources */
    public static final String RESOURCE = 'META-INF/grails-forge-plugins/plugins.tsv'

    /** Where the sources of the framework are */
    public static final String SOURCE = 'https://github.com/apache/grails-core'

    /** Where the issues of the framework are */
    public static final String ISSUES = 'https://github.com/apache/grails-core/issues'

    /** The license of the framework */
    public static final String LICENSE = 'Apache-2.0'

    private static final int COLUMNS = 6

    private BundledPlugins() {
    }

    /**
     * @return the plugins, in the order of the list; each is a new record
     * @throws IllegalStateException when the command line does not have the list, or a line of it is
     * not one: it is part of the command line
     */
    static List<PluginRecord> all() {
        return lines().collect { List<String> columns ->
            new PluginRecord(coordinates: columns[0], name: columns[1], description: columns[2], documentation: columns[3] ?: null,
                    features: columns[4] ? columns[4].split(',').toList() : Collections.<String>emptyList(),
                    source: SOURCE, issues: ISSUES, license: LICENSE, ofGrails: true)
        }
    }

    /**
     * @return the plugin class of each plugin in the sources of the framework, by the coordinates of
     * the plugin, for what holds the list to those sources
     */
    static Map<String, String> descriptors() {
        Map<String, String> descriptors = new LinkedHashMap<>()
        for (List<String> columns : lines()) {
            descriptors.put(columns[0], columns[5])
        }
        return descriptors
    }

    private static List<List<String>> lines() {
        InputStream resource = BundledPlugins.classLoader.getResourceAsStream(RESOURCE)
        if (resource == null) {
            throw new IllegalStateException("The command line is not whole: it has no ${RESOURCE}")
        }
        List<List<String>> lines = []
        resource.withCloseable { InputStream input ->
            for (String line : new String(input.readAllBytes(), StandardCharsets.UTF_8).readLines()) {
                if (line.trim().isEmpty() || line.startsWith('#')) {
                    continue
                }
                List<String> columns = line.split('\t', -1).toList()
                if (columns.size() != COLUMNS || !PluginIndex.isCoordinates(columns[0])) {
                    throw new IllegalStateException("The command line is not whole: ${RESOURCE} has a line that is not one of a plugin")
                }
                lines << columns
            }
        }
        return lines
    }
}
