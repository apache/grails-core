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
package grails.plugin.formfields

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Specification

class DefaultFieldTemplateSpec extends Specification implements TagLibUnitTest<FormFieldsTagLib> {

    Map model = [:]

    void setup() {
        model.invalid = false
        model.label = 'label'
        model.property = 'property'
        model.required = false
        model.widget = '<input name="property">'
        views["/default/_wrapper.gsp"] = '''\
<g:set var="classes" value="fieldcontain "/>
<g:if test="${required}">
    <g:set var="classes" value="${classes + 'required'}"/>
</g:if>
<g:if test="${invalid}">
    <g:set var="classes" value="${classes + 'error'}"/>
</g:if>
<div class="${classes}">
    <label for="${prefix}${property}">${label}<g:if test="${required}"><span class="required-indicator">*</span></g:if></label>
    <%= widget %>
</div>'''
    }

    void "default rendering"() {
        when:
        Element root = renderRoot()

        then:
        root.hasClass('fieldcontain')

        and:
        Element label = root.selectFirst('label')
        label.text() == 'label'
        label.attr('for') == 'property'

        and:
        label.nextElementSibling().is('input[name=property]')
    }

    void "container marked as invalid"() {
        given:
        model.invalid = true

        expect:
        renderRoot().hasClass('error')
    }

    void "container marked as required"() {
        given:
        model.required = true

        when:
        Element root = renderRoot()

        then:
        root.hasClass('required')

        and:
        Element indicator = root.selectFirst('label .required-indicator')
        indicator.text() == '*'
    }

    private Element renderRoot() {
        String output = tagLib.renderDefaultField(model).toString()
        Jsoup.parseBodyFragment(output).body().children().first()
    }

}
