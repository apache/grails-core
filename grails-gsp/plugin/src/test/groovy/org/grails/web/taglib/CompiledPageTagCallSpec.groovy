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

import org.grails.gsp.compiler.GroovyPageCompiler
import spock.lang.Specification
import spock.lang.TempDir

/**
 * A page reaches a tag written in an expression through the namespace dispatcher, which resolves the
 * tag by name every time the page renders. Where the namespace and the tag are both written in the
 * page and the index knows them, there is nothing left to resolve, so the call is compiled the same
 * way it is in a tag library.
 *
 * <p>Compiled to disk rather than in memory, because what is being asserted is what was compiled and
 * the dynamic route renders identically.
 */
class CompiledPageTagCallSpec extends Specification {

    private static final String INVOCATION = 'org/grails/taglib/CompiledTagInvocation'

    @TempDir
    Path tempDir

    private static final String STATIC = '<%@ page compileStatic="true" %>'

    void 'a tag expression naming a known tag is compiled into an invocation'() {
        when:
        byte[] page = compilePage('known.gsp', STATIC + '''${g.createLink(controller: 'book')}''')

        then:
        references(page)
    }

    void 'a page that has not declared compileStatic keeps resolving its tags as before'() {
        when: 'such a page resolves a name against the model it was rendered with, which is not known here'
        byte[] page = compilePage('dynamic.gsp', '''${g.createLink(controller: 'book')}''')

        then: 'so a model attribute named after a namespace still wins, as it always did'
        !references(page)
    }

    void 'a tag expression inside page markup is compiled into an invocation'() {
        when: 'the expression sits in a block the page compiles into a closure'
        byte[] page = compilePage('nested.gsp',
                STATIC + '''<g:if test="${true}">${g.createLink(controller: 'book')}</g:if>''')

        then:
        references(page)
    }

    void 'an expression in a namespace no compiled tag library declares is left dynamic'() {
        when: 'such a namespace has to be declared to a statically compiled page, as it always did'
        byte[] page = compilePage('unknown-ns.gsp',
                '''<%@ page compileStatic="true" taglibs="somepluginns" %>${somepluginns.anything(a: 1)}''')

        then: 'it keeps resolving through the dispatcher, which is what a runtime tag library needs'
        !references(page)
    }

    void 'a page variable named after a namespace is that variable, not the namespace'() {
        when: 'the page put the name into its own binding, where it is resolved before any tag library'
        byte[] page = compilePage('shadowed.gsp', STATIC +
                '''<g:set var="g" value="${[createLink: { 'local' }]}"/>${g.createLink(controller: 'book')}''')

        then:
        !references(page)
    }

    void 'an unqualified call in a page is left to resolve against the binding'() {
        when: 'the model a page renders with is not visible when it is compiled'
        byte[] page = compilePage('unqualified.gsp', STATIC + '''${createLink(controller: 'book')}''')

        then:
        !references(page)
    }

    void 'a tag written as markup stays an ordinary invokeTag call'() {
        when: 'markup already compiles into a direct call naming the tag, so there is nothing to rewrite'
        byte[] page = compilePage('markup.gsp', STATIC + '''<g:createLink controller="book"/>''')

        then:
        !references(page)
    }

    private byte[] compilePage(String name, String contents) {
        Path viewsDir = Files.createDirectories(tempDir.resolve('views-' + name))
        Path targetDir = Files.createDirectories(tempDir.resolve('classes-' + name))
        viewsDir.resolve(name).toFile().text = contents

        GroovyPageCompiler compiler = new GroovyPageCompiler()
        compiler.viewsDir = viewsDir.toFile()
        compiler.targetDir = targetDir.toFile()
        compiler.srcFiles = [viewsDir.resolve(name).toFile()]
        compiler.compile()

        List<File> compiled = []
        collectClasses(targetDir.toFile(), compiled)
        assert compiled : "the page was not compiled to a class file"
        // The page and any closure it compiles into are read together: a tag call written inside
        // markup lands in a closure rather than in the page class itself.
        compiled.collect { File file -> file.bytes }.flatten() as byte[]
    }

    private static void collectClasses(File directory, List<File> into) {
        directory.listFiles()?.each { File file ->
            if (file.isDirectory()) {
                collectClasses(file, into)
            }
            else if (file.name.endsWith('.class')) {
                into << file
            }
        }
    }

    private static boolean references(byte[] classBytes) {
        new String(classBytes, 'ISO-8859-1').contains(INVOCATION)
    }
}
