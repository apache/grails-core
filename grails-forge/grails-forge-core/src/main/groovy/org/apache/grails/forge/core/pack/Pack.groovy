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
import org.apache.grails.forge.core.pack.PackDescriptor.PackBuildPlugin
import org.apache.grails.forge.core.pack.PackDescriptor.PackDependency
import org.apache.grails.forge.core.pack.PackDescriptor.PackFile

/**
 * A feature pack that was read: its descriptor, and the content of the files it adds to an application,
 * as it was when the pack was read.
 *
 * @since 9.0
 * @see PackLoader
 */
@CompileStatic
final class Pack {

    /** The name of the descriptor in a pack */
    public static final String DESCRIPTOR = 'feature.yml'

    /** The directory of a pack that holds the files its descriptor names */
    public static final String TEMPLATES = 'templates'

    /** The end of the name of a file of a pack that is a template and not a file written as it is */
    public static final String TEMPLATE_SUFFIX = '.gsp'

    private final PackDescriptor descriptor
    private final String origin
    private final Map<String, byte[]> files

    /**
     * @param descriptor the descriptor of the pack
     * @param origin where the pack came from, for messages
     * @param files the content of the file of each {@code from} of the descriptor
     */
    Pack(PackDescriptor descriptor, String origin, Map<String, byte[]> files) {
        this.descriptor = descriptor
        this.origin = origin
        Map<String, byte[]> copies = new LinkedHashMap<>()
        files.each { String from, byte[] content -> copies.put(from, content.clone()) }
        this.files = Collections.unmodifiableMap(copies)
    }

    PackDescriptor getDescriptor() {
        return descriptor
    }

    /**
     * @return where the pack came from, for messages
     */
    String getOrigin() {
        return origin
    }

    /**
     * @param from the {@code from} of a file of the descriptor
     * @return a copy of the content of the file it names
     */
    byte[] content(String from) {
        byte[] content = files.get(from)
        if (content == null) {
            throw new IllegalArgumentException("The feature pack ${origin} has no file ${from}")
        }
        return content.clone()
    }

    /**
     * @param from the {@code from} of a file of the descriptor
     * @return whether the file is a template, which is rendered, and not a file that is written as it is
     */
    static boolean isTemplate(String from) {
        return from.toLowerCase(Locale.ROOT).endsWith(TEMPLATE_SUFFIX)
    }

    /**
     * What the pack adds to an application, for whoever is about to generate one with it: a line for
     * each kind of thing, and none for a kind the pack has nothing of. What runs as code, when the
     * application is generated or when it is built, is said to.
     *
     * @return the lines, which are text of the descriptor as the parser let it through
     */
    List<String> summary() {
        List<String> lines = []
        if (descriptor.requires) {
            lines << "requires the features ${descriptor.requires.join(', ')}".toString()
        }
        if (descriptor.platforms) {
            lines << "platforms: ${descriptor.platforms.collect { PackDependency each -> coordinates(each) }.join(', ')}".toString()
        }
        if (descriptor.dependencies) {
            lines << "dependencies: ${descriptor.dependencies.collect { PackDependency each -> each.scope + ' ' + coordinates(each) }.join(', ')}".toString()
        }
        if (descriptor.buildPlugins) {
            lines << "build plugins: ${descriptor.buildPlugins.collect { PackBuildPlugin each -> each.version ? each.id + ':' + each.version : each.id }.join(', ')}".toString()
        }
        if (descriptor.buildProperties) {
            lines << "build properties: ${descriptor.buildProperties.keySet().join(', ')}".toString()
        }
        if (descriptor.buildExtensions) {
            lines << "build file blocks, which are code of the build: ${descriptor.buildExtensions.keySet().join(', ')}".toString()
        }
        int settings = leaves(descriptor.configuration)
        if (settings > 0) {
            lines << "configuration: ${settings} ${settings == 1 ? 'setting' : 'settings'}".toString()
        }
        if (descriptor.files) {
            List<String> templates = descriptor.files.findAll { PackFile each -> isTemplate(each.from) }.collect { PackFile each -> each.from }.unique()
            int copied = descriptor.files.count { PackFile each -> !isTemplate(each.from) } as int
            if (copied > 0) {
                lines << "files written as they are: ${copied}".toString()
            }
            if (templates) {
                lines << "templates, which are code that runs when the application is generated: ${templates.join(', ')}".toString()
            }
        }
        return lines
    }

    private static String coordinates(PackDependency dependency) {
        String coordinates = dependency.groupId + ':' + dependency.artifactId
        return dependency.version ? coordinates + ':' + dependency.version : coordinates
    }

    private static int leaves(Object value) {
        if (value instanceof Map) {
            int count = 0
            for (Object each : ((Map) value).values()) {
                count += leaves(each)
            }
            return count
        }
        return 1
    }

    @Override
    String toString() {
        return "Pack(${descriptor.name} from ${origin})"
    }
}
