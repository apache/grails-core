/*
 * Copyright 2014-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.web.taglib

import grails.gorm.annotation.Entity
import grails.testing.gorm.DataTest
import grails.testing.web.taglib.TagLibUnitTest
import groovy.xml.XmlSlurper
import org.grails.core.io.MockStringResourceLoader
import org.grails.plugins.web.taglib.ValidationTagLib
import org.grails.web.util.GrailsApplicationAttributes
import org.springframework.beans.PropertyEditorRegistrar
import org.springframework.beans.PropertyEditorRegistry
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.MessageSourceResolvable
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.validation.FieldError
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Specification

import java.beans.PropertyEditorSupport

/**
 * @author graemerocher
 */
class ValidationTagLibSpec extends Specification implements TagLibUnitTest<ValidationTagLib>, DataTest {

    void setupSpec() {
        mockDomain(ValidationTagLibBook)
    }

    void testDefaultErrorMessage() {

        when:
        def b = new ValidationTagLibBook()

        then:
        !b.validate()
        b.hasErrors()

        when:
        def template = '<g:fieldError bean="${book}" field="title" />'
        webRequest.currentRequest.addPreferredLocale(Locale.US)
        def result = applyTemplate( template, [book:b] )

        then:
        result == 'Property [title] of class [class org.grails.web.taglib.ValidationTagLibBook] cannot be null'

        when:"the object is made valid"
        b.title = "The Stand"
        b.validate()
        then:"No message is output"

    }

    void testFieldValueWithClassAndPropertyNameLookupFromBundle() {
        given:
            LocaleContextHolder.setLocale(Locale.US)
            messageSource.addMessage("org.grails.web.taglib.ValidationTagLibBook.label", Locale.US, "Reading Material")
            messageSource.addMessage("org.grails.web.taglib.ValidationTagLibBook.title.label", Locale.US, "Subject")

        when:
            def b = new ValidationTagLibBook()

        then:
            !b.validate()
            b.hasErrors()

        when:
            def template = '<g:fieldError bean="${book}" field="title" />'
            webRequest.currentRequest.addPreferredLocale(Locale.US)
            def result = applyTemplate( template, [book:b] )

        then:
            result == 'Property [Subject] of class [Reading Material] cannot be null'
    }

    void testFieldValueWithShortClassAndPropertyNameLookupFromBundle() {
        given:
            LocaleContextHolder.setLocale(Locale.US)
            messageSource.addMessage("validationTagLibBook.label", Locale.US, "Reading Material")
            messageSource.addMessage("validationTagLibBook.title.label", Locale.US, "Subject")

        when:
            def b = new ValidationTagLibBook()

        then:
            !b.validate()
            b.hasErrors()

        when:
            def template = '<g:fieldError bean="${book}" field="title" />'
            webRequest.currentRequest.addPreferredLocale(Locale.US)
            def result = applyTemplate( template, [book:b] )

        then:
            result == 'Property [Subject] of class [Reading Material] cannot be null'
    }



    void testFieldValueHTMLEscape() {
        given:
            def b = new ValidationTagLibBook(title:"<script>alert('escape me')</script>")
            def template = '''<g:fieldValue bean="${book}" field="title" />'''
            def htmlCodecDirective = '<%@page defaultCodec="HTML" %>'
            def expected = "&lt;script&gt;alert(&#39;escape me&#39;)&lt;/script&gt;"
        expect:"content with tags is output"
            applyTemplate( template, [book:b] ) == expected
            applyTemplate( htmlCodecDirective + template, [book:b] ) == expected

    }

    void testFieldValueHtmlEscapingWithFunctionSyntaxCall() {
        given:
        def b = new ValidationTagLibBook()
        b.title = "<script>alert('escape me')</script>"

        def template = '''${fieldValue(bean:book, field:"title")}'''
        def htmlCodecDirective = '<%@page defaultCodec="HTML" %>'
        def expected = "&lt;script&gt;alert(&#39;escape me&#39;)&lt;/script&gt;"

        expect:
        applyTemplate( template, [book:b] ) == expected
        applyTemplate( htmlCodecDirective + template, [book:b] ) == expected
    }

    void testFieldValueHtmlEscapingDifferentEncodings() {
        given:
        def b = new ValidationTagLibBook()
        b.title = "<script>alert('escape me')</script>"

        def template = '''${fieldValue(bean:book, field:"title")}'''
        def htmlCodecDirective = '<%@page defaultCodec="HTML" %>'
        def expected = "&lt;script&gt;alert(&#39;escape me&#39;)&lt;/script&gt;"

        def resourceLoader = new MockStringResourceLoader()
        resourceLoader.registerMockResource('/_sometemplate.gsp', htmlCodecDirective + template)
        resourceLoader.registerMockResource('/_sometemplate_nocodec.gsp', template)
        applicationContext.groovyPagesTemplateEngine.groovyPageLocator.addResourceLoader(resourceLoader)

        expect:
        applyTemplate('<g:render template="/sometemplate" model="[book:book]" />', [book:b]) == expected
        applyTemplate(template + '<g:render template="/sometemplate" model="[book:book]" />', [book:b]) == expected + expected
        applyTemplate(htmlCodecDirective + template + '<g:render template="/sometemplate" model="[book:book]" />', [book:b]) == expected + expected
        applyTemplate('<g:render template="/sometemplate" model="[book:book]" />' + template, [book:b]) == expected + expected
        applyTemplate(htmlCodecDirective + '<g:render template="/sometemplate" model="[book:book]" />' + template, [book:b])== expected + expected

        applyTemplate('<g:render template="/sometemplate_nocodec" model="[book:book]" />', [book:b]) == expected
        applyTemplate(template + '<g:render template="/sometemplate_nocodec" model="[book:book]" />', [book:b]) == expected + expected
        applyTemplate(htmlCodecDirective + template + '<g:render template="/sometemplate_nocodec" model="[book:book]" />', [book:b])== expected + expected
        applyTemplate('<g:render template="/sometemplate_nocodec" model="[book:book]" />' + template, [book:b])== expected + expected
        applyTemplate(htmlCodecDirective + '<g:render template="/sometemplate_nocodec" model="[book:book]" />' + template, [book:b]) == expected + expected
    }

    @Ignore
    void testFieldValueTagForBadUrl() {
        given:
        def b = new ValidationTagLibBook()

        when:
        b.publisherURL = new URL("a_bad_url")

        then:
        b.hasErrors()
        applyTemplate('''<g:fieldValue bean="${book}" field="publisherURL" />''', [book:b]) == "a_bad_url"
    }

    void testFieldValueTag() {
        given:
        def b = new ValidationTagLibBook()

        when:
        b.publisherURL = new URL("http://google.com")

        then:
        !b.hasErrors()
        applyTemplate('''<g:fieldValue bean="${book}" field="publisherURL" />''', [book:b]) == "http://google.com"
    }

    void testFieldValueTagWithValueMessagePrefix() {
        given:
        def b = new ValidationTagLibBook()

        // With no message
        when:
        b.publisherURL = new URL("http://google.com")

        then:
        applyTemplate('''<g:fieldValue bean="${book}" field="publisherURL" valueMessagePrefix="default.book" />''', [book:b]) == "http://google.com"

        // With a French message
        when:
        webRequest.currentRequest.addPreferredLocale(Locale.FRENCH)
        messageSource.addMessage("default.book.publisherURL", Locale.FRENCH, "http://google.fr")

        then:
        applyTemplate('''<g:fieldValue bean="${book}" field="publisherURL" valueMessagePrefix="default.book" />''', [book:b]) == "http://google.fr"

        // With an English message
        when:
        webRequest.currentRequest.addPreferredLocale(Locale.US)
        messageSource.addMessage("default.book.publisherURL", Locale.US, "http://google.com")

        then:
        applyTemplate('''<g:fieldValue bean="${book}" field="publisherURL" valueMessagePrefix="default.book" />''', [book:b]) == "http://google.com"

        // With a message overriding a property
        when:
        webRequest.currentRequest.addPreferredLocale(Locale.FRENCH)
        messageSource.addMessage("default.book.publisherURL", Locale.FRENCH, "http://google.fr")
        b.publisherURL = new URL("http://google.com")

        then:
        applyTemplate('''<g:fieldValue bean="${book}" field="publisherURL" valueMessagePrefix="default.book" />''', [book:b]) == "http://google.fr"
    }

    void testFieldValueTagWithDecimalNumber() {
        given:
        def b = new ValidationTagLibBook()
        def template = '<g:fieldValue bean="${book}" field="usPrice" />'

        b.publisherURL = new URL("http://google.com")
        b.usPrice = 1045.99

        when:
        // First test with English.
        webRequest.currentRequest.addPreferredLocale(Locale.US)

        then:
        applyTemplate(template, [book:b]) == "1,045.99"

        when:

        webRequest.currentRequest.removeAttribute(GrailsApplicationAttributes.PROPERTY_REGISTRY)
        // And then with German.
        webRequest.currentRequest.addPreferredLocale(Locale.GERMANY)

        then:
        applyTemplate(template, [book:b]) == "1.045,99"

        // No decimal part.
        when:
        b.publisherURL = new URL("http://google.com")
        b.usPrice = 1045G

        then:
        applyTemplate(template, [book:b]) == "1.045"

        // Several decimal places.
        when:
        b.publisherURL = new URL("http://google.com")
        b.usPrice = 1045.45567

        then:
        applyTemplate(template, [book:b]) == "1.045,456"
    }

    @IgnoreIf({ jvm.isJava14() || jvm.isJava15() || jvm.isJava17() }) // because space is converted to narrow no-break space 8239 and is not encoded
    void testFieldValueTagWithFrenchLocaleInTextField() {
        given:
        def b = new ValidationTagLibBook()
        b.publisherURL = new URL("http://google.com")
        b.usPrice = 1045.99
        String template = '''<g:textField name="usPrice" value="${fieldValue(bean: book, field: 'usPrice')}" />'''

        when:
        // First test with English.
        webRequest.currentRequest.addPreferredLocale(Locale.US)

        then:
        applyTemplate(template, [book:b]) == '<input type="text" name="usPrice" value="1,045.99" id="usPrice" />'

        when:
        webRequest.currentRequest.removeAttribute(GrailsApplicationAttributes.PROPERTY_REGISTRY)
        // And then with French.
        webRequest.currentRequest.addPreferredLocale(Locale.FRENCH)

        then:
        applyTemplate(template, [book:b]) == '<input type="text" name="usPrice" value="1&nbsp;045,99" id="usPrice" />'
    }

    void testHasErrorsTag() {
        given:
        def b = new ValidationTagLibBook()
        def template = '''<g:hasErrors bean="${book}">success</g:hasErrors>'''

        when:
        b.validate()

        then:
        b.hasErrors()
        applyTemplate(template, [book:b]) == "success"

        when:
        b = new ValidationTagLibBook()
        b.title = "Groovy In Action"
        b.publisherURL = new URL("http://canoo.com/gia")
        b.validate()

        then:
        b.hasErrors()

        applyTemplate('''<g:hasErrors bean="${book}" field="releaseDate">success</g:hasErrors>''', [book:b]) == "success"
        applyTemplate('''<g:hasErrors model="[book:book]" field="releaseDate">success</g:hasErrors>''', [book:b]) == "success"
        applyTemplate('''${hasErrors(bean: book, field:"releaseDate") { "success" }}''', [book:b]) == "success"
        applyTemplate('''${hasErrors(model: [book: book], field:"releaseDate") { "success" }}''', [book:b]) == "success"
        applyTemplate('''${g.hasErrors(bean: book, field:"releaseDate") { "success" }}''', [book:b]) == "success"
        applyTemplate('''${g.hasErrors(model: [book: book], field:"releaseDate") { "success" }}''', [book:b]) == "success"
        applyTemplate('''<g:hasErrors bean="${book}" field="title">success</g:hasErrors>''', [book:b]) == ""
        applyTemplate('''<g:hasErrors model="[book:book]" field="title">success</g:hasErrors>''', [book:b]) == ""

        when:
        b.clearErrors()
        b.title = "Groovy in Action"
        b.publisherURL = new URL("http://canoo.com/gia")
        b.releaseDate = new Date()
        b.usPrice = 10.99

        then:
        b.validate()
        !b.hasErrors()
        applyTemplate(template, [book:b]) == ""

    }

    void testEachErrorTag() {
        given:
        def b = new ValidationTagLibBook()
        def template = '''<g:eachError var="err" bean="${book}">${err.field}|</g:eachError>'''

        when:
        b.validate()

        then:
        b.hasErrors()


        def result = applyTemplate(template, [book:b])
        then:
        result.contains("title|")
        result.contains("releaseDate|")
        result.contains("publisherURL|")

        when:
        template = '''<g:eachError bean="${book}">${it.field}|</g:eachError>'''
        result = applyTemplate(template, [book:b])
        then:
        result.contains("title|")
        result.contains("releaseDate|")
        result.contains("publisherURL|")
    }

    void testEachErrorTagInController() {
        given:
        def b = new ValidationTagLibBook()

        when:
        b.validate()

        then:
        b.hasErrors()

        when:
        def g = applicationContext.gspTagLibraryLookup.lookupNamespaceDispatcher("g")
        def errorFields = []
        g.eachError(bean: b) {
            errorFields << it.field
        }

        then:
        errorFields.contains("title")
        errorFields.contains("releaseDate")
        errorFields.contains("publisherURL")
    }

    void testRenderErrorsTag() {
        given:
        def b = new ValidationTagLibBook()

        when:
        b.validate()

        then:
        b.hasErrors()


        when:
        def template = '''<g:renderErrors bean="${book}" />'''

        def result = applyTemplate(template,[book:b])

        then:
        result.contains("<li>Property [Subject] of class [Reading Material] cannot be null</li>")
        result.contains("<li>Property [publisherURL] of class [Reading Material] cannot be null</li>")
        result.contains("<li>Property [releaseDate] of class [Reading Material] cannot be null</li>")
        result.startsWith("<ul>")
        result.endsWith("</ul>")

        when:
        b.clearErrors()
        b.title = "Groovy in Action"
        b.publisherURL = new URL("http://canoo.com/gia")
        b.releaseDate = new Date()
        b.usPrice = 10.99

        then:
        b.validate()
        !b.hasErrors()
        applyTemplate(template, [book:b]) == ""
    }

    void testRenderErrorsTagAsListWithNoBeanAttribute() {
        given:
        def b = new ValidationTagLibBook()

        when:
        b.validate()

        then:
        b.hasErrors()

        when:
        request.setAttribute("bookInstance", b)

        def template = '''<g:renderErrors as="list" />'''

        def result = applyTemplate(template,[book:b])

        then:
        result.count("<li>Property [Subject] of class [Reading Material] cannot be null</li>") == 1
        result.count("<li>Property [publisherURL] of class [Reading Material] cannot be null</li>") == 1
        result.count("<li>Property [releaseDate] of class [Reading Material] cannot be null</li>") == 1
        result.startsWith("<ul>")
        result.endsWith("</ul>")
    }

    @Ignore
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

    @Ignore
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

    @Ignore
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

    void testMessageTagWithError() {
        given:
        def error = new FieldError("foo", "bar",1, false, ["my.error.code"] as String[], null, "This is default")
        def template = '<g:message error="${error}" />'

        expect:
        applyTemplate(template, [error:error]) == "This is default"
    }

    void testMessageTagWithLocaleAttribute() {
        given:
        messageSource.addMessage("welcome.message", Locale.ENGLISH, "Hello!")
        messageSource.addMessage("welcome.message", Locale.ITALIAN, "Ciao!")

        def template = '<g:message code="welcome.message" />'

        expect:
        applyTemplate(template, [:]) == "Hello!"
        applyTemplate(template, [locale:Locale.ITALIAN]) == "Hello!"
    }

    void testMessageTagWithBlankButExistingMessageBundleValue() {

        messageSource.addMessage("test.blank.message", Locale.ENGLISH, "")

        def template = '<g:message code="test.blank.message" />'

        assertOutputEquals("", template, [:])
    }

    void testMessageTagWithMessage() {
        def resolvable = [
                getArguments: {-> [] as Object[] },
                getCodes: {-> ["my.message.code"] as String[] },
                getDefaultMessage: {-> "The Default Message" }
        ] as MessageSourceResolvable

        def template = '<g:message message="${message}" />'

        assertOutputEquals("The Default Message", template, [message: resolvable])
    }

    void testDefaultMessageAttributeWithAnEmptyStringValue() {
        def template  = '<g:message code="my.message.code" default=""/>'
        assertOutputEquals "", template
    }

    void testFieldValueTagWithMessageSourceResolvablePropertyUsesDefaultMessage() {
        def Title = ga.getClassForName("Title")
        def person = ga.getDomainClass("ValidationTagLibPerson").newInstance()
        person.properties = [title: Title.MR, name: "Al Coholic"]

        def template = '<g:fieldValue bean="${person}" field="title" />'

        assertOutputEquals "Mr", template, [person: person]
    }

    void testFieldValueTagWithMessageSourceResolvablePropertyUsesI18nBundle() {
        def Title = ga.getClassForName("Title")
        def person = ga.getDomainClass("ValidationTagLibPerson").newInstance()
        person.properties = [title: Title.MR, name: "Al Coholic"]

        def locale = new Locale("af", "ZA")
        messageSource.addMessage("Title.MR", locale, "Mnr")

        webRequest.currentRequest.addPreferredLocale(locale)
        def template = '<g:fieldValue bean="${person}" field="title" />'

        assertOutputEquals "Mnr", template, [person: person]
    }

    void testFieldValueTagForNonDomainInstance() {
        def template = '''<g:fieldValue bean="${book}" field="myUrl" />'''

        def myBook = gcl.parseClass('''
            import grails.persistence.*
            @Entity
            class MyBook {
                String title
                URL myUrl
                Date releaseDate
                BigDecimal usPrice
            }''').newInstance()

        myBook.myUrl = new URL("http://google.com")
        assertOutputEquals("http://google.com", template, [book:myBook])
    }

    void testFieldValueTagForNonDomainInstanceWithNestedField() {
        def template = '''<g:fieldValue bean="${book}" field="myUrl.publisherUrl" />'''

        // gcl.loadClass("MyClass", true)
        def myUrl = gcl.parseClass('''
            class MyUrl {
                URL publisherUrl
            }''').newInstance()

        def myBook = gcl.parseClass('''
            import grails.persistence.*
            @Entity
            class MyBook {
                String title
                MyUrl myUrl
                Date releaseDate
                BigDecimal usPrice
            }''').newInstance()

        myBook.myUrl = myUrl
        myBook.myUrl.publisherUrl = new URL("http://google.com")
        assertOutputEquals("http://google.com", template, [book:myBook])
    }


    void testFieldValueTagWithPropertyEditorForDomainClasses() {
        parsePhonePropertyEditorDomainClasses()

        def phonePropertyEditorDomainRegistrarClazz = gcl.loadClass("PhonePropertyEditorDomainRegistrar")
        appCtx.registerBeanDefinition(
                "phonePropertyEditorDomainRegistrar", new RootBeanDefinition(phonePropertyEditorDomainRegistrarClazz))

        def phoneUsInternationalDomain = ga.getDomainClass("PhoneUsInternationalDomain").newInstance()
        phoneUsInternationalDomain.properties = [country:"+1", area:"123", prefix:"123", number:"1234"]
        assertFalse phoneUsInternationalDomain.hasErrors()

        def personDomain = ga.getDomainClass("PersonDomain").newInstance()
        personDomain.properties = [firstName:"firstName1", lastName:"lastName1", phoneUsInternational:phoneUsInternationalDomain]
        assertFalse personDomain.hasErrors()

        def template = '''<g:fieldValue bean="${person}" field="phoneUsInternational" />'''
        assertOutputEquals("+1(123)123-1234", template, [person:personDomain])
    }

    void testFieldValueTagWithPropertyEditorForDomainClassesWithPropertyPath() {
        parsePhonePropertyEditorDomainClasses()

        def phonePropertyEditorDomainRegistrarClazz = gcl.loadClass("PhonePropertyEditorDomainRegistrar")
        appCtx.registerBeanDefinition(
                "phonePropertyEditorDomainRegistrar", new RootBeanDefinition(phonePropertyEditorDomainRegistrarClazz))

        def phoneUsDomain = ga.getDomainClass("PhoneUsDomain").newInstance()
        phoneUsDomain.properties = [area:"123", prefix:"123", number:"1234"]
        assertFalse phoneUsDomain.hasErrors()

        def personDomain = ga.getDomainClass("PersonDomain").newInstance()
        personDomain.properties = [firstName:"firstName1", lastName:"lastName1", phoneUs:phoneUsDomain, otherPhoneUs:phoneUsDomain]
        assertFalse personDomain.hasErrors()

        def template = '''<g:fieldValue bean="${person}" field="phoneUs" />'''
        assertOutputEquals("(123)123-1234", template, [person:personDomain])

        def otherTemplate = '''<g:fieldValue bean="${person}" field="otherPhoneUs" />'''
        assertOutputEquals("123-123-1234", otherTemplate, [person:personDomain])
    }



    void testFieldValueTagWithPropertyEditorForNonDomainClasses() {
        parsePhonePlainTestClasses()
        parsePhonePropertyEditorPlainClasses()

        def phonePropertyEditorRegistrarClazz = gcl.loadClass("PhonePropertyEditorRegistrar")
        appCtx.registerBeanDefinition(
                "phonePropertyEditorRegistrar", new RootBeanDefinition(phonePropertyEditorRegistrarClazz))

        def phoneUsInternational = gcl.loadClass("PhoneUsInternational").newInstance()
        phoneUsInternational.country = "+1"
        phoneUsInternational.area = "123"
        phoneUsInternational.prefix = "123"
        phoneUsInternational.number = "1234"

        def person = gcl.loadClass("PersonPlain").newInstance()
        person.firstName = "firstName1"
        person.lastName = "lastName1"
        person.phoneUsInternational = phoneUsInternational

        def template = '''<g:fieldValue bean="${person}" field="phoneUsInternational" />'''
        assertOutputEquals("+1(123)123-1234", template, [person:person])
    }

    void testFieldValueTagWithPropertyEditorForNonDomainClassesWithPropertyPath() {
        parsePhonePlainTestClasses()
        parsePhonePropertyEditorPlainClasses()

        def phonePropertyEditorRegistrarClazz = gcl.loadClass("PhonePropertyEditorRegistrar")
        appCtx.registerBeanDefinition(
                "phonePropertyEditorRegistrar", new RootBeanDefinition(phonePropertyEditorRegistrarClazz))

        def phoneUs = gcl.loadClass("PhoneUs").newInstance()
        phoneUs.area = "123"
        phoneUs.prefix = "123"
        phoneUs.number = "1234"

        def person = gcl.loadClass("PersonPlain").newInstance()
        person.firstName = "firstName1"
        person.lastName = "lastName1"
        person.phoneUs = phoneUs
        person.otherPhoneUs = phoneUs

        def template = '''<g:fieldValue bean="${person}" field="phoneUs" />'''
        assertOutputEquals("(123)123-1234", template, [person:person])

        def otherTemplate = '''<g:fieldValue bean="${person}" field="otherPhoneUs" />'''
        assertOutputEquals("123-123-1234", otherTemplate, [person:person])
    }
}

@Entity
class ValidationTagLibBook {
    String title
    URL publisherURL
    Date releaseDate
    BigDecimal usPrice
}

@Entity
class PhoneUsDomain {
    String area
    String prefix
    String number
}
class PersonPlain {
    String firstName
    String lastName
    PhoneUsInternational phoneUsInternational
    PhoneUs phoneUs
    PhoneUs otherPhoneUs
}
class PhoneUs {
    String area
    String prefix
    String number
}
class PhoneUsInternational {
    String country
    String area
    String prefix
    String number
}
class PersonDomain {
    String firstName
    String lastName
    PhoneUsInternationalDomain phoneUsInternational
    PhoneUsDomain phoneUs
    PhoneUsDomain otherPhoneUs
}
@Entity
class PhoneUsInternationalDomain {
    String country
    String area
    String prefix
    String number
}
class PhoneUsDomainMainEditor extends PropertyEditorSupport {
    String getAsText() {
        def phoneUsNumber = getValue()
        return "${phoneUsNumber.area}-${phoneUsNumber.prefix}-${phoneUsNumber.number}"
    }
}
class PhoneUsMainEditor extends PropertyEditorSupport {
    String getAsText() {
        def phoneUsNumber = getValue()
        return "${phoneUsNumber.area}-${phoneUsNumber.prefix}-${phoneUsNumber.number}"
    }
}
class PhoneUsDomainForPropertyPathEditor extends PropertyEditorSupport {
    String getAsText() {
        def phoneUsNumber = getValue()
        return "(${phoneUsNumber.area})${phoneUsNumber.prefix}-${phoneUsNumber.number}"
    }
}
class PhoneUsInternationalDomainEditor extends PropertyEditorSupport {
    String getAsText() {
        def phoneUsInternationalNumber = getValue()
        return "${phoneUsInternationalNumber.country}(${phoneUsInternationalNumber.area})" +
                "${phoneUsInternationalNumber.prefix}-${phoneUsInternationalNumber.number}"
    }
}
class PhonePropertyEditorDomainRegistrar implements PropertyEditorRegistrar {
    void registerCustomEditors(PropertyEditorRegistry registry) {
        registry.registerCustomEditor(PhoneUsInternationalDomain, new PhoneUsInternationalDomainEditor())
        registry.registerCustomEditor(PhoneUsDomain, new PhoneUsDomainMainEditor())
        registry.registerCustomEditor(PhoneUsDomain, "phoneUs", new PhoneUsDomainForPropertyPathEditor())
    }
}
