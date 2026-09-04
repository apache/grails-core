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
package org.grails.gradle.plugin.views.gsp

import java.nio.charset.StandardCharsets

import groovy.transform.CompileStatic

/**
 * The files the tag library index is made of, as the build writes them.
 *
 * <p>Written here rather than by the forked generator because they say what the build asked for
 * rather than what the sources declare, and because they have to be written even for a project with
 * no tag libraries of its own.
 *
 * @since 8.0.0
 */
@CompileStatic
final class TagLibraryIndexFiles {

    /**
     * Directory holding one descriptor per compiled tag library.
     *
     * <p>These restate the format {@code org.grails.taglib.index.TagLibraryIndex} owns. They cannot
     * be shared with it: the generator is forked against the project's compile classpath precisely
     * because this plugin does not have the framework on its own, so the constants there are not
     * reachable from here. {@code TagLibraryIndexFilesSpec} asserts the two agree, so a rename on
     * either side fails a test rather than quietly producing an index nothing reads.
     *
     * <p>Held without a trailing separator; {@code TagLibraryIndex.INDEX_LOCATION} carries one
     * because it resolves classpath resources by concatenation, where this resolves files.
     */
    static final String INDEX_LOCATION = 'META-INF/grails/taglibs'

    /**
     * Where the settings for this compilation are written, the file part of
     * {@code TagLibraryIndex.SETTINGS_LOCATION}.
     */
    static final String SETTINGS_FILE = 'compile-settings.properties'

    /**
     * The keys the settings file is written with, matching {@code TagLibraryIndex}.
     */
    static final String STRICT_KEY = 'strictTags'

    static final String DYNAMIC_NAMESPACES_KEY = 'dynamicTagNamespaces'

    static final String UNQUALIFIED_KEY = 'unqualifiedTagCalls'

    static final String LOCAL_NAMESPACES_KEY = 'localNamespaces'

    private TagLibraryIndexFiles() {
    }

    /**
     * Reads the namespaces of the descriptors beneath a directory.
     *
     * <p>Taken from what was generated rather than from the sources, so that a tag library the
     * generator could not read does not have its namespace counted as one this project describes.
     *
     * @param destination the directory the index was written beneath
     * @return the namespaces described there
     */
    static Set<String> readNamespaces(File destination) {
        Set<String> namespaces = new TreeSet<>()
        new File(destination, INDEX_LOCATION).listFiles()?.each { File file ->
            if (!file.isFile() || !file.name.endsWith('.properties') || file.name == SETTINGS_FILE) {
                return
            }
            Properties descriptor = new Properties()
            file.withInputStream { descriptor.load(it) }
            String namespace = descriptor.getProperty('namespace')
            if (namespace) {
                namespaces.add(namespace)
            }
        }
        namespaces
    }

    /**
     * Removes descriptors left by an earlier run, for a project that no longer declares any tag
     * library. Without it the index would keep describing tags that no longer exist.
     *
     * @param destination the directory the index is written beneath
     */
    static void clearIndex(File destination) {
        File indexDirectory = new File(destination, INDEX_LOCATION)
        indexDirectory.listFiles()?.each { File file ->
            if (file.isFile() && file.name.endsWith('.properties') && file.name != SETTINGS_FILE) {
                file.delete()
            }
        }
    }

    /**
     * Records what the build asked for, so that the compiler reads it as an ordinary classpath
     * resource and Gradle sees it as an output of a task with declared inputs.
     *
     * @param destination the directory the index is written beneath
     * @param strictTags whether an unknown tag fails compilation
     * @param dynamicNamespaces namespaces filled in while the application runs
     * @param unqualifiedTagCalls whether a call written without a namespace may be compiled
     * @param localNamespaces the namespaces this project's own tag libraries declare
     */
    static void writeSettings(File destination, boolean strictTags, Set<String> dynamicNamespaces,
            boolean unqualifiedTagCalls = false, Set<String> localNamespaces = [] as Set) {
        File indexDirectory = new File(destination, INDEX_LOCATION)
        indexDirectory.mkdirs()
        // Written by hand rather than through Properties.store, which stamps the current time into a
        // comment and would make the output differ between otherwise identical builds.
        String text = "${DYNAMIC_NAMESPACES_KEY}=${new TreeSet<String>(dynamicNamespaces).join(',')}\n" +
                "${STRICT_KEY}=${strictTags}\n" +
                "${UNQUALIFIED_KEY}=${unqualifiedTagCalls}\n" +
                "${LOCAL_NAMESPACES_KEY}=${new TreeSet<String>(localNamespaces).join(',')}\n"
        new File(indexDirectory, SETTINGS_FILE).setText(text, StandardCharsets.UTF_8.name())
    }
}
