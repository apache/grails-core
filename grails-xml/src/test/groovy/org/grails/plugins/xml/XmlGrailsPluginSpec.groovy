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
package org.grails.plugins.xml

import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.context.annotation.CommonAnnotationBeanPostProcessor
import org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import org.springframework.http.converter.HttpMessageConverter

import grails.converters.XML
import grails.rest.render.ContainerRenderer
import grails.rest.render.RenderContext
import grails.rest.render.Renderer
import grails.rest.render.errors.VndErrorXmlRenderer
import grails.rest.render.xml.XmlRenderer
import grails.web.mime.MimeType
import org.grails.plugins.web.rest.render.DefaultRendererRegistry
import org.grails.plugins.web.rest.render.SpringMessageConverters
import org.grails.plugins.web.rest.render.xml.DefaultXmlRenderer
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.ObjectMarshallerRegisterer
import org.grails.web.converters.configuration.XmlConvertersConfigurationInitializer
import org.grails.web.converters.marshaller.xml.ValidationErrorsMarshaller
import org.grails.web.databinding.bindingsource.HalXmlDataBindingSourceCreator
import org.grails.web.databinding.bindingsource.XmlDataBindingSourceCreator
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator
import org.grails.gsp.io.GroovyPageScriptSource

import spock.lang.Specification

class XmlGrailsPluginSpec extends Specification {

    def beanFactory = new DefaultListableBeanFactory()

    void setup() {
        def registrar = new XmlGrailsPlugin().beanRegistrar()
        new BeanRegistryAdapter(beanFactory, new StandardEnvironment(), registrar.getClass()).register(registrar)
    }

    void cleanup() {
        // Creating the plugin's converters initializer configures the XML converter for the whole JVM
        ConvertersConfigurationHolder.clear()
    }

    void 'beanRegistrar registers optional XML infrastructure'() {
        expect:
        with(beanFactory) {
            getBeanDefinition('xmlErrorsMarshaller').beanClassName == ValidationErrorsMarshaller.name
            getBeanDefinition('xmlConvertersConfigurationInitializer').beanClassName ==
                    XmlConvertersConfigurationInitializer.name
            getBeanDefinition('xmlDataBindingSourceCreator').beanClassName == XmlDataBindingSourceCreator.name
            getBeanDefinition('halXmlDataBindingSourceCreator').beanClassName ==
                    HalXmlDataBindingSourceCreator.name
            getBeanDefinition('grailsXmlRenderer').beanClassName == XmlFallbackRenderer.name
            getBeanDefinition('grailsXmlErrorsRenderer').beanClassName == XmlErrorsRenderer.name
            containsBeanDefinition('errorsXmlMarshallerRegisterer')
            !containsBeanDefinition('grailsJacksonXmlHttpMessageConverter')
        }
    }

    void "an application's renderer beans, whatever their names, do not replace the plugin's"() {
        given: 'renderer beans declared after the plugin, as resources.groovy declares them, under names an application may use'
        beanFactory.registerBeanDefinition('xmlRenderer',
                BeanDefinitionBuilder.genericBeanDefinition(XmlRenderer).addConstructorArgValue(String).beanDefinition)
        beanFactory.registerBeanDefinition('xmlErrorsRenderer',
                BeanDefinitionBuilder.genericBeanDefinition(VndErrorXmlRenderer).beanDefinition)

        expect:
        beanFactory.getBeansOfType(XmlFallbackRenderer).size() == 1
        beanFactory.getBeansOfType(XmlErrorsRenderer).size() == 1
    }

    void 'the errors marshaller registerer targets XML'() {
        when:
        def registerer = beanFactory.getBean('errorsXmlMarshallerRegisterer', ObjectMarshallerRegisterer)

        then:
        registerer.marshaller.is(beanFactory.getBean('xmlErrorsMarshaller', ValidationErrorsMarshaller))
        registerer.converterClass == XML
    }

    void 'the XML renderers are contributed as beans the registry autowires'() {
        given: "the renderer beans this plugin registers"
        def first = Stub(HttpMessageConverter)
        def second = Stub(HttpMessageConverter)
        def holder = new SpringMessageConverters()
        holder.setConverters([first, second])
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(
                new MapPropertySource('test', ['grails.converters.encoding': 'ISO-8859-1']))
        def beanFactory = new DefaultListableBeanFactory()
        beanFactory.registerSingleton('springMessageConverters', holder)
        def registrar = new XmlGrailsPlugin().beanRegistrar()
        new BeanRegistryAdapter(beanFactory, environment, registrar.getClass()).register(registrar)

        when: "the registry collects every Renderer bean, as Spring wires it to do"
        def rendererRegistry = new DefaultRendererRegistry()
        rendererRegistry.initialize()
        rendererRegistry.setRenderers([
                beanFactory.getBean('grailsXmlRenderer', DefaultXmlRenderer),
                beanFactory.getBean('grailsXmlErrorsRenderer', XmlErrorsRenderer),
        ] as Renderer[])

        then: "both are reachable, configured from the environment and the converter holder"
        with(rendererRegistry.findRenderer(MimeType.XML, new URL('https://grails.apache.org'))) {
            encoding == 'ISO-8859-1'
            springHttpMessageConvertersSupplier.get() == [first, second]
        }
        with(rendererRegistry.findContainerRenderer(
                MimeType.XML, Errors, new BeanPropertyBindingResult('value', 'target'))) {
            encoding == 'ISO-8859-1'
            springHttpMessageConvertersSupplier.get() == [first, second]
        }
    }

    void 'an application interface renderer takes precedence over the default XML bean'() {
        given:
        def registry = new DefaultRendererRegistry()
        registry.initialize()
        def custom = Stub(Renderer) {
            getTargetType() >> CharSequence
            getMimeTypes() >> ([MimeType.XML] as MimeType[])
        }
        registry.setRenderers([custom, beanFactory.getBean('grailsXmlRenderer', DefaultXmlRenderer)] as Renderer[])

        expect:
        registry.findRenderer(MimeType.XML, 'value').is(custom)
    }

    void 'an application renderer for Object takes precedence over the default XML bean, whichever is collected first'() {
        given:
        def custom = Stub(Renderer) {
            getTargetType() >> Object
            getMimeTypes() >> ([MimeType.XML] as MimeType[])
        }
        def fallback = beanFactory.getBean('grailsXmlRenderer', XmlFallbackRenderer)
        def registry = new DefaultRendererRegistry()

        when: 'Spring injects the renderer beans before the registry initializes'
        registry.setRenderers((customFirst ? [custom, fallback] : [fallback, custom]) as Renderer[])
        registry.initialize()

        then:
        registry.findRenderer(MimeType.XML, 'value').is(custom)

        and: 'the default XML bean still answers the media types the application renderer does not'
        registry.findRenderer(MimeType.TEXT_XML, 'value').is(fallback)

        where:
        customFirst << [true, false]
    }

    void "the plugin's renderer of validation errors answers XML over an application's renderer bean, as in Grails 8, whichever is collected first"() {
        given: 'a Vnd.Error renderer bean, which also names the XML media types'
        def vndError = new VndErrorXmlRenderer()
        def plugin = beanFactory.getBean('grailsXmlErrorsRenderer', XmlErrorsRenderer)
        def registry = new DefaultRendererRegistry()
        def errors = new BeanPropertyBindingResult(new Object(), 'book')

        when:
        registry.setRenderers((vndErrorFirst ? [vndError, plugin] : [plugin, vndError]) as Renderer[])
        registry.initialize()

        then:
        registry.findContainerRenderer(MimeType.XML, Errors, errors).is(plugin)
        registry.findContainerRenderer(MimeType.TEXT_XML, Errors, errors).is(plugin)

        and: 'the application renderer answers its own media type'
        registry.findContainerRenderer(VndErrorXmlRenderer.MIME_TYPE, Errors, errors).is(vndError)

        where:
        vndErrorFirst << [true, false]
    }

    void "a renderer of validation errors added once the registry is initialized answers XML over the plugin's"() {
        given:
        def custom = Stub(ContainerRenderer) {
            getTargetType() >> Errors
            getComponentType() >> Object
            getMimeTypes() >> ([MimeType.XML] as MimeType[])
        }
        def plugin = beanFactory.getBean('grailsXmlErrorsRenderer', XmlErrorsRenderer)
        def registry = new DefaultRendererRegistry()
        def errors = new BeanPropertyBindingResult(new Object(), 'book')
        registry.setRenderers([plugin] as Renderer[])
        registry.initialize()

        when:
        registry.addRenderer(custom)

        then:
        registry.findContainerRenderer(MimeType.XML, Errors, errors).is(custom)
        registry.findContainerRenderer(MimeType.TEXT_XML, Errors, errors).is(plugin)
    }

    void 'a renderer discovers a GSP locator registered after its creation'() {
        given:
        def renderer = beanFactory.getBean('grailsXmlRenderer', DefaultXmlRenderer)
        def locator = Mock(GrailsConventionGroovyPageLocator)
        beanFactory.registerSingleton('groovyPageLocator', locator)
        def context = Mock(RenderContext) {
            getAcceptMimeType() >> MimeType.XML
            getControllerName() >> 'book'
            getActionName() >> 'show'
        }

        when:
        renderer.render([title: 'Grails'], context)

        then:
        1 * locator.findViewForFormat('book', 'show', 'xml') >> Stub(GroovyPageScriptSource)
        1 * context.setModel([title: 'Grails'])
        1 * context.setViewName('show')
        0 * context.getWriter()
    }

    void 'nothing registers XML renderers other than those beans'() {
        given: "a registry the plugin's beans could reach, which nothing gives them"
        def rendererRegistry = new DefaultRendererRegistry()
        rendererRegistry.initialize()
        beanFactory.registerSingleton('rendererRegistry', rendererRegistry)

        and: 'a factory that autowires the beans and initializes them, as an application context does'
        def environment = new StandardEnvironment()
        beanFactory.autowireCandidateResolver = new ContextAnnotationAutowireCandidateResolver()
        beanFactory.addEmbeddedValueResolver { String value -> environment.resolvePlaceholders(value) }
        beanFactory.addBeanPostProcessor(new AutowiredAnnotationBeanPostProcessor(beanFactory: beanFactory))
        beanFactory.addBeanPostProcessor(new CommonAnnotationBeanPostProcessor(beanFactory: beanFactory))

        when: "every bean the plugin registers is created"
        beanFactory.beanDefinitionNames.each { beanFactory.getBean(it) }

        then: "no bean has registered a renderer behind the registry's back"
        rendererRegistry.findRenderer(MimeType.XML, new URL('https://grails.apache.org')) == null
        rendererRegistry.findContainerRenderer(MimeType.XML, Errors, new BeanPropertyBindingResult('value', 'target')) == null

        and: 'the renderers were autowired with the registry they could have reached'
        beanFactory.getBean('grailsXmlRenderer', DefaultXmlRenderer).rendererRegistryProvider.getIfAvailable().is(rendererRegistry)
    }

    void 'Atom feed rendering remains opt-in'() {
        given:
        def rendererRegistry = new DefaultRendererRegistry()

        when: "the registry collects every renderer bean the plugin registers"
        rendererRegistry.setRenderers(beanFactory.getBeansOfType(Renderer).values() as Renderer[])
        rendererRegistry.initialize()

        then: "XML is rendered, and no Atom renderer comes with it"
        rendererRegistry.findRenderer(MimeType.XML, new URL('https://grails.apache.org')) != null
        rendererRegistry.findRenderer(MimeType.ATOM_XML, new URL('https://grails.apache.org')) == null
    }
}
