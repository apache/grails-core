package org.grails.web.taglib

import grails.artefact.Artefact
import grails.testing.web.UrlMappingsUnitTest
import spock.lang.Specification

class LinkRenderingTagLibTests extends Specification implements UrlMappingsUnitTest<LinkRenderingTestUrlMappings> {


    def testMappingsWhichSpecifyAPlugin() {
        when:
        def template = '<g:link controller="first" action="index" plugin="firstUtil">click</g:link>'
        String output = applyTemplate(template)

        then:
        output == '<a href="/pluginOneFirstController">click</a>'

        when:
        template = '<g:link controller="first" action="index" plugin="secondUtil">click</g:link>'
        output = applyTemplate(template)

        then:
        output == '<a href="/pluginTwoFirstController">click</a>'

        when:
        template = '<g:link controller="first" action="index" plugin="thirdUtil">click</g:link>'
        output = applyTemplate(template)

        then:
        output == '<a href="/pluginThreeFirstController">click</a>'

        when:
        template = '<g:link controller="first" action="index" plugin="firstUtil" params="[num: 42]" >click</g:link>'
        output = applyTemplate(template)

        then:
        output == '<a href="/pluginOneFirstController?num=42">click</a>'

        when:
        template = '<g:link controller="first" action="index" plugin="secondUtil" params="[num: 42]" >click</g:link>'
        output = applyTemplate(template)

        then:
        output == '<a href="/pluginTwoFirstController/42">click</a>'

        when:
        template = '<g:link controller="first" action="index" plugin="thirdUtil" params="[num: 42]" >click</g:link>'
        output = applyTemplate(template)

        then:
        output == '<a href="/pluginThreeFirstController?num=42">click</a>'

        when:
        template = '<g:createLink controller="first" action="index" plugin="firstUtil" />'
        output = applyTemplate(template)

        then:
        output == '/pluginOneFirstController'

        when:
        template = '<g:createLink controller="first" action="index" plugin="secondUtil" />'
        output = applyTemplate(template)

        then:
        output == '/pluginTwoFirstController'

        when:
        template = '<g:createLink controller="first" action="index" plugin="thirdUtil" />'
        output = applyTemplate(template)

        then:
        output == '/pluginThreeFirstController'

        when:
        template = '<g:createLink controller="first" action="index" plugin="firstUtil" params="[num: 42]" />'
        output = applyTemplate(template)

        then:
        output == '/pluginOneFirstController?num=42'

        when:
        template = '<g:createLink controller="first" action="index" plugin="secondUtil" params="[num: 42]" />'
        output = applyTemplate(template)

        then:
        output == '/pluginTwoFirstController/42'

        when:
        template = '<g:createLink controller="first" action="index" plugin="thirdUtil" params="[num: 42]" />'
        output = applyTemplate(template)

        then:
        output == '/pluginThreeFirstController?num=42'
    }

    def testLinkTagWithAttributeValueContainingEqualSignFollowedByQuote() {
        //  Some of these tests look peculiar but they relate to
        //  scenarios that were broken before GRAILS-7229 was addressed

        when:
        def template = '''<g:link controller="demo" class="${(y == '5' && x == '4') ? 'A' : 'B'}" >demo</g:link>'''
        String output = applyTemplate(template, [x: '7', x: '4'])

        then:
        output == '<a href="/demo" class="B">demo</a>'

        when:
        template = '''<g:link controller="demo" class="${(y == '5' && x == '4') ? 'A' : 'B'}" >demo</g:link>'''
        output = applyTemplate(template, [x: '4', y: '5'])

        then:
        output == '<a href="/demo" class="A">demo</a>'

        when:
        template = '''<g:link controller="demo" class='${(y == "5" && x == "5") ? "A" : "B"}' >demo</g:link>'''
        output = applyTemplate(template,  [y: '0', x: '5'])

        then:
        output == '<a href="/demo" class="B">demo</a>'

        when:
        template = '''<g:link controller="demo" class='${(y == "5" && x == "5") ? "A" : "B"}' >demo</g:link>'''
        output = applyTemplate(template, [y: '5', x: '5'])

        then:
        output == '<a href="/demo" class="A">demo</a>'

        when:
        template = '''<g:link controller="demo" class="${(someVar == 'abcd')}" >demos</g:link>'''
        output = applyTemplate(template, [someVar: 'some value'])

        then:
        output == '<a href="/demo" class="false">demos</a>'

        when:
        template = '''<g:link controller="demo" class="${(someVar == 'abcd')}" >demos</g:link>'''
        output = applyTemplate(template, [someVar: 'abcd'])

        then:
        output == '<a href="/demo" class="true">demos</a>'

        when:
        template = '''<g:link controller="demo" class="${(someVar == 'abcd' )}" >demos</g:link>'''
        output = applyTemplate(template,[someVar: 'some value'])

        then:
        output == '<a href="/demo" class="false">demos</a>'

        when:
        template = '''<g:link controller="demo" class="${(someVar == 'abcd' )}" >demos</g:link>'''
        output = applyTemplate(template,[someVar: 'abcd'])

        then:
        output == '<a href="/demo" class="true">demos</a>'
    }

//    void testOverlappingReverseMappings() {
//        def template = '<g:link controller="searchable" action="index" >Search</g:link>'
//        assertOutputEquals('<a href="/searchable">Search</a>', template)
//
//        template = '<g:link controller="searchable" >Search</g:link>'
//        assertOutputEquals('<a href="/searchable">Search</a>', template)
//
//        template = '<g:link controller="searchable" action="other" >Search</g:link>'
//        assertOutputEquals('<a href="/searchable/other">Search</a>', template)
//
//        template = '<g:form controller="searchable" action="index" >Search</g:form>'
//        assertOutputEquals('<form action="/searchable" method="post" >Search</form>', template)
//
//        template = '<g:form controller="searchable" >Search</g:form>'
//        assertOutputEquals('<form action="/searchable" method="post" >Search</form>', template)
//    }

    def testLinkWithControllerAndId() {
        when:
        def template = '<g:link controller="book" id="10">${name}</g:link>'
        String output = applyTemplate(template,[name:"Groovy in Action"])

        then:
        output == '<a href="/book?id=10">Groovy in Action</a>'
    }

    def testRenderLinkWithReverseMapping() {
        when:
        def template = '<g:link controller="survey">${name}</g:link>'
        String output = applyTemplate(template,[name:"Food I Like"])

        then:
        output == '<a href="/surveys">Food I Like</a>'

        when:
        template = '<g:link controller="test" action="index" id="MacBook">${name}</g:link>'
        output = applyTemplate(template, [name:"MacBook"])

        then:
        output == '<a href="/products/MacBook">MacBook</a>'
    }

    def testUrlMapper() {
        when:
        def mappings = grailsApplication.getMainContext().getBean("grailsUrlMappingsHolder")

        then:
        mappings.urlMappings.length > 0
    }

    def testRenderLink() {
        when:
        def template = '<g:link controller="foo" action="list">${name}</g:link>'
        String output = applyTemplate(template, [name:"bar"])
        then:
        output == '<a href="/foo/list">bar</a>'
    }

    def testRenderForm() {
        when:
        def template = '<g:form controller="foo" action="list">${name}</g:form>'
        String output = applyTemplate(template,[name:"bar"])

        then:
        output == '<form action="/foo/list" method="post" >bar</form>'

        when:
        template = '<g:form controller="foo">${name}</g:form>'
        output = applyTemplate(template,[name:"bar"])

        then:
        output == '<form action="/foo" method="post" >bar</form>'
    }

    def testRenderFormWithUrlAttribute() {
        when:
        def template = '<g:form url="[controller:\'stuff\',action:\'list\']">${name}</g:form>'
        String output = applyTemplate(template,[name:"bar"])

        then:
        output == '<form action="/stuff/list" method="post" >bar</form>'

        when:
        template = '<g:form url="[controller:\'stuff\',action:\'show\', id:11]" id="myForm">${name}</g:form>'
        output = applyTemplate(template,[name:"bar"])

        then:
        output == '<form action="/stuff/show/11" method="post" id="myForm" >bar</form>'
    }

//    void testRenderFormWithUrlAttributeAndReverseMapping() {
//        def template = '<g:form url="[controller:\'test\',action:\'index\', id:\'MacBook\']">${name}</g:form>'
//        assertOutputEquals('<form action="/products/MacBook" method="post" >MacBook</form>', template, [name:"MacBook"])
//    }

    def testCreateLinkWithCollectionParamsGRAILS7096() {
        when:
        def template = '''<g:createLink controller="controller" action="action" params="[test:['1','2']]"/>'''
        String output = applyTemplate(template,[:])

        then:
        output == "/controller/action?test=1&test=2"

        when:
        template = '''<g:createLink controller="controller" action="action" params="[test:['2','3']]"/>'''
        output = applyTemplate(template,[:])

        then:
        output == "/controller/action?test=2&test=3"
    }

    def testCreateLinkWithObjectArrayParams() {
        when:
        def template = '''<g:createLink controller="controller" action="action" params="[test:['1','2'] as Object[]]"/>'''
        String output = applyTemplate(template,[:])

        then:
        output == "/controller/action?test=1&test=2"

        when:
        template = '''<g:createLink controller="controller" action="action" params="[test:['2','3'] as Object[]]"/>'''
        output = applyTemplate(template,[:])

        then:
        output == "/controller/action?test=2&test=3"
    }

    def testCreateLinkWithExtraParamsGRAILS8249() {
        when:
        def template = '''<g:createLink controller="test2" action="show" id="jim" params="[name: 'Jim Doe', age: 31]" />'''
        String output = applyTemplate(template,[:])

        then:
        output == "/dummy/show/Jim%20Doe/jim?age=31"

        // Ensure that without the required name param that it falls back to the conventional mapping
        when:
        template = '''<g:createLink controller="test2" action="show" id="jim" params="[age: 31]" />'''
        output = applyTemplate(template,[:])

        then:
        output == "/test2/show/jim?age=31"
    }
}

@Artefact("UrlMappings")
class LinkRenderingTestUrlMappings {
    static mappings = {
        "/$controller/$action?/$id?"{
            constraints {
                // apply constraints here
            }
        }

        "/products/$id" {
            controller = "test"
            action = "index"
        }
        "/surveys/$action?" {
            controller = "survey"
        }
        "/searchable" {
            controller = "searchable"
            action = "index"
        }
        "/searchable/$action?" {
            controller = "searchable"
        }

        "/dummy/$action/$name/$id"(controller: "test2")

        "/pluginOneFirstController" {
            controller = 'first'
            action = 'index'
            plugin = 'firstUtil'
        }

        "/pluginTwoFirstController/$num?" {
            controller = 'first'
            action = 'index'
            plugin = 'secondUtil'
        }

        "/pluginThreeFirstController"(controller: 'first', action: 'index', plugin: 'thirdUtil')
    }
}