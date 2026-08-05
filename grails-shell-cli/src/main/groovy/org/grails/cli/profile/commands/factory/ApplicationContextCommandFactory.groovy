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
package org.grails.cli.profile.commands.factory

import grails.build.logging.GrailsConsole
import grails.util.Named
import org.grails.cli.gradle.commands.GradleTaskCommandAdapter
import org.grails.cli.profile.Command
import org.grails.cli.profile.Profile

/**
 * Automatically populates ApplicationContext command instances and adapts the interface to the shell
 *
 * @author Graeme Rocher
 * @since 3.0
 */
class ApplicationContextCommandFactory implements CommandFactory {

    @Override
    Collection<Command> findCommands(Profile profile, boolean inherited) {
        if (inherited) return Collections.emptyList()

        try {
            Class registry = loadRegistryClass()
            if (registry == null) return []
            def commands = registry.instance.findCommands()
            return commands.collect() { Named named -> new GradleTaskCommandAdapter(profile, named) }
        } catch (Throwable e) {
            rethrowIfFatal(e)
            GrailsConsole.instance.error("Error occurred loading commands: $e.message", e)
            return []
        }
    }

    static String unknownCommandMessage(String commandName) {
        unknownCommandMessage(commandName, findMissingCommandHint())
    }

    static String unknownCommandMessage(String commandName, String missingCommandHint) {
        String message = "Command not found $commandName"
        missingCommandHint ? "${message}\n${missingCommandHint}" : message
    }

    private static String findMissingCommandHint() {
        try {
            Class registry = loadRegistryClass()
            registry?.instance?.missingCommandHint as String
        }
        catch (Throwable e) {
            rethrowIfFatal(e)
            null
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

    private static Class loadRegistryClass() {
        ClassLoader contextClassLoader = Thread.currentThread().contextClassLoader
        if (contextClassLoader != null) {
            try {
                return contextClassLoader.loadClass('org.apache.grails.core.cli.ApplicationContextCommandRegistry')
            }
            catch (ClassNotFoundException ignored) {
            }
        }
        try {
            return ApplicationContextCommandFactory.classLoader.loadClass('org.apache.grails.core.cli.ApplicationContextCommandRegistry')
        }
        catch (ClassNotFoundException missingRegistry) {
            return null
        }
    }
}
