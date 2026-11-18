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

package gspstatic

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import grails.testing.mixin.integration.Integration
import org.grails.gsp.CompileStaticGroovyPage
import org.grails.gsp.GroovyPagesTemplateEngine
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

/**
 * An application whose pages are compiled statically, rendered through a running server.
 *
 * <p>Static compilation of a page is settled by the build rather than by the page, so what proves it
 * is an application that turned it on and pages that only compile if it happened. Each page here uses
 * something the dynamic path would have resolved at render time - a declared model, a name the
 * framework binds, a tag call, a typed local - and the class the page compiled to is checked as well,
 * because a page that renders correctly renders correctly either way.
 */
@Integration
class GspCompileStaticSpec extends Specification {

    @Autowired
    GroovyPagesTemplateEngine templateEngine

    void 'a page that declares its model is compiled statically and renders'() {
        when:
        String body = new URL("http://localhost:${serverPort}/demo/declared").text

        then: 'the model is read with the types it declared'
        body.contains('<p id="title">Ubik</p>')
        body.contains('<p id="pages">224</p>')

        and: 'arithmetic on a declared int is done on the int'
        body.contains('<p id="total">672</p>')

        and: 'a typed g:set declares a local and still writes the scope'
        body.contains('<p id="upper">UBIK</p>')
    }

    void 'a page reading the names the framework binds is compiled statically and renders'() {
        when:
        String body = new URL("http://localhost:${serverPort}/demo/frameworkNames?n=7").text

        then:
        body.contains('<p id="controller">demo</p>')
        body.contains('<p id="action">frameworkNames</p>')
        body.contains('<p id="flash">from flash</p>')

        and: 'params keeps the conversions it declares, so int() is not a dynamic call'
        body.contains('<p id="param">7</p>')

        and: 'a tag call still runs'
        body.contains('<p id="link">/demo/declared</p>')

        and: 'the names this PR newly binds are bound, servletContext to the same object as application'
        body.contains('<p id="servletContext">true</p>')
        body.contains('<p id="webRequest">true</p>')
    }

    void 'a page using closures and a tag library renders what it computed'() {
        when:
        String body = new URL("http://localhost:${serverPort}/demo/index").text

        then:
        body.contains('<li id="book-0">Dune has 412 pages</li>')
        body.contains('<li id="book-1">Emma has 474 pages</li>')
        body.contains('<p id="count">2</p>')
        body.contains('<p id="longest">Emma</p>')

        and: 'a tag library in its own namespace is reached'
        body.contains('<p id="shout">QUIET</p>')
    }

    void 'a page that declares a model is compiled statically because it declared one'() {
        expect: 'the model directive implies static compilation, so these hold whatever the build asked for'
        CompileStaticGroovyPage.isAssignableFrom(pageClassFor(view))

        where:
        view << ['/demo/index.gsp', '/demo/declared.gsp']
    }

    void 'a page that declares nothing is compiled statically because the build asked for it'() {
        expect: 'nothing in this page opts in, so it is static only while grails.compileStatic.gsp is set'
        CompileStaticGroovyPage.isAssignableFrom(pageClassFor('/demo/frameworkNames.gsp'))
    }

    void 'the page compiler the build forked wrote statically compiled classes'() {
        given: 'the classes compileGroovyPages actually wrote, not what the page locator resolves now'
        Path output = Paths.get('build', 'gsp-classes', 'main')

        expect:
        Files.isDirectory(output)

        when:
        List<Path> pages = Files.list(output).filter { it.fileName.toString().endsWith('_gsp.class') }.toList()

        then: 'which is the half of "it reaches both places a page is compiled" the other cases cannot see'
        pages.size() >= 3
        pages.every {
            new String(Files.readAllBytes(it), 'ISO-8859-1').contains('org/grails/gsp/CompileStaticGroovyPage')
        }
    }

    private Class<?> pageClassFor(String view) {
        templateEngine.createTemplate(view).metaInfo.pageClass
    }
}
