package org.grails.web.taglib

import grails.core.GrailsUrlMappingsClass
import grails.testing.web.taglib.TagLibUnitTest
import grails.util.MockRequestDataValueProcessor
import org.grails.buffer.FastStringWriter
import org.grails.core.AbstractGrailsClass
import org.grails.core.artefact.UrlMappingsArtefactHandler
import org.grails.plugins.web.taglib.FormTagLib
import spock.lang.Specification

/**
 * Tests for the FormTagLib.groovy file which contains tags to help with the                                         l
 * creation of HTML forms
 *
 * Please note tests that require special config have been moved to FormTagLibWithConfigSpec you can test things
 * that require no special config here
 *
 * @author Graeme
 * @author rvanderwerf
 */
class FormTagLibTests extends Specification implements TagLibUnitTest<FormTagLib> {


    def setup() {
        tagLib.requestDataValueProcessor = new MockRequestDataValueProcessor()
    }

    def setupSpec() {
        def mappingsClosure = {
            "/admin/books"(controller:'books', namespace:'admin')
            "/books"(controller:'books')
        }
        grailsApplication.addArtefact(UrlMappingsArtefactHandler.TYPE, new FormTagLibTests.MockGrailsUrlMappingsClass(mappingsClosure))

    }

    def testFormNoNamespace() {
        when:
        def template = '<g:form controller="books"></g:form>'
        String output = applyTemplate(template)

        then:
        output == '<form action="/books" method="post" ><input type="hidden" name="requestDataValueProcessorHiddenName" value="hiddenValue" />\n</form>'
    }

    private static final class MockGrailsUrlMappingsClass extends AbstractGrailsClass implements GrailsUrlMappingsClass {
        Closure mappingClosure
        public MockGrailsUrlMappingsClass(Closure mappingClosure) {
            super(FormTagLibTests.class, "UrlMappings")
            this.mappingClosure = mappingClosure
        }
        @Override
        public Closure getMappingsClosure() {
            return mappingClosure
        }

        @Override
        public List getExcludePatterns() {
            return null
        }
    }

    def testFormTagWithAlternativeMethod() {
        unRegisterRequestDataValueProcessor()
        when:
        def template = '<g:form url="/foo/bar" method="delete"></g:form>'

        then:
        String output = ('<form action="/foo/bar" method="post" ><input type="hidden" name="_method" value="DELETE" id="_method" /></form>')
         output == applyTemplate(template)
    }

    def testFormTagWithAlternativeMethodAndRequestDataValueProcessor() {
        when:
        def template = '<g:form url="/foo/bar" method="delete"></g:form>'
        String output = applyTemplate(template)

        then:
        output == '<form action="/foo/bar" method="post" ><input type="hidden" name="_method" value="DELETE_PROCESSED_" id="_method" /><input type="hidden" name="requestDataValueProcessorHiddenName" value="hiddenValue" />\n</form>'
    }

    // test for GRAILS-3865
    def testHiddenFieldWithZeroValue() {
        when:
        unRegisterRequestDataValueProcessor()
        def template = '<g:hiddenField name="index" value="${0}" />'
        String output = applyTemplate('value="0"')

        then:
        output.contains('value="0"')
    }

    def testHiddenFieldWithZeroValueAndRequestDataValueProcessor() {
        when:
        def template = '<g:hiddenField name="index" value="${0}" />'
        String output = applyTemplate(template)

        then:
        output.contains('value="0_PROCESSED_"')
    }

    def testFormTagWithStringURL() {
        when:
        unRegisterRequestDataValueProcessor()
        def template = '<g:form url="/foo/bar"></g:form>'
        String output = applyTemplate(template)

        then:
        output == '<form action="/foo/bar" method="post" ></form>'
    }

    def testFormTagWithStringURLAndRequestDataValueProcessor() {
        when:
        def template = '<g:form url="/foo/bar"></g:form>'
        String output = applyTemplate(template)

        then:
        output == '<form action="/foo/bar" method="post" ><input type="hidden" name="requestDataValueProcessorHiddenName" value="hiddenValue" />\n</form>'
    }

    def testFormTagWithTrueUseToken() {
        when:
        unRegisterRequestDataValueProcessor()
        def template = '<g:form url="/foo/bar" useToken="true"></g:form>'
        String output = applyTemplate(template)

        then:
        output.contains('<form action="/foo/bar" method="post" >')
        output.contains('<input type="hidden" name="SYNCHRONIZER_TOKEN" value="')
        output.contains('<input type="hidden" name="SYNCHRONIZER_URI" value="')

        when:
        template = '<g:form url="/foo/bar" useToken="${2 * 3 == 6}"></g:form>'
        output = applyTemplate(template)

        then:
        output.contains('<form action="/foo/bar" method="post" >')
        output.contains('<input type="hidden" name="SYNCHRONIZER_TOKEN" value="')
        output.contains('<input type="hidden" name="SYNCHRONIZER_URI" value="')
    }

    def testFormTagWithTrueUseTokenAndRequestDataValueProcessor() {
        when:

        def template = '<g:form url="/foo/bar" useToken="true"></g:form>'
        String output = applyTemplate(template)

        then:
        output.contains('<form action="/foo/bar" method="post" >')
        output.contains('<input type="hidden" name="SYNCHRONIZER_TOKEN" value="')
        output.contains('<input type="hidden" name="SYNCHRONIZER_URI" value="')


        when:
        template = '<g:form url="/foo/bar" useToken="${2 * 3 == 6}"></g:form>'
        output = applyTemplate(template)

        then:
        output.contains('<form action="/foo/bar" method="post" >')
        output.contains('<input type="hidden" name="SYNCHRONIZER_TOKEN" value="')
        output.contains('<input type="hidden" name="SYNCHRONIZER_URI" value="')
    }

    def testFormTagWithNonTrueUseToken() {
        when:

        def template = '<g:form url="/foo/bar" useToken="false"></g:form>'
        String output = applyTemplate(template)

        then:
        output.contains('<form action="/foo/bar" method="post" >')
        !output.contains('SYNCHRONIZER_TOKEN')
        !output.contains('SYNCHRONIZER_URI')

        when:
        template = '<g:form url="/foo/bar" useToken="someNonTrueValue"></g:form>'
        output = applyTemplate(template)

        then:
        output.contains('<form action="/foo/bar" method="post" >')
        !output.contains('SYNCHRONIZER_TOKEN')
        !output.contains('SYNCHRONIZER_URI')

        when:
        template = '<g:form url="/foo/bar" useToken="${42 * 2112 == 3}"></g:form>'
        output = applyTemplate(template)

        then:
        output.contains('<form action="/foo/bar" method="post" >')
        !output.contains('SYNCHRONIZER_TOKEN')
        !output.contains('SYNCHRONIZER_URI')
    }

    def testTextFieldTag() {
        when:
        unRegisterRequestDataValueProcessor()
        def template = '<g:textField name="testField" value="1" />'
        String output = applyTemplate(template,[value:"1"])

        then:
        output == '<input type="text" name="testField" value="1" id="testField" />'

        when:
        template = '<g:textField name="testField" value="${value}" />'
        output = applyTemplate(template,[value:/foo > " & < '/])

        then:
        output == '<input type="text" name="testField" value="foo &gt; &quot; &amp; &lt; &#39;" id="testField" />'
    }

    def testTextFieldTagWithRequestDataValueProcessor() {

        when:
        String template = '<g:textField name="testField" value="1" />'
        String output = applyTemplate(template)

        then:
        output == '<input type="text" name="testField" value="1_PROCESSED_" id="testField" />'

        when:
        template = '<g:textField name="testField" value="${value}" />'
        output = applyTemplate(template, [value:/foo > " & < '/])

        then:
         output == '<input type="text" name="testField" value="foo &gt; &quot; &amp; &lt; &#39;_PROCESSED_" id="testField" />'
    }

    def testTextFieldTagWithNonBooleanAttributesAndNoConfig() {
        when:
        unRegisterRequestDataValueProcessor()
        def template = '<g:textField name="testField" value="1" disabled="false" checked="false" readonly="false" bogus="false" />'
        String output = applyTemplate(template)

        then:
        output == '<input type="text" name="testField" value="1" bogus="false" id="testField" />'

        when:
        template = '<g:textField name="testField" value="1" disabled="true" checked="true" readonly="true" required="true" bogus="true" />'
        output = applyTemplate(template)

        then:
        output == '<input type="text" name="testField" value="1" bogus="true" disabled="disabled" checked="checked" readonly="readonly" required="required" id="testField" />'
    }


    def testTextAreaWithBody() {
        when:
        unRegisterRequestDataValueProcessor()
        def template = '<g:textArea name="test">This is content</g:textArea>'
        String output = applyTemplate(template)

        then:
        output == '<textarea name="test" id="test" >This is content</textarea>'
    }

    def testTextAreaWithBodyAndRequestDataValueProcessor() {
        when:
        def template = '<g:textArea name="test">This is content</g:textArea>'
        String output = applyTemplate(template)

        then:
        output == '<textarea name="test" id="test" >This is content_PROCESSED_</textarea>'
    }

    def testPasswordTag() {
        when:
        unRegisterRequestDataValueProcessor()
        def template = '<g:passwordField name="myPassword" value="foo"/>'
        String output = applyTemplate(template)

        then:
        output == '<input type="password" name="myPassword" value="foo" id="myPassword" />'
    }

    def testPasswordTagWithRequestDataValueProcessor() {
        when:
        def template = '<g:passwordField name="myPassword" value="foo"/>'
        String output = applyTemplate(template)

        then:
        output =='<input type="password" name="myPassword" value="foo_PROCESSED_" id="myPassword" />'
    }

    def testFormWithURL() {
        when:
        unRegisterRequestDataValueProcessor()
        String output = tagLib.form(
                new TreeMap([
                        url: [controller: 'con', action: 'action'],
                        id: 'formElementId'
                ])
        )

        then:
        output == '<form action="/con/action" method="post" id="formElementId" ></form>'
    }

    def testFormWithURLAndRequestDataValueProcessor() {

        when:
        String output = tagLib.form(
                new TreeMap([
                        url: [controller: 'con', action: 'action'],
                        id: 'formElementId'
                ])
        )

        then:
        output == '<form action="/con/action" method="post" id="formElementId" ><input type="hidden" name="requestDataValueProcessorHiddenName" value="hiddenValue" />\n</form>'
    }

    def testFormActionSubmitWithController() {
        when:
        String output = tagLib.formActionSubmit([controller: 'con', id: 'formElementId', value: 'Submit'])

        then:
        output == '<input type="submit" formaction="/con" value="Submit" id="formElementId" />'
    }

    def testFormActionSubmitWithControllerAndAction() {
        when:
        String output = tagLib.formActionSubmit([controller: 'con', action: 'act', id: 'formElementId', value: 'Submit'])

        then:
        output == '<input type="submit" formaction="/con/act" value="Submit" id="formElementId" />'
    }

    def testFormActionSubmitWithURLAndNoParams() {
        when:
        unRegisterRequestDataValueProcessor()
        String output = tagLib.formActionSubmit(new TreeMap([url: [controller: 'con', action:'action'], id: 'formElementId', value: 'Submit']))

        then:
        output == '<input type="submit" formaction="/con/action" id="formElementId" value="Submit" />'
    }

    def testFormActionSubmitWithAURLAndRequestDataValueProcessor() {
        when:
        String output = tagLib.formActionSubmit(
            new TreeMap([
                url: [
                        controller: 'con',
                        action:'action',
                        params: [
                            requestDataValueProcessorParamName: 'paramValue'
                        ]
                ],
                id: 'formElementId',
                value: 'My Button'
            ])
        )

        then:
        output == '<input type="submit" formaction="/con/action" id="formElementId" value="My Button" />'
    }

    def testFormActionSubmitWithAURLAndWithoutRequestDataValueProcessor() {
        given:
        unRegisterRequestDataValueProcessor()

        when:
        String output = tagLib.formActionSubmit(
            new TreeMap([
                    url: [
                            controller: 'con',
                            action:'action',
                            params: [
                                    requestDataValueProcessorParamName: 'paramValue'
                            ]
                    ],
                    id: 'formElementId',
                    value: 'My Button'
            ])
        )

        then:
        output == '<input type="submit" formaction="/con/action?requestDataValueProcessorParamName=paramValue" id="formElementId" value="My Button" />'
    }

    def testActionSubmitWithoutAction() {

        when:
        unRegisterRequestDataValueProcessor()
        String output = tagLib.actionSubmit(new TreeMap([value:'Edit']))

        then:
        output == '<input type="submit" name="_action_Edit" value="Edit" />'
    }

    def testActionSubmitWithoutActionAndWithRequestDataValueProcessor() {

        when:
        String output = tagLib.actionSubmit(new TreeMap([value:'Edit']))

        then:
        output == '<input type="submit" name="_action_Edit" value="Edit_PROCESSED_" />'
    }

    def testActionSubmitWithAction() {

        when:
        unRegisterRequestDataValueProcessor()
        String output = tagLib.actionSubmit(new TreeMap([action:'Edit', value:'Some label for editing']))

        then:
        output == '<input type="submit" name="_action_Edit" value="Some label for editing" />'
    }

    def testActionSubmitWithActionAndRequestDataValueProcessor() {

        when:
        String output = tagLib.actionSubmit(new TreeMap([action:'Edit', value:'Some label for editing']))

        then:
        output == '<input type="submit" name="_action_Edit" value="Some label for editing_PROCESSED_" />'
    }

    /**
     * GRAILS-454 - Make sure that the 'name' attribute is ignored.
     */
    def testActionSubmitWithName() {

        when:
        unRegisterRequestDataValueProcessor()
        String output = tagLib.actionSubmit(new TreeMap([action:'Edit', value:'Some label for editing', name:'customName']))

        then:
        output == '<input type="submit" name="_action_Edit" value="Some label for editing" />'
    }

    def testActionSubmitWithAdditionalAttributes() {

        when:
        unRegisterRequestDataValueProcessor()
        String output = tagLib.actionSubmit(new TreeMap([action:'Edit', value:'Some label for editing', style:'width: 200px;']))

        then:
        output == '<input type="submit" name="_action_Edit" value="Some label for editing" style="width: 200px;" />'
    }

    def testActionSubmitImageWithoutAction() {

        when:
        unRegisterRequestDataValueProcessor()
        String output = tagLib.actionSubmitImage(new TreeMap([src:'edit.gif', value:'Edit']))

        then:
        output == '<input type="image" name="_action_Edit" value="Edit" src="edit.gif" />'
    }

    def testActionSubmitImageWithoutActionAndWithRequestDataValueProcessor() {

        when:
        String output = tagLib.actionSubmitImage(new TreeMap([src:'edit.gif', value:'Edit']))

        then:
        output == '<input type="image" name="_action_Edit" value="Edit_PROCESSED_" src="edit.gif?requestDataValueProcessorParamName=paramValue" />'
    }

    def testActionSubmitImageWithAction() {

        when:
        unRegisterRequestDataValueProcessor()
        String output = tagLib.actionSubmitImage(new TreeMap([src:'edit.gif', action:'Edit', value:'Some label for editing']))

        then:
        output == '<input type="image" name="_action_Edit" value="Some label for editing" src="edit.gif" />'
    }

    def testActionSubmitImageWithAdditionalAttributes() {

        when:
        unRegisterRequestDataValueProcessor()
        String output = tagLib.actionSubmitImage(new TreeMap([src:'edit.gif', action:'Edit', value:'Some label for editing', style:'border-line: 0px;']))

        then:
        output == '<input type="image" name="_action_Edit" value="Some label for editing" src="edit.gif" style="border-line: 0px;" />'
    }

    def testHtmlEscapingTextAreaTag() {

        when:
        unRegisterRequestDataValueProcessor()
        String output = tagLib.textArea([name: "testField", value: "<b>some text</b>"])

        then:
        output == '<textarea name="testField" id="testField" >&lt;b&gt;some text&lt;/b&gt;</textarea>'
    }

    def testTextAreaTag() {

        when:
        unRegisterRequestDataValueProcessor()
        String output = tagLib.textArea([name: "testField", value: "1"])

        then:
        output == '<textarea name="testField" id="testField" >1</textarea>'
    }

    def testPassingTheSameMapToTextField() {
        // GRAILS-8250
        when:
        unRegisterRequestDataValueProcessor()
        String output = tagLib.textField([name: 'A'])

        then:
        output == '<input type="text" name="A" value="" id="A" />'

        when:
        output = tagLib.textField([name: 'B'])

        then:
        output == '<input type="text" name="B" value="" id="B" />'
    }

    def testFieldImplDoesNotApplyAttributesFromPreviousInvocation() {
        unRegisterRequestDataValueProcessor()
        // GRAILS-8250
        when:
        def attrs = [:]
        def out = new FastStringWriter()
        attrs.name = 'A'
        attrs.type = 'text'
        attrs.tagName = 'textField'

        then:
        tagLib.fieldImpl out, attrs
         '<input type="text" name="A" value="" id="A" />' == out.toString()

        when:
        out = new FastStringWriter()
        attrs.name = 'B'
        attrs.type = 'text'
        attrs.tagName = 'textField'
        tagLib.fieldImpl out, attrs

        then:
         '<input type="text" name="B" value="" id="B" />' == out.toString()
    }

    def testBooleanAttributes() {
        // GRAILS-3468
        // Test readonly for string as boolean true
        when:
        unRegisterRequestDataValueProcessor()
        Map attributes = [name: 'myfield', value: '1', readonly: 'true']
        String output = tagLib.textField(attributes)

        then:
        output == '<input type="text" name="myfield" value="1" readonly="readonly" id="myfield" />'

        // Test readonly for string as boolean false
        when:
        attributes = [name: 'myfield', value: '1', readonly: 'false']
        output = tagLib.textField(attributes)

        then:
         output == '<input type="text" name="myfield" value="1" id="myfield" />'

        // Test readonly for real boolean true
        when:
        attributes = [name: 'myfield', value: '1', readonly: true]
        output = tagLib.textField(attributes)

        then:
        output == '<input type="text" name="myfield" value="1" readonly="readonly" id="myfield" />'

        // Test readonly for real boolean false
        when:
        attributes = [name: 'myfield', value: '1', readonly: false]
        output = tagLib.textField(attributes)

        then:
        output == '<input type="text" name="myfield" value="1" id="myfield" />'

        // Test readonly for its default value
        when:
        attributes = [name: 'myfield', value: '1', readonly: 'readonly']
        output = tagLib.textField(attributes)

        then:
        output == '<input type="text" name="myfield" value="1" readonly="readonly" id="myfield" />'

        // Test readonly for a value different from the defined in the spec
        when:
        attributes = [name: 'myfield', value: '1', readonly: 'other value']
        output = tagLib.textField(attributes)

        then:
        output == '<input type="text" name="myfield" value="1" readonly="other value" id="myfield" />'

        // Test readonly for null value
        when:
        attributes = [name: 'myfield', value: '1', readonly: null]
        output = tagLib.textField(attributes)

        then:
        output == '<input type="text" name="myfield" value="1" id="myfield" />'

        // Test disabled for string as boolean true
        when:
        attributes = [name: 'myfield', value: '1', disabled: 'true']
        output = tagLib.textField(attributes)

        then:
        output == '<input type="text" name="myfield" value="1" disabled="disabled" id="myfield" />'

        // Test disabled for string as boolean false
        when:
        attributes = [name: 'myfield', value: '1', disabled: 'false']
        output = tagLib.textField(attributes)

        then:
        output == '<input type="text" name="myfield" value="1" id="myfield" />'

        // Test disabled for real boolean true
        when:
        attributes = [name: 'myfield', value: '1', disabled: true]
        output = tagLib.textField(attributes)

        then:
        output == '<input type="text" name="myfield" value="1" disabled="disabled" id="myfield" />'

        // Test disabled for real boolean false
        when:
        attributes = [name: 'myfield', value: '1', disabled: false]
        output = tagLib.textField(attributes)

        then:
        output == '<input type="text" name="myfield" value="1" id="myfield" />'

        // Test disabled for its default value
        when:
        attributes = [name: 'myfield', value: '1', disabled: 'disabled']
        output = tagLib.textField(attributes)

        then:
        output == '<input type="text" name="myfield" value="1" disabled="disabled" id="myfield" />'

        // Test disabled for a value different from the defined in the spec
        when:
        attributes = [name: 'myfield', value: '1', disabled: 'other value']
        output = tagLib.textField(attributes)

        then:
        output == '<input type="text" name="myfield" value="1" disabled="other value" id="myfield" />'

        // Test disabled for null value
        when:
        attributes = [name: 'myfield', value: '1', disabled: null]
        output = tagLib.textField(attributes)

        then:
        output == '<input type="text" name="myfield" value="1" id="myfield" />'
    }

    private void unRegisterRequestDataValueProcessor() {
        tagLib.requestDataValueProcessor = null
    }
}
