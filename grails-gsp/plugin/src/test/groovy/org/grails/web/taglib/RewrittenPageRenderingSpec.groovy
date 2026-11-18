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

import grails.testing.web.taglib.TagLibUnitTest
import org.grails.plugins.web.taglib.ApplicationTagLib
import spock.lang.Specification

/**
 * A rewritten tag call has to render exactly what the dispatched one rendered - the same markup, the
 * same encoding, the same handling of a body - and a page that has not given up dynamic resolution has
 * to keep resolving names against the model it was given.
 */
class RewrittenPageRenderingSpec extends Specification implements TagLibUnitTest<ApplicationTagLib> {

    private static final String STATIC = '<%@ page compileStatic="true" %>'

    void 'a rewritten expression renders what the dispatched one rendered'() {
        expect:
        applyTemplate(STATIC + '''${g.createLink(controller: 'book', action: 'show')}''') ==
                applyTemplate('''${g.createLink(controller: 'book', action: 'show')}''')
    }

    void 'a rewritten expression carrying a body renders the body'() {
        when:
        String rendered = applyTemplate(STATIC + '''${g.link(controller: 'book') { 'inside' }}''')

        then:
        rendered == applyTemplate('''${g.link(controller: 'book') { 'inside' }}''')
        rendered.contains('inside')
    }

    void 'a rewritten expression encodes its output the same way'() {
        given: 'the output of a tag goes through the page codec, which the invocation must not bypass'
        String markup = '''${g.message(code: 'nonexistent', default: '<b>bold</b>')}'''

        expect:
        applyTemplate(STATIC + markup) == applyTemplate(markup)
    }

    void 'a rewritten expression whose attributes are built at runtime renders the same'() {
        given: 'forwarded arguments are adapted by the same rules dynamic dispatch applies'
        String markup = '''<% def attrs = [controller: 'book', action: 'show'] %>${g.createLink(attrs)}'''

        expect:
        applyTemplate(STATIC + markup) == applyTemplate(markup)
    }

    void 'a model attribute named after a namespace still wins in a page that is not compiled statically'() {
        given: 'a page resolves a name against its model before any tag library, and always has'
        Map model = [g: [createLink: { Map attrs -> 'from the model' }]]

        when:
        String rendered = applyTemplate('''${g.createLink(controller: 'book')}''', model)

        then:
        rendered == 'from the model'
    }

    void 'a model supplied namespace answering to a name no tag library declares still renders'() {
        given: 'the receiver is the model, so the name never had to be a tag'
        Map model = [g: [custom: { Map attrs -> 'from the model' }]]

        when:
        String rendered = applyTemplate('''${g.custom(code: 'x')}''', model)

        then:
        rendered == 'from the model'
    }

    void 'a tag written as markup renders the same in a statically compiled page'() {
        expect:
        applyTemplate(STATIC + '''<g:createLink controller="book" action="show"/>''') ==
                applyTemplate('''<g:createLink controller="book" action="show"/>''')
    }
}
