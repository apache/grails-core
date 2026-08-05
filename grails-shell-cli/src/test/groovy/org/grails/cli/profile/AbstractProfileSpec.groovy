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
package org.grails.cli.profile

import java.nio.file.Path

import grails.build.logging.GrailsConsole
import org.grails.build.parsing.CommandLine
import org.grails.cli.profile.commands.factory.StubRegistryClassLoader
import org.grails.io.support.FileSystemResource
import spock.lang.Specification
import spock.lang.TempDir

class AbstractProfileSpec extends Specification {

    private static final String LEGACY_COMMAND_HINT =
            'Grails 7 commands were detected; set grails { legacyCommandSupport = true } or upgrade the plugin.'

    @TempDir
    Path tempDir

    private ClassLoader originalContextClassLoader

    def setup() {
        originalContextClassLoader = Thread.currentThread().contextClassLoader
    }

    def cleanup() {
        Thread.currentThread().contextClassLoader = originalContextClassLoader
    }

    void "Test an unknown command reports the legacy command hint from the runtime registry"() {
        given: "a profile and a runtime registry that detected Grails 7 commands"
        Thread.currentThread().contextClassLoader = new StubRegistryClassLoader(
                getClass().classLoader, HintingCommandRegistry)
        GrailsConsole console = Mock()
        Profile profile = createProfile()

        when: "a command that does not exist is handled"
        boolean handled = profile.handleCommand(executionContext(console, 'zzz-unknown-command'))

        then: "the reported message carries the hint read from the registry"
        !handled
        1 * console.error('Command not found zzz-unknown-command\n' + LEGACY_COMMAND_HINT)
    }

    void "Test an unknown command reports no hint when the runtime registry has none"() {
        given: "a profile and a runtime registry without a hint"
        Thread.currentThread().contextClassLoader = new StubRegistryClassLoader(
                getClass().classLoader, SilentCommandRegistry)
        GrailsConsole console = Mock()
        Profile profile = createProfile()

        when: "a command that does not exist is handled"
        boolean handled = profile.handleCommand(executionContext(console, 'zzz-unknown-command'))

        then: "the reported message is left unchanged"
        !handled
        1 * console.error('Command not found zzz-unknown-command')
    }

    private Profile createProfile() {
        File profileDir = new File(tempDir.toFile(), 'profile')
        assert profileDir.mkdirs()
        new File(profileDir, 'profile.yml').text = 'name: web\n'
        ResourceProfile.create(Stub(ProfileRepository), 'web', new FileSystemResource("$profileDir/"))
    }

    private ExecutionContext executionContext(GrailsConsole console, String commandName) {
        CommandLine commandLine = Stub(CommandLine) {
            getCommandName() >> commandName
            getRemainingArgs() >> []
        }
        Stub(ExecutionContext) {
            getConsole() >> console
            getCommandLine() >> commandLine
            getBaseDir() >> tempDir.toFile()
        }
    }
}

class HintingCommandRegistry {

    static final HintingCommandRegistry instance = new HintingCommandRegistry()

    final String missingCommandHint =
            'Grails 7 commands were detected; set grails { legacyCommandSupport = true } or upgrade the plugin.'

    Collection<?> findCommands() {
        []
    }
}

class SilentCommandRegistry {

    static final SilentCommandRegistry instance = new SilentCommandRegistry()

    final String missingCommandHint = null

    Collection<?> findCommands() {
        []
    }
}
