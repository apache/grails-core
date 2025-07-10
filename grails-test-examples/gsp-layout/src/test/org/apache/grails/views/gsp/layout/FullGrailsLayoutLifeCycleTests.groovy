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

import com.opensymphony.module.sitemesh.Config
import com.opensymphony.module.sitemesh.Decorator
import com.opensymphony.module.sitemesh.DecoratorMapper
import com.opensymphony.module.sitemesh.PageParser
import com.opensymphony.module.sitemesh.RequestConstants
import com.opensymphony.module.sitemesh.factory.BaseFactory
import org.apache.grails.web.layout.FactoryHolder
import org.apache.grails.web.layout.GSPGrailsLayoutPage
import org.apache.grails.web.layout.GrailsHTMLPageParser
import org.apache.grails.web.layout.EmbeddedGrailsLayoutView
import org.grails.core.io.MockStringResourceLoader
import AbstractGrailsTagTests
import Test
import MockServletConfig

/**
 * Tests the grails layout capturing and rendering tags end-to-end
 *
 * @author Graeme Rocher
 * @since 1.2
 */
class FullGrailsLayoutLifeCycleTests extends AbstractGrailsTagTests {

    @Test
    void testSimpleLayout() {
        def template = '''
<html>
        <head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"><title>This is the title</title></head>
        <body onload="test();">body text</body>
</html>
'''

        assertOutputEquals('''
<html>
        <head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"><title>This is the title</title></head>
        <body onload="test();">body text</body>
</html>
''', template)

        def layout = '''
<html>
    <head><title>Decorated <g:layoutTitle /></title><g:layoutHead /></head>
    <body><h1>Hello</h1><g:layoutBody /></body>
</html>
'''
        def result = applyLayout(layout, template)

        org.junit.jupiter.api.Assertions.assertEquals '''
<html>
    <head><title>Decorated This is the title</title><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"></head>
    <body><h1>Hello</h1>body text</body>
</html>
''', result
    }

    @Test
    void testTitleInSubTemplate() {
        def resourceLoader = new MockStringResourceLoader()
        resourceLoader.registerMockResource('/_title.gsp', '<title>This is the title</title>')
        appCtx.groovyPageLocator.addResourceLoader(resourceLoader)

        def template = '''
<html>
        <head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"><g:render template="/title"/></head>
        <body onload="test();">body text</body>
</html>
'''

        assertOutputEquals('''
<html>
        <head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"><title>This is the title</title></head>
        <body onload="test();">body text</body>
</html>
''', template)

        def layout = '''
<html>
    <head><title>Decorated <g:layoutTitle /></title><g:layoutHead /></head>
    <body><h1>Hello</h1><g:layoutBody /></body>
</html>
'''
        def result = applyLayout(layout, template)

        org.junit.jupiter.api.Assertions.assertEquals('''
<html>
    <head><title>Decorated This is the title</title><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"></head>
    <body><h1>Hello</h1>body text</body>
</html>
''', result)
    }

    static class DummyGrailsLayoutFactory extends BaseFactory {

        DummyGrailsLayoutFactory(Config config) {
            super(config)
        }

        @Override
        void refresh() {
        }

        @Override
        public PageParser getPageParser(String contentType) {
            new GrailsHTMLPageParser()
        }
    }

    def configureGrailsLayout() {
        def mockServletConfig = new MockServletConfig()
        def grailsLayoutConfig = new Config(mockServletConfig)
        def grailsLayoutFactory = new DummyGrailsLayoutFactory(grailsLayoutConfig)
        def decorator = { name -> [getPage: { -> "/layout/${name}.gsp".toString() }] as Decorator }
        grailsLayoutFactory.decoratorMapper = [getNamedDecorator: { request, name -> decorator(name) }] as DecoratorMapper
        FactoryHolder.factory = grailsLayoutFactory
    }

    @Test
    void testMultipleLevelsOfLayouts() {
        def resourceLoader = new MockStringResourceLoader()
        resourceLoader.registerMockResource('/layout/dialog.gsp', '''<html>
        <head><g:layoutHead /><title>Dialog - <g:layoutTitle /></title></head>
        <body onload="${g.pageProperty(name:'body.onload')}"><div id="dialog"><g:layoutBody /></div></body>
</html>''')
        resourceLoader.registerMockResource('/layout/base.gsp', '''<html>
        <head><g:layoutHead /><title>Base - <g:layoutTitle /></title></head>
        <body onload="${g.pageProperty(name:'body.onload')}"><div id="base"><g:layoutBody /></div></body>
</html>''')
        appCtx.groovyPageLocator.addResourceLoader resourceLoader

        configureGrailsLayout()

        def template = '''
<g:applyLayout name="base"><g:applyLayout name="dialog">
<html>
        <head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"><title>This is the title</title></head>
        <body onload="test();">body text</body>
</html>
</g:applyLayout></g:applyLayout>
'''
        request.setAttribute(RequestConstants.PAGE, new GSPGrailsLayoutPage())
        request.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, new GSPGrailsLayoutPage())
        assertOutputEquals '''
<html>
        <head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"><title>Base - Dialog - This is the title</title></head>
        <body onload="test();"><div id="base"><div id="dialog">body text</div></div></body>
</html>
''', template

        def layout = '''
<html>
    <head><title>Decorated <g:layoutTitle default="defaultTitle"/></title><g:layoutHead /></head>
    <body><h1>Hello</h1><g:layoutBody /></body>
</html>
'''
        def result = applyLayout(layout, template)

        org.junit.jupiter.api.Assertions.assertEquals '''
<html>
    <head><title>Decorated Base - Dialog - This is the title</title><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"></head>
    <body><h1>Hello</h1><div id="base"><div id="dialog">body text</div></div></body>
</html>
''', result
    }

    @Test
    void testParameters() {
        def template = '''
<html>
  <head>
      <title>Simple GSP page</title>
      <meta name="layout" content="main"/>
      <parameter name="navigation" value="here!"/>
  </head>
  <body>Place your content here</body>
</html>
'''

        def layout = '<h1>pageProperty: ${pageProperty(name: \'page.navigation\')}</h1>'

        def result = applyLayout(layout, template)

        org.junit.jupiter.api.Assertions.assertEquals '<h1>pageProperty: here!</h1>', result
    }

    @Test
    void testParametersWithLogic() {
        def template = '''
<html>
  <head>
      <title>Simple GSP page</title>
      <meta name="layout" content="main"/>
      <parameter name="sideBarSetting" value="vendor"/>
  </head>
  <body>Place your content here</body>
</html>
'''
        def layout = '''<g:if test="${pageProperty(name:'page.sideBarSetting') == 'vendor'}">good</g:if>'''

        def result = applyLayout(layout, template, [:])

        org.junit.jupiter.api.Assertions.assertEquals 'good', result
    }

    // GRAILS-11484
    @Test
    void testMultilineTitle() {
        def template = '''
<html>
        <head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>
This is the title
</title></head>
        <body onload="test();">body text</body>
</html>
'''

        assertOutputEquals '''
<html>
        <head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>
This is the title
</title></head>
        <body onload="test();">body text</body>
</html>
''', template

        def layout = '''
<html>
    <head><title>Decorated <g:layoutTitle /></title><g:layoutHead /></head>
    <body><h1>Hello</h1><g:layoutBody /></body>
</html>
'''
        def result = applyLayout(layout, template)

        org.junit.jupiter.api.Assertions.assertEquals '''
<html>
    <head><title>Decorated 
This is the title
</title><meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
</head>
    <body><h1>Hello</h1>body text</body>
</html>
''', result
    }

}

