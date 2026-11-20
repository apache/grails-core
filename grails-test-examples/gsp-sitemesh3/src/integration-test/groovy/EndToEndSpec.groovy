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

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

@Integration
class EndToEndSpec extends ContainerGebSpec {

    def 'simple layout'() {
        when:
        go('endToEnd/simpleLayout')

        then:
        pageSource == """<html><head><title>Decorated This is the title</title><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"></head>
<body><h1>Hello</h1>body text
</body></html>"""
    }

    def 'title in subtemplate'() {
        when:
        go('endToEnd/titleInSubtemplate')

        then:
        pageSource == """<html><head><title>Decorated This is the title</title><meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
</head>
<body><h1>Hello</h1>body text
</body></html>"""
    }

    def 'multiple levels of layouts'() {
        when:
        go('endToEnd/multipleLevelsOfLayouts')

        then:
        pageSource == """<html><head><title>Decorated Base - Dialog - This is the title</title><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"></head>
<body><h1>Hello</h1><div id="base"><div id="dialog">body text</div></div>
</body></html>"""
    }

    def 'parameters'() {
        when:
        go('endToEnd/parameters')

        then:
        pageSource == """<html><head></head><body><h1>pageProperty: here!</h1></body></html>"""
    }

    def 'parameters with logic'() {
        when:
        go('endToEnd/parametersWithLogic')

        then:
        pageSource == "<html><head></head><body>good</body></html>"
    }

    def 'multiline title'() {
        when:
        go('endToEnd/multilineTitle')

        then:
        pageSource == """<html><head><title>Decorated 
    This is the title
    </title><meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    </head>
<body><h1>Hello</h1>body text
</body></html>"""
    }

    // The async dispatch returns on a different thread than the original
    // request — exercises Sitemesh3CapturedPage.propertiesMaterialized
    // cross-thread visibility (the volatile field).
    def 'async simple layout'() {
        when:
        go('endToEnd/asyncSimpleLayout')

        then:
        pageSource == """<html><head><title>Decorated This is the title</title><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"></head>
<body><h1>Hello</h1>body text
</body></html>"""
    }

    // Async dispatch + nested <g:applyLayout> — covers both the volatile
    // captured-page field and the ViewResolver-based dispatch that replaced
    // RequestDispatcher.forward() for nested layouts.
    def 'async multiple levels of layouts'() {
        when:
        go('endToEnd/asyncMultipleLevelsOfLayouts')

        then:
        pageSource == """<html><head><title>Decorated Base - Dialog - This is the title</title><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"></head>
<body><h1>Hello</h1><div id="base"><div id="dialog">body text</div></div>
</body></html>"""
    }

    // The template/url/action/model/parse attribute forms mirror SiteMesh 2's
    // <g:applyLayout> (RenderGrailsLayoutTagLib); the SiteMesh 2 twin app has
    // no coverage for them, so these cases encode the SiteMesh 2 semantics.
    def 'apply layout to a template'() {
        when:
        go('endToEnd/templateContent')

        then:
        pageSource.contains('<h1>Hello</h1>')
        pageSource.contains('template content with from the model')
    }

    def 'apply layout to a template that is a full document'() {
        when:
        go('endToEnd/templateDocument')

        then:
        title == 'Decorated Document title'
        pageSource.contains('<h1>Hello</h1>')
        pageSource.contains('document body')
    }

    def 'apply layout to the output of another controller action'() {
        when:
        go('endToEnd/actionContent')

        then:
        title == 'Decorated Included title'
        pageSource.contains('<h1>Hello</h1>')
        pageSource.contains('included body foo=bar')
    }

    def 'apply layout to content fetched from a url'() {
        when:
        go('endToEnd/urlContent')

        then:
        title == 'Decorated Included title'
        pageSource.contains('<h1>Hello</h1>')
        pageSource.contains('included body foo=none')
    }

    def 'parse attribute forces a SiteMesh parse of the tag body'() {
        when:
        go('endToEnd/parseContent')

        then:
        title == 'Decorated Parsed title'
        pageSource.contains('<h1>Hello</h1>')
        pageSource.contains('parsed body')
    }

    def 'model is available to the layout being applied'() {
        when:
        go('endToEnd/modelContent')

        then:
        pageSource.contains('<span id="model">hi from the model</span>')
        pageSource.contains('plain body')
    }
}
