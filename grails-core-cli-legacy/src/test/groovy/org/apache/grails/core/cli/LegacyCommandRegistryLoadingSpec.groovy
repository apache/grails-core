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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import grails.dev.commands.ExecutionContext as LegacyExecutionContext
import groovy.transform.CompileStatic
import org.apache.grails.core.cli.compat.LegacyApplicationCommandAdapter
import org.apache.grails.core.cli.compat.LegacyApplicationCommandProvider
import org.grails.build.parsing.CommandLine
import org.slf4j.LoggerFactory
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

class LegacyCommandRegistryLoadingSpec extends Specification {

    @TempDir
    File tempDir

    private ClassLoader originalContextClassLoader
    private URLClassLoader factoryClassLoader
    private Logger providerLogger
    private ListAppender<ILoggingEvent> providerAppender

    def cleanup() {
        Thread.currentThread().contextClassLoader = originalContextClassLoader
        factoryClassLoader?.close()
        providerLogger?.detachAppender(providerAppender)
    }

    def "discovers adapts and executes a command registered under the legacy factory key"() {
        given:
        useFactoryResources('grails.dev.commands.ApplicationCommand=org.apache.grails.core.cli.LegacyRegistryTestCommand')
        ConfigurableApplicationContext applicationContext = Mock()
        CommandLine commandLine = Mock()

        when:
        ApplicationCommand command = new ApplicationContextCommandRegistry().findCommand('legacy-registry-command')

        then:
        command instanceof LegacyApplicationCommandAdapter
        command instanceof ApplicationCommandTargetAware
        new ApplicationContextCommandRegistry().missingCommandHint == null
        LegacyApplicationCommandAdapter adapter = command as LegacyApplicationCommandAdapter
        LegacyRegistryTestCommand legacyCommand = ((ApplicationCommandTargetAware) command).target as LegacyRegistryTestCommand
        adapter.target.is(legacyCommand)

        when:
        adapter.applicationContext = applicationContext
        boolean result = adapter.handle(new ExecutionContext(commandLine))

        then:
        result
        legacyCommand.invoked
        legacyCommand.applicationContext.is(applicationContext)
        legacyCommand.executionContext instanceof LegacyExecutionContext
        legacyCommand.executionContext.commandLine.is(commandLine)
    }

    def "discovers adapts and executes a legacy Grails application command"() {
        given:
        useFactoryResources('grails.dev.commands.ApplicationCommand=org.apache.grails.core.cli.LegacyRegistryGrailsApplicationTestCommand')
        CommandLine commandLine = Mock()

        when:
        ApplicationCommand command = new ApplicationContextCommandRegistry().findCommand('legacy-registry-grails-application-command')

        then:
        command instanceof LegacyApplicationCommandAdapter
        command instanceof ApplicationCommandTargetAware
        LegacyApplicationCommandAdapter adapter = command as LegacyApplicationCommandAdapter
        LegacyRegistryGrailsApplicationTestCommand legacyCommand = ((ApplicationCommandTargetAware) command).target as LegacyRegistryGrailsApplicationTestCommand
        adapter.target.is(legacyCommand)

        when:
        boolean result = adapter.handle(new ExecutionContext(commandLine))

        then:
        result
        legacyCommand.handleCalls == 1
        legacyCommand.executionContext instanceof LegacyExecutionContext
        legacyCommand.executionContext.commandLine.is(commandLine)
        legacyCommand.templateRenderer != null
        legacyCommand.fileSystemInteraction != null
    }

    def "prefers a new-contract command when legacy and new registrations share a name"() {
        given:
        useFactoryResources(
                'grails.dev.commands.ApplicationCommand=org.apache.grails.core.cli.LegacyRegistryCollisionCommand',
                'org.apache.grails.core.cli.ApplicationCommand=org.apache.grails.core.cli.ConfigReportCommand')

        when:
        ApplicationCommand command = new ApplicationContextCommandRegistry().findCommand('config-report')

        then:
        command instanceof ConfigReportCommand
        !(command instanceof LegacyApplicationCommandAdapter)
    }

    def "prefers the ordered legacy command when two legacy commands share a name"() {
        given:
        useFactoryResources('grails.dev.commands.ApplicationCommand=' +
                "${AUnorderedLegacyCollisionCommand.name},${ZOrderedLegacyCollisionCommand.name}")

        when:
        ApplicationCommand command = new ApplicationContextCommandRegistry().findCommand('legacy-order-collision')

        then:
        ((ApplicationCommandTargetAware) command).target instanceof ZOrderedLegacyCollisionCommand
    }

    @Unroll
    def "resolves unordered legacy command name collisions by class name when declared #declarationOrder"() {
        given:
        useFactoryResources("grails.dev.commands.ApplicationCommand=${declaredClasses.join(',')}")

        when:
        ApplicationCommand command = new ApplicationContextCommandRegistry().findCommand('legacy-stable-collision')

        then:
        ((ApplicationCommandTargetAware) command).target instanceof AStableLegacyCollisionCommand

        where:
        declarationOrder   | declaredClasses
        'in class order'   | [AStableLegacyCollisionCommand.name, BStableLegacyCollisionCommand.name]
        'in reverse order' | [BStableLegacyCollisionCommand.name, AStableLegacyCollisionCommand.name]
    }

    def "continues loading commands when a legacy command constructor fails"() {
        given:
        useFactoryResources(
                'grails.dev.commands.ApplicationCommand=org.apache.grails.core.cli.ThrowingLegacyCommand,org.apache.grails.core.cli.LegacyRegistryTestCommand',
                'org.apache.grails.core.cli.ApplicationCommand=org.apache.grails.core.cli.ConfigReportCommand')

        when:
        ApplicationContextCommandRegistry registry = new ApplicationContextCommandRegistry()

        then:
        registry.findCommand('throwing-legacy-command') == null

        and:
        ApplicationCommand legacyCommand = registry.findCommand('legacy-registry-command')
        legacyCommand instanceof ApplicationCommandTargetAware

        and:
        registry.findCommand('config-report') instanceof ConfigReportCommand
    }

    def "does not warn when every legacy command collides with a modern command"() {
        given:
        attachProviderAppender()
        useFactoryResources(
                'grails.dev.commands.ApplicationCommand=org.apache.grails.core.cli.LegacyRegistryCollisionCommand',
                'org.apache.grails.core.cli.ApplicationCommand=org.apache.grails.core.cli.ConfigReportCommand')

        when:
        new ApplicationContextCommandRegistry()

        then:
        deprecationWarnings.empty
    }

    def "warns once when multiple legacy commands are installed"() {
        given:
        attachProviderAppender()
        useFactoryResources('grails.dev.commands.ApplicationCommand=org.apache.grails.core.cli.LegacyRegistryTestCommand,org.apache.grails.core.cli.LegacyRegistryGrailsApplicationTestCommand')

        when:
        ApplicationContextCommandRegistry registry = new ApplicationContextCommandRegistry()

        then:
        registry.findCommand('legacy-registry-command') != null
        registry.findCommand('legacy-registry-grails-application-command') != null
        deprecationWarnings.size() == 1
    }

    def "resolves a legacy command name once while installing and warning"() {
        given:
        attachProviderAppender()
        useFactoryResources('grails.dev.commands.ApplicationCommand=org.apache.grails.core.cli.SingleAccessNameLegacyCommand')

        when:
        ApplicationCommand command = new ApplicationContextCommandRegistry().findCommand('single-access-name')

        then:
        command instanceof ApplicationCommandTargetAware
        (((ApplicationCommandTargetAware) command).target as SingleAccessNameLegacyCommand).nameCalls == 1
        deprecationWarnings.size() == 1
        deprecationWarnings.first().formattedMessage.contains("Command 'single-access-name'")
    }

    def "logs linkage failures at error and continues loading legacy commands"() {
        given:
        attachProviderAppender()
        useFactoryResources('grails.dev.commands.ApplicationCommand=org.apache.grails.core.cli.LinkageErrorLegacyCommand,org.apache.grails.core.cli.LegacyRegistryTestCommand')

        when:
        ApplicationContextCommandRegistry registry = new ApplicationContextCommandRegistry()

        then:
        registry.findCommand('legacy-registry-command') != null
        List<ILoggingEvent> errors = providerAppender.list.findAll { ILoggingEvent event -> event.level == Level.ERROR }
        errors.size() == 1
        errors.first().formattedMessage.contains(LinkageErrorLegacyCommand.name)
        errors.first().formattedMessage.contains(LinkageErrorLegacyCommand.protectionDomain.codeSource.location.toExternalForm())
        errors.first().formattedMessage.contains('Grails binary-compatibility issue')
        errors.first().formattedMessage.contains('report it to the Grails framework')
    }

    def "reports a legacy command load-time linkage error with its factory origin and continues discovery"() {
        given:
        attachProviderAppender()
        useFactoryResources(
                'grails.dev.commands.ApplicationCommand=missing.LoadTimeLinkageCommand,org.apache.grails.core.cli.LegacyRegistryTestCommand',
                null,
                ['missing.LoadTimeLinkageCommand': new NoClassDefFoundError('simulated missing command dependency')])

        when:
        ApplicationContextCommandRegistry registry = new ApplicationContextCommandRegistry()

        then:
        registry.findCommand('legacy-registry-command') != null
        List<ILoggingEvent> errors = providerAppender.list.findAll { ILoggingEvent event -> event.level == Level.ERROR }
        errors.size() == 1
        errors.first().formattedMessage.contains('missing.LoadTimeLinkageCommand')
        errors.first().formattedMessage.contains(tempDir.toURI().toString())
        errors.first().formattedMessage.contains('report it to the Grails framework')
    }

    @Unroll
    def "propagates load-time fatal error #failure.class.simpleName from legacy commands"() {
        given:
        useFactoryResources(
                'grails.dev.commands.ApplicationCommand=missing.LoadTimeFatalCommand',
                null,
                ['missing.LoadTimeFatalCommand': failure])

        when:
        new ApplicationContextCommandRegistry()

        then:
        Throwable thrown = thrown(Throwable)
        thrown.is(failure)

        where:
        failure << [new StackOverflowError(), new OutOfMemoryError()]
    }

    @Unroll
    def "propagates direct fatal error #failure.class.simpleName from legacy commands"() {
        given:
        DirectFatalLegacyCommand.failure = failure
        useFactoryResources('grails.dev.commands.ApplicationCommand=org.apache.grails.core.cli.DirectFatalLegacyCommand')

        when:
        new ApplicationContextCommandRegistry()

        then:
        Throwable thrown = thrown(Throwable)
        thrown.is(failure)

        where:
        failure << [new StackOverflowError(), new OutOfMemoryError()]
    }

    @Unroll
    def "propagates reflection-wrapped fatal error #failure.class.simpleName from legacy command constructors"() {
        given:
        ConstructorFatalLegacyCommand.failure = failure
        useFactoryResources('grails.dev.commands.ApplicationCommand=org.apache.grails.core.cli.ConstructorFatalLegacyCommand')

        when:
        new ApplicationContextCommandRegistry()

        then:
        Throwable thrown = thrown(Throwable)
        thrown.is(failure)

        where:
        failure << [new StackOverflowError(), new OutOfMemoryError()]
    }

    private void attachProviderAppender() {
        providerLogger = LoggerFactory.getLogger(LegacyApplicationCommandProvider) as Logger
        providerAppender = new ListAppender<>()
        providerAppender.start()
        providerLogger.addAppender(providerAppender)
    }

    private List<ILoggingEvent> getDeprecationWarnings() {
        providerAppender.list.findAll { ILoggingEvent event ->
            event.formattedMessage.contains('deprecated grails.dev.commands compatibility layer')
        }
    }

    private void useFactoryResources(
            String legacyFactories,
            String cliFactories = null,
            Map<String, Throwable> loadFailures = [:]) {
        File metaInf = new File(tempDir, 'META-INF')
        assert metaInf.mkdirs()
        new File(metaInf, 'grails.factories').text = legacyFactories
        if (cliFactories != null) {
            new File(metaInf, 'grails-cli.factories').text = cliFactories
        }
        originalContextClassLoader = Thread.currentThread().contextClassLoader
        factoryClassLoader = new LegacyFactoryClassLoader(
                [tempDir.toURI().toURL()] as URL[], getClass().classLoader, loadFailures)
        Thread.currentThread().contextClassLoader = factoryClassLoader
    }
}

@CompileStatic
class LegacyFactoryClassLoader extends URLClassLoader {

    private final Map<String, Throwable> loadFailures

    LegacyFactoryClassLoader(URL[] urls, ClassLoader parent, Map<String, Throwable> loadFailures) {
        super(urls, parent)
        this.loadFailures = loadFailures
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Throwable failure = loadFailures.get(name)
        if (failure != null) {
            throw failure
        }
        super.loadClass(name, resolve)
    }
}

class LegacyRegistryTestCommand implements grails.dev.commands.ApplicationCommand {

    boolean invoked
    LegacyExecutionContext executionContext

    @Override
    String getName() {
        'legacy-registry-command'
    }

    @Override
    String getDescription() {
        'Legacy registry test command'
    }

    @Override
    boolean handle(LegacyExecutionContext executionContext) {
        invoked = true
        this.executionContext = executionContext
        true
    }
}

class LegacyRegistryGrailsApplicationTestCommand implements grails.dev.commands.GrailsApplicationCommand {

    int handleCalls

    @Override
    String getName() {
        'legacy-registry-grails-application-command'
    }

    @Override
    String getDescription() {
        'Legacy registry Grails application test command'
    }

    @Override
    boolean handle() {
        handleCalls++
        templateRenderer != null && fileSystemInteraction != null
    }
}

class LegacyRegistryCollisionCommand implements grails.dev.commands.ApplicationCommand {

    @Override
    String getName() {
        'config-report'
    }

    @Override
    String getDescription() {
        'Legacy registry collision command'
    }

    @Override
    boolean handle(LegacyExecutionContext executionContext) {
        true
    }
}

class AUnorderedLegacyCollisionCommand implements grails.dev.commands.ApplicationCommand {

    @Override
    String getName() {
        'legacy-order-collision'
    }

    @Override
    String getDescription() {
        'Unordered legacy collision command'
    }

    @Override
    boolean handle(LegacyExecutionContext executionContext) {
        true
    }
}

@Order(Ordered.HIGHEST_PRECEDENCE)
class ZOrderedLegacyCollisionCommand implements grails.dev.commands.ApplicationCommand {

    @Override
    String getName() {
        'legacy-order-collision'
    }

    @Override
    String getDescription() {
        'Ordered legacy collision command'
    }

    @Override
    boolean handle(LegacyExecutionContext executionContext) {
        true
    }
}

class AStableLegacyCollisionCommand implements grails.dev.commands.ApplicationCommand {

    @Override
    String getName() {
        'legacy-stable-collision'
    }

    @Override
    String getDescription() {
        'First stable legacy collision command'
    }

    @Override
    boolean handle(LegacyExecutionContext executionContext) {
        true
    }
}

class BStableLegacyCollisionCommand implements grails.dev.commands.ApplicationCommand {

    @Override
    String getName() {
        'legacy-stable-collision'
    }

    @Override
    String getDescription() {
        'Second stable legacy collision command'
    }

    @Override
    boolean handle(LegacyExecutionContext executionContext) {
        true
    }
}

class ThrowingLegacyCommand implements grails.dev.commands.ApplicationCommand {

    ThrowingLegacyCommand() {
        throw new RuntimeException('boom')
    }

    @Override
    String getName() {
        'throwing-legacy-command'
    }

    @Override
    String getDescription() {
        'Throwing legacy command'
    }

    @Override
    boolean handle(LegacyExecutionContext executionContext) {
        true
    }
}

class SingleAccessNameLegacyCommand implements grails.dev.commands.ApplicationCommand {

    int nameCalls

    @Override
    String getName() {
        if (++nameCalls > 1) {
            throw new IllegalStateException('name accessed more than once')
        }
        'single-access-name'
    }

    @Override
    String getDescription() {
        'Single access name legacy command'
    }

    @Override
    boolean handle(LegacyExecutionContext executionContext) {
        true
    }
}

class LinkageErrorLegacyCommand implements grails.dev.commands.ApplicationCommand {

    LinkageErrorLegacyCommand() {
        throw new NoClassDefFoundError('simulated missing command dependency')
    }

    @Override
    String getName() {
        'linkage-error-legacy-command'
    }

    @Override
    String getDescription() {
        'Linkage error legacy command'
    }

    @Override
    boolean handle(LegacyExecutionContext executionContext) {
        true
    }
}

class DirectFatalLegacyCommand implements grails.dev.commands.ApplicationCommand {

    static Throwable failure

    @Override
    String getName() {
        throw failure
    }

    @Override
    String getDescription() {
        'Direct fatal legacy command'
    }

    @Override
    boolean handle(LegacyExecutionContext executionContext) {
        true
    }
}

class ConstructorFatalLegacyCommand implements grails.dev.commands.ApplicationCommand {

    static Throwable failure

    ConstructorFatalLegacyCommand() {
        throw failure
    }

    @Override
    String getName() {
        'constructor-fatal-legacy-command'
    }

    @Override
    String getDescription() {
        'Constructor fatal legacy command'
    }

    @Override
    boolean handle(LegacyExecutionContext executionContext) {
        true
    }
}
