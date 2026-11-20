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
package legacycommands

import java.util.jar.JarFile

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.context.ConfigurableApplicationContext

import grails.testing.mixin.integration.Integration
import org.apache.grails.core.cli.ApplicationCommand
import org.apache.grails.core.cli.ApplicationContextCommandRegistry
import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.core.cli.ApplicationCommandTargetAware
import org.grails.build.parsing.CommandLine
import spock.lang.Specification

@Integration
class LegacyCommandCompatibilityIntegrationSpec extends Specification {

    @Autowired
    ConfigurableApplicationContext applicationContext

    def "discovers adapts and executes legacy application commands from a Grails 7 plugin"() {
        given: 'the registry created from the application classpath'
        ApplicationContextCommandRegistry registry = ApplicationContextCommandRegistry.instance
        ApplicationCommand applicationCommand = registry.findCommand('hello-legacy-app')
        ApplicationCommand grailsApplicationCommand = registry.findCommand('hello-legacy-grails')
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))
        File applicationMarkerFile = new File(executionContext.baseDir, 'hello-legacy-app.txt')
        File grailsApplicationMarkerFile = new File(executionContext.baseDir, 'hello-legacy-grails.txt')
        File generatedFile = new File(executionContext.baseDir, 'build/legacy-grails-command-output.txt')
        applicationMarkerFile.delete()
        grailsApplicationMarkerFile.delete()
        generatedFile.delete()

        expect: 'both legacy factory registrations are discovered and adapted'
        applicationCommand != null
        applicationCommand instanceof ApplicationCommandTargetAware
        grailsApplicationCommand != null
        grailsApplicationCommand instanceof ApplicationCommandTargetAware

        when: 'the legacy ApplicationCommand runs through its Grails 8 adapter'
        applicationCommand.applicationContext = applicationContext
        boolean applicationCommandResult = applicationCommand.handle(executionContext)

        then: 'the command succeeds and preserves its Grails 7 side effect'
        applicationCommandResult
        applicationMarkerFile.text == 'RAN'

        when: 'the legacy GrailsApplicationCommand runs through its Grails 8 adapter'
        grailsApplicationCommand.applicationContext = applicationContext
        boolean grailsApplicationCommandResult = grailsApplicationCommand.handle(executionContext)

        then: 'the subtype initializes its legacy execution context and succeeds'
        grailsApplicationCommandResult
        grailsApplicationMarkerFile.text == 'RAN'

        cleanup:
        applicationMarkerFile.delete()
        grailsApplicationMarkerFile.delete()
        generatedFile.delete()
    }

    def "autowires the legacy command unwrapped from its Grails 8 adapter"() {
        given: 'a legacy command adapter and its wrapped target'
        ApplicationCommand applicationCommand = ApplicationContextCommandRegistry.instance.findCommand('hello-legacy-app')
        ApplicationCommandTargetAware adapter = (ApplicationCommandTargetAware) applicationCommand
        Object legacyCommand = adapter.target
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))
        File applicationMarkerFile = new File(executionContext.baseDir, 'hello-legacy-app.txt')
        applicationMarkerFile.delete()

        when: 'the command runner autowires the real legacy command before invoking the adapter'
        applicationContext.autowireCapableBeanFactory.autowireBeanProperties(
            legacyCommand,
            AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE,
            false
        )
        adapter.applicationContext = applicationContext
        boolean result = applicationCommand.handle(executionContext)

        then: 'the wrapped command observes the application service injected by Spring'
        result
        applicationMarkerFile.text == 'INJECTED'

        cleanup:
        legacyCommand.greetingService = null
        applicationMarkerFile.delete()
    }

    def "runs the legacy GrailsApplicationCommand file DSL through its Grails 8 adapter"() {
        given: 'a legacy GrailsApplicationCommand adapter and execution context'
        ApplicationCommand applicationCommand = ApplicationContextCommandRegistry.instance.findCommand('hello-legacy-grails')
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))
        File generatedFile = new File(executionContext.baseDir, 'build/legacy-grails-command-output.txt')
        File grailsApplicationMarkerFile = new File(executionContext.baseDir, 'hello-legacy-grails.txt')
        generatedFile.delete()
        grailsApplicationMarkerFile.delete()

        when: 'the adapted legacy command runs with its legacy execution context'
        applicationCommand.applicationContext = applicationContext
        boolean result = applicationCommand.handle(executionContext)

        then: 'the inherited file DSL writes below the execution context base directory'
        result
        generatedFile.text == 'GENERATED'

        cleanup:
        generatedFile.delete()
        grailsApplicationMarkerFile.delete()
    }

    def "packages a hyphenated legacy plugin command script at its original command path"() {
        expect: 'the script is available for GroovyScriptCommandFactory discovery'
        // LegacyPluginScriptCompatSpec verifies factory discovery and hyphen-preserving name resolution.
        getClass().classLoader.getResource('META-INF/commands/hello-legacy-script.groovy') != null
    }

    def "discovers adapts and executes a Grails 7 Groovy 4 precompiled application command"() {
        given: 'the registry and a precompiled Grails 7 command binary'
        ApplicationCommand applicationCommand = ApplicationContextCommandRegistry.instance.findCommand('hello-g7-precompiled')
        ApplicationCommandTargetAware adapter = (ApplicationCommandTargetAware) applicationCommand
        Object legacyCommand = adapter.target
        File publishedArtifact = new File(legacyCommand.class.protectionDomain.codeSource.location.toURI())
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))
        File markerFile = new File(executionContext.baseDir, 'hello-g7-precompiled.txt')
        JarFile jarFile = new JarFile(publishedArtifact)
        String grailsCompileVersion
        String groovyCompileVersion
        try {
            grailsCompileVersion = jarFile.manifest.mainAttributes.getValue('Grails-Compile-Version')
            groovyCompileVersion = jarFile.manifest.mainAttributes.getValue('Groovy-Compile-Version')
        }
        finally {
            jarFile.close()
        }
        markerFile.delete()

        expect: 'the adapter targets the standalone Grails 7 / Groovy 4 binary and its resolved compile versions'
        applicationCommand != null
        applicationCommand instanceof ApplicationCommandTargetAware
        legacyCommand.class.name == 'legacy.g7.commands.HelloG7PrecompiledCommand'
        publishedArtifact.name.contains('legacy-g7-command-plugin')
        grailsCompileVersion.startsWith('7.')
        groovyCompileVersion.startsWith('4.')

        when: 'the precompiled command runs through the public Grails 8 adapter'
        applicationCommand.applicationContext = applicationContext
        boolean result = applicationCommand.handle(executionContext)

        then: 'the Grails 7 bytecode links and executes against the restored contract'
        result
        markerFile.text == 'G7-CONTEXT-true'

        cleanup:
        markerFile.delete()
    }

    def "executes a Grails 7 Groovy 4 precompiled Grails application command through its DSL forwarders"() {
        given: 'the registry and a precompiled GrailsApplicationCommand binary'
        ApplicationCommand applicationCommand = ApplicationContextCommandRegistry.instance.findCommand('hello-g7-precompiled-grails')
        ApplicationCommandTargetAware adapter = (ApplicationCommandTargetAware) applicationCommand
        Object legacyCommand = adapter.target
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))
        File outputDirectory = new File(executionContext.baseDir, 'build/hello-g7-precompiled-grails')
        File renderedFile = new File(outputDirectory, 'rendered.txt')
        renderedFile.delete()
        outputDirectory.delete()

        expect: 'the registry adapts the precompiled GrailsApplicationCommand binary'
        applicationCommand != null
        applicationCommand instanceof ApplicationCommandTargetAware
        legacyCommand.class.name == 'legacy.g7.commands.HelloG7PrecompiledGrailsCommand'

        when: 'the command runs through the public Grails 8 adapter'
        applicationCommand.applicationContext = applicationContext
        boolean result = applicationCommand.handle(executionContext)

        then: 'the restored application context, file, and template DSL ABI produces the expected output'
        result
        outputDirectory.directory
        renderedFile.text == 'G7-CONTEXT-true'

        cleanup:
        renderedFile.delete()
        outputDirectory.delete()
    }

    def "registers a precompiled command that relies on the trait to derive its name"() {
        given: 'the command shape create-command generated on Grails 7, declaring no name of its own'
        ApplicationCommand applicationCommand = ApplicationContextCommandRegistry.instance.findCommand('hello-derived-name')
        ExecutionContext executionContext = new ExecutionContext(Mock(CommandLine))
        File outputDirectory = new File(executionContext.baseDir, 'build/hello-derived-name')
        File renderedFile = new File(outputDirectory, 'rendered.txt')
        renderedFile.delete()
        outputDirectory.delete()

        expect: 'the trait default resolved the registration key from the class name across the version boundary'
        applicationCommand != null
        applicationCommand instanceof ApplicationCommandTargetAware
        ((ApplicationCommandTargetAware) applicationCommand).target.class.name ==
                'legacy.g7.commands.HelloDerivedNameCommand'
        applicationCommand.name == 'hello-derived-name'

        when: 'the command runs and reads its own derived name from inside the precompiled binary'
        applicationCommand.applicationContext = applicationContext
        boolean result = applicationCommand.handle(executionContext)

        then: 'the derivation is the same one the Groovy 4 bytecode observes at runtime'
        result
        renderedFile.text == 'G7-DERIVED-NAME-hello-derived-name'

        cleanup:
        renderedFile.delete()
        outputDirectory.delete()
    }

}
