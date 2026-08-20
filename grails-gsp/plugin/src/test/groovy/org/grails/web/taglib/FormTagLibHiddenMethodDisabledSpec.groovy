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

import grails.testing.web.UrlMappingsUnitTest

import org.grails.config.PropertySourcesConfig

import spock.lang.Specification

/**
 * With the hidden HTTP method override disabled, a form can no longer reach a PUT, PATCH or DELETE route --
 * browsers submit only GET and POST. The form tag therefore targets the POST variant routes a 'resources'
 * mapping generates in that mode, and stops emitting the '_method' field, which nothing would read.
 *
 * The enabled-by-default behaviour these contrast with is covered by {@link FormTagLibResourceTests}.
 */
class FormTagLibHiddenMethodDisabledSpec extends Specification implements UrlMappingsUnitTest<TestFormTagUrlMappings> {

    Closure doWithConfig() {{ PropertySourcesConfig config ->
        config.merge(['grails': ['web': ['hiddenmethod': ['filter': ['enabled': false]]]]])
    }}

    void 'an update form keeps its URL and drops the _method field'() {
        when: 'the update action, which the POST variant serves at the same member URL'
        String output = applyTemplate('<g:form resource="book" action="update" id="1"/>')

        then: 'the action is unchanged from the enabled case, so the template needs no edit'
        output == '<form action="/books/1" method="post" ></form>'
    }

    void 'an update form declared by method rather than action behaves the same'() {
        when:
        String output = applyTemplate('<g:form resource="book" id="1" method="PUT"/>')

        then:
        output == '<form action="/books/1" method="post" ></form>'
    }

    void 'a delete form targets the POST variant URL'() {
        when: 'the delete action, which needs a segment of its own'
        String output = applyTemplate('<g:form resource="book" action="delete" id="1"/>')

        then:
        output == '<form action="/books/1/delete" method="post" ></form>'
    }

    void 'a delete form declared by method targets the POST variant URL'() {
        when:
        String output = applyTemplate('<g:form resource="book" id="1" method="DELETE"/>')

        then:
        output == '<form action="/books/1/delete" method="post" ></form>'
    }

    void 'a patch form targets the update variant, which is what patch delegates to'() {
        when:
        String output = applyTemplate('<g:form resource="book" action="patch" id="1"/>')

        then:
        output == '<form action="/books/1" method="post" ></form>'
    }

    void 'a nested resource form targets the nested POST variant URL'() {
        when:
        String output = applyTemplate('<g:form resource="book/author" action="delete" id="2" params="[bookId:1]"/>')

        then:
        output == '<form action="/books/1/authors/2/delete" method="post" ></form>'
    }

    void 'a form given its target as a url map is retargeted too'() {
        when: 'the shape used by the Spring Security scaffolded views'
        String output = applyTemplate('<g:form url="[resource: \'book\', action: \'delete\', id: 1]" method="DELETE"/>')

        then:
        output == '<form action="/books/1/delete" method="post" ></form>'
    }

    void 'save and index forms are untouched'() {
        expect: 'POST and GET forms never involved the override'
        applyTemplate('<g:form resource="book" action="save"/>') ==
                '<form action="/books" method="post" ></form>'
    }

    void 'a literal URL is left alone, because there is no mapping to retarget against'() {
        when: 'nothing identifies a resource action, so no POST variant route exists'
        String output = applyTemplate('<g:form url="/foo/bar" method="delete"/>')

        then: 'the URL is emitted as given and the inert _method field is not written'
        output == '<form action="/foo/bar" method="post" ></form>'
    }
}
