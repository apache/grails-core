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
package org.grails.compiler.injection

import java.nio.file.Files

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Specification
import spock.lang.TempDir
import spock.util.environment.RestoreSystemProperties

import grails.util.BuildSettings

import org.apache.grails.core.plugins.PluginUtils

class PluginDescriptorTransformationSpec extends Specification {

    @TempDir
    File project

    void 'plain Groovy compilation discovers both plugins and reconciles removed classes on recompilation'() {
        given:
        File output = new File(project, 'classes')
        File sources = new File(project, 'src/test/groovy')
        sources.mkdirs()
        File alpha = new File(sources, 'AlphaGrailsPlugin.groovy')
        alpha.text = '@grails.plugins.metadata.GrailsPluginDescriptor class AlphaGrailsPlugin extends grails.plugins.Plugin { }'
        File beta = new File(sources, 'BetaGrailsPlugin.groovy')
        beta.text = '@grails.plugins.metadata.GrailsPluginDescriptor class BetaGrailsPlugin extends grails.plugins.Plugin { }'
        writeService(output, 'SampleService')

        when:
        compile(output, alpha, beta)

        then:
        descriptors(output) == [alpha: ['SampleService'], beta: ['SampleService']]

        when: 'the incremental compiler driver removes outputs belonging to deleted sources'
        Files.delete(new File(output, 'AlphaGrailsPlugin.class').toPath())
        Files.delete(new File(output, 'SampleService.class').toPath())
        writeService(output, 'RenamedService')
        assert alpha.delete()
        compile(output, beta)

        then:
        def clean = new File(project, 'clean')
        writeService(clean, 'RenamedService')
        compile(clean, beta)
        descriptors(output) == descriptors(clean)
        descriptors(output) == [beta: ['RenamedService']]
        !new File(output, 'META-INF/grails/plugins/alpha/plugin.properties').exists()

        when:
        Files.delete(new File(output, 'BetaGrailsPlugin.class').toPath())
        File unrelated = new File(sources, 'Unrelated.groovy')
        unrelated.text = 'class Unrelated {}'
        compile(output, unrelated)

        then:
        descriptors(output).isEmpty()
        !new File(output, 'META-INF/grails/plugins.imports').exists()
        !new File(output, 'META-INF/grails/plugins/beta/plugin.properties').exists()
    }

    void 'a Plugin subclass is not a plugin unless annotated, whatever its name'() {
        given: 'helper plugins a specification declares beside itself, as test fixtures'
        File output = new File(project, 'classes')
        File sources = new File(project, 'src/test/groovy')
        sources.mkdirs()
        File spec = new File(sources, 'HelperSpec.groovy')
        spec.text = """
            class HelperSpec {}
            class HelperGrailsPlugin extends grails.plugins.Plugin { }
            @grails.plugins.metadata.GrailsPluginDescriptor(name = 'declared')
            class DeclaredGrailsPlugin extends grails.plugins.Plugin { }
            @grails.plugins.metadata.GrailsPluginDescriptor
            class NoSuffix extends grails.plugins.Plugin { }
        """

        when:
        compile(output, spec)

        then: 'only the annotated ones are registered, so the helper is not discovered by every test'
        descriptors(output).keySet() == ['declared', 'noSuffix'] as Set
        !new File(output, 'META-INF/grails/plugins/helper/plugin.properties').exists()
    }

    void 'an annotated class that is not a concrete Plugin fails the compilation: #description'() {
        given:
        File output = new File(project, 'classes')
        File source = new File(project, 'src/main/groovy/Broken.groovy')
        source.parentFile.mkdirs()
        source.text = declaration

        when:
        compile(output, source)

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('is annotated with @GrailsPluginDescriptor, so it must be a concrete subclass of grails.plugins.Plugin')

        and: 'no descriptor is written'
        !new File(output, 'META-INF/grails/plugins.imports').exists()

        where:
        description               | declaration
        'a class that is no Plugin' | '@grails.plugins.metadata.GrailsPluginDescriptor class BrokenGrailsPlugin { }'
        'an abstract Plugin'      | '@grails.plugins.metadata.GrailsPluginDescriptor abstract class BrokenGrailsPlugin extends grails.plugins.Plugin { }'
        'an interface'            | '@grails.plugins.metadata.GrailsPluginDescriptor interface BrokenGrailsPlugin { }'
    }

    void 'a plugin that declares doWithSpring fails the compilation: #description'() {
        given:
        File output = new File(project, 'classes')
        File source = new File(project, 'src/main/groovy/sources.groovy')
        source.parentFile.mkdirs()
        source.text = declaration

        when:
        compile(output, source)

        then: 'a plugin has no such hook, so the method would never be called'
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('HookGrailsPlugin declares doWithSpring, which a plugin no longer has')
        e.message.contains('beanRegistrar()')

        where:
        description                | declaration
        'the closure form'         | '''@grails.plugins.metadata.GrailsPluginDescriptor
            class HookGrailsPlugin extends grails.plugins.Plugin { Closure doWithSpring() { { -> } } }'''
        'the bean builder form'    | '''@grails.plugins.metadata.GrailsPluginDescriptor
            class HookGrailsPlugin extends grails.plugins.Plugin { void doWithSpring(grails.spring.BeanBuilder beans) { } }'''
        'an inherited declaration' | '''abstract class HookBase extends grails.plugins.Plugin { Closure doWithSpring() { { -> } } }
            @grails.plugins.metadata.GrailsPluginDescriptor
            class HookGrailsPlugin extends HookBase { }'''
    }

    void 'a plugin may declare beanRegistrar, the hook that replaces doWithSpring'() {
        given:
        File output = new File(project, 'classes')
        File source = new File(project, 'src/main/groovy/RegistrarGrailsPlugin.groovy')
        source.parentFile.mkdirs()
        source.text = '''
            @grails.plugins.metadata.GrailsPluginDescriptor
            class RegistrarGrailsPlugin extends grails.plugins.Plugin {
                org.springframework.beans.factory.BeanRegistrar beanRegistrar() { null }
            }
        '''

        when:
        compile(output, source)

        then:
        descriptors(output).keySet() == ['registrar'] as Set
    }

    @RestoreSystemProperties
    void 'the descriptor records the build version and resolves ${plugin.version} in dependencies'() {
        given:
        System.setProperty(BuildSettings.PLUGIN_VERSION, '7.8.9')
        File output = new File(project, 'classes')
        File source = new File(project, 'src/main/groovy/VersionedGrailsPlugin.groovy')
        source.parentFile.mkdirs()
        source.text = '''
            @grails.plugins.metadata.GrailsPluginDescriptor(dependsOn = ['sibling:${plugin.version}', 'other'])
            class VersionedGrailsPlugin extends grails.plugins.Plugin { }
        '''

        when:
        compile(output, source)
        def definition = definitionsOf(output).versioned

        then:
        definition.pluginVersion == '7.8.9'
        definition.pluginClass == 'VersionedGrailsPlugin'
        definition.dependsOn == [sibling: '7.8.9', other: '*']
    }

    void 'a plugin compiled without a build version records none'() {
        given:
        File output = new File(project, 'classes')
        File source = new File(project, 'src/test/groovy/UnversionedGrailsPlugin.groovy')
        source.parentFile.mkdirs()
        source.text = '@grails.plugins.metadata.GrailsPluginDescriptor class UnversionedGrailsPlugin extends grails.plugins.Plugin { }'

        when:
        compile(output, source)

        then:
        definitionsOf(output).unversioned.pluginVersion == null
        !new File(output, PluginUtils.getPluginDescriptorPath('unversioned')).text.readLines().any { it.startsWith('version=') }
    }

    void 'javac discovers an annotated Java plugin without Gradle'() {
        given:
        File source = new File(project, 'JavaPlugin.java')
        source.text = '@grails.plugins.metadata.GrailsPluginDescriptor(name="java-probe") public class JavaPlugin extends grails.plugins.Plugin {}'
        File output = new File(project, 'java-classes')
        output.mkdirs()
        File clean = new File(project, 'clean-java')
        clean.mkdirs()

        when:
        compileJava(output, source)

        then:
        descriptors(output) == [javaProbe: []]
        new File(output, 'META-INF/grails/plugins/javaProbe/plugin.properties').text.readLines().contains('version=3.0')

        when: 'the last annotated class is recompiled as an ordinary class'
        source.text = 'public class JavaPlugin extends grails.plugins.Plugin {}'
        compileJava(output, source)
        compileJava(clean, source)

        then:
        descriptors(output) == descriptors(clean)
        descriptors(output).isEmpty()
        !new File(output, 'META-INF/grails/plugins.imports').exists()
        !new File(output, 'META-INF/grails/plugins/javaProbe/plugin.properties').exists()
    }

    void 'javac rejects an annotated Java class that is not a Plugin'() {
        given:
        File source = new File(project, 'JavaPlugin.java')
        source.text = '@grails.plugins.metadata.GrailsPluginDescriptor(name="java-probe") public class JavaPlugin {}'
        File output = new File(project, 'java-classes')
        output.mkdirs()

        expect:
        !compileJavaTask(output, source).call()
        !new File(output, 'META-INF/grails/plugins.imports').exists()
    }

    void 'javac rejects a Java plugin that declares doWithSpring'() {
        given:
        File source = new File(project, 'JavaHookPlugin.java')
        source.text = '@grails.plugins.metadata.GrailsPluginDescriptor(name="java-hook") public class JavaHookPlugin extends ' +
                'grails.plugins.Plugin { public groovy.lang.Closure doWithSpring() { return null; } }'
        File output = new File(project, 'java-classes')
        output.mkdirs()
        StringWriter messages = new StringWriter()

        expect:
        !compileJavaTask(output, source, messages).call()
        messages.toString().contains('JavaHookPlugin declares doWithSpring, which a plugin no longer has')
    }

    private static void compileJava(File output, File source) {
        assert compileJavaTask(output, source).call()
    }

    private static javax.tools.JavaCompiler.CompilationTask compileJavaTask(File output, File source,
                                                                          Writer messages = new StringWriter()) {
        def compiler = javax.tools.ToolProvider.systemJavaCompiler
        String classpath = System.getProperty('java.class.path')
        def files = compiler.getStandardFileManager(null, null, null)
        def task = compiler.getTask(messages, files, null, ['-classpath', classpath, '-d', output.path,
                '-Agrails.plugin.version=3.0'], null, files.getJavaFileObjects(source))
        task.processors = [new JavaPluginDescriptorProcessor()]
        task
    }

    private void writeService(File output, String name) {
        File source = new File(project, name + '.java')
        source.text = "@grails.artefact.Artefact(\"Service\") public class ${name} {}"
        output.mkdirs()
        String classpath = new File(grails.artefact.Artefact.protectionDomain.codeSource.location.toURI()).path
        assert javax.tools.ToolProvider.systemJavaCompiler.run(null, null, null,
                '-proc:none', '-classpath', classpath, '-d', output.path, source.path) == 0
    }

    private static void compile(File output, File... sources) {
        def unit = new CompilationUnit(new CompilerConfiguration(targetDirectory: output))
        unit.addSources(sources)
        unit.compile()
    }

    private static Map<String, grails.plugins.metadata.PluginDefinition> definitionsOf(File output) {
        new URLClassLoader([output.toURI().toURL()] as URL[], (ClassLoader) null).withCloseable { loader ->
            PluginUtils.scanPluginDescriptorResources(loader).values().collectEntries {
                [(it.pluginName): it]
            }
        }
    }

    private static Map descriptors(File output) {
        new URLClassLoader([output.toURI().toURL()] as URL[], (ClassLoader) null).withCloseable { loader ->
            PluginUtils.scanPluginDescriptorResources(loader).values().collectEntries {
                [(it.pluginName): it.providedClasses]
            }
        }
    }
}
