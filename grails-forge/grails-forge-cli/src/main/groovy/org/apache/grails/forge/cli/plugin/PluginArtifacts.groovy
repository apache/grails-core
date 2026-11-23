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
import java.util.regex.Pattern
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.pack.PackArtifactException
import org.apache.grails.forge.cli.pack.PackArtifacts

/**
 * What Maven repositories say of a plugin, for {@code plugin-info}: the versions its
 * {@code maven-metadata.xml} lists, and what the plugin says of itself in the
 * {@code <artifact>-<version>-plugin.xml} that is published beside its jar. A repository that is a
 * directory can be a local repository of Maven, into which {@code publishToMavenLocal} of Gradle and
 * {@code mvn install} write the versions as {@code maven-metadata-local.xml}: of a directory that file
 * is read when there is no {@code maven-metadata.xml}, and a repository that is asked over the network
 * is asked for {@code maven-metadata.xml} only. The files are read with {@link PackArtifacts}, as a
 * feature catalog is, by its rules for an address, a redirect, a time and a size; no jar is asked for
 * and nothing of what is read is run.
 *
 * <p>The repositories are asked in their order. One that does not have a file is passed over; one
 * that fails in another way ends the search for that file, with a note, so that the coordinates of a
 * plugin are sent to no repository after one that refused them. What a file says is text of someone
 * else: it is read element after element, within bounds, with no document type and nothing from
 * outside the document, and every text is kept as {@link PluginRecord#line} makes it.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class PluginArtifacts {

    /** The most bytes the description of a plugin by itself can have */
    public static final int MAX_DESCRIPTOR_BYTES = 256 * 1024

    /** The most versions that are kept of a {@code maven-metadata.xml} */
    public static final int MAX_VERSIONS = 2000

    private static final String METADATA = 'maven-metadata.xml'
    // the name of the metadata of what was installed into a local repository of Maven, and not deployed
    private static final String INSTALLED_METADATA = 'maven-metadata-local.xml'
    private static final String FILE = 'file'
    private static final Pattern VERSION = Pattern.compile(/[A-Za-z0-9][A-Za-z0-9_.\-]{0,59}/)
    private static final int MAX_DEPTH = 8
    private static final int MAX_ELEMENTS = 20000
    private static final int MAX_TEXT = 8192

    private final PackArtifacts artifacts
    private List<URI> repositories
    private final Duration within

    /** What kept a file from being read, one line each */
    final List<String> notes = []

    /**
     * @param artifacts what reads a file of an address, with its rules
     * @param repositories the repositories to ask, in this order, each the address of a directory
     * @param within how long one file can take
     */
    PluginArtifacts(PackArtifacts artifacts, List<URI> repositories, Duration within) {
        this.artifacts = artifacts
        this.repositories = repositories
        this.within = within
    }

    /**
     * @param coordinates a plugin, as {@code group:artifact}
     * @return what the first repository that has a {@code maven-metadata.xml} for it says, or of a
     * directory a {@code maven-metadata-local.xml}; {@code null} when none has, or none was asked
     */
    Versions versions(String coordinates) {
        Found found = find(coordinates, METADATA, PackArtifacts.MAX_METADATA_BYTES, INSTALLED_METADATA)
        if (found == null) {
            return null
        }
        Map<String, List<String>> said
        try {
            said = read(found.bytes, 'metadata')
        } catch (PackArtifactException e) {
            notes << "${found.name} of ${coordinates} from ${found.where} ${e.message}".toString()
            return null
        }
        List<String> group = said.get('metadata/groupId')
        List<String> artifact = said.get('metadata/artifactId')
        if ((group && group.first() != coordinates.substring(0, coordinates.indexOf(':'))) ||
                (artifact && artifact.first() != coordinates.substring(coordinates.indexOf(':') + 1))) {
            notes << "${found.name} of ${coordinates} from ${found.where} is that of another artifact, and is not used".toString()
            return null
        }
        List<String> versions = (said.get('metadata/versioning/versions/version') ?: Collections.<String>emptyList())
                .findAll { String version -> VERSION.matcher(version).matches() }.unique()
        return new Versions(found.where, version(said.get('metadata/versioning/release')), version(said.get('metadata/versioning/latest')), versions)
    }

    /**
     * @param coordinates a plugin, as {@code group:artifact}
     * @param version a version of it that is no snapshot
     * @return what the plugin says of itself in that version, as the first repository that has it gives
     * it: {@code title}, {@code description}, {@code license}, {@code documentation}, {@code issues},
     * {@code source}, {@code author} and {@code grailsVersion}, each only when it is said; {@code null}
     * when no repository has the file
     */
    Map<String, String> description(String coordinates, String version) {
        String artifact = coordinates.substring(coordinates.indexOf(':') + 1)
        String file = "${artifact}-${version}-plugin.xml"
        Found found = find(coordinates, "${version}/${file}", MAX_DESCRIPTOR_BYTES, null)
        if (found == null) {
            return null
        }
        Map<String, List<String>> said
        try {
            said = read(found.bytes, 'plugin')
        } catch (PackArtifactException e) {
            notes << "${file} from ${found.where} ${e.message}".toString()
            return null
        }
        Map<String, String> description = new LinkedHashMap<>()
        Closure<Void> put = { String key, String value ->
            if (value != null) {
                description.put(key, value)
            }
            return null
        }
        put('title', PluginRecord.line(first(said, 'plugin/title'), PluginRecord.MAX_NAME))
        put('description', PluginRecord.line(first(said, 'plugin/description'), PluginRecord.MAX_DESCRIPTION * 4))
        put('license', PluginRecord.line(first(said, 'plugin/license'), PluginRecord.MAX_NAME))
        put('author', PluginRecord.line(first(said, 'plugin/author'), PluginRecord.MAX_NAME))
        put('documentation', PluginRecord.address(first(said, 'plugin/documentation')))
        put('issues', PluginRecord.address(first(said, 'plugin/issueManagement@url')))
        put('source', PluginRecord.address(first(said, 'plugin/scm@url')))
        put('grailsVersion', PluginRecord.line(first(said, 'plugin/grailsVersion') ?: first(said, 'plugin@grailsVersion'), PluginRecord.MAX_NAME))
        description.put('from', found.where)
        return description
    }

    private static String first(Map<String, List<String>> said, String path) {
        List<String> values = said.get(path)
        return values ? values.first() : null
    }

    private static String version(List<String> said) {
        return said && VERSION.matcher(said.first()).matches() ? said.first() : null
    }

    /**
     * @param installed what a directory has in the place of the file when it is a local repository of
     * Maven, which is read when the directory does not have the file; {@code null} for nothing
     */
    private Found find(String coordinates, String file, int limit, String installed) {
        String directory = "${coordinates.substring(0, coordinates.indexOf(':')).replace('.', '/')}/${coordinates.substring(coordinates.indexOf(':') + 1)}/"
        String name = file.substring(file.lastIndexOf('/') + 1)
        for (URI repository : repositories) {
            String where = PackArtifacts.shownAddress(repository)
            String asked = name
            try {
                byte[] bytes = artifacts.fileIfThere(repository.resolve(directory + file), limit, name, within)
                if (bytes != null) {
                    return new Found(bytes, where, name)
                }
                // only of a directory: a repository of the network is asked for what it publishes, and for nothing else
                if (installed != null && FILE.equalsIgnoreCase(repository.scheme)) {
                    asked = installed
                    bytes = artifacts.fileIfThere(repository.resolve(directory + installed), limit, installed, within)
                    if (bytes != null) {
                        return new Found(bytes, where, installed)
                    }
                }
            } catch (PackArtifactException e) {
                notes << "${asked} of ${coordinates} was not read from ${where} (${e.message}), and no other repository was asked for it".toString()
                // nor is this one, or one after it, asked for anything else of the plugin
                repositories = repositories.takeWhile { URI each -> each != repository }
                return null
            }
        }
        return null
    }

    /**
     * Reads an XML file as far as its elements have text or attributes: the texts of each element by
     * the path of its names, and the values of each attribute by that path, an at sign and its name.
     */
    private static Map<String, List<String>> read(byte[] bytes, String root) {
        Map<String, List<String>> said = new HashMap<>()
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory()
            // neither a document type nor anything from outside the document is read
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false)
            XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(bytes))
            try {
                List<String> path = []
                List<StringBuilder> texts = []
                int elements = 0
                while (reader.hasNext()) {
                    int event = reader.next()
                    if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) {
                        throw new PackArtifactException('cannot be read')
                    }
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        if (path.size() == MAX_DEPTH || ++elements > MAX_ELEMENTS || (path.isEmpty() && reader.localName != root)) {
                            throw new PackArtifactException('is not what a Maven repository has under that name')
                        }
                        path << reader.localName
                        texts << new StringBuilder()
                        String at = path.join('/')
                        for (int index = 0; index < reader.attributeCount; index++) {
                            keep(said, "${at}@${reader.getAttributeLocalName(index)}".toString(), reader.getAttributeValue(index))
                        }
                    } else if (event == XMLStreamConstants.END_ELEMENT) {
                        keep(said, path.join('/'), texts.removeLast().toString())
                        path.removeLast()
                    } else if (!texts.isEmpty() && (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)) {
                        StringBuilder text = texts.last()
                        if (text.length() < MAX_TEXT) {
                            String more = reader.text
                            text.append(more, 0, Math.min(more.length(), MAX_TEXT - text.length()))
                        }
                    }
                }
            } finally {
                reader.close()
            }
        } catch (XMLStreamException ignored) {
            throw new PackArtifactException('cannot be read')
        } catch (RuntimeException e) {
            if (e instanceof PackArtifactException) {
                throw e
            }
            throw new PackArtifactException("cannot be read (${e.class.simpleName})")
        }
        return said
    }

    private static void keep(Map<String, List<String>> said, String path, String value) {
        String text = value == null ? '' : value.trim()
        if (text.isEmpty()) {
            return
        }
        List<String> values = said.computeIfAbsent(path) { String key -> new ArrayList<String>() }
        if (values.size() < MAX_VERSIONS) {
            values << (text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text)
        }
    }

    /**
     * What a {@code maven-metadata.xml} says of the versions of an artifact.
     */
    static final class Versions {

        /** The repository that said so, as a message has it */
        final String from
        /** The latest release, or {@code null} */
        final String release
        /** The latest version of any kind, or {@code null} */
        final String latest
        /** The versions, in the order of the file */
        final List<String> versions

        Versions(String from, String release, String latest, List<String> versions) {
            this.from = from
            this.release = release
            this.latest = latest
            this.versions = Collections.unmodifiableList(versions)
        }
    }

    private static final class Found {

        final byte[] bytes
        final String where
        /** The name of the file that was read */
        final String name

        Found(byte[] bytes, String where, String name) {
            this.bytes = bytes
            this.where = where
            this.name = name
        }
    }
}
