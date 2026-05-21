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

package org.apache.grails.web.layout

import com.opensymphony.module.sitemesh.RequestConstants
import com.opensymphony.module.sitemesh.html.util.CharArray
import com.opensymphony.module.sitemesh.parser.HTMLPageParser
import com.opensymphony.module.sitemesh.parser.TokenizedHTMLPage
import grails.artefact.Artefact
import grails.testing.web.UrlMappingsUnitTest
import org.grails.buffer.FastStringWriter
import org.grails.core.io.MockStringResourceLoader
import spock.lang.Specification

class RenderTagLibTests  extends Specification implements UrlMappingsUnitTest<RenderTagLibTestUrlMappings> {
    void testPageProperty() {

        def template = '<g:pageProperty name="foo.bar" />'

        def head = ""
        def page = new TokenizedHTMLPage([] as char[], new CharArray(0), new CharArray(0))
        request[RequestConstants.PAGE] = page

        page.addProperty('foo.bar', 'good')

        assertOutputEquals('good', template)

        template = '<g:pageProperty name="foo.bar" writeEntireProperty="true" />'
        assertOutputEquals(' bar="good"', template)
    }

    void testIfPageProperty() {
        def template = '<g:ifPageProperty name="foo.bar">Hello</g:ifPageProperty>'

        def page = new TokenizedHTMLPage([] as char[], new CharArray(0), new CharArray(0))
        request[RequestConstants.PAGE] = page

        page.addProperty('foo.bar', 'true')

        assertOutputEquals('Hello', template)

        template = '<g:ifPageProperty name="page.contentbuffer">Hello 2</g:ifPageProperty>'

        def smpage = new GSPGrailsLayoutPage()
        request[RequestConstants.PAGE] = smpage

        def sw = new FastStringWriter()
        sw.write('true')
        smpage.setContentBuffer('contentbuffer', sw.getBuffer())

        assertOutputEquals('Hello 2', template)
    }

    // TODO: There is no direct equivalent to this in sitemesh3
    void testApplyLayout() {
        def decoratorMapper = new GrailsLayoutDecoratorMapper()
        decoratorMapper.groovyPageLayoutFinder = appCtx.groovyPageLayoutFinder
        FactoryHolder.setFactory([
                getDecoratorMapper: { -> decoratorMapper }
        ] as com.opensymphony.module.sitemesh.Factory)
        def resourceLoader = new MockStringResourceLoader()
        resourceLoader.registerMockResource('/layouts/layout.gsp', '<layoutapplied><g:layoutTitle /> - <g:layoutBody/></layoutapplied>')
        appCtx.groovyPagesTemplateEngine.groovyPageLocator.addResourceLoader(resourceLoader)
        def template = '<g:applyLayout name="layout"><html><head><title>title here</title></head><body>Hello world!</body></html></g:applyLayout>'
        assertOutputEquals('<layoutapplied>title here - Hello world!</layoutapplied>', template)
    }

    // TODO: There is no direct equivalent to this in sitemesh3
    void testApplyLayoutParse() {
        def decoratorMapper = new GrailsLayoutDecoratorMapper()
        decoratorMapper.groovyPageLayoutFinder = appCtx.groovyPageLayoutFinder
        FactoryHolder.setFactory([
                getDecoratorMapper: { -> decoratorMapper },
                getPageParser     : { String contentType -> new HTMLPageParser() }
        ] as com.opensymphony.module.sitemesh.Factory)
        def resourceLoader = new MockStringResourceLoader()
        resourceLoader.registerMockResource('/layouts/layout.gsp', '<layoutapplied><g:layoutTitle /> - <g:layoutBody/></layoutapplied>')
        appCtx.groovyPagesTemplateEngine.groovyPageLocator.addResourceLoader(resourceLoader)
        def template = '<g:applyLayout name="layout" parse="${true}"><html><head><${"title"}>title here</${"title"}></head><body>Hello world!</body></html></g:applyLayout>'
        assertOutputEquals('<layoutapplied>title here - Hello world!</layoutapplied>', template)

        template = '<g:applyLayout name="layout" parse="false"><html><head><${"title"}>title here</${"title"}></head><body>Hello world!</body></html></g:applyLayout>'
        assertOutputEquals('<layoutapplied> - Hello world!</layoutapplied>', template)
    }
}

@Artefact('UrlMappings')
class RenderTagLibTestUrlMappings {

    static mappings = {
        name claimTab: "/claim/$id/$action" {
            controller = 'Claim'
            constraints { id(matches: /\d+/) }
        }
        "/userAdmin/$id?" {
            controller = 'admin'
            namespace = 'users'
        }
        "/reportAdmin/$id?" {
            controller = 'admin'
            namespace = 'reports'
        }
    }
}
