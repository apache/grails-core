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

/**
 * A controller can call tags too, through the tag library invoker trait rather than by being a tag
 * library, so the same rewriting has to reach it.
 *
 * <p>Checked in the class file, because a rewritten call and a dynamic one produce the same output.
 *
 * <p>A controller declared by convention, under {@code grails-app/controllers}, is not covered here.
 * Driving that path needs the artefact injector to recognise the source by its location, which depends
 * on where the compilation happens rather than on what is being compiled, and a version of this spec
 * that compiled a file into a temporary {@code grails-app/controllers} directory passed on one
 * operating system and failed on two others. The convention path is exercised for real by every
 * application under {@code grails-test-examples}, whose controllers live in that directory and whose
 * tag calls are compiled; what is pinned here is the trait, which is what the rewriting actually keys
 * on, and the annotated case below, which the trait reaches too late.
 */
class ControllerTagCallRewriteSpec extends Specification {

    @TempDir
    Path tempDir

    void 'a class that can call tags has its tag calls compiled into invocations'() {
        when: 'a class carrying the tag library invoker trait, as a controller does'
        byte[] compiled = compile('''
            import grails.artefact.gsp.TagLibraryInvoker
            class TagCallingController implements TagLibraryInvoker {
                def index() {
                    g.createLink(controller: 'book')
                }
            }
        ''', 'TagCallingController')

        then:
        references(compiled, 'org/grails/taglib/CompiledTagInvocation')
    }

    void 'a class that cannot call tags is left alone'() {
        when: 'no tag library invoker trait, so g is not a namespace here'
        byte[] compiled = compile('''
            class PlainService {
                def index() {
                    g.createLink(controller: 'book')
                }
            }
        ''', 'PlainService')

        then:
        !references(compiled, 'org/grails/taglib/CompiledTagInvocation')
    }

    void 'a controller declared by annotation outside that directory is rewritten too'() {
        when: 'the trait arrives from a local transform, which runs after every global one'
        byte[] compiled = compileAt('src/main/groovy/demo', '''
            package demo

            import grails.artefact.Artefact

            @Artefact('Controller')
            class AnnotatedController {
                def index() {
                    g.createLink(controller: 'book')
                }
            }
        ''', 'AnnotatedController', 'demo')

        then: 'the rewrite reads a class the traits have already been applied to, so where the source ' +
                'sat makes no difference to it'
        references(compiled, 'org/grails/taglib/CompiledTagInvocation')

        and: 'and it really is a controller'
        references(compiled, 'grails/artefact/gsp/TagLibraryInvoker')
    }

    void 'a controller recognised by its directory is rewritten'() {
        when: 'no annotation, so the trait comes from the artefact injector recognising the location'
        byte[] compiled = compileAt('grails-app/controllers/demo', '''
            package demo

            class ConventionController {
                def index() {
                    g.createLink(controller: 'book')
                }
            }
        ''', 'ConventionController', 'demo')

        then: 'which is the shape the rewrite has to handle, and the one it used to get wrong'
        references(compiled, 'org/grails/taglib/CompiledTagInvocation')

        and: 'the trait it decides on really did arrive'
        references(compiled, 'grails/artefact/gsp/TagLibraryInvoker')
    }

    private static boolean references(byte[] classBytes, String internalName) {
        new String(classBytes, 'ISO-8859-1').contains(internalName)
    }

    private byte[] compileAt(String relativeDir, String source, String className, String packageName) {
        Path sourceDir = Files.createDirectories(tempDir.resolve(relativeDir))
        Path sourceFile = sourceDir.resolve(className + '.groovy')
        sourceFile.toFile().text = source
        Path outputDir = Files.createDirectories(tempDir.resolve('out-' + className))

        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.targetDirectory = outputDir.toFile()
        configuration.parameters = true
        CompilationUnit unit = new CompilationUnit(configuration, null,
                new GroovyClassLoader(getClass().classLoader, configuration))
        unit.addSource(sourceFile.toFile())
        unit.compile()

        Files.readAllBytes(outputDir.resolve(packageName.replace('.', '/')).resolve(className + '.class'))
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
