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
package org.apache.grails.web.layout

import com.opensymphony.module.sitemesh.parser.TokenizedHTMLPage
import spock.lang.Specification

import org.springframework.beans.factory.config.RuntimeBeanReference
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.web.servlet.View

import org.grails.plugins.web.GroovyPagesPostProcessor
import org.grails.web.util.WebUtils
import org.grails.web.servlet.view.GroovyPageViewResolver

/**
 * Tests the small support classes of the layout module: the no-op decorator, the HTML page parser,
 * the content adapter and the bean definition post processor that installs the layout view resolver.
 */
class GrailsLayoutSupportClassesSpec extends Specification {

    private static final String HTML = '''<html lang="en"><head><title>The Title</title>
<meta name="author" content="someone"><style>x{}</style></head><body class="c">The body</body></html>'''

    private static String written(Closure writing) {
        def out = new StringWriter()
        writing(out)
        out.toString()
    }

    void 'the no decorator has no page, name, role or parameters'() {
        given:
        def decorator = new GrailsNoDecorator()

        expect:
        decorator.getPage() == null
        decorator.getName() == null
        decorator.getURIPath() == null
        decorator.getRole() == null
        decorator.getInitParameter('any') == null
        decorator.getInitParameterNames() == null
    }

    void 'the parser extracts the title, head, body and meta properties'() {
        given:
        def parser = new GrailsHTMLPageParser()

        when:
        def page = (TokenizedHTMLPage) parser.parse(HTML.toCharArray())

        then:
        page.getTitle() == 'The Title'
        page.getProperty('meta.author') == 'someone'
        page.getProperty('lang') == 'en'
        page.getProperty('body.class') == 'c'
        written { page.writeBody(it) } == 'The body'
        written { page.writeHead(it) }.contains('<style>x{}</style>')
        written { page.writePage(it) } == HTML
    }

    void 'the parser ignores titles inside an xml block'() {
        given:
        def parser = new GrailsHTMLPageParser()

        when:
        def page = parser.parse('<html><head><title>Real</title><xml><book><title>Fake</title></book></xml></head><body>b</body></html>'.toCharArray())

        then:
        page.getTitle() == 'Real'
    }

    void 'parseContent wraps the parsed page in a content adapter'() {
        when:
        def content = new GrailsHTMLPageParser().parseContent(HTML.toCharArray())

        then:
        content instanceof TokenizedHTMLPage2Content
        content.getTitle() == 'The Title'
        content.getProperty('meta.author') == 'someone'
        content.originalLength() == HTML.length()
        written { content.writeOriginal(it) } == HTML
        written { content.writeBody(it) } == 'The body'
        written { content.writeHead(it) }.contains('<style>')
        content.getPage().getTitle() == 'The Title'
    }

    void 'properties added to the content adapter are visible on the page'() {
        given:
        def content = new GrailsHTMLPageParser().parseContent(HTML.toCharArray())

        when:
        content.addProperty('custom', 'value')

        then:
        content.getProperty('custom') == 'value'
        content.getPage().getProperty('custom') == 'value'
    }

    void 'the post processor replaces the view resolver bean with a layout resolver wrapping the original'() {
        given:
        def registry = new DefaultListableBeanFactory()
        def original = new GenericBeanDefinition(beanClass: Object)
        registry.registerBeanDefinition('jspViewResolver', original)

        when:
        new GrailsLayoutViewResolverPostProcessor().postProcessBeanDefinitionRegistry(registry)

        then:
        def definition = registry.getBeanDefinition('jspViewResolver')
        definition.beanClassName == GrailsLayoutViewResolver.name
        definition.primary
        definition.lazyInit
        definition.propertyValues.getPropertyValue('innerViewResolver').value.is(original)
        def finderRef = (RuntimeBeanReference) definition.propertyValues.getPropertyValue('groovyPageLayoutFinder').value
        finderRef.beanName == 'groovyPageLayoutFinder'
        definition.parentName == null
    }

    void 'the post processor defaults the inner resolver when none is defined and honours its settings'() {
        given:
        def registry = new DefaultListableBeanFactory()
        def processor = new GrailsLayoutViewResolverPostProcessor(layoutViewResolverBeanParentName: 'parentResolver', markBeanPrimary: false)

        when:
        processor.postProcessBeanDefinitionRegistry(registry)

        then:
        def definition = registry.getBeanDefinition('jspViewResolver')
        !definition.primary
        definition.parentName == 'parentResolver'
        def inner = (GenericBeanDefinition) definition.propertyValues.getPropertyValue('innerViewResolver').value
        inner.beanClassName == GroovyPageViewResolver.name
        inner.parentName == 'abstractViewResolver'
        inner.lazyInit
    }

    void 'a disabled post processor leaves the registry untouched'() {
        given:
        def registry = new DefaultListableBeanFactory()

        when:
        new GrailsLayoutViewResolverPostProcessor(enabled: false).postProcessBeanDefinitionRegistry(registry)

        then:
        !registry.containsBeanDefinition('jspViewResolver')
    }

    void 'the post processor is ordered and has a no-op bean factory phase'() {
        given:
        def processor = new GrailsLayoutViewResolverPostProcessor()

        expect:
        processor.order == GroovyPagesPostProcessor.ORDER - 1

        when:
        processor.postProcessBeanFactory(new DefaultListableBeanFactory())

        then:
        noExceptionThrown()
    }

    void 'the layout selector prefers an explicit layout and otherwise depends on whether a view is rendered'() {
        given:
        def selector = new LayoutSelector()

        expect:
        selector.selectLayout('explicit', true) == 'explicit'
        selector.selectLayout('explicit', false) == 'explicit'
        selector.selectLayout(null, true) == null
        selector.selectLayout(null, false) == WebUtils.NONE_LAYOUT
    }

    void 'the render view mutator unwraps an automatically decorated view and rewraps it only when a layout is wanted'() {
        given:
        def finder = new GroovyPageLayoutFinder()
        def mutator = new GrailsLayoutRenderViewMutator(groovyPageLayoutFinder: finder)
        def plain = Stub(View)
        def decorated = new EmbeddedGrailsLayoutView(finder, plain)

        expect:
        mutator.mutateView(false, '/t', Locale.ENGLISH, plain).is(plain)
        mutator.mutateView(false, '/t', Locale.ENGLISH, decorated).is(plain)

        when:
        def wrapped = mutator.mutateView(true, '/t', Locale.ENGLISH, decorated)

        then:
        wrapped instanceof EmbeddedGrailsLayoutView
        !wrapped.is(decorated)
        ((EmbeddedGrailsLayoutView) wrapped).getInnerView().is(plain)
        ((EmbeddedGrailsLayoutView) wrapped).groovyPageLayoutFinder.is(finder)
    }
}
