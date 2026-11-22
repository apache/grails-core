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
package org.apache.grails.forge.core.pack

import grails.util.Environment
import groovy.transform.CompileStatic
import java.nio.charset.StandardCharsets
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import org.apache.grails.forge.core.pack.PackDescriptor.PackFile

/**
 * Compiles the templates of the packs Forge has in itself, for the build of Forge: the classes of
 * their pages and the index {@link PrecompiledPackPages#INDEX}, which the jar of Forge then has and
 * {@link PrecompiledPackPages} reads.
 *
 * <p>Every template of every pack {@link Pack#BUNDLED_INDEX} names is compiled, with
 * {@link PackPages#precompile}: one that does not compile fails the build. What is written is the same
 * for the same templates, whenever and wherever it is written: the name of a class is made of the key
 * of its template, the index is in the order of the keys, and nothing has a time in it.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class PackPagesCompiler {

    private PackPagesCompiler() {
    }

    /**
     * Compiles the templates of the packs a class loader has.
     *
     * @param resources the class loader that has the index of the packs and the packs it names, and
     * that the pages are compiled against
     * @return the pages, by the key of the template of each, in the order of the keys
     * @throws RuntimeException when a pack cannot be read or a template cannot be compiled
     */
    static SortedMap<String, PackPages.Precompiled> compile(ClassLoader resources) {
        SortedMap<String, PackPages.Precompiled> pages = new TreeMap<>()
        PackLoader packs = new PackLoader()
        for (String name : BundledPackFeatures.names(resources)) {
            Pack pack = packs.fromClasspath(resources, name)
            for (PackFile file : pack.descriptor.files) {
                if (!file.isTemplate()) {
                    continue
                }
                // the owner and the text a generation renders the file with
                String owner = pack.descriptor.name
                String text = new String(pack.content(file.from), StandardCharsets.UTF_8)
                PackPages.Precompiled page
                try {
                    page = PackPages.precompile(resources, owner, text)
                } catch (RuntimeException e) {
                    throw new IllegalStateException("The template ${file.from} of the feature ${owner} cannot be compiled: ${e.message}", e)
                }
                // a template that is written to several places is one page
                pages.put(page.key, page)
            }
        }
        return pages
    }

    /**
     * Writes the classes of the pages into a directory, and the index under it.
     *
     * @param pages the pages, by the key of the template of each
     * @param directory the directory, which is made when there is none; what an earlier call wrote into
     * it and this one does not is deleted
     */
    static void write(SortedMap<String, PackPages.Precompiled> pages, Path directory) throws IOException {
        // the classes are all of the one package of the pages
        String prefix = "${PrecompiledPackPages.PACKAGE}."
        Path ofPackage = Files.createDirectories(directory.resolve(PrecompiledPackPages.PACKAGE.replace('.', '/')))
        Set<String> written = new HashSet<>()
        Map<String, String> sources = new TreeMap<>()
        pages.each { String key, PackPages.Precompiled page ->
            sources.put(key, page.source)
            page.classes.each { String name, byte[] bytes ->
                if (!name.startsWith(prefix) || name.substring(prefix.length()).contains('.')) {
                    throw new IllegalStateException("The class ${name} of a page is not of the package ${PrecompiledPackPages.PACKAGE}")
                }
                String file = "${name.substring(prefix.length())}.class"
                Files.write(ofPackage.resolve(file), bytes)
                written << file
            }
        }
        // in the package, and in the directory itself, where an earlier form of this wrote its classes
        for (Path place : [ofPackage, directory]) {
            try (DirectoryStream<Path> earlier = Files.newDirectoryStream(place, "${PrecompiledPackPages.CLASS_PREFIX}*.class")) {
                for (Path each : earlier) {
                    if (place != ofPackage || !written.contains(each.fileName.toString())) {
                        Files.delete(each)
                    }
                }
            }
        }
        Path index = directory.resolve(PrecompiledPackPages.INDEX)
        Files.createDirectories(index.parent)
        Files.write(index, PrecompiledPackPages.index(sources).getBytes(StandardCharsets.UTF_8))
    }

    /**
     * Compiles the templates of the packs on the class path into the directory that is the one argument.
     */
    static void main(String[] args) {
        if (args == null || args.length != 1 || !args[0]) {
            System.err.println('Give the directory to write the pages of the templates into, and nothing else')
            System.exit(2)
        }
        // as the command line and the web application that generate applications run: a template is
        // compiled a little differently for each environment, and a page of the build is taken only by
        // a run that would have compiled it the same. Whatever the build that starts this has set: the
        // Grails Gradle plugin gives every JVM it starts the environment of development.
        System.setProperty(Environment.KEY, Environment.PRODUCTION.getName())
        SortedMap<String, PackPages.Precompiled> pages = compile(PackPagesCompiler.classLoader)
        write(pages, Path.of(args[0]))
        println("Compiled the pages of ${pages.size()} templates of the packs of Forge")
    }
}
