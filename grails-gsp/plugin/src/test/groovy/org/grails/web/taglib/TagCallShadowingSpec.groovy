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
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * A name that happens to match a tag library namespace is only a namespace when nothing else in scope
 * has claimed it.
 *
 * <p>Rewriting a call on a local variable, a parameter or a field named {@code g} would silently send
 * it to a tag library instead of the object the author meant, which is the one failure this rewriting
 * must never produce.
 */
class TagCallShadowingSpec extends Specification {

    @TempDir
    Path tempDir

    @Unroll
    void 'a call on #description is not rewritten'() {
        when:
        byte[] compiled = compile("""
            import grails.artefact.gsp.TagLibraryInvoker
            class ${className} implements TagLibraryInvoker {
                ${member}
                def index(${parameter}) {
                    ${body}
                    g.createLink(controller: 'book')
                }
            }
        """, className)

        then: 'the author meant their own g, not the tag library namespace'
        !references(compiled, 'org/grails/taglib/CompiledTagInvocation')

        where:
        description         | className            | member            | parameter | body
        'a local variable'  | 'LocalShadow'        | ''                | ''        | 'def g = new Expando(createLink: { Map a -> "x" })'
        'a parameter'       | 'ParameterShadow'    | ''                | 'Object g'| ''
        'a field'           | 'FieldShadow'        | 'Object g'        | ''        | ''
        'a typed local'     | 'TypedLocalShadow'   | ''                | ''        | 'Object g = null'
    }

    @Unroll
    void 'a getter named like a namespace shadows it: #description'() {
        when:
        byte[] compiled = compile('''
            import grails.artefact.gsp.TagLibraryInvoker
            class SUBJECT implements TagLibraryInvoker {
                GETTER
                def index() {
                    this.g.createLink(controller: 'book')
                }
            }
        '''.replace('SUBJECT', className).replace('GETTER', getter), className)

        then: 'the getter answers to the name, so it is not the tag library namespace'
        !references(compiled, 'org/grails/taglib/CompiledTagInvocation')

        where:
        description            | className         | getter
        'a getX getter'        | 'GetterShadow'    | 'Object getG() { null }'
        'a boolean isX getter' | 'BooleanIsShadow' | 'boolean isG() { true }'
    }

    void 'a namespace shadowed by an inherited getter is not rewritten'() {
        when:
        byte[] compiled = compile('''
            import grails.artefact.gsp.TagLibraryInvoker
            class GetterBase {
                Object getG() { null }
            }
            class InheritedGetterShadow extends GetterBase implements TagLibraryInvoker {
                def index() {
                    this.g.createLink(controller: 'book')
                }
            }
        ''', 'InheritedGetterShadow')

        then:
        !references(compiled, 'org/grails/taglib/CompiledTagInvocation')
    }
    void 'an unqualified call inside a closure is left for the delegate'() {
        when: 'the shape a controller writes for request.withFormat { form multipartForm { } }'
        boolean compiled = compileAndScanAll('''
            import grails.artefact.gsp.TagLibraryInvoker
            class DelegatingCaller implements TagLibraryInvoker {
                def index() {
                    withSomething {
                        link(controller: 'book')
                    }
                }
                def withSomething(Closure body) { body() }
            }
        ''', 'DelegatingCaller')

        then: 'a closure is given a delegate when it runs, and the delegate may answer to the name'
        !compiled
    }

    void 'a namespaced call inside a closure is still rewritten'() {
        when: 'the source named the tag library, so no delegate can claim it'
        boolean compiled = compileAndScanAll('''
            import grails.artefact.gsp.TagLibraryInvoker
            class QualifiedInClosureCaller implements TagLibraryInvoker {
                def index() {
                    withSomething {
                        g.link(controller: 'book')
                    }
                }
                def withSomething(Closure body) { body() }
            }
        ''', 'QualifiedInClosureCaller')

        then: 'so a tag body and a withFormat block keep the faster path for the calls that are tags'
        compiled
    }

    void 'a call whose arguments are not a tag shape is not rewritten'() {
        when: 'two arguments whose first is not a map, which a tag cannot be called with'
        byte[] compiled = compile('''
            import grails.artefact.gsp.TagLibraryInvoker
            class OverloadedCaller implements TagLibraryInvoker {
                def index() {
                    g.createLink('2026-08-19', 'yyyy')
                }
            }
        ''', 'OverloadedCaller')

        then: 'the invocation would drop both arguments, so the call is left to dispatch as it did'
        !references(compiled, 'org/grails/taglib/CompiledTagInvocation')
    }

    void 'a call on the namespace itself is still rewritten'() {
        when: 'nothing in scope claims the name'
        byte[] compiled = compile('''
            import grails.artefact.gsp.TagLibraryInvoker
            class UnshadowedCaller implements TagLibraryInvoker {
                def index() {
                    g.createLink(controller: 'book')
                }
            }
        ''', 'UnshadowedCaller')

        then:
        references(compiled, 'org/grails/taglib/CompiledTagInvocation')
    }

    void 'a call in an inherited method is not rewritten through a subclass'() {
        when: 'only the subclass can call tags; the superclass method is not its code to change'
        byte[] compiled = compile('''
            import grails.artefact.gsp.TagLibraryInvoker
            class PlainBase {
                def helper() {
                    g.createLink(controller: 'book')
                }
            }
            class TagAwareSubclass extends PlainBase implements TagLibraryInvoker {
                def index() { helper() }
            }
        ''', 'PlainBase')

        then: 'the superclass class file is untouched'
        !references(compiled, 'org/grails/taglib/CompiledTagInvocation')
    }

    /**
     * A closure body compiles into a class of its own, so a call written inside one is not in the
     * enclosing class file. Everything the compilation emitted is scanned.
     *
     * @param source the source to compile
     * @param className the class it declares
     * @return whether any emitted class references the invocation entry point
     */
    private boolean compileAndScanAll(String source, String className) {
        Path sourceFile = tempDir.resolve(className + '.groovy')
        sourceFile.toFile().text = source
        Path outputDir = Files.createDirectories(tempDir.resolve('all-' + className))

        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.targetDirectory = outputDir.toFile()
        configuration.parameters = true
        CompilationUnit unit = new CompilationUnit(configuration, null,
                new GroovyClassLoader(getClass().classLoader, configuration))
        unit.addSource(sourceFile.toFile())
        unit.compile()

        List<Path> emitted = Files.walk(outputDir).filter { it.toString().endsWith('.class') }.toList()
        assert emitted.size() > 1, "expected a closure class alongside ${className}, got ${emitted*.fileName}"
        emitted.any { references(Files.readAllBytes(it), 'org/grails/taglib/CompiledTagInvocation') }
    }

    private static boolean references(byte[] classBytes, String internalName) {
        new String(classBytes, 'ISO-8859-1').contains(internalName)
    }

    private byte[] compile(String source, String className) {
        Path sourceFile = tempDir.resolve(className + '.groovy')
        sourceFile.toFile().text = source
        Path outputDir = Files.createDirectories(tempDir.resolve('classes-' + className))

        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.targetDirectory = outputDir.toFile()
        configuration.parameters = true
        CompilationUnit unit = new CompilationUnit(configuration, null,
                new GroovyClassLoader(getClass().classLoader, configuration))
        unit.addSource(sourceFile.toFile())
        unit.compile()

        Files.readAllBytes(outputDir.resolve(className + '.class'))
    }
}
