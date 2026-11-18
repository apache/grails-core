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
package org.apache.grails.core.cli.compat

import java.lang.reflect.InvocationTargetException

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import grails.dev.commands.ApplicationCommand as LegacyApplicationCommand
import org.springframework.core.OrderComparator
import org.springframework.util.ClassUtils

import org.apache.grails.core.cli.ApplicationCommand
import org.apache.grails.core.cli.ApplicationCommandFactoryKeyProvider
import org.apache.grails.core.cli.ApplicationCommandRegistrar
import org.grails.core.io.support.GrailsFactoriesLoader
import org.grails.io.support.FactoriesLoaderSupport

/**
 * Loads commands implemented against the deprecated Grails 7 command contract.
 */
@Slf4j
@CompileStatic
class LegacyApplicationCommandProvider implements ApplicationCommandFactoryKeyProvider {

    private boolean warningLogged

    @Override
    Collection<String> getHandledFactoryKeys() {
        [LegacyApplicationCommand.name]
    }

    @Override
    @SuppressWarnings('deprecation')
    void contributeCommands(
        ClassLoader registryClassLoader,
        ClassLoader contextClassLoader,
        ApplicationCommandRegistrar registrar) {
        Set<Class<? extends LegacyApplicationCommand>> legacyClasses = new LinkedHashSet<>()
        addLegacyCommandClasses(legacyClasses, registryClassLoader)
        if (contextClassLoader != null && contextClassLoader != registryClassLoader) {
            addLegacyCommandClasses(legacyClasses, contextClassLoader)
        }

        List<LegacyApplicationCommandAdapter> adapters = []
        for (Class<? extends LegacyApplicationCommand> legacyClass : legacyClasses) {
            try {
                LegacyApplicationCommand legacyCommand = instantiate(legacyClass)
                adapters.add(new LegacyApplicationCommandAdapter(legacyCommand))
            }
            catch (LinkageError e) {
                rethrowIfFatal(e)
                log.error('Unable to link Grails 7 legacy command \'{}\' from \'{}\'. This is a Grails binary-compatibility issue; please report it to the Grails framework. The command is unavailable.',
                        legacyClass.name, codeSourceLocation(legacyClass), e)
            }
            catch (Throwable e) {
                rethrowIfFatal(e)
                log.warn('Failed to load a Grails 7 legacy command from class \'{}\' through the deprecated grails.dev.commands compatibility layer; skipping it.',
                        legacyClass.name, e)
            }
        }

        // Registration is first-wins, so the adapters are ordered the same way the Grails 8 commands are: by class
        // name for a stable baseline, then by the order the adapted command declares. Without this, two Grails 7
        // plugins shipping the same command name would be resolved by jar scan order.
        adapters.sort { LegacyApplicationCommandAdapter first, LegacyApplicationCommandAdapter second ->
            first.target.class.name <=> second.target.class.name
        }
        OrderComparator.sort(adapters)
        for (ApplicationCommand command : adapters) {
            String installedName = registrar.register(command)
            if (installedName != null && !warningLogged) {
                log.warn('Command \'{}\' from a Grails 7 plugin was loaded through the deprecated grails.dev.commands compatibility layer. Ask the plugin author to migrate to the org.apache.grails.core.cli command API and publish a -cli companion artifact; this compatibility path will be removed in a future major release.', installedName)
                warningLogged = true
            }
        }
    }

    private static void addLegacyCommandClasses(
            Set<Class<? extends LegacyApplicationCommand>> legacyClasses,
            ClassLoader classLoader) {
        Map<String, List<String>> declarations
        try {
            declarations = GrailsFactoriesLoader.loadFactoryDeclarations(
                    LegacyApplicationCommand, classLoader, FactoriesLoaderSupport.FACTORIES_RESOURCE_LOCATION)
        }
        catch (Throwable e) {
            rethrowIfFatal(e)
            log.warn('Unable to enumerate Grails 7 legacy command factories; skipping classloader {}.',
                    classLoader, e)
            return
        }

        for (Map.Entry<String, List<String>> entry : declarations) {
            for (String commandName : entry.value) {
                try {
                    Class<?> commandClass = ClassUtils.forName(commandName, classLoader)
                    if (!LegacyApplicationCommand.isAssignableFrom(commandClass)) {
                        throw new IllegalArgumentException(
                                "Class [${commandName}] is not assignable to [${LegacyApplicationCommand.name}]")
                    }
                    legacyClasses.add((Class<? extends LegacyApplicationCommand>) commandClass)
                }
                catch (LinkageError e) {
                    rethrowIfFatal(e)
                    log.error('Unable to link Grails 7 legacy command \'{}\' declared in \'{}\'. This is a Grails binary-compatibility issue; please report it to the Grails framework. The command is unavailable.',
                            commandName, entry.key, e)
                }
                catch (Throwable e) {
                    rethrowIfFatal(e)
                    log.warn('Failed to load a Grails 7 legacy command \'{}\' declared in \'{}\'; skipping it.',
                            commandName, entry.key, e)
                }
            }
        }
    }

    private static LegacyApplicationCommand instantiate(Class<? extends LegacyApplicationCommand> legacyClass) {
        try {
            legacyClass.getDeclaredConstructor().newInstance()
        }
        catch (InvocationTargetException e) {
            Throwable cause = e.cause
            rethrowIfFatal(cause)
            if (cause instanceof LinkageError) {
                throw (LinkageError) cause
            }
            throw e
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

    private static String codeSourceLocation(Class<?> type) {
        type.protectionDomain?.codeSource?.location?.toExternalForm() ?: 'unknown'
    }
}
