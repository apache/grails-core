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

import grails.artefact.Artefact
import grails.testing.web.UrlMappingsUnitTest
import spock.lang.Specification
import spock.lang.Unroll

class NamespaceInferenceTagLibSpec extends Specification implements UrlMappingsUnitTest<NamespaceInferenceTagLibUrlMappings> {

    @Override
    Class[] getControllersToMock() {
        [
                org.grails.web.taglib.nsinference.root.AuthorController,
                org.grails.web.taglib.nsinference.root.HomeController,
                org.grails.web.taglib.nsinference.admin.AuthorController,
                org.grails.web.taglib.nsinference.admin.BookController,
                org.grails.web.taglib.nsinference.admin.ReportController,
                org.grails.web.taglib.nsinference.sales.ReportController
        ] as Class[]
    }

    def setup() {
        def linkGenerator = applicationContext.getBean('grailsLinkGenerator')
        linkGenerator.grailsApplication = grailsApplication
        linkGenerator.resetControllerNamespaceCache()
        bindRequest('page', 'admin')
    }

    @Unroll
    def "#tagName respects namespace inference for #description"() {
        given:
        bindRequest(requestController, requestNamespace)

        expect:
        applyTemplate(template) == expectedOutput

        where:
        tagName        | description                    | requestController | requestNamespace | template                                                                 | expectedOutput
        'g:createLink' | 'same namespace controller'    | 'page'            | 'admin'          | '<g:createLink controller="book" action="list" />'                    | '/admin/book/list'
        'g:link'       | 'same namespace controller'    | 'page'            | 'admin'          | '<g:link controller="book" action="list">Book</g:link>'               | '<a href="/admin/book/list">Book</a>'
        'g:createLink' | 'unique namespaced controller' | 'page'            | 'sales'          | '<g:createLink controller="book" action="index" />'                   | '/admin/book/index'
        'g:createLink' | 'root and namespace ambiguity' | 'page'            | 'sales'          | '<g:createLink controller="author" action="index" />'                 | '/author/index'
        'g:createLink' | 'multiple namespace ambiguity' | 'page'            | 'editor'         | '<g:createLink controller="report" action="index" />'                 | '/report/index'
        'g:link'       | 'explicit namespace'           | 'page'            | 'admin'          | '<g:link controller="report" action="index" namespace="sales">Report</g:link>' | '<a href="/sales/report/index">Report</a>'
        'g:createLink' | 'empty namespace opt-out'      | 'page'            | 'admin'          | '<g:createLink controller="book" action="index" namespace="" />'    | '/book/index'
        'g:createLink' | 'null namespace opt-out'       | 'page'            | 'admin'          | '<g:createLink controller="book" action="index" namespace="${null}" />' | '/book/index'
        'g:createLink' | 'same controller request'      | 'book'            | 'admin'          | '<g:createLink controller="book" action="index" />'                   | '/admin/book/index'
    }

    def "form tags infer namespace for their action URLs"() {
        given:
        bindRequest('page', 'admin')

        expect:
        applyTemplate('<g:form controller="book" action="list">Body</g:form>') ==
                '<form action="/admin/book/list" method="post" >Body</form>'
        applyTemplate('<g:formActionSubmit controller="book" action="list" value="Save" />') ==
                '<input type="submit" formaction="/admin/book/list" value="Save" />'
    }

    def "paginate links infer namespace for a controller in the current namespace"() {
        given:
        bindRequest('page', 'admin')

        when:
        String output = applyTemplate('<g:paginate next="Next" prev="Prev" max="10" total="20" controller="book" action="list" />')

        then:
        output.contains('href="/admin/book/list?offset=10&amp;max=10"')
        !output.contains('href="/book/list?offset=10&amp;max=10"')
    }

    def "sortableColumn links infer the current controller namespace"() {
        given:
        bindRequest('book', 'admin')
        webRequest.actionName = 'list'

        expect:
        applyTemplate('<g:sortableColumn property="title" title="Title" />') ==
                '<th class="sortable" ><a href="/admin/book/list?sort=title&amp;order=asc">Title</a></th>'
    }

    // g:include performs a real servlet include/forward to the target controller, which the taglib
    // unit harness cannot dispatch; its namespace inference is covered by the functional Geb specs in
    // grails-test-examples/namespaces (see the #bookInclude assertion).

    private void bindRequest(String controllerName, String namespace) {
        webRequest.controllerName = controllerName
        webRequest.controllerNamespace = namespace
        webRequest.actionName = 'index'
    }
}

@Artefact('UrlMappings')
class NamespaceInferenceTagLibUrlMappings {
    static mappings = {
        "/admin/$controller/$action?/$id?" {
            namespace = 'admin'
        }
        "/sales/$controller/$action?/$id?" {
            namespace = 'sales'
        }
        "/$controller/$action?/$id?"()
    }
}
