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
package org.apache.grails.core.cli

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j

import org.grails.core.io.support.GrailsFactoriesLoader

/**
 * Tells of the plugins on the class path that have commands of Grails 7, which Grails 9 does not
 * run: a plugin compiled for Grails 7 registers them in {@code META-INF/grails.factories} under the
 * key {@code grails.dev.commands.ApplicationCommand}. Their classes are not loaded.
 *
 * @since 8.0
 */
@Slf4j
@CompileStatic
@PackageScope
class ApplicationCommandDiagnostics {

    private static final String GRAILS_7_COMMAND_FACTORY_KEY = 'grails.dev.commands.ApplicationCommand'

    private static final String FACTORIES_SUFFIX = '/' + GrailsFactoriesLoader.FACTORIES_RESOURCE_LOCATION

    /**
     * @param registryClassLoader the class loader of the registry
     * @param contextClassLoader the context class loader of the thread that makes the registry
     * @param handledFactoryKeys the keys a provider of commands says it handles
     * @return one line for each plugin that has commands of Grails 7, or {@code null} when there is none
     */
    static String detectMissingCommandHint(
            ClassLoader registryClassLoader,
            ClassLoader contextClassLoader,
            Set<String> handledFactoryKeys) {
        if (handledFactoryKeys.contains(GRAILS_7_COMMAND_FACTORY_KEY)) {
            return null
        }

        Map<String, List<String>> factoryDeclarations = new LinkedHashMap<>()
        addFactoryDeclarations(factoryDeclarations, registryClassLoader)
        if (contextClassLoader != null && contextClassLoader != registryClassLoader) {
            addFactoryDeclarations(factoryDeclarations, contextClassLoader)
        }

        List<String> lines = []
        for (String resource : factoryDeclarations.keySet()) {
            String line = "${origin(resource)} has commands of Grails 7, which Grails 9 does not run: use a version of the plugin for Grails 9"
            log.warn(line)
            lines << line
        }
        lines ? lines.join('\n') : null
    }

    // the jar or the directory of the file system a factories resource is in, and otherwise the resource
    private static String origin(String resource) {
        String location = resource
        if (location.startsWith('jar:') && location.contains('!/')) {
            location = location.substring('jar:'.length(), location.indexOf('!/'))
        } else if (location.endsWith(FACTORIES_SUFFIX)) {
            location = location.substring(0, location.length() - FACTORIES_SUFFIX.length())
        }
        if (location.startsWith('file:')) {
            try {
                return new File(new URI(location)).path
            } catch (URISyntaxException | IllegalArgumentException ignored) {
                return resource
            }
        }
        resource
    }

    private static void addFactoryDeclarations(
            Map<String, List<String>> declarations,
            ClassLoader classLoader) {
        if (classLoader == null) {
            return
        }
        try {
            Map<String, List<String>> found = GrailsFactoriesLoader.loadFactoryDeclarations(
                    GRAILS_7_COMMAND_FACTORY_KEY, classLoader, GrailsFactoriesLoader.FACTORIES_RESOURCE_LOCATION)
            for (Map.Entry<String, List<String>> entry : found) {
                declarations.putIfAbsent(entry.key, entry.value)
            }
        }
        catch (Throwable e) {
            rethrowIfFatal(e)
            log.warn('Unable to enumerate application command factory resources; skipping classloader {}.',
                    classLoader, e)
        }
    }

    private static void rethrowIfFatal(Throwable e) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>())
        Throwable current = e
        while (current != null && seen.add(current)) {
            if (current instanceof VirtualMachineError) {
                throw (VirtualMachineError) current
            }
            current = current.cause
        }
    }
}
