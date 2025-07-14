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
package grails.dev.commands

import groovy.transform.CompileStatic
import org.grails.core.io.support.GrailsFactoriesLoader

/**
 * A registry of {@grails.dev.commands.ApplicationContextCommand} instances
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@Singleton(strict = false)
class ApplicationContextCommandRegistry {

    private final Map<String, ApplicationCommand> commands = [:]

    ApplicationContextCommandRegistry() {
        def registeredCommands = GrailsFactoriesLoader.loadFactories(ApplicationCommand)

        def iterator = registeredCommands.iterator()
        while (iterator.hasNext()) {
            def cmd = iterator.next()
            commands[cmd.name] = cmd
        }
    }

    Collection<ApplicationCommand> findCommands() {
        commands.values()
    }

    ApplicationCommand findCommand(String name) {
        commands[name]
    }
}
