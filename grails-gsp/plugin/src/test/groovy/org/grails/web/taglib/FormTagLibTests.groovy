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

import grails.artefact.Artefact
import grails.testing.web.taglib.TagLibUnitTest
import grails.util.MockRequestDataValueProcessor
import org.grails.buffer.FastStringWriter
import org.grails.core.artefact.UrlMappingsArtefactHandler
import org.grails.plugins.web.taglib.ApplicationTagLib
import org.grails.plugins.web.taglib.FormTagLib
import org.grails.plugins.web.taglib.UrlMappingTagLib
import org.grails.web.mapping.UrlMappingsHolderFactoryBean
import spock.lang.Issue
import spock.lang.Rollup
import spock.lang.Specification

/**
 * Tests for the FormTagLib.groovy file which contains tags to help with the                                         l
 * creation of HTML forms.
 *
 * Please note tests that require special config have been moved to FormTagLibWithConfigSpec.
 * You can test things that require no special config here.
 *
 * @author Graeme
 * @author rvanderwerf
 */
class FormTagLibTests extends Specification implements TagLibUnitTest<FormTagLib> {
    def setupSpec() {
        // Test pollution causes these to be cached on the LazyTagLibraryLookup
        [ApplicationTagLib, FormTagLib, UrlMappingTagLib].each {
            GroovySystem.metaClassRegistry.removeMetaClass(it)
        }
    }

    def setup() {
        tagLib.requestDataValueProcessor = new MockRequestDataValueProcessor()

        grailsApplication.addArtefact(UrlMappingsArtefactHandler.TYPE, FormTagLibUrlMappings)

        defineBeans {
            grailsUrlMappingsHolder(UrlMappingsHolderFactoryBean) {
                delegate.grailsApplication = grailsApplication
            }
        }
    }

    def testFormNoNamespace() {
        expect:
        applyTemplate('<g:form controller="books"></g:form>') ==
                '<form action="/books" method="post" ><input type="hidden" name="requestDataValueProcessorHiddenName" value="hiddenValue" />\n</form>'
    }

    def testFormTagWithAlternativeMethod() {
        given:
        unRegisterRequestDataValueProcessor()

        expect:
        applyTemplate('<g:form url="/foo/bar" method="delete"></g:form>') ==
                '<form action="/foo/bar" method="post" ><input type="hidden" name="_method" value="DELETE" id="_method" /></form>'
    }

    def testFormTagWithAlternativeMethodAndRequestDataValueProcessor() {
        expect:
        applyTemplate('<g:form url="/foo/bar" method="delete"></g:form>') ==
                '<form action="/foo/bar" method="post" ><input type="hidden" name="_method" value="DELETE_PROCESSED_" id="_method" /><input type="hidden" name="requestDataValueProcessorHiddenName" value="hiddenValue" />\n</form>'
    }

    @Issue('https://github.com/apache/grails-core/issues/6653')
    def testHiddenFieldWithZeroValue() {
        given:
        unRegisterRequestDataValueProcessor()

        expect:
        applyTemplate('<g:hiddenField name="index" value="${0}" />').contains('value="0"')
    }

    def testHiddenFieldWithZeroValueAndRequestDataValueProcessor() {
        expect:
        applyTemplate('<g:hiddenField name="index" value="${0}" />').contains('value="0_PROCESSED_"')
    }

    def testFormTagWithStringURL() {
        given:
        unRegisterRequestDataValueProcessor()

        expect:
        applyTemplate('<g:form url="/foo/bar"></g:form>') ==
                '<form action="/foo/bar" method="post" ></form>'
    }

    def testFormTagWithStringURLAndRequestDataValueProcessor() {
        expect:
        applyTemplate('<g:form url="/foo/bar"></g:form>') ==
                '<form action="/foo/bar" method="post" ><input type="hidden" name="requestDataValueProcessorHiddenName" value="hiddenValue" />\n</form>'
    }

    def testFormTagWithTrueUseToken() {
        given:
        unRegisterRequestDataValueProcessor()

        when:
        String output = applyTemplate('<g:form url="/foo/bar" useToken="true"></g:form>')

        then:
        output.contains('<form action="/foo/bar" method="post" >')
        output.contains('<input type="hidden" name="SYNCHRONIZER_TOKEN" value="')
        output.contains('<input type="hidden" name="SYNCHRONIZER_URI" value="')

        when:
        output = applyTemplate('<g:form url="/foo/bar" useToken="${2 * 3 == 6}"></g:form>')

        then:
        output.contains('<form action="/foo/bar" method="post" >')
        output.contains('<input type="hidden" name="SYNCHRONIZER_TOKEN" value="')
        output.contains('<input type="hidden" name="SYNCHRONIZER_URI" value="')
    }

    def testFormTagWithTrueUseTokenAndRequestDataValueProcessor() {
        when:
        String output = applyTemplate('<g:form url="/foo/bar" useToken="true"></g:form>')

        then:
        output.contains('<form action="/foo/bar" method="post" >')
        output.contains('<input type="hidden" name="SYNCHRONIZER_TOKEN" value="')
        output.contains('<input type="hidden" name="SYNCHRONIZER_URI" value="')


        when:
        output = applyTemplate('<g:form url="/foo/bar" useToken="${2 * 3 == 6}"></g:form>')

        then:
        output.contains('<form action="/foo/bar" method="post" >')
        output.contains('<input type="hidden" name="SYNCHRONIZER_TOKEN" value="')
        output.contains('<input type="hidden" name="SYNCHRONIZER_URI" value="')
    }

    def testFormTagWithNonTrueUseToken() {
        when:
        String output = applyTemplate('<g:form url="/foo/bar" useToken="false"></g:form>')

        then:
        output.contains('<form action="/foo/bar" method="post" >')
        !output.contains('SYNCHRONIZER_TOKEN')
        !output.contains('SYNCHRONIZER_URI')

        when:
        output = applyTemplate('<g:form url="/foo/bar" useToken="someNonTrueValue"></g:form>')

        then:
        output.contains('<form action="/foo/bar" method="post" >')
        !output.contains('SYNCHRONIZER_TOKEN')
        !output.contains('SYNCHRONIZER_URI')

        when:
        output = applyTemplate('<g:form url="/foo/bar" useToken="${42 * 2112 == 3}"></g:form>')

        then:
        output.contains('<form action="/foo/bar" method="post" >')
        !output.contains('SYNCHRONIZER_TOKEN')
        !output.contains('SYNCHRONIZER_URI')
    }

    @Rollup
    def testTextFieldTag(String template, Map model, String expectedOutput) {
        given:
        unRegisterRequestDataValueProcessor()

        expect:
        applyTemplate(template, model) == expectedOutput

        where:
        template | model || expectedOutput
        '<g:textField name="testField" value="1" />' | [value: '1'] ||
                '<input type="text" name="testField" value="1" id="testField" />'
        '<g:textField name="testField" value="${value}" />' | [value: /foo > " & < '/] ||
                '<input type="text" name="testField" value="foo &gt; &quot; &amp; &lt; &#39;" id="testField" />'
    }

    @Rollup
    def testTextFieldTagWithRequestDataValueProcessor(String template, Map model, String expectedOutput) {
        expect:
        applyTemplate(template, model) == expectedOutput

        where:
        template | model || expectedOutput
        '<g:textField name="testField" value="1" />' | [:] ||
                '<input type="text" name="testField" value="1_PROCESSED_" id="testField" />'
        '<g:textField name="testField" value="${value}" />' | [value:/foo > " & < '/] ||
                '<input type="text" name="testField" value="foo &gt; &quot; &amp; &lt; &#39;_PROCESSED_" id="testField" />'
    }

    @Rollup
    def testTextFieldTagWithNonBooleanAttributesAndNoConfig(String template, String expectedOutput) {
        given:
        unRegisterRequestDataValueProcessor()

        expect:
        applyTemplate(template) == expectedOutput

        where:
        template || expectedOutput
        '<g:textField name="testField" value="1" disabled="false" checked="false" readonly="false" bogus="false" />' ||
                '<input type="text" name="testField" value="1" bogus="false" id="testField" />'
        '<g:textField name="testField" value="1" disabled="true" checked="true" readonly="true" required="true" bogus="true" />' ||
                '<input type="text" name="testField" value="1" bogus="true" disabled="disabled" checked="checked" readonly="readonly" required="required" id="testField" />'
    }


    def testTextAreaWithBody() {
        given:
        unRegisterRequestDataValueProcessor()

        expect:
        applyTemplate('<g:textArea name="test">This is content</g:textArea>') ==
                '<textarea name="test" id="test" >This is content</textarea>'
    }

    def testTextAreaWithBodyAndRequestDataValueProcessor() {
        expect:
        applyTemplate('<g:textArea name="test">This is content</g:textArea>') ==
                '<textarea name="test" id="test" >This is content_PROCESSED_</textarea>'
    }

    def testPasswordTag() {
        given:
        unRegisterRequestDataValueProcessor()

        expect:
        applyTemplate('<g:passwordField name="myPassword" value="foo"/>') ==
                '<input type="password" name="myPassword" value="foo" id="myPassword" />'
    }

    def testPasswordTagWithRequestDataValueProcessor() {
        expect:
        applyTemplate('<g:passwordField name="myPassword" value="foo"/>') ==
                '<input type="password" name="myPassword" value="foo_PROCESSED_" id="myPassword" />'
    }

    def testFormWithURL() {
        given:
        unRegisterRequestDataValueProcessor()

        expect:
        tagLib.form(
                new TreeMap([
                        url: [
                                controller: 'con',
                                action: 'action'
                        ],
                        id: 'formElementId'
                ]),
                null
        ).toString() == '<form action="/con/action" method="post" id="formElementId" ></form>'
    }

    def testFormWithURLAndRequestDataValueProcessor() {
        expect:
        tagLib.form(
                new TreeMap([
                        url: [
                                controller: 'con',
                                action: 'action'
                        ],
                        id: 'formElementId'
                ]),
                null
        ).toString() == '<form action="/con/action" method="post" id="formElementId" ><input type="hidden" name="requestDataValueProcessorHiddenName" value="hiddenValue" />\n</form>'
    }

    def testFormActionSubmitWithController() {
        expect:
        tagLib.formActionSubmit([
                controller: 'books',
                id: 'formElementId',
                value: 'Submit'
        ]).toString() ==
                '<input type="submit" formaction="/books" value="Submit" id="formElementId" />'
    }

    def testFormActionSubmitWithControllerAndAction() {
        expect:
        tagLib.formActionSubmit([
                controller: 'con',
                action: 'act',
                id: 'formElementId',
                value: 'Submit'
        ]).toString() ==
                '<input type="submit" formaction="/con/act" value="Submit" id="formElementId" />'
    }

    def testFormActionSubmitWithURLAndNoParams() {
        given:
        unRegisterRequestDataValueProcessor()

        expect:
        tagLib.formActionSubmit(new TreeMap([
                url: [
                        controller: 'con',
                        action: 'action'
                ],
                id: 'formElementId',
                value: 'Submit'
        ])).toString() ==
                '<input type="submit" formaction="/con/action" id="formElementId" value="Submit" />'
    }

    def testFormActionSubmitWithAURLAndRequestDataValueProcessor() {
        expect:
        tagLib.formActionSubmit(new TreeMap([
                url: [
                        controller: 'con',
                        action: 'action',
                        params: [
                            requestDataValueProcessorParamName: 'paramValue'
                        ]
                ],
                id: 'formElementId',
                value: 'My Button'
        ])).toString() ==
                '<input type="submit" formaction="/con/action" id="formElementId" value="My Button" />'
    }

    def testFormActionSubmitWithAURLAndWithoutRequestDataValueProcessor() {
        given:
        unRegisterRequestDataValueProcessor()

        expect:
        tagLib.formActionSubmit(new TreeMap([
                url: [
                        controller: 'con',
                        action: 'action',
                        params: [
                                requestDataValueProcessorParamName: 'paramValue'
                        ]
                ],
                id: 'formElementId',
                value: 'My Button'
        ])).toString() ==
                '<input type="submit" formaction="/con/action?requestDataValueProcessorParamName=paramValue" id="formElementId" value="My Button" />'
    }

    def testActionSubmitWithoutAction() {
        given:
        unRegisterRequestDataValueProcessor()

        expect:
        tagLib.actionSubmit(new TreeMap([
                value: 'Edit'
        ])).toString() ==
                '<input type="submit" name="_action_Edit" value="Edit" />'
    }

    def testActionSubmitWithoutActionAndWithRequestDataValueProcessor() {
        expect:
        tagLib.actionSubmit(new TreeMap([
                value: 'Edit'
        ])).toString() ==
                '<input type="submit" name="_action_Edit" value="Edit_PROCESSED_" />'
    }

    def testActionSubmitWithAction() {
        given:
        unRegisterRequestDataValueProcessor()

        expect:
        tagLib.actionSubmit(new TreeMap([
                action: 'Edit',
                value: 'Some label for editing'
        ])).toString() ==
                '<input type="submit" name="_action_Edit" value="Some label for editing" />'
    }

    def testActionSubmitWithActionAndRequestDataValueProcessor() {
        expect:
        tagLib.actionSubmit(new TreeMap([
                action:'Edit',
                value:'Some label for editing'
        ])).toString() ==
                '<input type="submit" name="_action_Edit" value="Some label for editing_PROCESSED_" />'
    }

    /**
     * GRAILS-454 - Make sure that the 'name' attribute is ignored.
     */
    def testActionSubmitWithName() {
        given:
        unRegisterRequestDataValueProcessor()

        expect:
        tagLib.actionSubmit(new TreeMap([
                action: 'Edit',
                value: 'Some label for editing',
                name:'customName'
        ])).toString() ==
                '<input type="submit" name="_action_Edit" value="Some label for editing" />'
    }

    def testActionSubmitWithAdditionalAttributes() {
        given:
        unRegisterRequestDataValueProcessor()

        expect:
        tagLib.actionSubmit(new TreeMap([
                action: 'Edit',
                value: 'Some label for editing',
                style: 'width: 200px;'
        ])).toString() ==
                '<input type="submit" name="_action_Edit" value="Some label for editing" style="width: 200px;" />'
    }

    def testActionSubmitImageWithoutAction() {
        given:
        unRegisterRequestDataValueProcessor()

        expect:
        tagLib.actionSubmitImage(new TreeMap([
                src: 'edit.gif',
                value: 'Edit'
        ])).toString() ==
                '<input type="image" name="_action_Edit" value="Edit" src="edit.gif" />'
    }

    def testActionSubmitImageWithoutActionAndWithRequestDataValueProcessor() {
        expect:
        tagLib.actionSubmitImage(new TreeMap([
                src: 'edit.gif', value: 'Edit'
        ])).toString() ==
                '<input type="image" name="_action_Edit" value="Edit_PROCESSED_" src="edit.gif?requestDataValueProcessorParamName=paramValue" />'
    }

    def testActionSubmitImageWithAction() {
        given:
        unRegisterRequestDataValueProcessor()

        expect:
        tagLib.actionSubmitImage(new TreeMap([
                src: 'edit.gif',
                action: 'Edit',
                value: 'Some label for editing'
        ])).toString() ==
                '<input type="image" name="_action_Edit" value="Some label for editing" src="edit.gif" />'
    }

    def testActionSubmitImageWithAdditionalAttributes() {
        given:
        unRegisterRequestDataValueProcessor()

        expect:
        tagLib.actionSubmitImage(new TreeMap([
                src: 'edit.gif',
                action: 'Edit',
                value: 'Some label for editing',
                style:'border-line: 0px;'
        ])).toString() ==
                '<input type="image" name="_action_Edit" value="Some label for editing" src="edit.gif" style="border-line: 0px;" />'
    }

    def testHtmlEscapingTextAreaTag() {
        given:
        unRegisterRequestDataValueProcessor()

        expect:
        tagLib.textArea([name: 'testField', value: '<b>some text</b>'], null) ==
                '<textarea name="testField" id="testField" >&lt;b&gt;some text&lt;/b&gt;</textarea>'
    }

    def testTextAreaTag() {
        given:
        unRegisterRequestDataValueProcessor()

        expect:
        tagLib.textArea([name: 'testField', value: '1'], null).toString() ==
                '<textarea name="testField" id="testField" >1</textarea>'
    }

    @Rollup
    def testPassingTheSameMapToTextField(Map attributes, String expectedOutput) {
        // GRAILS-8250
        given:
        unRegisterRequestDataValueProcessor()

        expect:
        tagLib.textField(attributes).toString() == expectedOutput

        where:
        attributes  || expectedOutput
        [name: 'A'] || '<input type="text" name="A" value="" id="A" />'
        [name: 'B'] || '<input type="text" name="B" value="" id="B" />'
    }

    def testFieldImplDoesNotApplyAttributesFromPreviousInvocation() {
        // GRAILS-8250
        given:
        unRegisterRequestDataValueProcessor()

        when:
        def attrs = [:]
        def out = new FastStringWriter()
        attrs.name = 'A'
        attrs.type = 'text'
        attrs.tagName = 'textField'

        then:
        tagLib.fieldImpl(out, attrs)
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

    @Rollup
    def testBooleanAttributes(Map attribute, String expectedOutput) {
        // GRAILS-3468
        given:
        unRegisterRequestDataValueProcessor()

        expect:
        tagLib.textField(attribute + [name: 'myfield', value: '1']).toString() == expectedOutput

        where:
        attribute              || expectedOutput
        [readonly: 'true']     || '<input type="text" name="myfield" value="1" readonly="readonly" id="myfield" />'
        [readonly: 'false']    || '<input type="text" name="myfield" value="1" id="myfield" />'
        [readonly: true]       || '<input type="text" name="myfield" value="1" readonly="readonly" id="myfield" />'
        [readonly: false]      || '<input type="text" name="myfield" value="1" id="myfield" />'
        [readonly: 'readonly'] || '<input type="text" name="myfield" value="1" readonly="readonly" id="myfield" />'
        [readonly: 'foo bar']  || '<input type="text" name="myfield" value="1" readonly="foo bar" id="myfield" />'
        [readonly: null]       || '<input type="text" name="myfield" value="1" id="myfield" />'
        [disabled: 'true']     || '<input type="text" name="myfield" value="1" disabled="disabled" id="myfield" />'
        [disabled: 'false']    || '<input type="text" name="myfield" value="1" id="myfield" />'
        [disabled: true]       || '<input type="text" name="myfield" value="1" disabled="disabled" id="myfield" />'
        [disabled: false]      || '<input type="text" name="myfield" value="1" id="myfield" />'
        [disabled: 'disabled'] || '<input type="text" name="myfield" value="1" disabled="disabled" id="myfield" />'
        [disabled: 'foo bar']  || '<input type="text" name="myfield" value="1" disabled="foo bar" id="myfield" />'
        [disabled: null]       || '<input type="text" name="myfield" value="1" id="myfield" />'
    }

    private void unRegisterRequestDataValueProcessor() {
        tagLib.requestDataValueProcessor = null
    }
}

@Artefact('UrlMappings')
class FormTagLibUrlMappings {
    static mappings = {
        '/admin/books'(controller: 'books', namespace: 'admin')
        '/books'(controller: 'books')
    }
}