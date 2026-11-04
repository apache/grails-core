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
import org.grails.taglib.index.TagLibraryIndex
import spock.lang.Specification
import spock.lang.TempDir

/**
 * That a rewritten call produces the right output says nothing about whether it was rewritten, since
 * the dynamic route produces the same output. This looks at what was actually compiled.
 */
class CompiledTagCallBytecodeSpec extends Specification {

    private static final String INVOCATION = 'org/grails/taglib/CompiledTagInvocation'

    @TempDir
    Path tempDir

    void 'a call to a known tag is compiled as an invocation, not a dynamic call'() {
        when:
        Path compiled = compile('''
            import grails.gsp.TagLib
            @TagLib
            class BytecodeCheckTagLib {
                static namespace = 'bytecheck'
                def calls(Map attrs) {
                    out << g.createLink(controller: 'book')
                }
            }
        ''', 'BytecodeCheckTagLib')

        then: 'the invocation entry point is referenced'
        references(compiled, 'BytecodeCheckTagLib')
    }

    void 'a call to a known tag written inside a closure is compiled as an invocation'() {
        when: 'the call is in a block passed to another method, where most tag calls in real code are'
        Path compiled = compile('''
            import grails.gsp.TagLib
            @TagLib
            class ClosureBodyCallerTagLib {
                static namespace = 'closurebody'
                def calls(Map attrs) {
                    [1, 2].each { n ->
                        out << g.createLink(controller: 'book')
                    }
                }
            }
        ''', 'ClosureBodyCallerTagLib')

        then: 'the closure carries the invocation, not a dynamic call site'
        references(compiled, 'ClosureBodyCallerTagLib$_calls_closure1')
    }

    void 'a call to a known tag written inside a tag body is compiled as an invocation'() {
        when: 'a tag body is a closure, so a tag called within one has to be reached through it'
        Path compiled = compile('''
            import grails.gsp.TagLib
            @TagLib
            class NestedBodyTagLib {
                static namespace = 'nestedbody'
                def calls(Map attrs) {
                    out << g.formatDate(date: new Date()) {
                        g.createLink(controller: 'book')
                    }
                }
            }
        ''', 'NestedBodyTagLib')

        then: 'both the outer call and the one inside the body are rewritten'
        references(compiled, 'NestedBodyTagLib')
        references(compiled, 'NestedBodyTagLib$_calls_closure1')
    }

    void 'a call to a known tag written in a constructor is compiled as an invocation'() {
        when:
        Path compiled = compile('''
            import grails.gsp.TagLib
            @TagLib
            class ConstructorCallerTagLib {
                static namespace = 'ctorcaller'
                String cached
                ConstructorCallerTagLib() {
                    cached = g.createLink(controller: 'book')
                }
                def calls(Map attrs) { out << cached }
            }
        ''', 'ConstructorCallerTagLib')

        then:
        references(compiled, 'ConstructorCallerTagLib')
    }

    void 'a call whose attributes are only known at runtime is compiled as an invocation too'() {
        when: 'the shape is not evident in the source, so the arguments are forwarded as written'
        Path compiled = compile('''
            import grails.gsp.TagLib
            @TagLib
            class ComputedAttrsTagLib {
                static namespace = 'computedattrs'
                def calls(Map attrs) {
                    Map linkAttrs = [controller: 'book']
                    out << g.createLink(linkAttrs)
                }
            }
        ''', 'ComputedAttrsTagLib')

        then:
        references(compiled, 'ComputedAttrsTagLib')
    }

    void 'an unqualified call to a known tag is left dynamic unless the build asks otherwise'() {
        when: 'nothing in the tag library answers to the name, but the source named no namespace'
        Path compiled = compile('''
            import grails.gsp.TagLib
            @TagLib
            class UnqualifiedCallerTagLib {
                static namespace = 'unqualified'
                def calls(Map attrs) {
                    out << createLink(controller: 'book')
                }
            }
        ''', 'UnqualifiedCallerTagLib')

        then: 'compiling it would need this build to have enabled unqualifiedTagCalls'
        !references(compiled, 'UnqualifiedCallerTagLib')
    }

    void 'an unqualified call a local variable answers to is left alone'() {
        when: 'a local holding a closure answers to the name, so the call is not a tag call'
        Path compiled = compile('''
            import grails.gsp.TagLib
            @TagLib
            class LocalShadowTagLib {
                static namespace = 'localshadow'
                def calls(Map attrs) {
                    def createLink = { Map a -> 'local' }
                    out << createLink(controller: 'book')
                }
            }
        ''', 'LocalShadowTagLib')

        then:
        !references(compiled, 'LocalShadowTagLib')
    }

    void 'a call into a namespace no compiled tag library declares is left dynamic'() {
        when:
        Path compiled = compile('''
            import grails.gsp.TagLib
            @TagLib
            class UntouchedTagLib {
                static namespace = 'untouched'
                def calls(Map attrs) {
                    out << nosuchnamespace.whatever(a: 1)
                }
            }
        ''', 'UntouchedTagLib')

        then: 'nothing was rewritten, so it resolves as it did before'
        !references(compiled, 'UntouchedTagLib')
    }

    void 'a closure based tag is a valid target, since the tag is still selected at runtime'() {
        when: 'g.link is declared as a Closure field; the invocation resolves it by name as before'
        Path compiled = compile('''
            import grails.gsp.TagLib
            @TagLib
            class ClosureTagCallerTagLib {
                static namespace = 'closurecaller'
                def calls(Map attrs) {
                    out << g.link(controller: 'book')
                }
            }
        ''', 'ClosureTagCallerTagLib')

        then:
        references(compiled, 'ClosureTagCallerTagLib')

        and: 'it is known, so it is never reported as a misspelling'
        TagLibraryIndex.load(getClass().classLoader).isKnown('g', 'link')
    }

    void 'a known tag in a namespace the build declared dynamic is left alone'() {
        given: 'declaring a namespace dynamic is how a build keeps its tags decided while it runs'
        ClassLoader dynamicNamespace = loaderDeclaring('dynamicTagNamespaces=g\n')

        when:
        Path compiled = compile('''
            import grails.gsp.TagLib
            @TagLib
            class DeclaredDynamicTagLib {
                static namespace = 'declareddynamic'
                def calls(Map attrs) {
                    out << g.createLink(controller: 'book')
                }
            }
        ''', 'DeclaredDynamicTagLib', dynamicNamespace)

        then: 'the tag is known, but the declaration turns resolution off rather than only reporting'
        TagLibraryIndex.load(getClass().classLoader).isKnown('g', 'createLink')
        !references(compiled, 'DeclaredDynamicTagLib')
    }

    void 'an unqualified call to a tag in a namespace declared dynamic is left alone'() {
        given:
        ClassLoader dynamicNamespace = loaderDeclaring('dynamicTagNamespaces=g\n')

        when:
        Path compiled = compile('''
            import grails.gsp.TagLib
            @TagLib
            class DeclaredDynamicUnqualifiedTagLib {
                static namespace = 'declareddynamicunqualified'
                def calls(Map attrs) {
                    out << createLink(controller: 'book')
                }
            }
        ''', 'DeclaredDynamicUnqualifiedTagLib', dynamicNamespace)

        then:
        !references(compiled, 'DeclaredDynamicUnqualifiedTagLib')
    }

    void 'the index this build compiles against is populated'() {
        expect: 'otherwise the first case would pass for the wrong reason'
        TagLibraryIndex.load(getClass().classLoader).lookup('g', 'createLink') != null
    }

    /**
     * What a build declares reaches the compiler as a classpath resource written by the
     * {@code generateTagLibraryIndex} task, so a compilation meant to see it is given a loader that can.
     */
    private ClassLoader loaderDeclaring(String settings) {
        Path settingsDir = Files.createDirectories(tempDir.resolve('settings-' + settings.hashCode()))
        Path indexDir = Files.createDirectories(settingsDir.resolve(TagLibraryIndex.INDEX_LOCATION))
        indexDir.resolve('compile-settings.properties').toFile().text = settings
        new URLClassLoader([settingsDir.toUri().toURL()] as URL[], getClass().classLoader)
    }

    private static boolean references(Path outputDir, String className) {
        File classFile = outputDir.resolve(className + '.class').toFile()
        assert classFile.exists() : "no class file compiled for ${className}"
        new String(classFile.bytes, 'ISO-8859-1').contains(INVOCATION)
    }

    private Path compile(String source, String className, ClassLoader parent = null) {
        Path sourceFile = tempDir.resolve(className + '.groovy')
        sourceFile.toFile().text = source
        Path outputDir = Files.createDirectories(tempDir.resolve('classes-' + className))

        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.targetDirectory = outputDir.toFile()
        configuration.parameters = true
        CompilationUnit unit = new CompilationUnit(configuration, null,
                new GroovyClassLoader(parent ?: getClass().classLoader, configuration))
        unit.addSource(sourceFile.toFile())
        unit.compile()

        outputDir
    }
}
