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

import grails.plugin.json.view.api.GrailsJsonViewHelper
import grails.plugin.json.view.api.internal.TemplateRenderer
import spock.lang.Specification

/**
 * Created by graemerocher on 13/04/16.
 */
class TemplateRendererSpec extends Specification {

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
