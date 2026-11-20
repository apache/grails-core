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

import jakarta.servlet.http.HttpServletRequest

import org.springframework.core.io.FileSystemResource
import org.springframework.web.servlet.support.RequestDataValueProcessor
import spock.lang.Specification

import grails.testing.web.taglib.TagLibUnitTest
import org.grails.core.io.StaticResourceLocator
import org.grails.plugins.web.DefaultGrailsTagDateHelper
import org.grails.plugins.web.taglib.ApplicationTagLib
import org.grails.plugins.web.taglib.FormTagLib
import org.grails.plugins.web.taglib.RenderTagLib
import org.grails.plugins.web.taglib.UrlMappingTagLib
import org.grails.plugins.web.taglib.ValidationTagLib
import org.grails.web.errors.ErrorsViewStackTracePrinter

class TagAttributeEncodingSpec extends Specification implements TagLibUnitTest<FormTagLib> {

    private static final String VALUE = 'a"b<c&d'
    private static final String ENCODED = 'a&quot;b&lt;c&amp;d'

    def setup() {
        mockTagLib(ApplicationTagLib)
        mockTagLib(UrlMappingTagLib)
        mockTagLib(ValidationTagLib)
        tagLib.grailsTagDateHelper = new DefaultGrailsTagDateHelper()
    }

    void '#tag encodes the #attribute it writes'() {
        when:
        def output = applyTemplate(template, [value: VALUE])

        then:
        output.contains(expected)
        !output.contains(VALUE)

        where:
        tag                    | attribute               | template                                                                                    || expected
        'g:form'               | 'url'                   | '<g:form url="/books?q=${value}"></g:form>'                                                 || '<form action="/books?q=' + ENCODED + '" '
        'g:form'               | 'uri'                   | '<g:form uri="/books?q=${value}"></g:form>'                                                 || '<form action="/books?q=' + ENCODED + '" '
        'g:form'               | 'base'                  | '<g:form base="https://example.org/${value}" controller="book" action="list"></g:form>'     || '<form action="https://example.org/' + ENCODED + '/book/list" '
        'g:form'               | 'params separator'      | '<g:form controller="book" action="list" params="${[q: 1, r: 2]}"></g:form>'                || '<form action="/book/list?q=1&amp;r=2" '
        'g:formActionSubmit'   | 'url'                   | '<g:formActionSubmit value="Go" url="/books?q=${value}"/>'                                  || '<input type="submit" formaction="/books?q=' + ENCODED + '" '
        'g:formActionSubmit'   | 'uri'                   | '<g:formActionSubmit value="Go" uri="/books?q=${value}"/>'                                  || '<input type="submit" formaction="/books?q=' + ENCODED + '" '
        'g:checkBox'           | 'name'                  | '<g:checkBox name="${value}"/>'                                                             || '<input type="hidden" name="_' + ENCODED + '" /><input type="checkbox" name="' + ENCODED + '" '
        'g:checkBox'           | 'name of a nested field'| '<g:checkBox name="book.${value}"/>'                                                        || '<input type="hidden" name="book._' + ENCODED + '" /><input type="checkbox" name="book.' + ENCODED + '" '
        'g:checkBox'           | 'value'                 | '<g:checkBox name="c" value="${value}"/>'                                                   || ' value="' + ENCODED + '" '
        'g:checkBox'           | 'form'                  | '<g:checkBox name="c" form="${value}"/>'                                                    || '<input type="hidden" name="_c" form="' + ENCODED + '" />'
        'g:actionSubmit'       | 'value'                 | '<g:actionSubmit value="${value}"/>'                                                        || '<input type="submit" name="_action_' + ENCODED + '" value="' + ENCODED + '" '
        'g:actionSubmit'       | 'action'                | '<g:actionSubmit value="Go" action="${value}"/>'                                            || '<input type="submit" name="_action_' + ENCODED + '" value="Go" '
        'g:actionSubmitImage'  | 'value'                 | '<g:actionSubmitImage value="${value}" src="/images/go.png"/>'                              || '<input type="image" name="_action_' + ENCODED + '" value="' + ENCODED + '" '
        'g:actionSubmitImage'  | 'action and src'        | '<g:actionSubmitImage value="Go" action="${value}" src="/images/go.png?${value}"/>'         || '<input type="image" name="_action_' + ENCODED + '" value="Go" src="/images/go.png?' + ENCODED + '" '
        'g:datePicker'         | 'name'                  | '<g:datePicker name="${value}" precision="year" years="${2000..2000}"/>'                    || '<select name="' + ENCODED + '_year" id="' + ENCODED + '_year" aria-labelledby="' + ENCODED + ' ' + ENCODED + '_year"'
        'g:datePicker'         | 'id'                    | '<g:datePicker name="date" id="${value}" precision="year" years="${2000..2000}"/>'          || '<select name="date_year" id="' + ENCODED + '_year" '
        'g:datePicker'         | 'selectDateClass'       | '<g:datePicker name="date" selectDateClass="${value}" precision="day" years="${2000..2000}"/>' || ' class="' + ENCODED + '">'
        'g:select'             | 'noSelection key'       | '<g:select name="s" from="${[1]}" noSelection="${[(value): \'Pick\']}"/>'                   || '<option value="' + ENCODED + '">Pick</option>'
        'g:radio'              | 'name'                  | '<g:radio name="${value}" value="v"/>'                                                      || '<input type="radio" name="' + ENCODED + '" value="v" id="' + ENCODED + '" '
        'g:radioGroup'         | 'name'                  | '<g:radioGroup name="${value}" values="${[1]}">${it.radio}</g:radioGroup>'                  || '<input type="radio" name="' + ENCODED + '" value="1" />'
        'g:field'              | 'type'                  | '<g:field type="${value}" name="f"/>'                                                       || '<input type="' + ENCODED + '" name="f" '
        'g:withTag'            | 'attrs'                 | '<g:withTag name="span" attrs="${[title: value]}">x</g:withTag>'                            || '<span title="' + ENCODED + '">x</span>'
        'g:external'           | 'uri of a script'       | '<g:external uri="/js/app.js?${value}" type="js"/>'                                         || '<script src="/js/app.js?' + ENCODED + '" '
        'g:external'           | 'uri of a stylesheet'   | '<g:external uri="/css/app.css?${value}" type="css"/>'                                      || '<link href="/css/app.css?' + ENCODED + '" '
        'g:paginate'           | 'class'                 | '<g:paginate total="20" class="${value}" controller="book" action="list"/>'                 || '<span class="' + ENCODED + ' currentStep">1</span>'
    }

    void '#tag writes an attribute value that was encoded before it reached the tag once'() {
        when:
        def output = applyTemplate(template, [value: VALUE])

        then:
        output.contains(expected)
        !output.contains('&amp;quot;')

        where:
        tag                    | template                                                                                || expected
        'g:checkBox'           | '<g:checkBox name="${value.encodeAsHTML()}" value="${value.encodeAsHTML()}"/>'           || '<input type="hidden" name="_' + ENCODED + '" /><input type="checkbox" name="' + ENCODED + '" checked="checked" value="' + ENCODED + '" '
        'g:actionSubmit'       | '<g:actionSubmit value="${value.encodeAsHTML()}"/>'                                     || '<input type="submit" name="_action_' + ENCODED + '" value="' + ENCODED + '" '
        'g:actionSubmitImage'  | '<g:actionSubmitImage value="${value.encodeAsHTML()}" src="${value.encodeAsHTML()}"/>'  || '<input type="image" name="_action_' + ENCODED + '" value="' + ENCODED + '" src="' + ENCODED + '" '
        'g:datePicker'         | '<g:datePicker name="${value.encodeAsHTML()}" precision="year" years="${2000..2000}"/>' || '<select name="' + ENCODED + '_year" id="' + ENCODED + '_year" '
        'g:select'             | '<g:select name="s" from="${[1]}" noSelection="${[(value.encodeAsHTML()): \'Pick\']}"/>' || '<option value="' + ENCODED + '">Pick</option>'
        'g:select'             | '<g:select name="s" from="${[value.encodeAsHTML()]}"/>'                                 || '<option value="' + ENCODED + '" >'
        'g:radio'              | '<g:radio name="${value.encodeAsHTML()}" value="v"/>'                                   || '<input type="radio" name="' + ENCODED + '" value="v" id="' + ENCODED + '" '
        'g:field'              | '<g:field type="${value.encodeAsHTML()}" name="f"/>'                                    || '<input type="' + ENCODED + '" name="f" '
        'g:withTag'            | '<g:withTag name="span" attrs="${[title: value.encodeAsHTML()]}">x</g:withTag>'         || '<span title="' + ENCODED + '">x</span>'
        'g:external'           | '<g:external uri="${value.encodeAsHTML()}" type="js"/>'                                 || '<script src="' + ENCODED + '" '
    }

    void 'g:radio encodes #description value once'() {
        when:
        def output = applyTemplate(template, [value: VALUE])

        then:
        output.contains('<input type="radio" name="r" value="' + ENCODED + '" id="r" ')
        !output.contains('&amp;quot;')

        where:
        description          | template
        'a plain'            | '<g:radio name="r" value="${value}"/>'
        'an already encoded' | '<g:radio name="r" value="${value.encodeAsHTML()}"/>'
    }

    void '#tag writes #description value once through a processor that returns it unchanged'() {
        given:
        tagLib.requestDataValueProcessor = new PassThroughProcessor()

        when:
        def output = applyTemplate(template, [value: VALUE])

        then:
        output.contains(expected)
        !output.contains(VALUE)
        !output.contains('&amp;quot;')

        where:
        tag                   | description          | template                                                                      || expected
        'g:checkBox'          | 'a plain'            | '<g:checkBox name="c" value="${value}"/>'                                     || ' value="' + ENCODED + '" '
        'g:checkBox'          | 'an already encoded' | '<g:checkBox name="c" value="${value.encodeAsHTML()}"/>'                      || ' value="' + ENCODED + '" '
        'g:actionSubmit'      | 'a plain'            | '<g:actionSubmit value="${value}"/>'                                          || ' value="' + ENCODED + '" '
        'g:actionSubmit'      | 'an already encoded' | '<g:actionSubmit value="${value.encodeAsHTML()}"/>'                           || ' value="' + ENCODED + '" '
        'g:actionSubmitImage' | 'a plain'            | '<g:actionSubmitImage value="${value}" src="/images/go.png"/>'                || ' value="' + ENCODED + '" '
        'g:actionSubmitImage' | 'an already encoded' | '<g:actionSubmitImage value="${value.encodeAsHTML()}" src="/images/go.png"/>' || ' value="' + ENCODED + '" '
        'g:radio'             | 'a plain'            | '<g:radio name="r" value="${value}"/>'                                        || ' value="' + ENCODED + '" '
        'g:radio'             | 'an already encoded' | '<g:radio name="r" value="${value.encodeAsHTML()}"/>'                         || ' value="' + ENCODED + '" '
        'g:textField'         | 'an already encoded' | '<g:textField name="t" value="${value.encodeAsHTML()}"/>'                     || ' value="' + ENCODED + '" '
        'g:hiddenField'       | 'an already encoded' | '<g:hiddenField name="t" value="${value.encodeAsHTML()}"/>'                   || ' value="' + ENCODED + '" '
        'g:select'            | 'an already encoded' | '<g:select name="s" from="${[value.encodeAsHTML()]}"/>'                       || '<option value="' + ENCODED + '" >'
    }

    void '#tag encodes the value that a processor returns'() {
        given:
        tagLib.requestDataValueProcessor = new AppendingProcessor()

        when:
        def output = applyTemplate(template, [value: VALUE])

        then:
        output.contains(' value="' + ENCODED + '_processed" ')
        !output.contains(VALUE)

        where:
        tag                   | template
        'g:checkBox'          | '<g:checkBox name="c" value="${value}"/>'
        'g:actionSubmit'      | '<g:actionSubmit value="${value}"/>'
        'g:actionSubmitImage' | '<g:actionSubmitImage value="${value}" src="/images/go.png"/>'
        'g:textField'         | '<g:textField name="t" value="${value}"/>'
    }

    void 'g:paginate writes #description class once next to the classes it adds'() {
        when:
        def output = applyTemplate(template, [value: VALUE])

        then:
        output.contains(' class="' + ENCODED + ' prevLink"')
        output.contains('<span class="' + ENCODED + ' currentStep">2</span>')
        output.contains(' class="' + ENCODED + ' step"')
        output.contains(' class="' + ENCODED + ' nextLink"')
        !output.contains(VALUE)
        !output.contains('&amp;quot;')

        where:
        description          | template
        'a plain'            | '<g:paginate total="30" offset="10" class="${value}" controller="book" action="list"/>'
        'an already encoded' | '<g:paginate total="30" offset="10" class="${value.encodeAsHTML()}" controller="book" action="list"/>'
    }

    void 'g:sortableColumn writes #description class once next to the classes it adds'() {
        given:
        params.sort = 'title'
        params.order = 'asc'

        when:
        def output = applyTemplate(template, [value: VALUE])

        then:
        output.contains('<th class="' + ENCODED + ' sortable sorted asc" ')
        !output.contains(VALUE)
        !output.contains('&amp;quot;')

        where:
        description          | template
        'a plain'            | '<g:sortableColumn property="title" title="Title" class="${value}" action="list"/>'
        'an already encoded' | '<g:sortableColumn property="title" title="Title" class="${value.encodeAsHTML()}" action="list"/>'
    }

    void 'g:sortableColumn writes #description title encoded once as the text of its link'() {
        when:
        def output = applyTemplate(template, [value: VALUE])

        then:
        output.contains('">' + ENCODED + '</a></th>')
        !output.contains(VALUE)
        !output.contains('&amp;quot;')

        where:
        description          | template
        'a plain'            | '<g:sortableColumn property="title" title="${value}" action="list"/>'
        'an already encoded' | '<g:sortableColumn property="title" title="${value.encodeAsHTML()}" action="list"/>'
        'a plain fallback'   | '<g:sortableColumn property="title" titleKey="no.such.code" title="${value}" action="list"/>'
    }

    void 'g:sortableColumn writes a title marked raw, or from the message source, as given'() {
        given:
        messageSource.addMessage('book.title', request.locale, '<i class="icon"></i> Title')

        expect:
        applyTemplate(template, [value: '<i class="icon"></i> Title']).contains('"><i class="icon"></i> Title</a></th>')

        where:
        template << [
            '<g:sortableColumn property="title" title="${raw(value)}" action="list"/>',
            '<g:sortableColumn property="title" titleKey="book.title" action="list"/>',
        ]
    }

    void 'g:sortableColumn encodes the sort order of the request'() {
        given:
        params.sort = 'title'
        params.order = VALUE

        when:
        def output = applyTemplate('<g:sortableColumn property="title" title="Title" action="list"/>')

        then:
        output.contains('<th class="sortable sorted ' + ENCODED + '" ')
        !output.contains(VALUE)
    }

    void '#tag writes a value captured from a GSP body once through a processor that returns it unchanged'() {
        given:
        tagLib.requestDataValueProcessor = new PassThroughProcessor()

        when:
        def output = applyTemplate('<g:set var="captured">${value.encodeAsHTML()}</g:set>' + template, [value: VALUE])

        then:
        output.contains(' value="' + ENCODED + '"')
        !output.contains(VALUE)
        !output.contains('&amp;quot;')

        where:
        tag                   | template
        'g:checkBox'          | '<g:checkBox name="c" value="${captured}"/>'
        'g:actionSubmit'      | '<g:actionSubmit value="${captured}"/>'
        'g:actionSubmitImage' | '<g:actionSubmitImage value="${captured}" src="/images/go.png"/>'
        'g:radio'             | '<g:radio name="r" value="${captured}"/>'
        'g:textField'         | '<g:textField name="t" value="${captured}"/>'
    }

    void '#tag writes a value from fieldValue() once through a processor that returns it unchanged'() {
        given:
        tagLib.requestDataValueProcessor = new PassThroughProcessor()

        when:
        def output = applyTemplate(template, [book: new Book(title: VALUE)])

        then:
        output.contains(' value="' + ENCODED + '"')
        !output.contains(VALUE)
        !output.contains('&amp;quot;')

        where:
        tag                   | template
        'g:checkBox'          | '<g:checkBox name="c" value="${fieldValue(bean: book, field: \'title\')}"/>'
        'g:actionSubmit'      | '<g:actionSubmit value="${fieldValue(bean: book, field: \'title\')}"/>'
        'g:actionSubmitImage' | '<g:actionSubmitImage value="${fieldValue(bean: book, field: \'title\')}" src="/images/go.png"/>'
    }

    void '#tag encodes a value from message() that a processor returns unchanged'() {
        given:
        tagLib.requestDataValueProcessor = new PassThroughProcessor()

        when:
        def output = applyTemplate(template, [value: VALUE])

        then:
        output.contains(' value="' + ENCODED + '"')
        !output.contains(VALUE)

        where:
        tag              | template
        'g:textField'    | '<g:textField name="t" value="${message(code: \'no.such.code\', default: value)}"/>'
        'g:hiddenField'  | '<g:hiddenField name="t" value="${message(code: \'no.such.code\', default: value)}"/>'
        'g:submitButton' | '<g:submitButton name="t" value="${message(code: \'no.such.code\', default: value)}"/>'
        'g:field'        | '<g:field type="text" name="t" value="${message(code: \'no.such.code\', default: value)}"/>'
        'g:radio'        | '<g:radio name="r" value="${message(code: \'no.such.code\', default: value)}"/>'
    }

    void 'g:radio encodes a value marked raw, as it did before'() {
        when:
        def output = applyTemplate('<g:radio name="r" value="${raw(value)}"/>', [value: VALUE])

        then:
        output.contains(' value="' + ENCODED + '"')
        !output.contains(VALUE)
    }

    void 'g:checkBox takes a name captured from a GSP body when a processor is registered'() {
        given:
        tagLib.requestDataValueProcessor = new PassThroughProcessor()

        when:
        def output = applyTemplate('<g:set var="captured">${value.encodeAsHTML()}</g:set><g:checkBox name="${captured}"/>', [value: VALUE])

        then:
        output.contains('<input type="hidden" name="_' + ENCODED + '" /><input type="checkbox" name="' + ENCODED + '" ')
        !output.contains('&amp;quot;')
    }

    void '#tag writes a url that was encoded beforehand once #description'() {
        given:
        tagLib.requestDataValueProcessor = processor

        when:
        def output = applyTemplate(template, [value: VALUE])

        then:
        output.contains(expected)
        !output.contains('&amp;quot;')

        where:
        tag                  | description                        | processor                  | template                                                         || expected
        'g:form'             | 'without a processor'              | null                       | '<g:form url="${value.encodeAsHTML()}"></g:form>'                || '<form action="' + ENCODED + '" '
        'g:form'             | 'through a pass-through processor' | new PassThroughProcessor() | '<g:form url="${value.encodeAsHTML()}"></g:form>'                || '<form action="' + ENCODED + '" '
        'g:formActionSubmit' | 'without a processor'              | null                       | '<g:formActionSubmit value="Go" url="${value.encodeAsHTML()}"/>' || ' formaction="' + ENCODED + '" '
        'g:formActionSubmit' | 'through a pass-through processor' | new PassThroughProcessor() | '<g:formActionSubmit value="Go" url="${value.encodeAsHTML()}"/>' || ' formaction="' + ENCODED + '" '
    }

    void 'g:form encodes the hidden fields of the request data value processor'() {
        given:
        tagLib.requestDataValueProcessor = new PassThroughProcessor(fields: [(VALUE): VALUE])

        when:
        def output = applyTemplate('<g:form controller="book" action="save"></g:form>')

        then:
        output.contains('<input type="hidden" name="' + ENCODED + '" value="' + ENCODED + '" />')
        !output.contains(VALUE)
    }

    void 'g:renderException encodes the classes it writes'() {
        given:
        File source = File.createTempFile('Broken', '.groovy')
        source.text = 'class Broken {\n    void run() {\n        throw new IllegalStateException()\n    }\n}\n'
        def resourceLocator = new StaticResourceLocator()
        resourceLocator.addClassResource('Broken', new FileSystemResource(source))
        def renderTagLib = mockTagLib(RenderTagLib)
        renderTagLib.errorsViewStackTracePrinter = new ErrorsViewStackTracePrinter(resourceLocator)
        request.setAttribute('jakarta.servlet.error.status_code', 500)
        def exception = new IllegalStateException('boom')
        exception.stackTrace = [new StackTraceElement('Broken', 'run', 'Broken.groovy', 3)] as StackTraceElement[]
        def classes = ['detailsClass', 'stackClass', 'filenameClass', 'snippetClass', 'lineClass', 'lineNumberClass', 'lineErrorClass']

        when:
        def output = applyTemplate(
                '<g:renderException exception="${exception}" ' + classes.collect { "${it}=\"\${value}\"" }.join(' ') + '/>',
                [exception: exception, value: VALUE]
        )

        then:
        !output.contains(VALUE)
        output.contains('<dl class="' + ENCODED + '">')
        output.contains('<span class="' + ENCODED + '">')
        output.contains('<pre class="' + ENCODED + '">')
        output.contains('<code class="' + ENCODED + '"><span class="' + ENCODED + '">1:</span>')
        output.contains('<code class="' + ENCODED + '"><span class="' + ENCODED + '">3:</span>')
        output.contains('<h2>Trace</h2><pre class="' + ENCODED + '">')

        cleanup:
        source?.delete()
    }

    static class PassThroughProcessor implements RequestDataValueProcessor {

        Map<String, String> fields = [:]

        @Override
        String processAction(HttpServletRequest request, String action, String httpMethod) {
            action
        }

        @Override
        String processFormFieldValue(HttpServletRequest request, String name, String value, String type) {
            value
        }

        @Override
        Map<String, String> getExtraHiddenFields(HttpServletRequest request) {
            fields
        }

        @Override
        String processUrl(HttpServletRequest request, String url) {
            url
        }
    }

    static class Book {
        String title
    }

    static class AppendingProcessor extends PassThroughProcessor {

        @Override
        String processFormFieldValue(HttpServletRequest request, String name, String value, String type) {
            value + '_processed'
        }
    }
}
