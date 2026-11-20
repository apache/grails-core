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
package org.grails.web.taglib

import java.nio.file.Files
import java.nio.file.Path

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.grails.taglib.index.TagLibraryIndexGenerator
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * A tag whose name is also a method Groovy gives every object must not capture an unqualified call to
 * that method.
 *
 * <p>{@code with}, {@code each} and the rest of {@code DefaultGroovyMethods} are real methods on every
 * receiver, so a bare {@code with { }} reached one directly and never went near {@code methodMissing}.
 * Rewriting it into a tag invocation because a tag library happens to declare a tag of that name would
 * silently send the call somewhere the author never wrote — and the collision is not hypothetical:
 * grails-fields declares {@code f:with}.
 *
 * <p>Checked in the class file, because a call left dynamic and one rewritten wrongly both compile.
 */
class GroovyMethodNameCollisionSpec extends Specification {

    @TempDir
    Path tempDir

    Path indexDir

    def setup() {
        Path taglibSources = Files.createDirectories(tempDir.resolve('grails-app/taglib/demo'))
        taglibSources.resolve('CollidingTagLib.groovy').toFile().text = '''
            package demo

            import grails.gsp.TagLib

            @TagLib
            class CollidingTagLib {
                static namespace = 'collide'
                def with(Map attrs, Closure body) { 'tag' }
                def each(Map attrs, Closure body) { 'tag' }
                def greeting(Map attrs) { 'hello' }
            }
        '''
        indexDir = Files.createDirectories(tempDir.resolve('build/generated/grails-taglibs'))
        TagLibraryIndexGenerator.generate(
                tempDir.resolve('grails-app/taglib').toFile(), indexDir.toFile(), true, 'UTF-8')
    }

    @Unroll
    void 'the index describes the colliding tag #tagName'() {
        expect: 'otherwise a case below would pass because the tag was unknown, not because it was reserved'
        new File(indexDir.toFile(), 'META-INF/grails/taglibs/demo.CollidingTagLib.properties').text
                .contains(tagName)

        where:
        tagName << ['with', 'each']
    }

    @Unroll
    void 'an unqualified call to #tagName is left for Groovy to answer'() {
        when: 'a tag library in the same namespace as the tag library declaring that tag'
        byte[] compiled = compileWithIndexOnClasspath("""
            package demo

            import grails.artefact.gsp.TagLibraryInvoker

            class ${className} implements TagLibraryInvoker {
                static namespace = 'collide'
                def run() {
                    ${expression}
                }
            }
        """, className, 'demo')

        then: 'DefaultGroovyMethods still wins, as it did before any of this existed'
        !references(compiled)

        where: 'each written bare, so the receiver is this and the call is the shape that gets rewritten'
        tagName | className    | expression
        'with'  | 'WithCaller' | 'with { 1 }'
        'each'  | 'EachCaller' | 'each { it }'
    }

    void 'a namespaced call to the same tag is still rewritten'() {
        when: 'the source says which tag library it means, so nothing is being guessed'
        byte[] compiled = compileWithIndexOnClasspath('''
            package demo

            import grails.artefact.gsp.TagLibraryInvoker

            class QualifiedCaller implements TagLibraryInvoker {
                static namespace = 'collide'
                def run() {
                    collide.with(a: 1) { 'body' }
                }
            }
        ''', 'QualifiedCaller', 'demo')

        then: 'reserving the name only ever affects a call that did not name its namespace'
        references(compiled)
    }

    void 'an unqualified call is not compiled unless the build asks for it'() {
        when: 'nothing else answers to the name, but the build has not enabled unqualified calls'
        byte[] compiled = compileWithIndexOnClasspath('''
            package demo

            import grails.artefact.gsp.TagLibraryInvoker

            class GreetingCaller implements TagLibraryInvoker {
                static namespace = 'collide'
                def run() {
                    greeting(name: 'world')
                }
            }
        ''', 'GreetingCaller', 'demo')

        then: 'a bare name is a tag only when nothing nearer answers to it, which is not fully visible here'
        !references(compiled)
    }

    void 'an unqualified call is compiled when the build asks for it'() {
        given:
        enableUnqualifiedCalls()

        when:
        byte[] compiled = compileWithIndexOnClasspath("""
            package demo

            import grails.artefact.gsp.TagLibraryInvoker

            class OptedInCaller implements TagLibraryInvoker {
                static namespace = 'collide'
                def run() {
                    greeting(name: 'world')
                }
            }
        """, 'OptedInCaller', 'demo')

        then:
        references(compiled)
    }

    void 'a name Groovy answers to stays dynamic even when the build asks for unqualified calls'() {
        given: 'the opt-in widens which calls are considered, not which names may be captured'
        enableUnqualifiedCalls()

        when:
        byte[] compiled = compileWithIndexOnClasspath("""
            package demo

            import grails.artefact.gsp.TagLibraryInvoker

            class OptedInCollider implements TagLibraryInvoker {
                static namespace = 'collide'
                def run() {
                    with { 1 }
                }
            }
        """, 'OptedInCollider', 'demo')

        then:
        !references(compiled)
    }

    private void enableUnqualifiedCalls() {
        File settings = new File(indexDir.toFile(), 'META-INF/grails/taglibs/compile-settings.properties')
        settings.parentFile.mkdirs()
        settings.text = 'dynamicTagNamespaces=\nstrictTags=false\nunqualifiedTagCalls=true\n'
    }

    private static boolean references(byte[] classBytes) {
        new String(classBytes, 'ISO-8859-1').contains('org/grails/taglib/CompiledTagInvocation')
    }

    private byte[] compileWithIndexOnClasspath(String source, String className, String packageName) {
        Path sourceFile = tempDir.resolve(className + '.groovy')
        sourceFile.toFile().text = source
        Path outputDir = Files.createDirectories(tempDir.resolve('out-' + className))

        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.targetDirectory = outputDir.toFile()
        configuration.parameters = true
        CompilationUnit unit = new CompilationUnit(configuration, null, new GroovyClassLoader(
                new URLClassLoader([indexDir.toUri().toURL()] as URL[], getClass().classLoader)))
        unit.addSource(sourceFile.toFile())
        unit.compile()

        Files.readAllBytes(outputDir.resolve(packageName.replace('.', '/')).resolve(className + '.class'))
    }
}
