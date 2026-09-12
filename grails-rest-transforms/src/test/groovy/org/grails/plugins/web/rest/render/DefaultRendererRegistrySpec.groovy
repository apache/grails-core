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
package org.grails.plugins.web.rest.render

import grails.rest.render.AbstractRenderer
import grails.rest.render.RenderContext
import grails.rest.render.hal.HalJsonCollectionRenderer
import grails.rest.render.errors.ValidationProblemDetailFactory
import grails.web.mime.MimeType
import org.grails.plugins.web.rest.render.json.DefaultJsonRenderer
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.core.env.MapPropertySource
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import spock.lang.Specification

class DefaultRendererRegistrySpec extends Specification {

    void 'validation errors negotiate the problem JSON media type'() {
        given:
        def registry = new DefaultRendererRegistry()
        registry.initialize()
        def errors = new BeanPropertyBindingResult(new Object(), 'book')

        expect:
        registry.findContainerRenderer(DefaultJsonRenderer.PROBLEM_JSON, Errors, errors) instanceof DefaultJsonRenderer
    }

    void "Test the registry resolves grails.converters.encoding from the environment"() {
        given: "an application context whose environment configures a non-default encoding"
            def context = new AnnotationConfigApplicationContext()
            context.environment.propertySources.addFirst(
                    new MapPropertySource('test', ['grails.converters.encoding': 'ISO-8859-1']))
            context.registerBean(PropertySourcesPlaceholderConfigurer)
            context.registerBean(DefaultRendererRegistry)
            context.refresh()

        when: "the registry bean is created"
            def registry = context.getBean(DefaultRendererRegistry)

        then: "the encoding is driven by the environment, not the property default"
            registry.encoding == 'ISO-8859-1'

        and: "the default renderers it creates inherit it"
            registry.findRenderer(MimeType.HTML, new URL('https://grails.apache.org')).encoding == 'ISO-8859-1'
            registry.findRenderer(MimeType.JSON, new URL('https://grails.apache.org')).encoding == 'ISO-8859-1'

        cleanup:
            context.close()
    }

    void "Test the registry propagates its configured encoding to the default renderers"() {
        given: "a registry configured with a non-default encoding"
            def registry = new DefaultRendererRegistry()
            registry.encoding = 'ISO-8859-1'
            registry.initialize()

        expect: "every default renderer stamps content types with that encoding"
            registry.findRenderer(MimeType.HTML, new URL('https://grails.apache.org')).encoding == 'ISO-8859-1'
            registry.findRenderer(MimeType.JSON, new URL('https://grails.apache.org')).encoding == 'ISO-8859-1'
            registry.findRenderer(MimeType.XML, new URL('https://grails.apache.org')) == null
    }

    void 'Spring JSON rendering uses the converters configured by MVC in their established order'() {
        given:
        def first = Stub(HttpMessageConverter)
        def second = Stub(HttpMessageConverter)
        def holder = new SpringMessageConverters()
        holder.extendMessageConverters([first, second])
        def registry = new DefaultRendererRegistry(springMessageConverters: holder, useSpringJson: true)
        registry.initialize()

        when:
        def renderer = registry.findRenderer(MimeType.JSON, new URL('https://grails.apache.org'))

        then:
        renderer.useSpringJson
        renderer.springHttpMessageConvertersSupplier.get() == [first, second]
    }

    void 'an application supplied validation problem factory reaches the error renderers'() {
        given: "an application that opts in to exposing rejected values"
        def factory = new ValidationProblemDetailFactory(true)
        def registry = new DefaultRendererRegistry(validationProblemDetailFactory: factory)
        registry.initialize()
        def errors = new BeanPropertyBindingResult(new Object(), 'book')

        expect: "both the default JSON renderer and the errors container renderer use it"
        registry.findRenderer(MimeType.JSON, new URL('https://grails.apache.org'))
                .validationProblemDetailFactory.is(factory)
        registry.findContainerRenderer(MimeType.JSON, Errors, errors)
                .validationProblemDetailFactory.is(factory)
    }

    void 'converters are resolved when a response is written, not when the registry is built'() {
        given: "a holder that MVC has not populated yet, as during bean creation"
        def holder = new SpringMessageConverters()
        def registry = new DefaultRendererRegistry(springMessageConverters: holder, useSpringJson: true)

        when: "the registry initializes before MVC has contributed any converter"
        registry.initialize()
        def renderer = registry.findRenderer(MimeType.JSON, new URL('https://grails.apache.org'))

        then: "nothing was captured at build time"
        renderer.springHttpMessageConvertersSupplier.get() == []

        when: "MVC finishes configuring the converters"
        def converter = Stub(HttpMessageConverter)
        holder.extendMessageConverters([converter])

        then: "the already-built renderer sees them"
        renderer.springHttpMessageConvertersSupplier.get() == [converter]
    }

    void 'a converter added by a later configurer is still seen'() {
        given: "the list Spring will install, handed to this holder mid-way through configuration"
        def early = Stub(HttpMessageConverter)
        def late = Stub(HttpMessageConverter)
        def installed = [early]
        def holder = new SpringMessageConverters()
        holder.extendMessageConverters(installed)

        when: "a WebMvcConfigurer ordered after this one contributes another converter"
        installed << late

        then: "rendering sees the final list, not a snapshot taken too early"
        holder.converters == [early, late]
    }

    void 'Spring JSON rendering can be disabled during migration'() {
        given:
        def context = new AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(
                new MapPropertySource('test', ['grails.web.rendering.json.spring': 'false']))
        context.registerBean(PropertySourcesPlaceholderConfigurer)
        context.registerBean(DefaultRendererRegistry)
        context.refresh()

        expect:
        !context.getBean(DefaultRendererRegistry)
                .findRenderer(MimeType.JSON, new URL('https://grails.apache.org')).useSpringJson

        cleanup:
        context.close()
    }

    void "Test that registering a HAL collection renderer works"() {
        given:"A registry with a specific renderer"
            def registry = new DefaultRendererRegistry()

        when:"A HAL collection renderer is specified"
            registry.addRenderer(new HalJsonCollectionRenderer(URL))
            def list = new LinkedList()
            list << new URL("https://grails.apache.org")
        then:"The renderer is available"
            registry.findContainerRenderer(MimeType.HAL_JSON, LinkedList, list) != null

}
    void "Test that the registry returns an appropriate render for a container type"() {
        when:"A registry with a specific renderer"
            def registry = new DefaultRendererRegistry()
            registry.initialize()


        then:"XML renderers are not installed by the core registry"
            !registry.findContainerRenderer(MimeType.XML, Errors, new BeanPropertyBindingResult("foo", "bar"))
            !registry.findContainerRenderer(MimeType.XML, List, new URL("https://grails.apache.org"))

        when:"A collection renderer is specified"
            registry.addContainerRenderer(URL, new AbstractRenderer(List, MimeType.XML) {
                @Override
                void render(Object object, RenderContext context) {
                    //To change body of implemented methods use File | Settings | File Templates.
                }
            })
            List<URL> list =  [new URL("https://grails.apache.org")]

        then:"A renderer is found"
            registry.findContainerRenderer(MimeType.XML, List, new URL("https://grails.apache.org"))
            registry.findContainerRenderer(MimeType.XML, List, list)
    }

    void "Test that registry returns appropriate renderer for type"() {
        given:"A registry with a specific renderer"
            def registry = new DefaultRendererRegistry()
            registry.initialize()
            def mimeType = new MimeType("text/xml", 'xml')
            registry.addRenderer(new AbstractRenderer(URL,mimeType) {
                @Override
                void render(Object object, RenderContext context) {

                }
            })

        expect:"A renderer is found"
            registry.findRenderer(mimeType, URL)
            registry.findRenderer(mimeType, URL).mimeTypes.contains mimeType
            registry.findRenderer(mimeType, new URL("https://grails.apache.org"))
            registry.findRenderer(mimeType, new URL("https://grails.apache.org")).mimeTypes.contains mimeType
    }

    void "Test that registry returns appropriate renderer for subclass"() {
        given:"A registry with a specific renderer"
            def registry = new DefaultRendererRegistry()
            registry.initialize()
            def mimeType = new MimeType("text/xml", 'xml')
            registry.addRenderer(new AbstractRenderer(CharSequence,mimeType) {
                @Override
                void  render(Object object, RenderContext context) {

                }
            })

        expect:"A renderer is found"
            registry.findRenderer(mimeType, "foo")
            registry.findRenderer(mimeType, "foo").mimeTypes.contains mimeType

            registry.findRenderer(mimeType, String)
            registry.findRenderer(mimeType, String).mimeTypes.contains mimeType
    }

    void "Test that registry fallbacks to a default renderer if none found"() {
        given:"A registry with a specific renderer"
            def registry = new DefaultRendererRegistry()
            registry.initialize()
            def mimeType = new MimeType("text/xml", 'xml')
            registry.addDefaultRenderer(new AbstractRenderer(Object,mimeType) {
                @Override
                void  render(Object object, RenderContext context) {

                }
            })

        expect:"A renderer is found"
            registry.findRenderer(mimeType, String)
            registry.findRenderer(mimeType, String).mimeTypes.contains mimeType
            registry.findRenderer(mimeType, "foo")
            registry.findRenderer(mimeType, "foo").mimeTypes.contains mimeType
    }
}
