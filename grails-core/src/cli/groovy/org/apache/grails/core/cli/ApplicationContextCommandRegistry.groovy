/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.grails.core.cli

import java.lang.reflect.InvocationTargetException

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.core.OrderComparator
import org.springframework.util.ClassUtils

import org.apache.grails.gradle.model.cli.CommandDescriptor
import org.grails.core.io.support.GrailsFactoriesLoader

/**
 * A registry of {@link org.apache.grails.core.cli.ApplicationCommand} instances
 *
 * @since 3.0
 */
@Slf4j
@CompileStatic
@Singleton(strict = false)
class ApplicationContextCommandRegistry {

    private final Map<String, ApplicationCommand> commands = [:]
    // every command made, by its class, also one whose name another command has
    private final Map<Class<?>, ApplicationCommand> madeCommands = new LinkedHashMap<>()
    private final String missingCommandHint

    ApplicationContextCommandRegistry() {
        ClassLoader registryClassLoader = ApplicationContextCommandRegistry.classLoader
        ClassLoader contextClassLoader = Thread.currentThread().contextClassLoader

        addApplicationCommands(registryClassLoader, contextClassLoader)

        Set<String> handledFactoryKeys = loadCommandProviders(registryClassLoader, contextClassLoader)
        missingCommandHint = ApplicationCommandDiagnostics.detectMissingCommandHint(
                registryClassLoader, contextClassLoader, handledFactoryKeys)
    }

    private void addApplicationCommands(ClassLoader registryClassLoader, ClassLoader contextClassLoader) {
        Map<Class<? extends ApplicationCommand>, String> commandOrigins = new LinkedHashMap<>()
        addApplicationCommandClasses(commandOrigins, registryClassLoader)

        // If this is reflectively loaded from the delegating cli, we need to make sure the context class loader is
        // also used to pull any commands that are loaded from the gradle classpath. The classes are collected before
        // any of them is instantiated: a class reachable through both classloaders would otherwise be instantiated
        // twice (its constructor may have side effects) just to be discarded on the name-collision check below.
        if (contextClassLoader != null && contextClassLoader != registryClassLoader) {
            addApplicationCommandClasses(commandOrigins, contextClassLoader)
        }

        List<ApplicationCommand> discoveredCommands = []
        for (Map.Entry<Class<? extends ApplicationCommand>, String> entry : commandOrigins) {
            try {
                discoveredCommands.add(instantiate(entry.key))
            }
            catch (LinkageError e) {
                rethrowIfFatal(e)
                log.error('Unable to link application command \'{}\' declared in \'{}\'. This is a Grails binary-compatibility issue; please report it to the Grails framework. The command is unavailable.',
                        entry.key.name, entry.value, e)
            }
            catch (Throwable e) {
                rethrowIfFatal(e)
                log.warn('Failed to load application command \'{}\' declared in \'{}\'; skipping it.',
                        entry.key.name, entry.value, e)
            }
        }

        discoveredCommands.sort { ApplicationCommand first, ApplicationCommand second ->
            first.class.name <=> second.class.name
        }
        OrderComparator.sort(discoveredCommands)
        for (ApplicationCommand command : discoveredCommands) {
            madeCommands.putIfAbsent(commandClass(command), command)
            if (!commands.containsKey(command.name)) {
                commands[command.name] = command
            }
        }
    }

    private static void addApplicationCommandClasses(
            Map<Class<? extends ApplicationCommand>, String> commandOrigins,
            ClassLoader classLoader) {
        Map<String, List<String>> declarations
        try {
            declarations = GrailsFactoriesLoader.loadFactoryDeclarations(
                    ApplicationCommand, classLoader, GrailsFactoriesLoader.CLI_FACTORIES_RESOURCE_LOCATION)
        }
        catch (Throwable e) {
            rethrowIfFatal(e)
            log.warn('Unable to enumerate application command factories; skipping classloader {}.', classLoader, e)
            return
        }

        Map<String, String> declaredOrigins = new LinkedHashMap<>()
        for (Map.Entry<String, List<String>> entry : declarations) {
            for (String commandName : entry.value) {
                declaredOrigins.putIfAbsent(commandName, entry.key)
            }
        }

        for (Map.Entry<String, String> entry : declaredOrigins) {
            try {
                Class<?> commandClass = ClassUtils.forName(entry.key, classLoader)
                if (!ApplicationCommand.isAssignableFrom(commandClass)) {
                    throw new IllegalArgumentException(
                            "Class [${entry.key}] is not assignable to [${ApplicationCommand.name}]")
                }
                commandOrigins.putIfAbsent((Class<? extends ApplicationCommand>) commandClass, entry.value)
            }
            catch (LinkageError e) {
                rethrowIfFatal(e)
                log.error('Unable to link application command \'{}\' declared in \'{}\'. This is a Grails binary-compatibility issue; please report it to the Grails framework. The command is unavailable.',
                        entry.key, entry.value, e)
            }
            catch (Throwable e) {
                rethrowIfFatal(e)
                log.warn('Failed to load application command \'{}\' declared in \'{}\'; skipping it.',
                        entry.key, entry.value, e)
            }
        }
    }

    private Set<String> loadCommandProviders(ClassLoader registryClassLoader, ClassLoader contextClassLoader) {
        Set<Class<? extends ApplicationCommandProvider>> providerClasses = new LinkedHashSet<>()
        addCommandProviderClasses(providerClasses, registryClassLoader)
        if (contextClassLoader != null && contextClassLoader != registryClassLoader) {
            addCommandProviderClasses(providerClasses, contextClassLoader)
        }

        ApplicationCommandRegistrar registrar = new ApplicationCommandRegistrar() {
            @Override
            String register(ApplicationCommand command) {
                madeCommands.putIfAbsent(commandClass(command), command)
                String name = command.name
                if (commands.containsKey(name)) {
                    return null
                }
                commands[name] = command
                name
            }
        }

        Set<String> handledFactoryKeys = new LinkedHashSet<>()
        for (Class<? extends ApplicationCommandProvider> providerClass : providerClasses) {
            try {
                ApplicationCommandProvider provider = instantiate(providerClass)
                if (provider instanceof ApplicationCommandFactoryKeyProvider) {
                    handledFactoryKeys.addAll(((ApplicationCommandFactoryKeyProvider) provider).handledFactoryKeys)
                }
                provider.contributeCommands(registryClassLoader, contextClassLoader, registrar)
            }
            catch (LinkageError e) {
                rethrowIfFatal(e)
                log.error('Unable to link application command provider \'{}\' from \'{}\'. This is a Grails binary-compatibility issue; please report it to the Grails framework. The provider is unavailable.',
                        providerClass.name, codeSourceLocation(providerClass), e)
            }
            catch (Throwable e) {
                rethrowIfFatal(e)
                log.warn('Failed to load application commands from provider \'{}\'; skipping it.', providerClass.name, e)
            }
        }
        handledFactoryKeys
    }

    private static void addCommandProviderClasses(
            Set<Class<? extends ApplicationCommandProvider>> providerClasses,
            ClassLoader classLoader) {
        Map<String, List<String>> declarations
        try {
            declarations = GrailsFactoriesLoader.loadFactoryDeclarations(
                    ApplicationCommandProvider, classLoader, GrailsFactoriesLoader.CLI_FACTORIES_RESOURCE_LOCATION)
        }
        catch (Throwable e) {
            rethrowIfFatal(e)
            log.warn('Unable to enumerate application command provider factories; skipping classloader {}.',
                    classLoader, e)
            return
        }

        for (Map.Entry<String, List<String>> entry : declarations) {
            for (String providerName : entry.value) {
                try {
                    Class<?> providerClass = ClassUtils.forName(providerName, classLoader)
                    if (!ApplicationCommandProvider.isAssignableFrom(providerClass)) {
                        throw new IllegalArgumentException(
                                "Class [${providerName}] is not assignable to [${ApplicationCommandProvider.name}]")
                    }
                    providerClasses.add((Class<? extends ApplicationCommandProvider>) providerClass)
                }
                catch (LinkageError e) {
                    rethrowIfFatal(e)
                    log.error('Unable to link application command provider \'{}\' declared in \'{}\'. This is a Grails binary-compatibility issue; please report it to the Grails framework. The provider is unavailable.',
                            providerName, entry.key, e)
                }
                catch (Throwable e) {
                    rethrowIfFatal(e)
                    log.warn('Failed to load application command provider \'{}\' declared in \'{}\'; skipping it.',
                            providerName, entry.key, e)
                }
            }
        }
    }

    private static <T> T instantiate(Class<? extends T> type) {
        try {
            type.getDeclaredConstructor().newInstance()
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

    Collection<ApplicationCommand> findCommands() {
        commands.values()
    }

    /**
     * Finds a command by its name, or, when no command has that name, by the name its class name
     * gives ({@code ImportDataCommand} is {@code import-data}). The catalog of the commands of a
     * build lists a command that has no descriptor by the name its class name gives, as its own
     * name is known only once the command is made; the command is run by the name it is listed by.
     *
     * @param name the name of the command
     * @return the command, or {@code null} when no command has the name, and the class of no
     * command, or of more than one, gives it
     */
    ApplicationCommand findCommand(String name) {
        ApplicationCommand command = commands[name]
        command != null ? command : findCommandByClassName(name)
    }

    // the command whose class gives the name; of two classes that give it, the one without a descriptor, as the
    // catalog lists a command with a descriptor by the name the descriptor gives
    private ApplicationCommand findCommandByClassName(String name) {
        List<Class<?>> named = madeCommands.keySet().findAll { Class<?> type -> CommandDescriptor.conventionName(type.name) == name }.toList()
        if (named.size() > 1) {
            named = named.findAll { Class<?> type -> !hasDescriptor(type) }
        }
        named.size() == 1 ? madeCommands[named[0]] : null
    }

    // the class of the command, which the catalog names it by: for an adapter, the class of the command it adapts
    private static Class<?> commandClass(ApplicationCommand command) {
        Object target = command instanceof ApplicationCommandTargetAware ? ((ApplicationCommandTargetAware) command).target : command
        target.getClass()
    }

    private static boolean hasDescriptor(Class<?> type) {
        String location = CommandDescriptor.location(type.name)
        location != null && type.classLoader?.getResource(location) != null
    }

    String getMissingCommandHint() {
        missingCommandHint
    }
}
