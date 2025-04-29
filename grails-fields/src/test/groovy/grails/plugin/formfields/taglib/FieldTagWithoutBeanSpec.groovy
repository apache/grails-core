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
package grails.plugin.formfields.taglib

import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Issue
import grails.plugin.formfields.*

@Issue('https://github.com/grails-fields-plugin/grails-fields/pull/16')
class FieldTagWithoutBeanSpec extends AbstractFormFieldsTagLibSpec implements TagLibUnitTest<FormFieldsTagLib> {

	def mockFormFieldsTemplateService = Mock(FormFieldsTemplateService)

	def setup() {
		mockFormFieldsTemplateService.findTemplate(_, 'wrapper', null, null) >> [path: '/_fields/default/wrapper']
        mockFormFieldsTemplateService.getTemplateFor('wrapper') >> "wrapper"
        mockFormFieldsTemplateService.getTemplateFor('widget') >> "widget"
        mockFormFieldsTemplateService.getTemplateFor('displayWrapper') >> "displayWrapper"
        mockFormFieldsTemplateService.getTemplateFor('displayWidget') >> "displayWidget"
		tagLib.formFieldsTemplateService = mockFormFieldsTemplateService
	}

	void 'f:field can work without a bean attribute'() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '${property}'

		expect:
		applyTemplate('<f:field property="name"/>') == 'name'
	}

	void 'label is the natural property name if there is no bean attribute'() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '${label}'

		expect:
		applyTemplate('<f:field property="name"/>') == 'Name'
	}

}