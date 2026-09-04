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

import groovy.xml.XmlSlurper
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer

import ch.qos.logback.classic.Level
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir
import spock.util.environment.RestoreSystemProperties

import grails.artefact.Artefact
import grails.plugins.metadata.GrailsPlugin
import grails.util.GrailsNameUtils
import org.apache.grails.common.compiler.GroovyTransformOrder
import org.apache.grails.core.testing.support.LogCapture

class GlobalGrailsClassInjectorTransformationSpec extends Specification {

    @TempDir
    File tempDir

    @Subject
    def transformation = new GlobalGrailsClassInjectorTransformation()

    def setup() {
        // Set zero trait injectors before each test to avoid cross-test contamination
        TraitInjectionUtils.@traitInjectors = []
    }

    def cleanup() {
        // Reset the trait injectors after each test to avoid cross-test contamination
        TraitInjectionUtils.@traitInjectors = null
    }

    void "a correct plugin xml file is generated when the plugin xml doesn't exist"() {
        given: "a file that doesn't yet exist"
            def pluginXml = new File(tempDir, 'plugin-xml-gen-test.test.xml')

        and: "the class node for a plugin descriptor"
            def classNode = compilePlugin('class FooGrailsPlugin {}')

        expect: "the file doesn't exist"
            !pluginXml.exists()

        when: "the transformation generates the xml file"
            transformation.generatePluginXml(
                    classNode,
                    '1.0',
                    ['Foo'] as Set,
                    pluginXml
            )

        then: "the file exists"
            pluginXml.exists()

        when: "the xml is parsed"
            def xml = new XmlSlurper().parse(pluginXml)

        then: "the generated xml is valid"
            xml.@name.text() == 'foo'
            xml.type.text() == 'FooGrailsPlugin'
            xml.resources.size() == 1
            xml.resources.resource.text() == 'Foo'
    }

    void "a correct plugin xml file is updated when the plugin xml does exist"() {
        given: "an existing file"
            def pluginXml = File.createTempFile('plugin-xml-gen-test', '.test.xml', tempDir)
            def classNode = compilePlugin('class BarGrailsPlugin {}')
            pluginXml.text = '''
                <plugin name="foo">
                    <type>FooGrailsPlugin</type>
                    <resources>
                        <resource>Foo</resource>
                        <resource>Bar</resource>
                        <resource>Baz</resource>
                    </resources>
                </plugin>
            '''

        when: "the transformation generates the xml"
            transformation.generatePluginXml(
                    classNode,
                    '1.0',
                    ['Foo', 'Bar'] as Set,
                    pluginXml
            )

        then: "the file still exists"
            pluginXml.exists()

        when: "the xml is parsed"
            def xml = new XmlSlurper().parse(pluginXml)

        then: "the generated plugin.xml is valid"
            xml.@name.text() == 'bar'
            xml.type.text() == 'BarGrailsPlugin'
            xml.resources.resource.size() == 3
            xml.resources.resource.text() == 'FooBarBaz'
    }

    def "resolveGrailsVersionWithFrameworkVersion prefers declared plugin metadata"() {
        expect:
            GlobalGrailsClassInjectorTransformation.resolveGrailsVersionWithFrameworkVersion(
                    [grailsVersion: '3.0 > *'],
                    '8.0.0'
            ) == '3.0 > *'
    }

    def "resolveGrailsVersionWithFrameworkVersion formats the framework version when plugin metadata is absent"() {
        expect:
            GlobalGrailsClassInjectorTransformation.resolveGrailsVersionWithFrameworkVersion([:], '8.0.0') == '8.0.0 > *'
    }

    def "resolveGrailsVersionWithFrameworkVersion returns null when both plugin and framework metadata are absent"() {
        expect:
            GlobalGrailsClassInjectorTransformation.resolveGrailsVersionWithFrameworkVersion([:], null) == null
    }

    @RestoreSystemProperties
    void "isIsolatedBuild reflects the 'grails.isolated.build' system property"() {
        when:
            System.setProperty('grails.isolated.build', value)

        then:
            GlobalGrailsClassInjectorTransformation.isIsolatedBuild() == expected

        where:
            value   || expected
            'true'  || true
            'false' || false
            'TRUE'  || true
            'y'     || true
            'null'  || false
            '1'     || true
            '0'     || false
            '-1'    || false
            ''      || false
    }

    void "resolveCompilationTargetDirectory returns the configured target directory"() {
        given:
            def targetDir = new File(tempDir, 'isolated-target/build/classes/groovy/main')
            def source = sourceUnitWithTarget(targetDir)

        expect: "the configured directory is used regardless of build isolation"
            GlobalGrailsClassInjectorTransformation.resolveCompilationTargetDirectory(source, false) == targetDir
            GlobalGrailsClassInjectorTransformation.resolveCompilationTargetDirectory(source, true) == targetDir
    }

    void "resolveCompilationTargetDirectory falls back to the shared relative path for a non-isolated build"() {
        given: "a source unit without a configured target directory"
            def source = sourceUnitWithTarget(null)

        when:
            def resolvedDir = GlobalGrailsClassInjectorTransformation.resolveCompilationTargetDirectory(source, false)

        then: "the legacy relative fallback is used"
            resolvedDir == new File('build/classes/main')
    }

    void "resolveCompilationTargetDirectory fails fast instead of falling back for an isolated build"() {
        given: "a source unit without a configured target directory"
            def source = sourceUnitWithTarget(null)

        when: "the target directory cannot be resolved in an isolated build"
            GlobalGrailsClassInjectorTransformation.resolveCompilationTargetDirectory(source, true)

        then: "the build fails loudly rather than writing to a shared location"
            def e = thrown(IllegalStateException)
            e.message.contains('grails.isolated.build')
    }

    @RestoreSystemProperties
    void "findSourceDirectory prefers the per-project base.dir system property when set"() {
        given: "base.dir points at an existing directory"
            System.setProperty('base.dir', tempDir.absolutePath)
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when:
            def resolvedDir = FactoriesFileWriter.findSourceDirectory(targetDir)

        then: "the build-tool supplied base.dir wins"
            resolvedDir == tempDir
    }

    @RestoreSystemProperties
    void "findSourceDirectory walks up to the project directory when base.dir is not set"() {
        given: "no base.dir and a standard per-project compile target"
            System.clearProperty('base.dir')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when:
            def resolvedDir = FactoriesFileWriter.findSourceDirectory(targetDir)

        then: "it resolves to the parent of the build directory"
            resolvedDir == tempDir
    }

    void "priority returns the global grails transform order"() {
        expect:
            new GlobalGrailsClassInjectorTransformation().priority() == GroovyTransformOrder.GLOBAL_GRAILS_TRANSFORM_ORDER
    }

    void "the global transform ignores a source without a resolvable URL"() {
        when:
            new GlobalGrailsClassInjectorTransformation().visit([] as ASTNode[], Stub(SourceUnit) {
                getName() >> null
            })

        then:
            noExceptionThrown()
    }

    void "the global transform stamps a GrailsPlugin descriptor class with a version property"() {
        given: "a *GrailsPlugin class with no explicit version property, and nowhere for plugin.xml to exist yet"
            def sourceFile = new File(tempDir, 'PlainGrailsPlugin.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when: "the source is compiled, exercising the registered global transform"
            def classNode = compileToFile(
                    sourceFile,
                    'class PlainGrailsPlugin {}',
                    targetDir, [projectVersion: '1.5']
            )

        then: "the plugin class is stamped with the resolved version"
            classNode.getProperty('version') != null

        and: "the plugin.xml describing it is generated as a side effect"
            new File(targetDir, 'META-INF/grails-plugin.xml').exists()
    }

    void "the global transform resolves a plugin version declared on the plugin class"() {
        given: "a plugin descriptor with a declared version and no compiler project metadata"
            def sourceFile = new File(tempDir, 'DeclaredGrailsPlugin.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when:
            def classNode = compileToFile(
                    sourceFile,
                    '''
                        class DeclaredGrailsPlugin {
                            def version = '3.0'
                        }
                    ''',
                    targetDir
            )

        then:
            classNode.getProperty('version').initialExpression.text == '3.0'
            new File(targetDir, 'META-INF/grails-plugin.xml').exists()
    }

    void "the implicit beans convention leaves a plugin descriptor's unrelated beans property alone"() {
        given: "a descriptor whose beans property is not the DSL, as a pre-8.0 plugin's may well be"
            def sourceFile = new File(tempDir, 'UnrelatedBeansGrailsPlugin.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when: "it is compiled with the beans DSL on the classpath"
            def classNode = compileToFile(
                    sourceFile,
                    '''
                        class UnrelatedBeansGrailsPlugin {
                            def version = '1.0'
                            def beans = [someKey: 'someValue']
                        }
                    ''',
                    targetDir
            )

        then: "the property survives, rather than being claimed and failed as a malformed DSL block"
            classNode.getProperty('beans') != null
    }

    void "the implicit beans convention leaves a closure that is not DSL-shaped alone"() {
        given: "a descriptor with a beans closure of ordinary Groovy"
            def sourceFile = new File(tempDir, 'OtherClosureGrailsPlugin.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when:
            def classNode = compileToFile(
                    sourceFile,
                    '''
                        class OtherClosureGrailsPlugin {
                            def version = '1.0'
                            def beans = {
                                println 'not the DSL'
                            }
                        }
                    ''',
                    targetDir
            )

        then:
            classNode.getProperty('beans') != null
    }

    void "the implicit beans convention claims a DSL-shaped beans closure"() {
        given: "a descriptor whose beans closure is the DSL, chained qualifiers included"
            def sourceFile = new File(tempDir, 'DslBeansGrailsPlugin.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when:
            def classNode = compileToFile(
                    sourceFile,
                    '''
                        @org.springframework.boot.autoconfigure.AutoConfiguration
                        class DslBeansGrailsPlugin extends grails.plugins.Plugin {
                            def version = '1.0'
                            def beans = {
                                bean('greeting', String) { 'hello' }
                                bean('lazyGreeting', String).lazy() { 'later' }
                            }
                        }
                    ''',
                    targetDir
            )

        then: "the property is consumed by the transform, unlike the two cases above"
            classNode.getProperty('beans') == null

        and: "the name settled by the local transform is registered at the global transform's target"
            new File(targetDir,
                    'META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports').text.trim() ==
                    'DslBeansAutoConfiguration'
    }

    void "an explicitly annotated descriptor registers its sibling too"() {
        given: "the entry path the convention does not take: the local transform runs after this one"
            def sourceFile = new File(tempDir, 'AnnotatedBeansGrailsPlugin.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when:
            def classNode = compileToFile(
                    sourceFile,
                    '''
                        @grails.compiler.beans.GrailsBeans
                        @org.springframework.boot.autoconfigure.AutoConfiguration
                        class AnnotatedBeansGrailsPlugin extends grails.plugins.Plugin {
                            def version = '1.0'
                            def beans = {
                                bean('greeting', String) { 'hello' }
                            }
                        }
                    ''',
                    targetDir
            )

        then: "the annotation's own transform consumed the closure"
            classNode.getProperty('beans') == null

        and: "and registered the sibling, which is what silently did not happen before"
            new File(targetDir,
                    'META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports').text.trim() ==
                    'AnnotatedBeansAutoConfiguration'
    }

    void "the implicit beans convention claims the closure of a descriptor that is not a Plugin"() {
        given: "the descriptor names itself *GrailsPlugin but does not extend Plugin, so nothing is generated"
            def sourceFile = new File(tempDir, 'PlainDslBeansGrailsPlugin.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when:
            def classNode = compileToFile(
                    sourceFile,
                    '''
                        @org.springframework.boot.autoconfigure.AutoConfiguration
                        class PlainDslBeansGrailsPlugin {
                            def version = '1.0'
                            def beans = {
                                bean('greeting', String) { 'hello' }
                                bean('lazyGreeting', String).lazy() { 'later' }
                            }
                        }
                    ''',
                    targetDir
            )

        then: "the closure is still compiled, onto the class itself rather than onto a sibling"
            classNode.getProperty('beans') == null

        and: "only the sibling generated for a plugin descriptor is registered, and there is none here"
            !new File(targetDir,
                    'META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports').exists()
    }

    void "a generated class missing from a hand-authored imports file is reported"() {
        given: "a hand-authored file listing something else, and a descriptor whose sibling is not in it"
            def targetDir = new File(tempDir, 'build/classes/groovy/main')
            def handAuthored = new File(tempDir,
                    'src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports')
            handAuthored.parentFile.mkdirs()
            handAuthored.text = 'com.elsewhere.FromAnotherJar\n'

        when: "it compiles"
            def warnings = compileCollectingWarnings(
                    new File(tempDir, 'WarnOnceGrailsPlugin.groovy'),
                    '''
                        @org.springframework.boot.autoconfigure.AutoConfiguration
                        class WarnOnceGrailsPlugin extends grails.plugins.Plugin {
                            def version = '1.0'
                            def beans = {
                                bean('greeting', String) { 'hello' }
                            }
                        }
                    ''',
                    targetDir
            )

        then: "the entry that has to be added by hand is named"
            warnings.any { it.contains('WarnOnceAutoConfiguration') && it.contains('AutoConfiguration.imports') }

        and: "and the module's own file is left as the only one"
            !new File(targetDir,
                    'META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports').exists()
    }

    void "the implicit beans convention claims an application class's DSL-shaped beans closure"() {
        given: "an application class where a generated project puts it, carrying no @GrailsBeans"
            def sourceFile = new File(tempDir, 'grails-app/init/Application.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when:
            def classNode = compileToFile(
                    sourceFile,
                    '''
                        class Application extends grails.boot.config.GrailsAutoConfiguration {
                            def beans = {
                                bean('greeting', String) { 'hello' }
                            }
                        }
                    ''',
                    targetDir
            )

        then: "the property is consumed, so an application declares beans without annotating anything"
            classNode.getProperty('beans') == null
    }

    void "a stray statement among real declarations fails an application class rather than silently registering nothing"() {
        given: "an application whose beans block has one statement that is not a declaration - a typo, here"
            def sourceFile = new File(tempDir, 'grails-app/init/Application.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when:
            compileToFile(
                    sourceFile,
                    """
                        class Application extends grails.boot.config.GrailsAutoConfiguration {
                            def beans = {
                                bean('greeting', String) { 'hello' }
                                bea('typo', String) { 'oops' }
                                bean('farewell', String) { 'bye' }
                            }
                        }
                    """,
                    targetDir
            )

        then: "the build fails, naming what to do, instead of dropping all three declarations"
            MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
            e.message.contains('not a bean(...), field(...) or method(...) declaration')
            e.message.contains('must be one of those three')
    }

    void "an if wrapped around beans is reported, since the beans inside it would register nothing"() {
        given: "the shape a conditional-registration attempt takes"
            def sourceFile = new File(tempDir, 'grails-app/init/Application.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when:
            compileToFile(
                    sourceFile,
                    """
                        class Application extends grails.boot.config.GrailsAutoConfiguration {
                            def beans = {
                                bean('greeting', String) { 'hello' }
                                if (System.getProperty('dev')) {
                                    bean('devOnly', String) { 'dev' }
                                }
                            }
                        }
                    """,
                    targetDir
            )

        then: "it points at the qualifier that does express a condition"
            MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
            e.message.contains('ConditionalOnProperty')
    }

    void "a plugin descriptor with a stray statement fails the same way an application class does"() {
        given: "a descriptor is compiled by the plugin author, but its missing beans are felt downstream"
            def sourceFile = new File(tempDir, 'StrayBeansGrailsPlugin.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when:
            compileToFile(
                    sourceFile,
                    """
                        class StrayBeansGrailsPlugin {
                            def version = '1.0'
                            def beans = {
                                bean('greeting', String) { 'hello' }
                                println 'not a declaration'
                            }
                        }
                    """,
                    targetDir
            )

        then: "no leniency for being a descriptor - the severity must not depend on the class name"
            MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
            e.message.contains('not a bean(...), field(...) or method(...) declaration')

        and: "and the message names the way out for a beans property that genuinely is not the DSL"
            e.message.contains('rename it')
    }

    void "a beans closure with no declarations at all stays silent, being an unrelated property"() {
        given: "the case the all-or-nothing claim exists to protect"
            def sourceFile = new File(tempDir, 'grails-app/init/Application.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when:
            def classNode = compileToFile(
                    sourceFile,
                    """
                        class Application extends grails.boot.config.GrailsAutoConfiguration {
                            def beans = {
                                println 'not the DSL'
                                System.currentTimeMillis()
                            }
                        }
                    """,
                    targetDir
            )

        then: "no diagnostic - nothing here claims to be a declaration"
            noExceptionThrown()
            classNode.getProperty('beans') != null
    }

    void "the global transform fails when a plugin descriptor class has no version"() {
        given: "a plugin descriptor class without a declared or compiler-provided version"
            def sourceFile = new File(tempDir, 'UnversionedGrailsPlugin.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when:
            compileToFile(sourceFile, 'class UnversionedGrailsPlugin {}', targetDir)

        then: "the compilation fails with a clear message and no xml file is generated"
            def exception = thrown(MultipleCompilationErrorsException)
            exception.message.contains('does not define a plugin version')
            !new File(targetDir, 'META-INF/grails-plugin.xml').exists()
    }

    void "the global transform annotates a plain Grails resource class with @GrailsPlugin metadata"() {
        given: "a class under grails-app that isn't matched by any registered ArtefactHandler"
            def sourceFile = new File(tempDir, 'grails-app/services/FooWidget.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when: "the source is compiled, exercising the registered global transform"
            def classNode = compileToFile(
                    sourceFile,
                    'class FooWidget {}',
                    targetDir,
                    [projectName: 'foowidget', projectVersion: '2.0']
            )

        then: "the class is stamped with the project's @GrailsPlugin metadata"
            def annotations = classNode.getAnnotations(ClassHelper.make(GrailsPlugin))
            annotations.size() == 1
            with(annotations.first()) {
                getMember('name').text == GrailsNameUtils.getPropertyNameForLowerCaseHyphenSeparatedName('foowidget')
                getMember('version').text == '2.0'
            }
    }

    void "the global transform processes a Grails service as an artefact"() {
        given: "a service source under grails-app"
            def sourceFile = new File(tempDir, 'grails-app/services/FooService.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when:
            def classNode = compileToFile(
                    sourceFile,
                    'class FooService {}',
                    targetDir
            )

        then:
            with(classNode.getAnnotations(ClassHelper.make(Artefact))) {
                size() == 1
                first().getMember('value').text == 'Service'
            }
    }

    void "the global transform registers a concrete artefact handler in grails.factories"() {
        given: "an artefact handler source"
            def sourceFile = new File(tempDir, 'src/main/groovy/TestHandler.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when:
            compileToFile(
                    sourceFile,
                    '''
                        class TestHandler extends grails.core.ArtefactHandlerAdapter {
                            TestHandler() {
                                super('Test', null, null, 'Handler')
                            }
                        }
                    ''',
                    targetDir
            )

        then:
            def factories = new File(targetDir, 'META-INF/grails.factories')
            factories.exists()
            factories.text.contains('TestHandler')
    }

    void "the global transform skips classes whose source falls outside the Grails resource patterns"() {
        given: "a plain source under src/main/groovy, which is project source but not a Grails resource"
            def sourceFile = new File(tempDir, 'src/main/groovy/PlainClass.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when: "the source is compiled, exercising the registered global transform"
            def classNode = compileToFile(
                    sourceFile,
                    'class PlainClass {}',
                    targetDir,
                    [projectName: 'plain', projectVersion: '1.0']
            )

        then: "no @GrailsPlugin metadata is stamped on the class"
            classNode.getAnnotations(ClassHelper.make(GrailsPlugin)).empty
    }

    void "plugin xml excludes are honoured and metadata refreshed when updating an existing file"() {
        given: "an existing plugin.xml with a resource that the plugin now wants excluded"
            def pluginXml = File.createTempFile('plugin-xml-excludes', 'test.xml', tempDir)
            def classNode = null
            def cu = new CompilationUnit(new GroovyClassLoader())
            cu.addSource('BazGrailsPlugin', '''
                class BazGrailsPlugin {
                    def pluginExcludes = ['Excluded*']
                    def grailsVersion = '3.0 > *'
                }
            ''')
            cu.addPhaseOperation({ SourceUnit source, GeneratorContext context, ClassNode cn ->
                if (cn.name.endsWith('GrailsPlugin')) {
                    classNode = cn
                }
            } as CompilationUnit.IPrimaryClassNodeOperation, Phases.CONVERSION)
            cu.compile(Phases.CONVERSION)
            pluginXml.text = '''
                <plugin name="baz" version="1.0" grailsVersion="1.0 > *">
                    <type>BazGrailsPlugin</type>
                    <resources>
                        <resource>ExcludedThing</resource>
                        <resource>ExistingThing</resource>
                    </resources>
                </plugin>
            '''

        when: "the transformation updates the plugin.xml"
            transformation.generatePluginXml(
                    classNode,
                    '2.0',
                    ['ExcludedThing', 'NewThing'] as Set,
                    pluginXml
            )

        then: "the file exists"
            pluginXml.exists()

        when: "the xml is parsed"
            def xml = new XmlSlurper().parse(pluginXml)

        then: "the excluded resource was removed, the kept resource was added, and metadata was refreshed"
            xml.@version.text() == '2.0'
            xml.@grailsVersion.text() == '3.0 > *'
            xml.resources.resource*.text() == ['ExistingThing', 'NewThing']
    }

    void "plugin xml excludes are applied when writing a new descriptor"() {
        given:
            def pluginXml = new File(tempDir, 'plugin-xml-write-excludes.xml')
            def classNode = compilePlugin('''
                class WrittenExcludesGrailsPlugin {
                    def pluginExcludes = ['Excluded*']
                    def grailsVersion = '4.0 > *'
                }
            ''')

        when:
            transformation.generatePluginXml(
                    classNode,
                    '1.0',
                    ['ExcludedThing', 'KeptThing'] as Set,
                    pluginXml
            )

        then:
            new XmlSlurper().parse(pluginXml).resources.resource*.text() == ['KeptThing']
    }

    void "excludes recorded by the descriptor still apply to a later source unit"() {
        given: "an existing descriptor listing a resource the plugin excludes"
            def pluginXml = new File(tempDir, 'carry-over-plugin.xml')
            def seed = '''
                <plugin name="carryOver" version="1.0" grailsVersion="1.0 > *">
                    <type>CarryOverGrailsPlugin</type>
                    <resources>
                        <resource>ExcludedThing</resource>
                        <resource>KeptThing</resource>
                    </resources>
                </plugin>
            '''
            pluginXml.text = seed
            def classNode = compilePlugin('''
                class CarryOverGrailsPlugin {
                    def pluginExcludes = ['Excluded*']
                }
            ''')

        when: "source unit 1 is the plugin descriptor"
        transformation.generatePluginXml(classNode, '1.0', ['KeptThing'] as Set, pluginXml)

        then: "the excludes are honoured and the kept resource is recorded"
        new XmlSlurper().parse(pluginXml).resources.resource*.text() == ['KeptThing']

        when: "source unit 2 is an ordinary artefact source"
        pluginXml.text = seed
        transformation.generatePluginXml(null, null, ['AnotherThing'] as Set, pluginXml)

        then: "the excludes recorded in source unit 1 are still honoured"
        new XmlSlurper().parse(pluginXml).resources.resource*.text() == ['KeptThing', 'AnotherThing']
    }

    void "plugin xml resources are updated when an existing descriptor has no plugin class"() {
        given:
            def pluginXml = new File(tempDir, 'existing-plugin.xml')
            pluginXml.text = '''
                <plugin>
                    <resources>
                        <resource>ExistingThing</resource>
                    </resources>
                </plugin>
            '''

        when:
            transformation.generatePluginXml(
                    null,
                    null,
                    ['NewThing'] as Set,
                    pluginXml
            )

        then:
            new XmlSlurper().parse(pluginXml).resources.resource*.text() == ['ExistingThing', 'NewThing']
    }

    void "plugin xml metadata and excludes are updated when no new artefacts are discovered"() {
        given:
            def pluginXml = new File(tempDir, 'metadata-only-plugin.xml')
            pluginXml.text = '''
                <plugin name="oldName" version="1.0" grailsVersion="1.0 > *">
                    <type>OldGrailsPlugin</type>
                    <resources>
                        <resource>ExcludedThing</resource>
                        <resource>KeptThing</resource>
                    </resources>
                </plugin>
            '''
            def classNode = compilePlugin('''
                class UpdatedGrailsPlugin {
                    def grailsVersion = '3.0 > *'
                    def pluginExcludes = ['Excluded*']
                }
            ''')

        when:
            transformation.updatePluginXml(classNode, '2.0', pluginXml, [])

        then:
            def xml = new XmlSlurper().parse(pluginXml)
            xml.@name.text() == 'updated'
            xml.@version.text() == '2.0'
            xml.@grailsVersion.text() == '3.0 > *'
            xml.type.text() == 'UpdatedGrailsPlugin'
            xml.resources.resource*.text() == ['KeptThing']
    }

    void "plugin xml update recreates safely when the existing descriptor is malformed"() {
        given:
            def logCapture = new LogCapture(GlobalGrailsClassInjectorTransformation, Level.WARN)

        and: 'a malformed plugin.xml that cannot be parsed'
            def pluginXml = new File(tempDir, 'malformed-plugin.xml')
            pluginXml.text = '<plugin><resources>'

        when: 'the transformation attempts to update the malformed plugin.xml'
            transformation.updatePluginXml(null, null, pluginXml, ['Foo'])

        then: 'the corrupt descriptor is removed, names are deferred, and a warning is logged'
            noExceptionThrown()
            !pluginXml.exists()
            transformation.generatePluginXml(
                    compilePlugin('class RecoveredGrailsPlugin {}'),
                    '1.0',
                    [] as Set,
                    pluginXml
            )
            def recoveredXml = new XmlSlurper().parse(pluginXml)
            recoveredXml.resources.resource*.text() == ['Foo']
            logCapture.events.size() == 1
            with(logCapture.events[0]) {
                level == Level.WARN
                formattedMessage == "Failed to update existing file ${pluginXml.absolutePath}. Recreating it instead..."
            }

        cleanup:
            logCapture.close()
    }

    void "plugin xml update recreates malformed descriptors with available plugin metadata"() {
        given:
            def logCapture = new LogCapture(GlobalGrailsClassInjectorTransformation, Level.WARN)
            def pluginXml = new File(tempDir, 'malformed-plugin-with-metadata.xml')
            pluginXml.text = '<plugin><resources>'
            def classNode = compilePlugin('''
                class RecoveryGrailsPlugin {
                    def grailsVersion = '3.0 > *'
                }
            ''')

        when: 'the transformation updates the malformed descriptor with plugin metadata'
            transformation.updatePluginXml(classNode, '2.0', pluginXml, ['RecoveryService'])

        then: 'a warning is logged and the descriptor is recreated from the plugin class'
            logCapture.events.size() == 1
            with(logCapture.events[0]) {
                level == Level.WARN
                formattedMessage == "Failed to update existing file ${pluginXml.absolutePath}. Recreating it instead..."
            }
            def xml = new XmlSlurper().parse(pluginXml)
            xml.@name.text() == 'recovery'
            xml.@version.text() == '2.0'
            xml.@grailsVersion.text() == '3.0 > *'
            xml.type.text() == 'RecoveryGrailsPlugin'
            xml.resources.resource*.text() == ['RecoveryService']

        cleanup:
            logCapture.close()
    }

    void "artefact class names are deferred and then included when the plugin descriptor is compiled later"() {
        given: 'a plugin.xml file that does not exist yet'
            def pluginXml = new File(tempDir, 'deferred-plugin.xml')

        and: 'no plugin descriptor class has been compiled yet'
            def classNode = compilePlugin('class DeferredGrailsPlugin {}')

        when: 'generatePluginXml is called with artefact classes but no plugin class or existing xml'
            transformation.generatePluginXml(
                    null,
                    null,
                    ['FirstDeferredClass'] as Set,
                    pluginXml
            )

        then: 'no xml file is written because there is no plugin descriptor'
            !pluginXml.exists()

        when: 'the plugin descriptor is compiled and generatePluginXml is called again'
            transformation.generatePluginXml(
                    classNode,
                    '1.0',
                    ['SecondClass'] as Set,
                    pluginXml
            )

        then: 'the xml now includes both the deferred class and the new class'
            pluginXml.exists()
            def xml = new XmlSlurper().parse(pluginXml)
            xml.@name.text() == 'deferred'
            xml.resources.resource*.text() as Set == ['FirstDeferredClass', 'SecondClass'] as Set
    }

    void "updatePluginXml returns early and leaves the file unchanged when artefact list is empty"() {
        given:
            def pluginXml = new File(tempDir, 'empty-artefacts-plugin.xml')
            pluginXml.text = '<plugin name="test" version="1.0"/>'

        when:
            transformation.updatePluginXml(null, null, pluginXml, [])

        then:
            def xml = new XmlSlurper().parse(pluginXml)
            xml.@name.text() == 'test'
            xml.@version.text() == '1.0'
    }

    void "abstract GrailsPlugin class is not treated as a plugin descriptor so artefact names are deferred"() {
        given:
            def classNode = compilePlugin('abstract class AbstractGrailsPlugin {}')
            def pluginXml = new File(tempDir, 'abstract-grails-plugin.xml')
            String version = null

        when: 'called with an abstract plugin class'
            transformation.generatePluginXml(classNode, version, ['DeferredArtefact'] as Set, pluginXml)

        then: 'no xml is written because the abstract class is skipped'
            !pluginXml.exists()

        when: 'a concrete plugin descriptor is compiled and generatePluginXml is called again'
            def concreteClass = compilePlugin('class ConcreteGrailsPlugin {}')
            transformation.generatePluginXml(concreteClass, '1.0', ['NewArtefact'] as Set, pluginXml)

        then: 'both deferred and new artefacts appear in the generated xml'
            pluginXml.exists()
            def xml = new XmlSlurper().parse(pluginXml)
            xml.resources.resource*.text() as Set == ['DeferredArtefact', 'NewArtefact'] as Set
    }

    void "artefact class that already has @Artefact annotation is not re-annotated or double-registered"() {
        given: 'a service source under grails-app that already carries @Artefact'
            def sourceFile = new File(tempDir, 'grails-app/services/AnnotatedService.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when:
            def classNode = compileToFile(
                    sourceFile,
                    '''
                        import grails.artefact.Artefact
                        @Artefact('Service')
                        class AnnotatedService {}
                    ''',
                    targetDir
            )

        then: 'the class still has exactly one @Artefact annotation'
            classNode.getAnnotations(ClassHelper.make(Artefact)).size() == 1
    }

    void "abstract GrailsPlugin class is not identified as a plugin descriptor by the visit method"() {
        given: 'an abstract GrailsPlugin class under grails-app'
            def sourceFile = new File(tempDir, 'grails-app/AbstractPluginGrailsPlugin.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when: 'the source is compiled, exercising the registered global transform'
            compileToFile(
                    sourceFile,
                    'abstract class AbstractPluginGrailsPlugin {}',
                    targetDir,
                    [projectName: 'abstractPlugin', projectVersion: '1.0']
            )

        then: 'no plugin.xml file is generated because the class was not treated as a plugin descriptor'
            !new File(targetDir, 'META-INF/grails-plugin.xml').exists()
    }

    private SourceUnit sourceUnitWithTarget(File targetDirectory) {
        def cc = new CompilerConfiguration()
        cc.setTargetDirectory((File) targetDirectory)
        Stub(SourceUnit) {
            getConfiguration() >> cc
            getName() >> 'TestSource'
        }
    }

    private static ClassNode compilePlugin(String pluginSource) {
        def classNode = null
        def cu = new CompilationUnit(new GroovyClassLoader())
        cu.addSource('GrailsPlugin', pluginSource)
        cu.addPhaseOperation({ SourceUnit source, GeneratorContext context, ClassNode cn ->
            if (cn.name.endsWith('GrailsPlugin')) {
                classNode = cn
            }
        } as CompilationUnit.IPrimaryClassNodeOperation, Phases.CONVERSION)
        cu.compile(Phases.CONVERSION)
        classNode
    }

    /**
     * Compiles the given source to a real file on disk (required so {@code GrailsASTUtils.getSourceUrl}
     * resolves a URL). Because {@code GlobalGrailsClassInjectorTransformation} is itself registered as a
     * global AST transformation (via {@code META-INF/services}) and grails-core's own compiled classes are
     * on this test's classpath, compiling all the way through {@code CANONICALIZATION} exercises the real
     * transformation exactly as production Grails builds do - no manual {@code visit()} call is needed.
     * {@code nodeMetaData} is stamped onto the class during {@code CONVERSION}, before the transformation's
     * own {@code CANONICALIZATION} pass runs, mirroring how the Grails Gradle plugin stamps project
     * name/version metadata via its own compiler customizer.
     */
    private static List<String> compileCollectingWarnings(File sourceFile, String source, File targetDirectory) {
        sourceFile.parentFile.mkdirs()
        sourceFile.text = source
        def cu = new CompilationUnit(new CompilerConfiguration(targetDirectory: targetDirectory))
        cu.addSource(sourceFile)
        cu.compile(Phases.CANONICALIZATION)
        (cu.errorCollector.warnings ?: []).collect { it.message?.toString() ?: it.toString() }
    }

    private static ClassNode compileToFile(File sourceFile, String source, File targetDirectory, Map<String, String> nodeMetaData = [:]) {
        sourceFile.parentFile.mkdirs()
        sourceFile.text = source
        def configuration = new CompilerConfiguration(targetDirectory: targetDirectory)
        if (nodeMetaData) {
            configuration.addCompilationCustomizers(new CompilationCustomizer(CompilePhase.CONVERSION) {
                @Override
                void call(SourceUnit source1, GeneratorContext context, ClassNode cn) throws CompilationFailedException {
                    nodeMetaData.each {
                        cn.putNodeMetaData(it.key, it.value)
                    }
                }
            })
        }
        def cu = new CompilationUnit(configuration)
        cu.addSource(sourceFile)
        def capturedClassNode = null
        cu.addPhaseOperation({ SourceUnit source1, GeneratorContext context, ClassNode cn ->
            capturedClassNode = cn
        } as CompilationUnit.IPrimaryClassNodeOperation, Phases.CANONICALIZATION)
        cu.compile(Phases.CANONICALIZATION)
        capturedClassNode
    }
}
