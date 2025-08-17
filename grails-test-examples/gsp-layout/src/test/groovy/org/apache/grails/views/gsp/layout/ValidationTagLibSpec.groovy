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

package org.apache.grails.views.gsp.layout

import grails.gorm.annotation.Entity
import grails.testing.gorm.DataTest
import grails.testing.web.taglib.TagLibUnitTest
import groovy.xml.XmlSlurper
import org.grails.core.io.MockStringResourceLoader
import org.grails.plugins.web.taglib.ValidationTagLib
import spock.lang.PendingFeature
import spock.lang.Specification

/**
 * These tests were moved here because the layout mechanism can be "selected" as of Grails 7 between sitemesh2 & sitemesh3
 */
class ValidationTagLibSpec extends Specification implements TagLibUnitTest<ValidationTagLib>, DataTest {

    void testMessageHtmlEscaping() {
        given:
        def b = new ValidationTagLibBook()
        b.title = "<script>alert('escape me')</script>"

        messageSource.addMessage("default.show.label", Locale.ENGLISH, ">{0}<")

        def template = '''<title><g:message code="default.show.label" args="[book.title]" /></title>'''
        def htmlCodecDirective = '<%@page defaultCodec="HTML" %>'
        def expected = "<title>>&lt;script&gt;alert(&#39;escape me&#39;)&lt;/script&gt;<</title>"

        expect:
        applyTemplate(template, [book:b]) == expected
        applyTemplate(htmlCodecDirective + template, [book:b]) == expected
    }

    void testMessageRawEncodeAs() {
        given:
        def b = new ValidationTagLibBook()
        b.title = "<b>bold</b> is ok"

        messageSource.addMessage("default.show.label", Locale.ENGLISH, ">{0}<")

        def template = '''<title><g:message code="default.show.label" args="[book.title]" encodeAs="raw"/></title>'''
        def htmlCodecDirective = '<%@page defaultCodec="HTML" %>'
        def expected = "<title>><b>bold</b> is ok<</title>"

        expect:
        applyTemplate(template, [book:b]) == expected
        applyTemplate(htmlCodecDirective + template, [book:b]) == expected
    }

    void testMessageNoneEncodeAs() {
        given:
        def b = new ValidationTagLibBook()
        b.title = "<b>bold</b> is ok"

        messageSource.addMessage("default.show.label", Locale.ENGLISH, ">{0}<")

        def template = '''<title><g:message code="default.show.label" args="[book.title]" encodeAs="none"/></title>'''
        def htmlCodecDirective = '<%@page defaultCodec="HTML" %>'
        def expected = "<title>><b>bold</b> is ok<</title>"

        expect:
        applyTemplate(template, [book:b]) == expected
        applyTemplate(htmlCodecDirective + template, [book:b]) == expected
    }

    void testMessageHtmlEscapingWithFunctionSyntaxCall() {
        given:
        def b = new ValidationTagLibBook()
        b.title = "<script>alert('escape me')</script>"

        messageSource.addMessage("default.show.label", Locale.ENGLISH, "{0}")

        def template = '''<title>${g.message([code:"default.show.label", args:[book.title]])}</title>'''
        def htmlCodecDirective = '<%@page defaultCodec="HTML" %>'
        def expected = "<title>&lt;script&gt;alert(&#39;escape me&#39;)&lt;/script&gt;</title>"
        expect:
        applyTemplate(template, [book:b]) == expected
        applyTemplate(htmlCodecDirective + template, [book:b]) == expected

    }

    void testMessageHtmlEscapingDifferentEncodings() {
        given:
        def b = new ValidationTagLibBook()

        b.title = "<script>alert('escape me')</script>"

        messageSource.addMessage("default.show.label", Locale.ENGLISH, "{0}")

        def template = '''<title>${g.message([code:"default.show.label", args:[book.title]])}</title>'''
        def htmlCodecDirective = '<%@page defaultCodec="HTML" %>'
        def expected = "<title>&lt;script&gt;alert(&#39;escape me&#39;)&lt;/script&gt;</title>"

        def resourceLoader = new MockStringResourceLoader()
        resourceLoader.registerMockResource('/_sometemplate.gsp', htmlCodecDirective + template)
        resourceLoader.registerMockResource('/_sometemplate_nocodec.gsp', template)
        applicationContext.groovyPagesTemplateEngine.groovyPageLocator.addResourceLoader(resourceLoader)

        expect:
        applyTemplate( '<g:render template="/sometemplate" model="[book:book]" />', [book:b]) == expected
        applyTemplate( template + '<g:render template="/sometemplate" model="[book:book]" />', [book:b])  == expected + expected
        applyTemplate( htmlCodecDirective + template + '<g:render template="/sometemplate" model="[book:book]" />', [book:b]) == expected + expected
        applyTemplate( '<g:render template="/sometemplate" model="[book:book]" />' + template, [book:b]) == expected + expected
        applyTemplate( htmlCodecDirective + '<g:render template="/sometemplate" model="[book:book]" />' + template, [book:b])  == expected + expected

        applyTemplate( '<g:render template="/sometemplate_nocodec" model="[book:book]" />', [book:b])  == expected
        applyTemplate( template + '<g:render template="/sometemplate_nocodec" model="[book:book]" />', [book:b]) == expected + expected
        applyTemplate( htmlCodecDirective + template + '<g:render template="/sometemplate_nocodec" model="[book:book]" />', [book:b])== expected + expected
        applyTemplate( '<g:render template="/sometemplate_nocodec" model="[book:book]" />' + template, [book:b])== expected + expected
        applyTemplate( htmlCodecDirective + '<g:render template="/sometemplate_nocodec" model="[book:book]" />' + template, [book:b])== expected + expected
    }

    @PendingFeature
    void testFieldValueTagForBadUrl() {
        given:
        def b = new ValidationTagLibBook()

        when:
        b.publisherURL = new URL("a_bad_url")

        then:
        b.hasErrors()
        applyTemplate('''<g:fieldValue bean="${book}" field="publisherURL" />''', [book:b]) == "a_bad_url"
    }

    @PendingFeature
    void testRenderErrorsAsXMLTag() {
        given:
        def b = new ValidationTagLibBook()
        def template = '''<g:renderErrors bean="${book}" as="xml" />'''

        when:
        b.validate()

        then:
        b.hasErrors()


        when:
        def result = applyTemplate(template,[book:b])
        def xml = new XmlSlurper().parseText(result)

        then:
        xml.error.size() == 4
        xml.error[0].@object.text() == ValidationTagLibBook.name
        xml.error[0].@field.text() == "releaseDate"
        xml.error[0].@message.text() == "Property [releaseDate] of class [Reading Material] cannot be null"
    }
}

@Entity
class ValidationTagLibBook {
    String title
    URL publisherURL
    Date releaseDate
    BigDecimal usPrice
}