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

@Slf4j
@CompileStatic
@PackageScope
class ApplicationCommandDiagnostics {

    private static final String LEGACY_COMMAND_FACTORY_KEY = 'grails.dev.commands.ApplicationCommand'
    private static final String MISSING_COMMAND_HINT =
            'Grails 7 commands were detected; set grails { legacyCommandSupport = true } or upgrade the plugin.'

    static String detectMissingCommandHint(
            ClassLoader registryClassLoader,
            ClassLoader contextClassLoader,
            Set<String> handledFactoryKeys) {
        if (handledFactoryKeys.contains(LEGACY_COMMAND_FACTORY_KEY)) {
            return null
        }

        Map<String, List<String>> factoryDeclarations = new LinkedHashMap<>()
        addFactoryDeclarations(factoryDeclarations, registryClassLoader)
        if (contextClassLoader != null && contextClassLoader != registryClassLoader) {
            addFactoryDeclarations(factoryDeclarations, contextClassLoader)
        }

        for (String origin : factoryDeclarations.keySet()) {
            log.error('Plugin {} ships Grails 7 commands; set grails { legacyCommandSupport = true } or upgrade the plugin.',
                    origin)
        }
        factoryDeclarations ? MISSING_COMMAND_HINT : null
    }

    private static void addFactoryDeclarations(
            Map<String, List<String>> declarations,
            ClassLoader classLoader) {
        if (classLoader == null) {
            return
        }
        try {
            Map<String, List<String>> found = GrailsFactoriesLoader.loadFactoryDeclarations(
                    LEGACY_COMMAND_FACTORY_KEY, classLoader, GrailsFactoriesLoader.FACTORIES_RESOURCE_LOCATION)
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
