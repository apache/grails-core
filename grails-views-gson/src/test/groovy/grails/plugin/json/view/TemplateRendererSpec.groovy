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
package grails.plugin.json.view

import groovy.json.StreamingJsonBuilder

import grails.plugin.json.view.api.GrailsJsonViewHelper
import grails.plugin.json.view.api.internal.TemplateRenderer
import spock.lang.Specification

/**
 * Created by graemerocher on 13/04/16.
 */
class TemplateRendererSpec extends Specification {

    void "the render overloads of the view helper are available on the template renderer and forward to it"() {
        given: "a template renderer"
        def mockViewHelper = Mock(GrailsJsonViewHelper)
        def tmpl = new TemplateRenderer(mockViewHelper)
        def o = new Object()
        def customizer = { -> }

        when:
        tmpl.render(template: 'foo')
        tmpl.render(o)
        tmpl.render(o, [includes: ['name']])
        tmpl.render(o, customizer)
        tmpl.render(o, [excludes: ['name']], customizer)

        then:
        1 * mockViewHelper.render([template: 'foo'])
        1 * mockViewHelper.render(o)
        1 * mockViewHelper.render(o, [includes: ['name']])
        1 * mockViewHelper.render(o, customizer)
        1 * mockViewHelper.render(o, [excludes: ['name']], customizer)
        0 * _
    }

    void "the inline overloads of the view helper are available on the template renderer and forward to it"() {
        given: "a template renderer"
        def mockViewHelper = Mock(GrailsJsonViewHelper)
        def tmpl = new TemplateRenderer(mockViewHelper)
        def o = new Object()
        def customizer = { -> }
        def delegate = Mock(StreamingJsonBuilder.StreamingJsonDelegate)

        when:
        tmpl.inline(o)
        tmpl.inline(o, [includes: ['name']])
        tmpl.inline(o, customizer)
        tmpl.inline(o, [excludes: ['name']], customizer)
        tmpl.inline(o, [excludes: ['id']], customizer, delegate)

        then:
        1 * mockViewHelper.inline(o)
        1 * mockViewHelper.inline(o, [includes: ['name']])
        1 * mockViewHelper.inline(o, customizer)
        1 * mockViewHelper.inline(o, [excludes: ['name']], customizer)
        1 * mockViewHelper.inline(o, [excludes: ['id']], customizer, delegate)
        0 * _
    }

    void "the link and message methods of the view helper are available on the template renderer and forward to it"() {
        given: "a template renderer"
        def mockViewHelper = Mock(GrailsJsonViewHelper)
        def tmpl = new TemplateRenderer(mockViewHelper)

        when:
        def results = [
                tmpl.message(code: 'foo'),
                tmpl.resource(dir: 'css'),
                tmpl.link(controller: 'book'),
                tmpl.link([controller: 'author'], 'UTF-8'),
                tmpl.getDefaultNamespace('book', 'myPlugin'),
                tmpl.resolveNamespace('book', 'myPlugin', [namespace: 'admin']),
                tmpl.contextPath,
                tmpl.serverBaseURL
        ]

        then:
        1 * mockViewHelper.message([code: 'foo']) >> 'message'
        1 * mockViewHelper.resource([dir: 'css']) >> 'resource'
        1 * mockViewHelper.link([controller: 'book']) >> 'link'
        1 * mockViewHelper.link([controller: 'author'], 'UTF-8') >> 'encoded link'
        1 * mockViewHelper.getDefaultNamespace('book', 'myPlugin') >> 'default namespace'
        1 * mockViewHelper.resolveNamespace('book', 'myPlugin', [namespace: 'admin']) >> 'namespace'
        1 * mockViewHelper.getContextPath() >> '/context'
        1 * mockViewHelper.getServerBaseURL() >> 'http://localhost'
        0 * _
        results == ['message', 'resource', 'link', 'encoded link', 'default namespace', 'namespace', '/context', 'http://localhost']
    }

    void "the template renderer is itself a view helper"() {
        expect:
        new TemplateRenderer(Mock(GrailsJsonViewHelper)) instanceof GrailsJsonViewHelper
    }

    void "Test template renderer calls the correct render method"() {
        given:"A template renderer"

        def mockViewHelper = Mock(GrailsJsonViewHelper)
        def tmpl = new TemplateRenderer(mockViewHelper)

        def o = new Object()
        when:
        tmpl.foo(o)

        then:
        1 * mockViewHelper.render([template:"foo", model:[foo:o, object:o]])

        when:
        tmpl."/foo/foo"(o)

        then:
        1 * mockViewHelper.render([template:"/foo/foo", model:[foo:o, object: o]])

        when:
        tmpl."/foo/foo"(null)

        then:
        0 * mockViewHelper.render([template:"/foo/foo", model:[foo:o]])

        when:
        tmpl.foo(null)

        then:
        0 * mockViewHelper.render([template:"foo", model:[foo:null]])

        when:
        tmpl.foo([o])

        then:
        1 * mockViewHelper.render([template:"foo", var:'foo', collection:[o]])

        when:
        tmpl."/foo/foo"([o])

        then:
        1 * mockViewHelper.render([template:"/foo/foo", var:'foo', collection:[o]])

        when:
        tmpl."/foo/foo"("bar", [o])

        then:
        1 * mockViewHelper.render([template:"/foo/foo", var:'bar', collection:[o]])

        when:
        tmpl."/foo/foo"("bar", [o], [foo:null])

        then:
        1 * mockViewHelper.render([template:"/foo/foo", model:[foo:null], collection:[o], var:'bar'])

        when:
        tmpl."/foo/foo"([o], [foo:null])

        then:
        1 * mockViewHelper.render([template:"/foo/foo", model:[foo:null], collection:[o], var:'foo'])
    }
}
