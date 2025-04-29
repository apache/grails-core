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

import grails.testing.web.taglib.TagLibUnitTest
import jodd.lagarto.dom.jerry.Jerry
import spock.lang.Specification
import static jodd.lagarto.dom.jerry.Jerry.jerry

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
	
	static Jerry $(String html) {
		jerry(html).children()
	}
	
	void "default rendering"() {
		when:
		def output = tagLib.renderDefaultField(model)

		then:
		def root = $(output.toString())
		root.is('div.fieldcontain')

		and:
		def label = root.find('label')
		label.text() == 'label'
		label.attr('for') == 'property'
		
		and:
		label.next().is('input[name=property]')
	}

	void "container marked as invalid"() {
		given:
		model.invalid = true

		when:
		def output = tagLib.renderDefaultField(model)
		
		then:
		$(output.toString()).hasClass('error')
	}

	void "container marked as required"() {
		given:
		model.required = true

		when:
		def output = tagLib.renderDefaultField(model)

		then:
		def root = $(output.toString())
		root.hasClass('required')
		
		and:
		def indicator = root.find('label .required-indicator')
		indicator.size()
		indicator.text() == '*'
	}

}
