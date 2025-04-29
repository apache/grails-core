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
import org.grails.config.PropertySourcesConfig
import org.grails.plugins.web.taglib.FormTagLib
import spock.lang.Specification


/**
 * Tests for the FormTagLib.groovy file which contains tags to help with the                                         l
 * creation of HTML forms
 *
 * Please note tests this test users doWithConfig to override special configuration settings for tests that need this
 *
 * @author Graeme
 * @author rvanderwerf
 */
class FormTagLibWithConfigSpec extends Specification implements TagLibUnitTest<FormTagLib> {

    Closure doWithConfig() {{ PropertySourcesConfig config ->
        config.merge(
            ['grails':
                 ['tags':
                      ['booleanToAttributes':
                           ['disabled', 'checked', 'readonly', 'required', 'bogus']
                      ]
                 ]
            ]
        )
    }}

    def testTextFieldTagWithNonBooleanAttributesAndConfig() {
        when:

        def template = '<g:textField name="testField" value="1" disabled="false" checked="false" readonly="false" required="false" bogus="false" />'
        String output = applyTemplate(template)

        then:
        assert output == '<input type="text" name="testField" value="1" id="testField" />'

        when:
        template = '<g:textField name="testField" value="1" disabled="true" checked="true" readonly="true" required="true" bogus="true" />'
        output = applyTemplate(template)

        then:
        assert output == '<input type="text" name="testField" value="1" disabled="disabled" checked="checked" readonly="readonly" required="required" bogus="bogus" id="testField" />'

    }

}
