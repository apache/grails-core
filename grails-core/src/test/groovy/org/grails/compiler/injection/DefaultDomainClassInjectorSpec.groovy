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

import groovy.transform.Generated
import groovy.transform.ToString
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration

import spock.lang.Specification
import spock.lang.TempDir

import grails.persistence.Entity

/**
 * @author James Kleeh
 */
class DefaultDomainClassInjectorSpec extends Specification {

    @TempDir
    File tempDir

    private List originalTraitInjectors

    void setup() {
        // Runtime compilation must not load the test-only trait factories from this module.
        originalTraitInjectors = TraitInjectionUtils.@traitInjectors
        TraitInjectionUtils.@traitInjectors = []
    }

    void cleanup() {
        TraitInjectionUtils.@traitInjectors = originalTraitInjectors
    }

    void "test default id and version"() {
        expect:
        Test.getDeclaredField('id').type == Long
        Test.getDeclaredField('version').type == Long
    }

    void "test a declared id is left alone"() {
        expect: 'the injector only supplies what is missing, which is what lets GORM decide the type first'
        TestWithStringId.getDeclaredField('id').type == String
    }

    void 'generated fields stay in the domain hierarchy above a shared plain base'() {
        given:
        def loader = new GroovyClassLoader()
        loader.parseClass("""
            import grails.persistence.Entity

            $declarations

            abstract class SharedDetails {
                String name
            }

            class PlainCommand extends SharedDetails { }
        """)

        when:
        def root = loader.loadClass('RootEntity')
        def child = loader.loadClass('ChildEntity')
        def command = loader.loadClass('PlainCommand').getDeclaredConstructor().newInstance()

        then:
        root.getDeclaredField('id').type == Long
        root.getDeclaredField('version').type == Long
        child.getMethod('getId').declaringClass == root
        child.getMethod('getVersion').declaringClass == root
        !child.declaredFields*.name.contains('id')
        !child.declaredFields*.name.contains('version')
        !child.superclass.declaredFields*.name.contains('id') || child.superclass == root
        !child.superclass.declaredFields*.name.contains('version') || child.superclass == root
        command.hasProperty('id') == null
        command.hasProperty('version') == null

        cleanup:
        loader.close()

        where:
        declarations << [
            '@Entity abstract class RootEntity extends SharedDetails { }; @Entity class ChildEntity extends RootEntity { }',
            '@Entity class ChildEntity extends RootEntity { }; @Entity abstract class RootEntity extends SharedDetails { }',
            '@Entity abstract class RootEntity extends SharedDetails { }; abstract class PlainIntermediate extends RootEntity { }; @Entity class ChildEntity extends PlainIntermediate { }',
            '@Entity class ChildEntity extends PlainIntermediate { }; abstract class PlainIntermediate extends RootEntity { }; @Entity abstract class RootEntity extends SharedDetails { }'
        ]
    }

    void 'domain source locations preserve inheritance without contaminating shared source classes'() {
        given:
        def output = new File(tempDir, 'classes')
        output.mkdirs()
        def loader = new GroovyClassLoader()
        loader.addClasspath(output.absolutePath)
        def unit = new CompilationUnit(new CompilerConfiguration(targetDirectory: output), null, loader)
        def sources = [
            'grails-app/domain/example/Child.groovy': 'package example; class Child extends Root { }',
            'grails-app/domain/example/Root.groovy': 'package example; abstract class Root extends Details { }',
            'src/main/groovy/example/Details.groovy': 'package example; abstract class Details { String name }',
            'src/main/groovy/example/Command.groovy': 'package example; class Command extends Details { }'
        ]
        sources.each { path, text ->
            def source = new File(tempDir, path)
            source.parentFile.mkdirs()
            source.text = text
            unit.addSource(source)
        }

        when:
        unit.compile()
        def root = loader.loadClass('example.Root')
        def child = loader.loadClass('example.Child')
        def command = loader.loadClass('example.Command').getDeclaredConstructor().newInstance()

        then:
        root.getDeclaredField('id').type == Long
        root.getDeclaredField('version').type == Long
        child.getMethod('getId').declaringClass == root
        child.getMethod('getVersion').declaringClass == root
        command.hasProperty('id') == null
        command.hasProperty('version') == null

        cleanup:
        loader.close()
    }

    void 'explicit persistence properties inherited from a plain base are preserved'() {
        given:
        def loader = new GroovyClassLoader()
        def entity = loader.parseClass('''
            import grails.persistence.Entity

            @Entity
            class ExplicitEntity extends ExplicitDetails { }

            abstract class ExplicitDetails {
                String id
                Integer version
            }
        ''')

        expect:
        entity.getMethod('getId').returnType == String
        entity.getMethod('getVersion').returnType == Integer
        !entity.declaredFields*.name.contains('id')
        !entity.declaredFields*.name.contains('version')

        cleanup:
        loader.close()
    }

    void "test default toString"() {
        when:
        Test test = new Test()
        test.id = 1

        then:
        test.toString().endsWith("Test : 1")

        and: 'toString is marked as Generated'
        test.class.getMethod('toString').isAnnotationPresent(Generated)
    }

    void "test domain with groovy.transform.ToString"() {
        when:
        TestWithGroovy test = new TestWithGroovy()
        test.id = 1

        then:
        test.toString().endsWith("TestWithGroovy(1)")

        and: 'toString is marked as Generated'
        test.class.getMethod('toString').isAnnotationPresent(Generated)
    }

    @Entity
    class Test {
    }

    @Entity
    @ToString(includes = ["id"])
    class TestWithGroovy {
    }

    @Entity
    class TestWithStringId {
        String id
    }
}
