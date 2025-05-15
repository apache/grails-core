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
package org.grails.web.mapping

import grails.core.DefaultGrailsApplication
import grails.gorm.validation.ConstrainedProperty
import grails.gorm.validation.DefaultConstrainedProperty
import grails.testing.web.UrlMappingsUnitTest
import grails.web.mapping.UrlMapping
import grails.web.mapping.exceptions.UrlMappingException
import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry
import org.springframework.context.support.StaticMessageSource
import spock.lang.PendingFeatureIf
import spock.lang.Specification

class RegexUrlMappingTests extends Specification implements UrlMappingsUnitTest<UrlMappings> {


    void testExtensionPrecededByTokenWhichMayContainDots() {
        when:
        def holder = urlMappingsHolder
        def info = holder.match("/plugins/grails-csv/tags/RELEASE_0.3.1/csv-0.3.1.pom")

        then:
        info
        'website'== info.controllerName
        'displayPlugin'== info.actionName
        '0.3.1'== info.params.version
        'csv'== info.params.plugin
        'csv-0.3.1'== info.params.fullName
        'pom'== info.params.type
    }

    void testHyphenDelimiters() {
        given:
        def holder = urlMappingsHolder


        when:
        def info = holder.match("/one-alpha-two/three-beta-four-foo.json")

        then:
        info
        'hyphenTests'== info.controllerName
        'view'== info.actionName
        'one'== info.params.first
        'two'== info.params.second
        'three'== info.params.third
        'four'== info.params.fourth
        'json'== info.params.format


        when:
        info = holder.match("/one-alpha-two/three-beta-four-foo")

        then:
        info
        'hyphenTests'== info.controllerName
        'view'== info.actionName
        'one'== info.params.first
        'two'== info.params.second
        'three'== info.params.third
        'four'== info.params.fourth
        !info.params.format
    }

    void testMaptoURI() {
        given:
        def holder = urlMappingsHolder

        when:
        def info = holder.match("/bar")

        then:
        "/x/y"== info.getURI()
    }

    void testParseRequestArgument() {
        given:
        def holder = urlMappingsHolder

        when:
        def info = holder.match("/foo")

        then:"should have been a request parsing mapping"
        info.parsingRequest

        when:
        info = holder.match("/foo2")

        then:"should have been a request parsing mapping"
        info.parsingRequest


    }

    void testNullableConstraintsInMapping() {
        given:
        def holder = urlMappingsHolder

        def mappings = holder.urlMappings
        def m = holder.urlMappings.find() { it.controllerName == 'survey' && !it.actionName }

        expect:
        m.urlData.isOptional(0)
        1 == m.constraints.length
        m.constraints[0].nullable
    }

    @PendingFeatureIf({
        // thrown() does currently not work with Groovy 5
        GroovySystem.version.startsWith('5')
    })
    void testCreateUrlFromMapping() {
        given:
        def holder = urlMappingsHolder
        def mappings = holder.urlMappings

        when:
        def m = holder.urlMappings.find() { it.controllerName == 'book'}

        then:
        m
        "/book/dierk/gina/foo"== m.createURL(author: "dierk", title: "gina", test: "foo", "utf-8")

        when:
        m = holder.urlMappings.find() { it.controllerName == 'blog'}

        then:
        m
        "/blog/foo/2007/10/24" == m.createURL(entry: "foo", year: 2007, month: 10, day: 24, "utf-8")
        "/blog/foo/2007/10" == m.createURL(entry: "foo", year: 2007, month: 10, "utf-8")
        "/blog/foo/2007" == m.createURL(entry: "foo", year: 2007, "utf-8")
        "/blog/foo/2007" == m.createURL(entry: "foo", year: 2007, null)
        "/blog/foo" == m.createURL(entry: "foo", "utf-8")
        "/blog/foo" == m.createURL(entry: "foo", null)
        '/blog/My%20%2410' == m.createURL(entry: 'My $10', "utf-8")
        '/blog/My%20%24temp' == m.createURL(entry: 'My $temp', "utf-8")
        "/blog/foo?day=24" ==  m.createURL(entry: "foo", day: 24, "utf-8")

        when:
        m.createURL([:], "utf-8")

        then:
        thrown(Throwable)

        when:
        m = holder.urlMappings.find() { it.controllerName == 'files'}

        then:
        m
        "/files/path/to/my/file" == m.createURL([path:"/path/to/my/file"], "utf-8")

        when:
        m = holder.urlMappings.find() { it.controllerName == 'download'}

        then:
        m
        "/filenameext/grails" == m.createURL([fname:'grails'], "utf-8")
        "/filenameext/grails." == m.createURL([fname:'grails.'], "utf-8")
        "/filenameext/grails.jpg" == m.createURL(fname:"grails",fext:".jpg", "utf-8")

        when:
        m = holder.urlMappings.find() { it.controllerName == 'myFiles'}

        then:
        m
        "/another/arbitrary/something-source.jar" ==
                m.createURL(controller:"myFiles",action:"index", prefix:"source", ext:"jar", "utf-8")

        // "ext" is a required property, so if it isn't specified an
        // exception should be thrown.
        when:
        m.createURL(controller:"myFiles",action:"index", prefix:"source", "utf-8")

        then:
        thrown(UrlMappingException)
    }

    void testCreateUrlWithFragment() {
        given:
        def holder = urlMappingsHolder
        def mappings = holder.urlMappings.find() { it.controllerName == 'book'}

        when:
        def m = holder.urlMappings.find() { it.controllerName == 'book'}

        then:
        m
        "/book/dierk/gina/foo#testfrag" == m.createURL(author: "dierk", title: "gina", test: "foo", "utf-8", "testfrag")

        when:
        m = holder.urlMappings.find() { it.controllerName == 'blog'}

        then:
        m
        "/blog/foo/2007/10/24#testfrag2" == m.createURL(entry: "foo", year: 2007, month: 10, day: 24, "utf-8", "testfrag2")

        // Test the behaviour of a null encoding.
        "/blog/foo/2007/10/24#testfrag2" == m.createURL(entry: "foo", year: 2007, month: 10, day: 24, null, "testfrag2")
    }



    void testMatchUriWithConstraints() {

        given:
        def cp = new DefaultConstrainedProperty(RegexUrlMappingTests.class, "hello", String.class, new DefaultConstraintRegistry(new StaticMessageSource()))
        cp.nullable = false

        // mapping would be "/foo/$hello/bar
        def parser = new DefaultUrlMappingParser()

        def m = new RegexUrlMapping(parser.parse('/foo/(*)/bar'), "test", "action", null, null, null, null,UrlMapping.ANY_VERSION, [cp] as ConstrainedProperty[], grailsApplication)

        def info = m.match("/foo/world/bar")

        expect:
        assert info
        "test" == info.controllerName
        "action" == info.actionName
        "world" == info.parameters.hello
    }

    void testMatchUriWithMatchesConstraints() {

        given:
        def cp = new DefaultConstrainedProperty(RegexUrlMappingTests.class, "year", String.class, new DefaultConstraintRegistry(new StaticMessageSource()))
        cp.matches = /\d{4}/

        // mapping would be "/foo/$hello/bar
        def parser = new DefaultUrlMappingParser()
        def m = new RegexUrlMapping(parser.parse('/foo/(*)/bar'), "test", "action", null, null, null, null, UrlMapping.ANY_VERSION,[cp] as ConstrainedProperty[], grailsApplication)


        def info = m.match("/foo/2007/bar")
        def info2 = m.match("/foo/blah/bar")

        expect:
        assert info
        "test" == info.controllerName
        "action" == info.actionName
        "2007" == info.parameters.year
        !info2

    }

    void testConstraintAsTiebreaker() {
        // test that two similar rules that only differ by # of constraints are evaluated correctly
        given:
        def holder = urlMappingsHolder


        when:
        def info = holder.match("/surveys/view/123")

        then:
        info
        'survey' == info.controllerName
        'viewById' == info.actionName

        when:
        info = holder.match("/surveys/view/foo")

        then:
        info
        'survey' == info.controllerName
        'viewByName' == info.actionName
    }
    
    void testParameterContainingADot() {
        given:
        def holder = urlMappingsHolder


        when:
        def info = holder.match("/reports/my")

        then:
        info
        'reporting' == info.controllerName
        'view' == info.actionName
        'my' == info.params.foo

        when:
        info = holder.match("/reports/my.id")

        then:
        info
        'reporting' == info.controllerName
        'view' == info.actionName
        'my.id' == info.params.foo
    }
    
    void testInit() {
        given:
        def parser = new DefaultUrlMappingParser()
        expect:
        new RegexUrlMapping(parser.parse("/(*)/hello"), "test", null, null, null, null, null, UrlMapping.ANY_VERSION,[] as ConstrainedProperty[], grailsApplication)
    }

    void testMatchUriNoConstraints() {
        given:
        def parser = new DefaultUrlMappingParser()
        def m = new RegexUrlMapping(parser.parse("/foo/(*)/bar"), "test", null, null, null, null, null,UrlMapping.ANY_VERSION, [] as ConstrainedProperty[], new DefaultGrailsApplication())
        def info = m.match("/foo/test/bar")
        def info2 = m.match("/foo/bar/test")

        expect:
        info
        "test" == info.controllerName
        !info2
    }

    static class UrlMappings {
        static mappings = {
            "/book/$author/$title/$test" {
                controller = "book"
                action = "show"
            }
            "/blog/$entry/$year?/$month?/$day?" {
                controller = "blog"
                action = "show"
            }
            "/surveys/$action?" {
                controller = "survey"
            }
            "/files/$path**?" {
                controller = "files"
            }
            "/filenameext/$fname$fext?" {
                controller = "download"
            }
            "/another/arbitrary/something-$prefix.$ext" {
                controller = "myFiles"
                action = "index"
            }
            "/foo"(controller:"foo", parseRequest:true)

            "/foo2"(controller: "foo") {
                parseRequest = true
            }

            "/foo3" {
                controller = "foo"
                parseRequest = true
            }

            name foo4: "/foo4" {
                controller = "foo"
                parseRequest = true
            }

            "/bar"(uri:"/x/y")

            "/surveys/view/$id" {
                controller = "survey"
                action = "viewById"
                constraints {
                    id(matches:/\d+/)
                }
            }
            "/surveys/view/$name" {
                controller = "survey"
                action = "viewByName"
            }
            "/reports/$foo" {
                controller = 'reporting'
                action = 'view'
            }
            "/$first-alpha-$second/$third-beta-$fourth-foo(.$format)?" {
                controller = 'hyphenTests'
                action = 'view'
            }
            "/plugins/grails-$plugin/tags/RELEASE_$version/$fullName(.$type)" {
                controller = 'website'
                action = 'displayPlugin'
            }
        }
    }
}
