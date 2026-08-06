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

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

import groovy.transform.CompileStatic

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent

import org.apache.grails.core.testing.support.LogCapture
import org.springframework.core.Ordered

import org.grails.core.io.support.GrailsFactoriesLoader

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

class ApplicationCommandProviderSpec extends Specification {

    @TempDir
    Path tempDir

    private ClassLoader originalContextClassLoader
    private URLClassLoader factoryClassLoader
    private URLClassLoader registryDefiningClassLoader
    private LogCapture logCapture

    def cleanup() {
        logCapture?.close()
        Thread.currentThread().contextClassLoader = originalContextClassLoader
        factoryClassLoader?.close()
        registryDefiningClassLoader?.close()
    }

    def "continues loading providers when one provider constructor fails"() {
        given:
        File metaInf = new File(tempDir.toFile(), 'META-INF')
        assert metaInf.mkdirs()
        new File(metaInf, 'grails-cli.factories').text = "${ApplicationCommandProvider.name}=${ThrowingCommandProvider.name},${TestCommandProvider.name}"
        originalContextClassLoader = Thread.currentThread().contextClassLoader
        factoryClassLoader = new URLClassLoader([tempDir.toUri().toURL()] as URL[], getClass().classLoader)
        Thread.currentThread().contextClassLoader = factoryClassLoader

        when:
        ApplicationCommand command = new ApplicationContextCommandRegistry().findCommand('provider-test')

        then:
        command instanceof ProviderTestCommand
    }

    def "instantiates the same provider class once across registry and context classloaders"() {
        given:
        ConstructorCountingCommandProvider.constructorCalls = 0
        File metaInf = new File(tempDir.toFile(), 'META-INF')
        assert metaInf.mkdirs()
        new File(metaInf, 'grails-cli.factories').text = "${ApplicationCommandProvider.name}=${ConstructorCountingCommandProvider.name}"
        originalContextClassLoader = Thread.currentThread().contextClassLoader
        factoryClassLoader = new URLClassLoader([tempDir.toUri().toURL()] as URL[], getClass().classLoader)
        Thread.currentThread().contextClassLoader = factoryClassLoader

        when:
        new ApplicationContextCommandRegistry()

        then:
        ConstructorCountingCommandProvider.constructorCalls == 1
    }

    def "instantiates a modern command once when distinct resources declare the same class"() {
        given:
        ConstructorCountingApplicationCommand.constructorCalls = 0
        URL firstPlugin = createFactoryJar(
                'first-counting-command.jar',
                'example.FirstFactory=example.FirstImplementation',
                "${ApplicationCommand.name}=${ConstructorCountingApplicationCommand.name}")
        URL secondPlugin = createFactoryJar(
                'second-counting-command.jar',
                'example.SecondFactory=example.SecondImplementation',
                "${ApplicationCommand.name}=${ConstructorCountingApplicationCommand.name}")
        useFactoryResources([firstPlugin, secondPlugin])

        when:
        ApplicationContextCommandRegistry registry = new ApplicationContextCommandRegistry()

        then:
        registry.findCommand('counting-modern') instanceof ConstructorCountingApplicationCommand
        ConstructorCountingApplicationCommand.constructorCalls == 1
    }

    def "instantiates the same command class once across registry and context classloaders"() {
        given: 'a factories declaration reachable from the registry classloader and the context classloader'
        SharedCountingApplicationCommand.constructorCalls = 0
        URL plugin = createFactoryJar(
                'shared-counting-command.jar',
                'example.OtherFactory=example.OtherImplementation',
                "${ApplicationCommand.name}=${SharedCountingApplicationCommand.name}")
        // The registry scans its own defining classloader and then the thread context classloader.
        // Define the registry class in a loader that also carries the factories jar so the first
        // scan finds the declaration, and hand the same jar to the context classloader for the
        // second. The command class itself stays on the shared parent test classpath, so both
        // scans resolve the identical Class - the case that was previously constructed twice.
        registryDefiningClassLoader = new RegistryDefiningClassLoader([plugin] as URL[], getClass().classLoader)
        useFactoryResources([plugin])

        when: 'the singleton instance is read, since the eager @Singleton initializer constructs it'
        def registry = registryDefiningClassLoader
                .loadClass(ApplicationContextCommandRegistry.name)
                .instance

        then:
        registry.findCommand('counting-shared') instanceof SharedCountingApplicationCommand
        SharedCountingApplicationCommand.constructorCalls == 1
    }

    def "ordered modern commands win duplicate-name collisions"() {
        given:
        URL plugin = createFactoryJar(
                'ordered-command.jar',
                'example.OtherFactory=example.OtherImplementation',
                "${ApplicationCommand.name}=${AUnorderedCollisionCommand.name},${ZOrderedCollisionCommand.name}")
        useFactoryResources([plugin])

        expect:
        new ApplicationContextCommandRegistry().findCommand('ordered-collision') instanceof ZOrderedCollisionCommand
    }

    def "continues loading providers after a linkage error"() {
        given:
        URL plugin = createFactoryJar(
                'linkage-provider.jar',
                'example.OtherFactory=example.OtherImplementation',
                "${ApplicationCommandProvider.name}=${LinkageErrorCommandProvider.name},${TestCommandProvider.name}")
        useFactoryResources([plugin])

        when:
        ApplicationCommand command = new ApplicationContextCommandRegistry().findCommand('provider-test')

        then:
        command instanceof ProviderTestCommand
    }

    def "reports an application command load-time linkage error with its factory origin and continues discovery"() {
        given:
        URL plugin = createFactoryJar(
                'load-time-linkage-command.jar',
                'example.OtherFactory=example.OtherImplementation',
                "${ApplicationCommand.name}=missing.LinkageCommand,${ModernTestCommand.name}")
        useFactoryResources([plugin], false,
                ['missing.LinkageCommand': new NoClassDefFoundError('simulated missing command dependency')])
        logCapture = new LogCapture(ApplicationContextCommandRegistry)

        when:
        ApplicationContextCommandRegistry registry = new ApplicationContextCommandRegistry()

        then:
        registry.findCommand('modern-test') instanceof ModernTestCommand
        List<ILoggingEvent> errors = logCapture.events.findAll { it.level == Level.ERROR }
        errors.any { it.formattedMessage.contains('missing.LinkageCommand') }
        errors.any { it.formattedMessage.contains('load-time-linkage-command.jar') }
        errors.any { it.formattedMessage.contains('report it to the Grails framework') }
    }

    @Unroll
    def "propagates load-time fatal error #failure.class.simpleName from application commands"() {
        given:
        URL plugin = createFactoryJar(
                'load-time-fatal-command.jar',
                'example.OtherFactory=example.OtherImplementation',
                "${ApplicationCommand.name}=missing.FatalCommand")
        useFactoryResources([plugin], false, ['missing.FatalCommand': failure])

        when:
        new ApplicationContextCommandRegistry()

        then:
        Throwable thrown = thrown(Throwable)
        thrown.is(failure)

        where:
        failure << [new StackOverflowError(), new OutOfMemoryError()]
    }

    def "reports a provider load-time linkage error with its factory origin and continues discovery"() {
        given:
        URL plugin = createFactoryJar(
                'load-time-linkage-provider.jar',
                'example.OtherFactory=example.OtherImplementation',
                "${ApplicationCommandProvider.name}=missing.LinkageProvider,${TestCommandProvider.name}")
        useFactoryResources([plugin], false,
                ['missing.LinkageProvider': new NoClassDefFoundError('simulated missing provider dependency')])
        logCapture = new LogCapture(ApplicationContextCommandRegistry)

        when:
        ApplicationContextCommandRegistry registry = new ApplicationContextCommandRegistry()

        then:
        registry.findCommand('provider-test') instanceof ProviderTestCommand
        List<ILoggingEvent> errors = logCapture.events.findAll { it.level == Level.ERROR }
        errors.any { it.formattedMessage.contains('missing.LinkageProvider') }
        errors.any { it.formattedMessage.contains('load-time-linkage-provider.jar') }
        errors.any { it.formattedMessage.contains('report it to the Grails framework') }
    }

    @Unroll
    def "propagates load-time fatal error #failure.class.simpleName from command providers"() {
        given:
        URL plugin = createFactoryJar(
                'load-time-fatal-provider.jar',
                'example.OtherFactory=example.OtherImplementation',
                "${ApplicationCommandProvider.name}=missing.FatalProvider")
        useFactoryResources([plugin], false, ['missing.FatalProvider': failure])

        when:
        new ApplicationContextCommandRegistry()

        then:
        Throwable thrown = thrown(Throwable)
        thrown.is(failure)

        where:
        failure << [new StackOverflowError(), new OutOfMemoryError()]
    }

    @Unroll
    def "propagates direct fatal error #failure.class.simpleName from command providers"() {
        given:
        DirectFatalCommandProvider.failure = failure
        URL plugin = createFactoryJar(
                'direct-fatal-provider.jar',
                'example.OtherFactory=example.OtherImplementation',
                "${ApplicationCommandProvider.name}=${DirectFatalCommandProvider.name}")
        useFactoryResources([plugin])

        when:
        new ApplicationContextCommandRegistry()

        then:
        Throwable thrown = thrown(Throwable)
        thrown.is(failure)

        where:
        failure << [new StackOverflowError(), new OutOfMemoryError()]
    }

    @Unroll
    def "propagates reflection-wrapped fatal error #failure.class.simpleName from command provider constructors"() {
        given:
        ConstructorFatalCommandProvider.failure = failure
        URL plugin = createFactoryJar(
                'constructor-fatal-provider.jar',
                'example.OtherFactory=example.OtherImplementation',
                "${ApplicationCommandProvider.name}=${ConstructorFatalCommandProvider.name}")
        useFactoryResources([plugin])

        when:
        new ApplicationContextCommandRegistry()

        then:
        Throwable thrown = thrown(Throwable)
        thrown.is(failure)

        where:
        failure << [new StackOverflowError(), new OutOfMemoryError()]
    }

    def "reports each legacy command plugin once without loading its command classes"() {
        given:
        URL firstPlugin = createFactoryJar('first-plugin.jar',
                'grails.dev.commands.ApplicationCommand=missing.FirstLegacyCommand')
        URL secondPlugin = createFactoryJar('second-plugin.jar',
                'grails.dev.commands.ApplicationCommand=missing.SecondLegacyCommand')
        useFactoryResources([firstPlugin, secondPlugin], true)
        logCapture = new LogCapture(ApplicationCommandDiagnostics)

        when:
        ApplicationContextCommandRegistry registry = new ApplicationContextCommandRegistry()

        then:
        registry.missingCommandHint == 'Grails 7 commands were detected; set grails { legacyCommandSupport = true } or upgrade the plugin.'
        !((TrackingFactoryClassLoader) factoryClassLoader).legacyCommandClassLoaded
        List<ILoggingEvent> errors = logCapture.events.findAll {
            it.level == Level.ERROR && it.formattedMessage.contains('ships Grails 7 commands')
        }
        errors.size() == 2
        errors.count { it.formattedMessage.contains('first-plugin.jar') } == 1
        errors.count { it.formattedMessage.contains('second-plugin.jar') } == 1
        errors.every {
            it.formattedMessage.contains('set grails { legacyCommandSupport = true } or upgrade the plugin.')
        }
    }

    def "does not report legacy command diagnostics when no legacy factory key exists"() {
        given:
        URL plugin = createFactoryJar('modern-plugin.jar', 'example.OtherFactory=missing.OtherFactory')
        useFactoryResources([plugin])
        logCapture = new LogCapture(ApplicationCommandDiagnostics)

        when:
        ApplicationContextCommandRegistry registry = new ApplicationContextCommandRegistry()

        then:
        registry.missingCommandHint == null
        !logCapture.events.any { it.formattedMessage.contains('ships Grails 7 commands') }
    }

    def "reports a malformed factory resource and continues command discovery"() {
        given:
        String malformedFactory = 'grails.dev.commands.ApplicationCommand=' + '\\' + 'uInvalid'
        URL plugin = createFactoryJar('malformed-plugin.jar', malformedFactory)
        useFactoryResources([plugin])
        logCapture = new LogCapture(GrailsFactoriesLoader)

        when:
        ApplicationContextCommandRegistry registry = new ApplicationContextCommandRegistry()

        then:
        registry.missingCommandHint == null
        noExceptionThrown()
        ILoggingEvent warning = logCapture.events.find {
            it.level == Level.WARN && it.formattedMessage.contains('malformed-plugin.jar')
        }
        warning.formattedMessage.contains('Unable to read factory declarations')
        warning.formattedMessage.contains('META-INF/grails.factories')

        and: 'the cause is reported, since it is what tells the user why the resource was dropped'
        warning.throwableProxy.className == IllegalArgumentException.name
        warning.throwableProxy.message.contains('Malformed')
    }

    def "reports a malformed cli factory resource without losing another plugin's modern commands"() {
        given:
        String malformedCliFactory = ApplicationCommand.name + '=' + '\\' + 'uInvalid'
        URL malformedPlugin = createFactoryJar(
                'malformed-cli-plugin.jar',
                'example.OtherFactory=example.OtherImplementation',
                malformedCliFactory)
        URL modernPlugin = createFactoryJar(
                'modern-command-plugin.jar',
                'example.OtherFactory=example.OtherImplementation',
                "${ApplicationCommand.name}=${ModernTestCommand.name}")
        useFactoryResources([malformedPlugin, modernPlugin])
        logCapture = new LogCapture(GrailsFactoriesLoader)

        when:
        ApplicationContextCommandRegistry registry = new ApplicationContextCommandRegistry()

        then:
        registry.findCommand('modern-test') instanceof ModernTestCommand
        ILoggingEvent warning = logCapture.events.find {
            it.level == Level.WARN && it.formattedMessage.contains('malformed-cli-plugin.jar')
        }
        warning.formattedMessage.contains('Unable to read factory declarations')
        warning.formattedMessage.contains('META-INF/grails-cli.factories')

        and: 'the cause is reported, since it is what tells the user why the resource was dropped'
        warning.throwableProxy.className == IllegalArgumentException.name
        warning.throwableProxy.message.contains('Malformed')
    }

    def "continues command discovery when factory resources cannot be enumerated"() {
        given:
        originalContextClassLoader = Thread.currentThread().contextClassLoader
        factoryClassLoader = new ThrowingFactoryResourcesClassLoader(getClass().classLoader)
        Thread.currentThread().contextClassLoader = factoryClassLoader

        when:
        ApplicationContextCommandRegistry registry = new ApplicationContextCommandRegistry()

        then:
        registry.missingCommandHint == null
        noExceptionThrown()
    }

    def "reports the factory resource URL as the plugin origin when the resource is not a jar"() {
        given:
        URL factoryResource = new URL(null, 'memory:legacy-plugin/META-INF/grails.factories',
                new FixedFactoryResourceUrlStreamHandler())
        originalContextClassLoader = Thread.currentThread().contextClassLoader
        factoryClassLoader = new FixedFactoryResourceClassLoader(factoryResource, getClass().classLoader)
        Thread.currentThread().contextClassLoader = factoryClassLoader
        logCapture = new LogCapture(ApplicationCommandDiagnostics)

        when:
        ApplicationContextCommandRegistry registry = new ApplicationContextCommandRegistry()

        then:
        registry.missingCommandHint != null
        logCapture.events.any {
            it.level == Level.ERROR && it.formattedMessage.contains(
                    'Plugin memory:legacy-plugin/META-INF/grails.factories ships Grails 7 commands')
        }
    }

    def "does not report legacy command diagnostics when a provider handles the factory key"() {
        given:
        URL plugin = createFactoryJar(
                'supported-plugin.jar',
                'grails.dev.commands.ApplicationCommand=missing.SupportedLegacyCommand',
                "${ApplicationCommandProvider.name}=${DiagnosticSupportCommandProvider.name}")
        useFactoryResources([plugin])
        logCapture = new LogCapture(ApplicationCommandDiagnostics)

        when:
        ApplicationContextCommandRegistry registry = new ApplicationContextCommandRegistry()

        then:
        registry.missingCommandHint == null
        !logCapture.events.any { it.formattedMessage.contains('ships Grails 7 commands') }
    }

    def "does not report missing support when a factory-key provider fails to contribute commands"() {
        given:
        URL plugin = createFactoryJar(
                'failing-supported-plugin.jar',
                'grails.dev.commands.ApplicationCommand=missing.SupportedLegacyCommand',
                "${ApplicationCommandProvider.name}=${ThrowingDiagnosticSupportCommandProvider.name}")
        useFactoryResources([plugin])
        logCapture = new LogCapture(ApplicationCommandDiagnostics)

        when:
        ApplicationContextCommandRegistry registry = new ApplicationContextCommandRegistry()

        then:
        registry.missingCommandHint == null
        !logCapture.events.any { it.formattedMessage.contains('ships Grails 7 commands') }
    }

    private URL createFactoryJar(String name, String legacyFactories, String cliFactories = null) {
        Path jar = tempDir.resolve(name)
        new JarOutputStream(Files.newOutputStream(jar)).withCloseable { JarOutputStream output ->
            writeEntry(output, 'META-INF/grails.factories', legacyFactories)
            if (cliFactories != null) {
                writeEntry(output, 'META-INF/grails-cli.factories', cliFactories)
            }
        }
        jar.toUri().toURL()
    }

    private static void writeEntry(JarOutputStream output, String name, String content) {
        output.putNextEntry(new JarEntry(name))
        output.write(content.getBytes('UTF-8'))
        output.closeEntry()
    }

    private void useFactoryResources(
            List<URL> resources,
            boolean duplicateResources = false,
            Map<String, Throwable> loadFailures = [:]) {
        originalContextClassLoader = Thread.currentThread().contextClassLoader
        factoryClassLoader = new TrackingFactoryClassLoader(
                resources as URL[], getClass().classLoader, duplicateResources, loadFailures)
        // Bootstrap loadClass before the loader becomes the context classloader: the JDK bean
        // introspector resolves *BeanInfo classes through the context classloader, and a
        // first-ever invocation of this Groovy-compiled loadClass in the middle of that lookup
        // triggers Groovy class initialization that recurses into the same lookup and dies with
        // a ClassCircularityError.
        factoryClassLoader.loadClass(Object.name)
        Thread.currentThread().contextClassLoader = factoryClassLoader
    }

}

class ThrowingCommandProvider implements ApplicationCommandProvider {

    ThrowingCommandProvider() {
        throw new RuntimeException('boom')
    }

    @Override
    void contributeCommands(ClassLoader registryClassLoader, ClassLoader contextClassLoader, ApplicationCommandRegistrar registrar) {
    }
}

class TestCommandProvider implements ApplicationCommandProvider {

    @Override
    void contributeCommands(ClassLoader registryClassLoader, ClassLoader contextClassLoader, ApplicationCommandRegistrar registrar) {
        registrar.register(new ProviderTestCommand())
    }
}

class ProviderTestCommand implements ApplicationCommand {

    @Override
    String getName() {
        'provider-test'
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        true
    }
}

class ModernTestCommand implements ApplicationCommand {

    @Override
    String getName() {
        'modern-test'
    }

    @Override
    String getDescription() {
        'Modern test command'
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        true
    }
}

class ConstructorCountingApplicationCommand implements ApplicationCommand {

    static int constructorCalls

    ConstructorCountingApplicationCommand() {
        constructorCalls++
    }

    @Override
    String getName() {
        'counting-modern'
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        true
    }
}

class SharedCountingApplicationCommand implements ApplicationCommand {

    static int constructorCalls

    SharedCountingApplicationCommand() {
        constructorCalls++
    }

    @Override
    String getName() {
        'counting-shared'
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        true
    }
}

class AUnorderedCollisionCommand implements ApplicationCommand {

    @Override
    String getName() {
        'ordered-collision'
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        true
    }
}

class ZOrderedCollisionCommand implements ApplicationCommand, Ordered {

    @Override
    int getOrder() {
        Ordered.HIGHEST_PRECEDENCE
    }

    @Override
    String getName() {
        'ordered-collision'
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        true
    }
}

class ConstructorCountingCommandProvider implements ApplicationCommandProvider {

    static int constructorCalls

    ConstructorCountingCommandProvider() {
        constructorCalls++
    }

    @Override
    void contributeCommands(ClassLoader registryClassLoader, ClassLoader contextClassLoader, ApplicationCommandRegistrar registrar) {
    }
}

class LinkageErrorCommandProvider implements ApplicationCommandProvider {

    LinkageErrorCommandProvider() {
        throw new NoClassDefFoundError('simulated missing provider dependency')
    }

    @Override
    void contributeCommands(ClassLoader registryClassLoader, ClassLoader contextClassLoader, ApplicationCommandRegistrar registrar) {
    }
}

class DirectFatalCommandProvider implements ApplicationCommandProvider {

    static Throwable failure

    @Override
    void contributeCommands(ClassLoader registryClassLoader, ClassLoader contextClassLoader, ApplicationCommandRegistrar registrar) {
        throw failure
    }
}

class ConstructorFatalCommandProvider implements ApplicationCommandProvider {

    static Throwable failure

    ConstructorFatalCommandProvider() {
        throw failure
    }

    @Override
    void contributeCommands(ClassLoader registryClassLoader, ClassLoader contextClassLoader, ApplicationCommandRegistrar registrar) {
    }
}

class DiagnosticSupportCommandProvider implements ApplicationCommandFactoryKeyProvider {

    @Override
    Collection<String> getHandledFactoryKeys() {
        ['grails.dev.commands.ApplicationCommand']
    }

    @Override
    void contributeCommands(ClassLoader registryClassLoader, ClassLoader contextClassLoader, ApplicationCommandRegistrar registrar) {
    }
}

class ThrowingDiagnosticSupportCommandProvider extends DiagnosticSupportCommandProvider {

    @Override
    void contributeCommands(ClassLoader registryClassLoader, ClassLoader contextClassLoader, ApplicationCommandRegistrar registrar) {
        throw new IllegalStateException('boom')
    }
}

@CompileStatic
class TrackingFactoryClassLoader extends URLClassLoader {

    private final boolean duplicateResources
    private final Map<String, Throwable> loadFailures
    private boolean legacyCommandClassLoaded

    TrackingFactoryClassLoader(
            URL[] urls,
            ClassLoader parent,
            boolean duplicateResources,
            Map<String, Throwable> loadFailures) {
        super(urls, parent)
        this.duplicateResources = duplicateResources
        this.loadFailures = loadFailures
    }

    @Override
    Enumeration<URL> getResources(String name) throws IOException {
        List<URL> resources = Collections.list(super.getResources(name))
        if (duplicateResources && name == 'META-INF/grails.factories') {
            resources.addAll(new ArrayList<>(resources))
        }
        Collections.enumeration(resources)
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Throwable failure = loadFailures[name]
        if (failure != null) {
            throw failure
        }
        if (name.startsWith('missing.')) {
            legacyCommandClassLoaded = true
        }
        super.loadClass(name, resolve)
    }

    boolean isLegacyCommandClassLoaded() {
        legacyCommandClassLoaded
    }
}

/**
 * Defines {@link ApplicationContextCommandRegistry} (and its generated inner classes) itself, so
 * that the registry's own classloader sees the factory jars handed to this loader. The
 * package-scoped {@link ApplicationCommandDiagnostics} collaborator must be defined alongside it,
 * since package-private access does not cross classloaders. Everything else - including the
 * command classes the factories declare - delegates to the parent test classpath, which is what
 * makes the same command Class reachable through two distinct classloaders.
 */
@CompileStatic
class RegistryDefiningClassLoader extends URLClassLoader {

    private static final List<String> CHILD_FIRST_CLASSES =
            [ApplicationContextCommandRegistry.name, ApplicationCommandDiagnostics.name].asImmutable()

    RegistryDefiningClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent)
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Deliberately free of closures and other Groovy runtime dispatch: this runs in the middle
        // of classloading, where triggering metaclass initialization recurses into this method.
        if (childFirst(name)) {
            Class<?> loaded = findLoadedClass(name)
            if (loaded == null) {
                byte[] classBytes = readParentClassBytes(name)
                loaded = defineClass(name, classBytes, 0, classBytes.length)
            }
            if (resolve) {
                resolveClass(loaded)
            }
            return loaded
        }
        super.loadClass(name, resolve)
    }

    private byte[] readParentClassBytes(String name) throws ClassNotFoundException {
        InputStream input = parent.getResourceAsStream(name.replace('.', '/') + '.class')
        if (input == null) {
            throw new ClassNotFoundException(name)
        }
        try {
            input.readAllBytes()
        }
        finally {
            input.close()
        }
    }

    private static boolean childFirst(String name) {
        for (String childFirstClass : CHILD_FIRST_CLASSES) {
            if (name == childFirstClass || name.startsWith(childFirstClass + '$')) {
                return true
            }
        }
        false
    }
}

@CompileStatic
class ThrowingFactoryResourcesClassLoader extends URLClassLoader {

    ThrowingFactoryResourcesClassLoader(ClassLoader parent) {
        super([] as URL[], parent)
    }

    @Override
    Enumeration<URL> getResources(String name) throws IOException {
        if (name == 'META-INF/grails.factories') {
            throw new AssertionError('unavailable')
        }
        super.getResources(name)
    }
}

@CompileStatic
class FixedFactoryResourceClassLoader extends URLClassLoader {

    private final URL factoryResource

    FixedFactoryResourceClassLoader(URL factoryResource, ClassLoader parent) {
        super([] as URL[], parent)
        this.factoryResource = factoryResource
    }

    @Override
    Enumeration<URL> getResources(String name) throws IOException {
        name == 'META-INF/grails.factories' ?
                Collections.enumeration([factoryResource]) : super.getResources(name)
    }
}

@CompileStatic
class FixedFactoryResourceUrlStreamHandler extends URLStreamHandler {

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        new URLConnection(url) {
            @Override
            void connect() {
            }

            @Override
            InputStream getInputStream() {
                new ByteArrayInputStream(
                        'grails.dev.commands.ApplicationCommand=missing.LegacyCommand'.getBytes('UTF-8'))
            }
        }
    }
}
