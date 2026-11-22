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

import groovy.transform.CompileStatic
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import org.grails.gsp.GroovyPage

/**
 * The pages of the templates of the packs Forge has in itself, as the build of Forge compiled them:
 * a template of those packs does not have to be compiled each time Forge runs.
 *
 * <p>The build compiles each template with {@link PackPages}, as a run would, and writes beside the
 * classes an index, the resource {@link #INDEX}: a line for each template, with the key
 * {@code PackPages} keeps a page by, which is a SHA-256 of the name of the feature, the directives and
 * the text of the template, the SHA-256 of what the page was compiled from, and the name of its
 * class, which is {@link #CLASS_PREFIX} and the key, in the package {@link #PACKAGE}.</p>
 *
 * <p>The text of a template is not all its class comes of. The parser of the pages writes an
 * expression one way for the environments of development and test and another way for every other,
 * and reads directives, system properties and its own version; the compiler has a version and
 * settings. So a class is given for a key and for what it was compiled from: the template is still
 * parsed in each run, and its class is the one of the build only when the Groovy source it would be
 * compiled from now is, byte for byte, the source it was compiled from then, and the compiler is the
 * same and set the same. The build compiles for the production environment, which the command line
 * and the web application that generate applications run in. A template with another text, of another
 * feature, or rendered in another environment, by another version of Grails or of Groovy, is compiled
 * as it would be without this. So is every template when the index is missing or cannot be read, and
 * one whose class cannot be loaded.</p>
 *
 * <p>The index and the classes are taken from one class loader, the one Forge itself is loaded by, and
 * a class counts only when that loader defined it. A feature pack is data: nothing of it is on a
 * class path, and nothing it holds is looked at here, so a pack can neither bring a class of its own
 * nor be given the class of another template.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class PrecompiledPackPages {

    /** The resource that names the class of each template the build compiled. */
    public static final String INDEX = 'META-INF/grails-forge-pages/index'

    /**
     * The package of the classes of the pages, which no other module has and which holds nothing else:
     * a jar that is a module of Java can have no class without a package.
     */
    public static final String PACKAGE = 'org.apache.grails.forge.core.pack.pages'

    /** What the simple name of the class of a template starts with, in front of its key. */
    public static final String CLASS_PREFIX = 'PackPage_'

    private static final Pattern DIGEST = Pattern.compile('[0-9a-f]{64}')
    private static final int FIELDS = 3
    // far more than the packs of Forge have templates: an index that is longer is not one of the build
    private static final int MAX_INDEX_BYTES = 4 * 1024 * 1024

    private final ClassLoader loader
    private final Map<String, String> sources

    private PrecompiledPackPages(ClassLoader loader, Map<String, String> sources) {
        this.loader = loader
        this.sources = sources
    }

    /**
     * @return the pages the build of Forge compiled, read once from where Forge itself is loaded from
     */
    static PrecompiledPackPages ofForge() {
        return OfForge.PAGES
    }

    /**
     * @param loader the class loader that has the index and the classes
     * @return the pages the index of that class loader names, which are none when it has no index or
     * one that is not what the build writes
     */
    static PrecompiledPackPages of(ClassLoader loader) {
        Map<String, String> sources
        try {
            sources = read(loader)
        } catch (IOException | RuntimeException ignored) {
            sources = Collections.<String, String>emptyMap()
        }
        return new PrecompiledPackPages(loader, sources)
    }

    /**
     * @param key the key of a template
     * @return the name of the page the template is compiled as for the build, which gives its class the
     * name {@link #className}
     */
    static String pageName(String key) {
        return CLASS_PREFIX + key
    }

    /**
     * @param key the key of a template
     * @return the name of the class the build compiles the template to, in {@link #PACKAGE}
     */
    static String className(String key) {
        return "${PACKAGE}.${CLASS_PREFIX}${key}".toString()
    }

    /**
     * @param sources for the key of each template, the SHA-256 of the source its page was compiled from
     * @return the index, a line for each template in the order of the keys
     */
    static String index(Map<String, String> sources) {
        StringBuilder index = new StringBuilder()
        new TreeMap<String, String>(sources).each { String key, String source ->
            index << key << '\t' << source << '\t' << className(key) << '\n'
        }
        return index.toString()
    }

    /**
     * @return the number of templates the index names
     */
    int size() {
        return sources.size()
    }

    /**
     * @param key the key of a template
     * @return whether the build compiled a template with that key
     */
    boolean has(String key) {
        return sources.containsKey(key)
    }

    /**
     * @param key the key of a template
     * @param source the SHA-256 of the source its page would be compiled from now
     * @return the class the build compiled that source to, or {@code null} when it compiled none for the
     * key, compiled another source, or its class is not to be had from the class loader of Forge
     */
    Class<?> load(String key, String source) {
        if (source == null || source != sources.get(key)) {
            return null
        }
        try {
            Class<?> page = Class.forName(className(key), false, loader)
            // of the class loader itself: not of one above it, and not of one that says it is it
            return page.getClassLoader().is(loader) && GroovyPage.isAssignableFrom(page) ? page : null
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null
        }
    }

    // nothing of an index with a line that is not one of the build is used: a part of it would say that
    // templates are compiled which are not
    private static Map<String, String> read(ClassLoader loader) throws IOException {
        InputStream resource = loader.getResourceAsStream(INDEX)
        if (resource == null) {
            return Collections.<String, String>emptyMap()
        }
        byte[] bytes
        try (InputStream input = resource) {
            bytes = input.readNBytes(MAX_INDEX_BYTES + 1)
        }
        if (bytes.length > MAX_INDEX_BYTES) {
            return Collections.<String, String>emptyMap()
        }
        Map<String, String> sources = new HashMap<>()
        for (String line : new String(bytes, StandardCharsets.UTF_8).split('\n', -1)) {
            if (line.isEmpty()) {
                continue
            }
            String[] fields = line.split('\t', -1)
            if (fields.length != FIELDS || !DIGEST.matcher(fields[0]).matches() || !DIGEST.matcher(fields[1]).matches() ||
                    fields[2] != className(fields[0]) || sources.put(fields[0], fields[1]) != null) {
                return Collections.<String, String>emptyMap()
            }
        }
        return Collections.unmodifiableMap(sources)
    }

    // read when the first template of a pack of Forge is rendered: many uses of Forge render none
    private static final class OfForge {

        static final PrecompiledPackPages PAGES = of(PrecompiledPackPages.classLoader)
    }
}
