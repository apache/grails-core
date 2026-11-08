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

import groovy.transform.CompileStatic

import spock.lang.Specification

class ApplicationContextCommandFactorySpec extends Specification {

    private static final String LEGACY_COMMAND_HINT =
            'Grails 7 commands were detected; set grails { legacyCommandSupport = true } or upgrade the plugin.'

    private ClassLoader originalContextClassLoader

    def setup() {
        originalContextClassLoader = Thread.currentThread().contextClassLoader
    }

    def cleanup() {
        Thread.currentThread().contextClassLoader = originalContextClassLoader
    }

    def "shell unknown command output appends the legacy command hint"() {
        expect:
        ApplicationContextCommandFactory.unknownCommandMessage('missing-command', LEGACY_COMMAND_HINT) ==
                "Command not found missing-command\n${LEGACY_COMMAND_HINT}"
    }

    def "shell unknown command output remains unchanged without a legacy command hint"() {
        expect:
        ApplicationContextCommandFactory.unknownCommandMessage('missing-command', null) ==
                'Command not found missing-command'
    }

    def "shell unknown command path reads the hint from the runtime registry"() {
        given:
        Thread.currentThread().contextClassLoader = new StubRegistryClassLoader(
                getClass().classLoader, StubApplicationContextCommandRegistry)

        expect:
        ApplicationContextCommandFactory.unknownCommandMessage('missing-command') ==
                "Command not found missing-command\n${LEGACY_COMMAND_HINT}"
    }

    def "shell command discovery rethrows #errorType.simpleName from registry construction"() {
        given:
        Thread.currentThread().contextClassLoader = new StubRegistryClassLoader(getClass().classLoader, registryType)

        when:
        new ApplicationContextCommandFactory().findCommands(null, false)

        then:
        thrown(errorType)

        where:
        errorType           | registryType
        StackOverflowError  | StackOverflowRegistry
        OutOfMemoryError    | OutOfMemoryRegistry
    }
}

@CompileStatic
class StubRegistryClassLoader extends ClassLoader {

    private final Class<?> registryType

    StubRegistryClassLoader(ClassLoader parent, Class<?> registryType) {
        super(parent)
        this.registryType = registryType
    }

    @Override
    Class<?> loadClass(String name) throws ClassNotFoundException {
        name == 'org.apache.grails.core.cli.ApplicationContextCommandRegistry' ?
                registryType : super.loadClass(name)
    }
}

class StubApplicationContextCommandRegistry {

    static final StubApplicationContextCommandRegistry instance = new StubApplicationContextCommandRegistry()
    final String missingCommandHint =
            'Grails 7 commands were detected; set grails { legacyCommandSupport = true } or upgrade the plugin.'
}

class StackOverflowRegistry {

    static Object getInstance() {
        throw new StackOverflowError()
    }
}

class OutOfMemoryRegistry {

    static Object getInstance() {
        throw new OutOfMemoryError()
    }
}
