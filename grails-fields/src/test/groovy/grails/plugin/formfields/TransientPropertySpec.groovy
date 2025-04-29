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

import grails.plugin.formfields.taglib.AbstractFormFieldsTagLibSpec
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Issue
import grails.plugin.formfields.mock.User

@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/87')
class TransientPropertySpec extends AbstractFormFieldsTagLibSpec implements TagLibUnitTest<FormFieldsTagLib> {

    FormFieldsTemplateService mockFormFieldsTemplateService = Mock(FormFieldsTemplateService)
    User userInstance

    def setupSpec() {
        mockDomain(User)
    }

    def setup() {
        mockFormFieldsTemplateService.findTemplate(_, 'wrapper', null, null) >> [path: '/_fields/default/wrapper']
        mockFormFieldsTemplateService.getTemplateFor('wrapper') >> "wrapper"
        tagLib.formFieldsTemplateService = mockFormFieldsTemplateService

        userInstance = new User(email: 'rob@freeside.co', password: 'yuonocanhaz', confirmPassword: 'yuonocanhaz').save(failOnError: true)
    }

    void 'transient properties can be rendered by f:field'() {
        given:
        views["/_fields/default/_wrapper.gsp"] = '${value}'

        when:
        def output = applyTemplate('<f:field bean="userInstance" property="confirmPassword"/>', [userInstance: userInstance])

        then:
        output == userInstance.confirmPassword
    }
}
