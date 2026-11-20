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
package org.grails.compiler.gorm

import java.lang.reflect.Modifier
import java.security.CodeSource

import grails.gorm.annotation.Entity
import groovy.transform.Canonical
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.SourceUnit
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.datastore.gorm.GormEntity
import org.springframework.core.Ordered
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Exercises {@link GormTransformer} through every entry point the compiler uses to drive a class injector: the
 * URL based {@code shouldInject} check plus the three-argument {@code performInjection} that the global artefact
 * transform invokes on each matching class, the two-argument variant, and {@code performInjectionOnAnnotatedClass}
 * used for explicitly annotated artefacts. For the entity transformation the injector is invoked on the parsed
 * {@link ClassNode} during semantic analysis, the phase the global Grails class injector transform runs at, then
 * compilation continues through class generation so the resulting class can be inspected. The {@code @Canonical}
 * rejection is driven on a hand-built class node because Groovy expands that meta-annotation into the annotations it
 * collects before any transform in the same phase sees the class.
 */
class GormTransformerSpec extends Specification {

    private static final Map<String, Closure<Void>> ENTRY_POINTS = [
            'performInjection(source, context, classNode)'       : { GormTransformer transformer, SourceUnit source, GeneratorContext context, ClassNode classNode ->
                transformer.performInjection(source, context, classNode)
            },
            'performInjection(source, classNode)'                : { GormTransformer transformer, SourceUnit source, GeneratorContext context, ClassNode classNode ->
                transformer.performInjection(source, classNode)
            },
            'performInjectionOnAnnotatedClass(source, classNode)': { GormTransformer transformer, SourceUnit source, GeneratorContext context, ClassNode classNode ->
                transformer.performInjectionOnAnnotatedClass(source, classNode)
            }
    ]

    void "getArtefactTypes returns the domain class artefact type"() {
        expect:
        new GormTransformer().artefactTypes == [DomainClassArtefactHandler.TYPE] as String[]
    }

    void "getOrder runs the transformer before every other class injector"() {
        expect:
        new GormTransformer().order == Ordered.HIGHEST_PRECEDENCE
    }

    @Unroll
    void "shouldInject is #expected for #description"() {
        expect:
        new GormTransformer().shouldInject(url) == expected

        where:
        description                                     | url                                                                  | expected
        'a source file under grails-app/domain'         | new URL('file:/app/grails-app/domain/example/Book.groovy')           | true
        'a source file under another grails-app folder' | new URL('file:/app/grails-app/services/example/BookService.groovy') | false
        'a source file outside grails-app'              | new URL('file:/app/src/main/groovy/example/Book.groovy')             | false
        'a null url'                                    | null                                                                 | false
    }

    @Unroll
    void "#entryPoint rejects a class marked with @Canonical"() {
        given: "a class node that still carries the @Canonical meta-annotation"
        SourceUnit source = SourceUnit.create('CanonicalBook.groovy', 'class CanonicalBook { String title }')
        ClassNode classNode = new ClassNode('CanonicalBook', Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        classNode.addAnnotation(new AnnotationNode(ClassHelper.make(Canonical)))

        when:
        ENTRY_POINTS[entryPoint].call(new GormTransformer(), source, null, classNode)

        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('Class [CanonicalBook] is marked with @groovy.transform.Canonical which is not supported for GORM entities.')
        !GormTransformer.getKnownEntityNames().contains('CanonicalBook')

        where:
        entryPoint << ENTRY_POINTS.keySet()
    }

    @Unroll
    void "#entryPoint turns a plain class into a GORM entity and records it in getKnownEntityNames"() {
        given:
        String className = "GormTransformerSpecEntity${index}"

        when:
        Class<?> compiled = compile(className, """
            class ${className} {
                String name
            }
        """, ENTRY_POINTS[entryPoint])

        then:
        GormEntity.isAssignableFrom(compiled)
        compiled.isAnnotationPresent(Entity)
        GormTransformer.getKnownEntityNames().contains(className)

        where:
        [index, entryPoint] << ENTRY_POINTS.keySet().withIndex().collect { String name, int i -> [i, name] }
    }

    private static Class<?> compile(String className, String source, Closure<Void> inject) {
        GormTransformer transformer = new GormTransformer()
        GroovyClassLoader classLoader = new GroovyClassLoader(GormTransformerSpec.classLoader) {
            @Override
            protected CompilationUnit createCompilationUnit(CompilerConfiguration config, CodeSource codeSource) {
                CompilationUnit unit = super.createCompilationUnit(config, codeSource)
                unit.addPhaseOperation(new CompilationUnit.IPrimaryClassNodeOperation() {
                    @Override
                    void call(SourceUnit src, GeneratorContext context, ClassNode classNode) throws CompilationFailedException {
                        if (classNode.nameWithoutPackage == className) {
                            inject.call(transformer, src, context, classNode)
                        }
                    }
                }, Phases.SEMANTIC_ANALYSIS)
                unit
            }
        }
        classLoader.parseClass(source, "${className}.groovy")
    }
}
