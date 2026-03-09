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
 *  Unless required by applicable law or agreed in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.compiler.injection.testing

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.control.SourceUnit
import org.springframework.context.annotation.Import

import spock.lang.Specification
import spock.lang.TempDir

class IntegrationTestAutoConfigurationSupportSpec extends Specification {

    private static final String IMPORTS_RESOURCE = 'META-INF/grails/IntegrationTestAutoConfiguration.imports'

    @TempDir
    File tempDir

    void 'adds only eligible auto-configurations from registry'() {
        given:
        def fixture = new AutoConfigurationFixture(testClassLoader("""
            # comment
            fixture.EligibleConfig
            fixture.SecondaryConfig
            fixture.NotAnnotatedConfig
            missing.example.DoesNotExist
            fixture.EligibleConfig # duplicate with inline comment
        """.stripIndent()))
        fixture.compileFixtures()
        def source = Stub(SourceUnit) {
            getClassLoader() >> fixture.classLoader
        }
        def classNode = new ClassNode('fixture.TargetSpec', 0, ClassHelper.OBJECT_TYPE)

        when:
        IntegrationTestAutoConfigurationSupport.addIntegrationTestAutoConfigurations(classNode, source)

        then:
        importedClassNames(classNode) == [
                fixture.eligibleConfigName,
                fixture.secondaryConfigName
        ] as Set
    }

    void 'ignores blank and comment-only registry lines'() {
        given:
        def fixture = new AutoConfigurationFixture(testClassLoader('''

            # first comment
            fixture.EligibleConfig

            # second comment
            fixture.SecondaryConfig

        '''.stripIndent()))
        fixture.compileFixtures()
        def source = Stub(SourceUnit) {
            getClassLoader() >> fixture.classLoader
        }
        def classNode = new ClassNode('fixture.TargetSpec', 0, ClassHelper.OBJECT_TYPE)
        classNode.addAnnotation(new AnnotationNode(ClassHelper.make(Deprecated)))

        when:
        IntegrationTestAutoConfigurationSupport.addIntegrationTestAutoConfigurations(classNode, source)

        then:
        importedClassNames(classNode) == [fixture.eligibleConfigName, fixture.secondaryConfigName] as Set
    }

    void 'does not add duplicate import when class already imports candidate directly'() {
        given:
        def fixture = new AutoConfigurationFixture(testClassLoader('fixture.EligibleConfig\n'))
        fixture.compileFixtures()
        def source = Stub(SourceUnit) {
            getClassLoader() >> fixture.classLoader
        }
        def classNode = new ClassNode('fixture.TargetSpec', 0, ClassHelper.OBJECT_TYPE)
        def importAnnotation = new AnnotationNode(ClassHelper.make(Import))
        importAnnotation.setMember('value', new ClassExpression(ClassHelper.make(fixture.classLoader.loadClass(fixture.eligibleConfigName))))
        classNode.addAnnotation(importAnnotation)

        when:
        IntegrationTestAutoConfigurationSupport.addIntegrationTestAutoConfigurations(classNode, source)

        then:
        classNode.annotations.count { it.classNode?.name == Import.name } == 1
        importedClassNames(classNode) == [fixture.eligibleConfigName] as Set
    }

    void 'does not add duplicate import when class already imports candidate in list form'() {
        given:
        def fixture = new AutoConfigurationFixture(testClassLoader('fixture.EligibleConfig\n'))
        fixture.compileFixtures()
        def source = Stub(SourceUnit) {
            getClassLoader() >> fixture.classLoader
        }
        def classNode = new ClassNode('fixture.TargetSpec', 0, ClassHelper.OBJECT_TYPE)
        def importAnnotation = new AnnotationNode(ClassHelper.make(Import))
        importAnnotation.setMember('value', new ListExpression([
                new ClassExpression(ClassHelper.make(String)),
                new ClassExpression(ClassHelper.make(fixture.classLoader.loadClass(fixture.eligibleConfigName)))
        ]))
        classNode.addAnnotation(importAnnotation)

        when:
        IntegrationTestAutoConfigurationSupport.addIntegrationTestAutoConfigurations(classNode, source)

        then:
        classNode.annotations.count { it.classNode?.name == Import.name } == 1
        importedClassNames(classNode) == [String.name, fixture.eligibleConfigName] as Set
    }

    /**
     * Creates a classloader that exposes a synthetic imports registry resource for this spec.
     *
     * @param importsContent registry content to expose at {@code META-INF/grails/IntegrationTestAutoConfiguration.imports}
     * @return classloader used by the transformation under test
     */
    private GroovyClassLoader testClassLoader(String importsContent) {
        def resourceFile = new File(tempDir, IMPORTS_RESOURCE).tap { it.parentFile.mkdirs() }
        Files.writeString(resourceFile.toPath(), importsContent, StandardCharsets.UTF_8)
        new ResourceOverridingClassLoader(
                resourceFile.parentFile.parentFile.parentFile.toURI().toURL(),
                IntegrationTestAutoConfigurationSupport.classLoader
        )
    }

    /**
     * Extracts fully qualified class names declared in {@code @Import} annotations.
     *
     * @param classNode class node to inspect
     * @return imported class names from single-value and list-value {@code @Import} declarations
     */
    private static Set<String> importedClassNames(ClassNode classNode) {
        def names = [] as Set<String>
        for (def annotationNode in classNode.annotations) {
            if (annotationNode.classNode?.name != Import.name) {
                continue
            }
            def value = annotationNode.getMember('value')
            if (value instanceof ClassExpression) {
                names << value.type.name
            }
            if (value instanceof ListExpression) {
                for (def expression in value.expressions) {
                    if (expression instanceof ClassExpression) {
                        names << expression.type.name
                    }
                }
            }
        }
        names
    }

    /**
     * Compiles fixture classes used as auto-configuration candidates in this spec.
     */
    private static class AutoConfigurationFixture {

        private final GroovyClassLoader classLoader

        String eligibleConfigName = 'fixture.EligibleConfig'
        String secondaryConfigName = 'fixture.SecondaryConfig'

        AutoConfigurationFixture(GroovyClassLoader classLoader) {
            this.classLoader = classLoader
        }

        /**
         * Compiles a small set of candidate and non-candidate classes into the fixture classloader.
         */
        void compileFixtures() {
            classLoader.parseClass('''
                package fixture
                import grails.testing.spring.IntegrationTestAutoConfiguration
                @IntegrationTestAutoConfiguration
                class EligibleConfig {}
            ''')
            classLoader.parseClass('''
                package fixture
                import grails.testing.spring.IntegrationTestAutoConfiguration
                @IntegrationTestAutoConfiguration
                class SecondaryConfig {}
            ''')
            classLoader.parseClass('''
                package fixture
                class NotAnnotatedConfig {}
            ''')
        }
    }

    /**
     * Classloader that overrides lookup for the imports registry used by the spec.
     */
    private static class ResourceOverridingClassLoader extends GroovyClassLoader {

        private final URL resourceRoot

        /**
         * Creates a classloader that serves a deterministic imports registry resource.
         *
         * @param resourceRoot root URL used to resolve the synthetic imports resource
         * @param parent parent classloader for normal class/resource delegation
         */
        ResourceOverridingClassLoader(URL resourceRoot, ClassLoader parent) {
            super(parent)
            this.resourceRoot = resourceRoot
            addURL(resourceRoot)
        }

        /**
         * Returns only the synthetic imports resource for the known registry path.
         *
         * @param name resource name
         * @return enumeration containing the synthetic resource for the registry path, otherwise delegated lookup results
         */
        @Override
        Enumeration<URL> getResources(String name) {
            if (name == IMPORTS_RESOURCE) {
                def resourceUrl = new URL(resourceRoot, name)
                return Collections.enumeration([resourceUrl])
            }
            return super.getResources(name)
        }
    }
}
