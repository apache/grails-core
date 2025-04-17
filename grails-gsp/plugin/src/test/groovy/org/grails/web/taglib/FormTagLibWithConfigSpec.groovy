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
