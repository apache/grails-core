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

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir
import spock.util.environment.RestoreSystemProperties

import grails.artefact.Artefact
import org.apache.grails.common.compiler.GroovyTransformOrder

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

    void "the global transform recognizes a @GrailsPluginDescriptor plugin without the GrailsPlugin suffix"() {
        given:
            def sourceFile = new File(tempDir, 'src/main/groovy/StarterPlugin.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when:
            def classNode = compileToFile(
                    sourceFile,
                    '''
                        import grails.plugins.metadata.GrailsPluginDescriptor
                        import org.springframework.boot.autoconfigure.AutoConfiguration
                        @AutoConfiguration
                        @GrailsPluginDescriptor(name = 'starter')
                        class StarterPlugin extends grails.plugins.Plugin {
                            def beans = {
                                bean('greeting', String) { 'hello' }
                            }
                        }
                    ''',
                    targetDir
            )

        then: "no redundant version property is injected and the beans DSL is compiled into a sibling auto-configuration"
            classNode.getProperty('version') == null
            classNode.getProperty('beans') == null
            new File(targetDir,
                    'META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports').text.trim() ==
                    'StarterPluginAutoConfiguration'
    }

    void "the implicit beans convention leaves a plugin descriptor's unrelated beans property alone"() {
        given: "a descriptor whose beans property is not the DSL, as a pre-8.0 plugin's may well be"
            def sourceFile = new File(tempDir, 'UnrelatedBeansGrailsPlugin.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when: "it is compiled with the beans DSL on the classpath"
            def classNode = compileToFile(
                    sourceFile,
                    '''
                        @grails.plugins.metadata.GrailsPluginDescriptor
                        class UnrelatedBeansGrailsPlugin extends grails.plugins.Plugin {
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
                        @grails.plugins.metadata.GrailsPluginDescriptor
                        class OtherClosureGrailsPlugin extends grails.plugins.Plugin {
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
                        @grails.plugins.metadata.GrailsPluginDescriptor
                        class DslBeansGrailsPlugin extends grails.plugins.Plugin {
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
                        @grails.plugins.metadata.GrailsPluginDescriptor
                        class AnnotatedBeansGrailsPlugin extends grails.plugins.Plugin {
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

    void "a *GrailsPlugin class without @GrailsPluginDescriptor is not a plugin, so its beans closure is left alone"() {
        given: "a class named like a plugin, extending Plugin, with a DSL-shaped beans closure but no annotation"
            def sourceFile = new File(tempDir, 'PlainDslBeansGrailsPlugin.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when:
            def classNode = compileToFile(
                    sourceFile,
                    '''
                        @org.springframework.boot.autoconfigure.AutoConfiguration
                        class PlainDslBeansGrailsPlugin extends grails.plugins.Plugin {
                            def beans = {
                                bean('greeting', String) { 'hello' }
                            }
                        }
                    ''',
                    targetDir
            )

        then: "the name convention no longer makes it a plugin: the closure is not compiled"
            classNode.getProperty('beans') != null

        and: "no auto-configuration sibling is generated or registered"
            !new File(targetDir,
                    'META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports').exists()
    }

    void "the implicit beans convention compiles a unit test's beans closure onto a nested configuration class"() {
        given: "a Spock spec implementing the testing support's trait, stood in for here, in a test source directory"
            def testSources = new File(tempDir, 'src/test/groovy')
            def trait = new File(testSources, 'org/grails/testing/GrailsUnitTest.groovy')
            trait.parentFile.mkdirs()
            trait.text = 'package org.grails.testing\ninterface GrailsUnitTest { }\n'
            def spec = new File(testSources, 'ReportServiceSpec.groovy')
            spec.text = '''
                class ReportServiceSpec extends spock.lang.Specification implements org.grails.testing.GrailsUnitTest {
                    def beans = {
                        bean('greeting', String) { 'hello' }
                    }
                }
            '''
            def targetDir = new File(tempDir, 'build/classes/groovy/test')

        when:
            def cu = new CompilationUnit(new CompilerConfiguration(targetDirectory: targetDir))
            cu.addSource(trait)
            cu.addSource(spec)
            cu.compile(Phases.CANONICALIZATION)
            ClassNode test = cu.AST.getClass('ReportServiceSpec')
            ClassNode configuration = cu.AST.getClass('ReportServiceSpec$BeansConfiguration')

        then: "the property is consumed with no @GrailsBeans written"
            test.getProperty('beans') == null

        and: "the beans are on the nested class the testing support registers"
            configuration != null
            configuration.getMethod('greeting', [] as Parameter[]) != null

        and: "nothing is registered as an auto-configuration"
            !new File(targetDir,
                    'META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports').exists()
    }

    void "the implicit beans convention reports a unit test's @Shared beans block rather than dropping it"() {
        given: "Spock renames a shared field and removes its property before this transform runs"
            def testSources = new File(tempDir, 'src/test/groovy')
            def trait = new File(testSources, 'org/grails/testing/GrailsUnitTest.groovy')
            trait.parentFile.mkdirs()
            trait.text = 'package org.grails.testing\ninterface GrailsUnitTest { }\n'
            def spec = new File(testSources, 'SharedBeansSpec.groovy')
            spec.text = '''
                class SharedBeansSpec extends spock.lang.Specification implements org.grails.testing.GrailsUnitTest {
                    @spock.lang.Shared
                    def beans = {
                        bean('greeting', String) { 'hello' }
                    }
                }
            '''

        when:
            def cu = new CompilationUnit(new CompilerConfiguration(targetDirectory: new File(tempDir, 'build/classes/groovy/test')))
            cu.addSource(trait)
            cu.addSource(spec)
            cu.compile(Phases.CANONICALIZATION)

        then:
            MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
            e.message.contains("A unit test's 'beans' block cannot be @Shared")
    }

    void "a unit test's unrelated @Shared beans field is left alone"() {
        given: "a shared beans field that is not the DSL"
            def testSources = new File(tempDir, 'src/test/groovy')
            def spec = new File(testSources, 'SharedMapSpec.groovy')
            spec.parentFile.mkdirs()
            spec.text = '''
                class SharedMapSpec extends spock.lang.Specification implements org.grails.testing.GrailsUnitTest {
                    @spock.lang.Shared
                    def beans = [someKey: 'someValue']
                }
            '''

        when: "it compiles"
            def cu = unitTestCompilation(spec)
            cu.compile(Phases.CANONICALIZATION)

        then: "nothing is reported or generated"
            cu.AST.getClass('SharedMapSpec$BeansConfiguration') == null
    }

    void "a unit test's unrelated beans closure stays where Spock put it"() {
        given: "a beans closure that declares nothing, which Spock moves into its initializer method"
            def testSources = new File(tempDir, 'src/test/groovy')
            def spec = new File(testSources, 'UnrelatedClosureSpec.groovy')
            spec.parentFile.mkdirs()
            spec.text = '''
                class UnrelatedClosureSpec extends spock.lang.Specification implements org.grails.testing.GrailsUnitTest {
                    def beans = { 'not the DSL' }
                }
            '''

        when:
            def cu = unitTestCompilation(spec)
            cu.compile(Phases.CANONICALIZATION)
            ClassNode unrelatedSpec = cu.AST.getClass('UnrelatedClosureSpec')

        then: "it is not moved back onto the field, and nothing is generated"
            unrelatedSpec.getProperty('beans').field.initialExpression == null
            cu.AST.getClass('UnrelatedClosureSpec$BeansConfiguration') == null
    }

    /** A compilation of a test source alongside a stand-in for the testing support's trait. */
    private CompilationUnit unitTestCompilation(File spec) {
        def trait = new File(tempDir, 'src/test/groovy/org/grails/testing/GrailsUnitTest.groovy')
        trait.parentFile.mkdirs()
        trait.text = 'package org.grails.testing\ninterface GrailsUnitTest { }\n'
        def cu = new CompilationUnit(new CompilerConfiguration(targetDirectory: new File(tempDir, 'build/classes/groovy/test')))
        cu.addSource(trait)
        cu.addSource(spec)
        cu
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
                        @grails.plugins.metadata.GrailsPluginDescriptor
                        class WarnOnceGrailsPlugin extends grails.plugins.Plugin {
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
                        @grails.plugins.metadata.GrailsPluginDescriptor
                        class StrayBeansGrailsPlugin extends grails.plugins.Plugin {
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

    void "the global transform stamps no plugin ownership on a Grails resource class, which its plugin's descriptor records"() {
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

        then: "the class carries no annotation naming a plugin"
            classNode.annotations.every { !it.classNode.name.startsWith('grails.plugins.metadata.') }
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

    void "an artefact handler compiled again as a plain class leaves grails.factories"() {
        given: "two artefact handlers compiled into the same output"
            def sourceFile = new File(tempDir, 'src/main/groovy/TestHandler.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')
            compileToFile(sourceFile, artefactHandlerSource('TestHandler'), targetDir)
            compileToFile(new File(tempDir, 'src/main/groovy/OtherHandler.groovy'), artefactHandlerSource('OtherHandler'), targetDir)

        when: "incremental compilation recompiles only the edited one, which is no longer a handler"
            compileToFile(sourceFile, 'class TestHandler {}', targetDir)

        then:
            factoriesIn(targetDir).getProperty('grails.core.ArtefactHandler') == 'OtherHandler'
    }

    void "a class that changes which factory type it implements is registered under the new one only"() {
        given: "a trait injector"
            def sourceFile = new File(tempDir, 'src/main/groovy/SwitchingType.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')
            compileToFile(sourceFile, '''
                class SwitchingType implements grails.compiler.traits.TraitInjector {
                    Class getTrait() { null }
                    String[] getArtefactTypes() { [] as String[] }
                }
            ''', targetDir)

        when: "it is compiled again as an artefact handler"
            compileToFile(sourceFile, artefactHandlerSource('SwitchingType'), targetDir)

        then:
            def factories = factoriesIn(targetDir)
            factories.getProperty('grails.core.ArtefactHandler') == 'SwitchingType'
            factories.getProperty('grails.compiler.traits.TraitInjector') == null
    }

    void "a class that is both an artefact handler and a trait injector is registered under both types"() {
        given:
            def sourceFile = new File(tempDir, 'src/main/groovy/BothTypes.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when:
            compileToFile(sourceFile, '''
                class BothTypes extends grails.core.ArtefactHandlerAdapter implements grails.compiler.traits.TraitInjector {
                    BothTypes() {
                        super('BothTypes', null, null, 'Handler')
                    }
                    Class getTrait() { null }
                    String[] getArtefactTypes() { [] as String[] }
                }
            ''', targetDir)

        then: "every type is checked, not only until one registers the class"
            def factories = factoriesIn(targetDir)
            factories.getProperty('grails.core.ArtefactHandler') == 'BothTypes'
            factories.getProperty('grails.compiler.traits.TraitInjector') == 'BothTypes'
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

        then: "the class is not treated as an artefact"
            classNode.getAnnotations(ClassHelper.make(grails.artefact.Artefact)).empty
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

    void "an abstract Plugin subclass without the annotation compiles as an ordinary class"() {
        given: 'an abstract plugin base class under grails-app'
            def sourceFile = new File(tempDir, 'grails-app/AbstractPluginGrailsPlugin.groovy')
            def targetDir = new File(tempDir, 'build/classes/groovy/main')

        when: 'the source is compiled, exercising the registered global transform'
            def classNode = compileToFile(
                    sourceFile,
                    '''
                        abstract class AbstractPluginGrailsPlugin extends grails.plugins.Plugin {
                            def beans = {
                                bean('greeting', String) { 'hello' }
                            }
                        }
                    ''',
                    targetDir,
                    [projectName: 'abstractPlugin', projectVersion: '1.0']
            )

        then: 'it is not treated as a plugin: the beans closure is left alone and no descriptor is listed'
            classNode.getProperty('beans') != null
            !new File(targetDir, 'META-INF/grails/plugins.imports').exists()
    }

    private SourceUnit sourceUnitWithTarget(File targetDirectory) {
        def cc = new CompilerConfiguration()
        cc.setTargetDirectory((File) targetDirectory)
        Stub(SourceUnit) {
            getConfiguration() >> cc
            getName() >> 'TestSource'
        }
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

    private static String artefactHandlerSource(String className) {
        """
            class ${className} extends grails.core.ArtefactHandlerAdapter {
                ${className}() {
                    super('${className}', null, null, 'Handler')
                }
            }
        """
    }

    private static Properties factoriesIn(File targetDirectory) {
        Properties properties = new Properties()
        new File(targetDirectory, 'META-INF/grails.factories').withInputStream { properties.load(it) }
        properties
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
