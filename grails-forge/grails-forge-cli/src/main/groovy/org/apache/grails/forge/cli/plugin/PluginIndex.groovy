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

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.BoundedJson
import org.apache.grails.forge.cli.pack.PackArtifactException
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.cli.pack.PackCatalog
import org.apache.grails.forge.cli.plugin.PluginRecord.Release

/**
 * The plugin index of Grails, read as a list of plugins: {@code grails-plugins-index.json} as
 * {@code apache/grails-plugins-metadata} publishes it, which is what the plugin portal of the Grails
 * website shows. It is a JSON array of plugins, of which this reads:
 * <pre>
 * [ { "coords": "org.grails.plugins:grails-mail", "name": "Mail", "desc": "...", "docs": "https://...",
 *     "vcs": "https://github.com/...", "licenses": ["Apache-2.0"], "deprecated": "why it is",
 *     "maven-repo": "https://repo1.maven.org/maven2",
 *     "versions": [ { "version": "5.0.3", "grailsVersion": "7.0.0 > *" } ] } ]
 * </pre>
 *
 * <p>The index is read from elsewhere and is not trusted: it is data that is shown, and nothing of it
 * is run or resolved. A document that is not such an array, or is beyond the bounds given here, is
 * refused as a whole; an entry without the coordinates of a plugin is left out and counted. Each text
 * is kept as {@link PluginRecord#line} makes it, and each address as {@link PluginRecord#address} does.
 * A version that the index says has no artifact is not kept, and neither is one that was published
 * under other coordinates than the plugin has now.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class PluginIndex {

    /** The most bytes an index can have: several times what the index has (0.64 MiB in November 2026) */
    public static final int MAX_BYTES = PackCatalog.MAX_BYTES

    /** How deep the objects and arrays of an index can be */
    public static final int MAX_DEPTH = 8

    /** The most values an index can have (it has about 26,000) */
    public static final int MAX_VALUES = 200000

    /** The most characters of one text of an index (the longest it has is 678) */
    public static final int MAX_TEXT = 16384

    /** The most plugins an index can have */
    public static final int MAX_PLUGINS = 5000

    /** The most versions that are kept of one plugin */
    public static final int MAX_VERSIONS = 1000

    private static final Pattern PART = Pattern.compile(/[A-Za-z0-9][A-Za-z0-9_.\-]*/)
    private static final int MAX_COORDINATES = 120
    private static final int MAX_VERSION = 60
    private static final int MAX_LICENSES = 5

    /** The plugins, in the order of the index */
    final List<PluginRecord> plugins

    /** How many entries of the index are left out, as no plugin with coordinates */
    final int leftOut

    private PluginIndex(List<PluginRecord> plugins, int leftOut) {
        this.plugins = Collections.unmodifiableList(plugins)
        this.leftOut = leftOut
    }

    /**
     * @param bytes the document
     * @return the index
     * @throws PackArtifactException when the document as a whole is no plugin index, with what is wrong
     * with it, which repeats nothing of the document, as its message
     */
    static PluginIndex parse(byte[] bytes) {
        if (bytes.length > MAX_BYTES) {
            throw new PackArtifactException("it has more than the ${MAX_BYTES} bytes a plugin index can have")
        }
        Object document
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            document = BoundedJson.parse(text.startsWith('﻿') ? text.substring(1) : text, MAX_DEPTH, MAX_VALUES, MAX_TEXT)
        } catch (CharacterCodingException ignored) {
            throw new PackArtifactException('it is not written in UTF-8')
        } catch (BoundedJson.Refused e) {
            throw new PackArtifactException("it ${e.message}")
        } catch (OutOfMemoryError ignored) {
            throw new PackArtifactException('it is too large for the memory this command has, and was not read')
        } catch (RuntimeException | StackOverflowError e) {
            throw new PackArtifactException("it could not be read (${e.class.simpleName})")
        }
        if (!(document instanceof List)) {
            throw new PackArtifactException('it is not an array of plugins, as the plugin index is')
        }
        List<Object> entries = (List<Object>) document
        if (entries.size() > MAX_PLUGINS) {
            throw new PackArtifactException("it has more than the ${MAX_PLUGINS} plugins a plugin index can have")
        }
        List<PluginRecord> plugins = []
        Set<String> seen = new HashSet<>()
        int leftOut = 0
        for (Object entry : entries) {
            PluginRecord plugin = entry instanceof Map ? plugin((Map<String, Object>) entry) : null
            if (plugin == null || !seen.add(plugin.coordinates)) {
                leftOut++
            } else {
                plugins << plugin
            }
        }
        return new PluginIndex(plugins, leftOut)
    }

    /**
     * @param coordinates what could be {@code group:artifact}
     * @return whether it is, in the characters such coordinates have
     */
    static boolean isCoordinates(String coordinates) {
        if (coordinates == null || coordinates.length() > MAX_COORDINATES) {
            return false
        }
        List<String> parts = coordinates.split(':', -1).toList()
        return parts.size() == 2 && parts.every { String part -> PART.matcher(part).matches() } && !parts[0].contains('..') && !parts[0].endsWith('.')
    }

    private static PluginRecord plugin(Map<String, Object> entry) {
        String coordinates = text(entry.get('coords'))
        if (!isCoordinates(coordinates)) {
            return null
        }
        PluginRecord plugin = new PluginRecord(coordinates: coordinates)
        plugin.name = PluginRecord.line(text(entry.get('name')), PluginRecord.MAX_NAME) ?: plugin.artifact
        plugin.description = PluginRecord.line(text(entry.get('desc')), PluginRecord.MAX_DESCRIPTION)
        plugin.documentation = PluginRecord.address(text(entry.get('docs')))
        plugin.source = PluginRecord.address(text(entry.get('vcs')))
        Object licenses = entry.get('licenses')
        if (licenses instanceof List) {
            plugin.license = PluginRecord.line(((List<Object>) licenses).findAll { Object each -> each instanceof String }.take(MAX_LICENSES).join(', '),
                    PluginRecord.MAX_NAME)
        }
        Object deprecated = entry.get('deprecated')
        if (deprecated == true) {
            plugin.deprecated = ''
        } else if (deprecated instanceof String && !((String) deprecated).trim().isEmpty() && !'false'.equalsIgnoreCase(((String) deprecated).trim())) {
            plugin.deprecated = PluginRecord.line((String) deprecated, PluginRecord.MAX_DESCRIPTION) ?: ''
        }
        try {
            String repository = text(entry.get('maven-repo'))
            plugin.repository = repository == null ? null : PackArtifacts.repositoryOfCatalog(repository)
        } catch (IllegalArgumentException ignored) {
            // an address no request is made to: the plugin is listed, and that repository is not asked
            plugin.repository = null
        }
        Object versions = entry.get('versions')
        if (versions instanceof List) {
            Set<String> seen = new HashSet<>()
            for (Object each : (List<Object>) versions) {
                if (plugin.versions.size() == MAX_VERSIONS) {
                    break
                }
                if (!(each instanceof Map) || ((Map) each).get('artifactAvailable') == false) {
                    continue
                }
                // a version that was published under other coordinates is no version of these
                Object own = ((Map) each).get('coords')
                if (own != null && own != coordinates) {
                    continue
                }
                String version = text(((Map) each).get('version'))
                if (version != null && version.length() <= MAX_VERSION && PART.matcher(version).matches() && seen.add(version)) {
                    plugin.versions << new Release(version, PluginRecord.line(text(((Map) each).get('grailsVersion')), MAX_VERSION))
                }
            }
        }
        return plugin
    }

    private static String text(Object value) {
        return value instanceof String ? (String) value : null
    }
}
