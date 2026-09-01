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

import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import org.springframework.http.converter.HttpMessageConverter

import grails.converters.XML
import grails.rest.render.Renderer
import grails.web.mime.MimeType
import org.grails.plugins.web.rest.render.DefaultRendererRegistry
import org.grails.plugins.web.rest.render.SpringMessageConverters
import org.grails.plugins.web.rest.render.xml.DefaultXmlRenderer
import org.grails.web.converters.configuration.ObjectMarshallerRegisterer
import org.grails.web.converters.configuration.XmlConvertersConfigurationInitializer
import org.grails.web.converters.marshaller.xml.ValidationErrorsMarshaller
import org.grails.web.databinding.bindingsource.HalXmlDataBindingSourceCreator
import org.grails.web.databinding.bindingsource.XmlDataBindingSourceCreator

import spock.lang.Specification

class XmlGrailsPluginSpec extends Specification {

    def beanFactory = new DefaultListableBeanFactory()

    void setup() {
        def registrar = new XmlGrailsPlugin().beanRegistrar()
        new BeanRegistryAdapter(beanFactory, new StandardEnvironment(), registrar.getClass()).register(registrar)
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
            getBeanDefinition('xmlRenderer').beanClassName == DefaultXmlRenderer.name
            getBeanDefinition('xmlErrorsRenderer').beanClassName == XmlErrorsRenderer.name
            containsBeanDefinition('errorsXmlMarshallerRegisterer')
            !containsBeanDefinition('grailsJacksonXmlHttpMessageConverter')
        }
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
        holder.extendMessageConverters([first, second])
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
                beanFactory.getBean('xmlRenderer', DefaultXmlRenderer),
                beanFactory.getBean('xmlErrorsRenderer', XmlErrorsRenderer),
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

    void 'nothing registers XML renderers other than those beans'() {
        given: "a registry that never sees the renderer beans"
        def rendererRegistry = new DefaultRendererRegistry()
        rendererRegistry.initialize()
        def registrar = new XmlGrailsPlugin().beanRegistrar()
        new BeanRegistryAdapter(beanFactory, new StandardEnvironment(), registrar.getClass()).register(registrar)

        when: "every bean the plugin registers is created"
        beanFactory.beanDefinitionNames.each { beanFactory.getBean(it) }

        then: "no bean has registered a renderer behind the registry's back"
        rendererRegistry.findRenderer(MimeType.XML, new URL('https://grails.apache.org')) == null
    }

    void 'Atom feed rendering remains opt-in'() {
        given:
        def rendererRegistry = new DefaultRendererRegistry()
        rendererRegistry.initialize()

        when: "the plugin's renderer beans are registered"
        rendererRegistry.setRenderers([new DefaultXmlRenderer<Object>(Object)] as Renderer[])

        then: "no Atom renderer comes with them"
        rendererRegistry.findRenderer(MimeType.ATOM_XML, new URL('https://grails.apache.org')) == null
    }
}
