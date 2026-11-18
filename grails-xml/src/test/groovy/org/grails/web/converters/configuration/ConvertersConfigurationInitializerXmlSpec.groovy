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
package org.grails.web.converters.configuration

import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.context.support.GenericApplicationContext
import org.springframework.context.support.StaticApplicationContext

import grails.converters.XML
import grails.core.DefaultGrailsApplication
import grails.core.support.proxy.DefaultProxyHandler
import org.grails.plugins.converters.ConvertersGrailsPlugin
import org.grails.plugins.xml.XmlGrailsPlugin
import org.grails.web.converters.marshaller.ObjectMarshaller
import org.grails.web.converters.marshaller.xml.MapMarshaller
import org.grails.web.converters.marshaller.xml.ValidationErrorsMarshaller
import spock.lang.Specification

/**
 * The converters' initializer configures the XML converter of this module too, as it did before the converter moved
 * here, unless the XML plugin's initializer does as the application starts.
 */
class ConvertersConfigurationInitializerXmlSpec extends Specification {

    GenericApplicationContext context

    void cleanup() {
        context?.close()
        ConvertersConfigurationHolder.clear()
    }

    void 'initializing the converters by hand configures the XML converter and its deep configuration'() {
        when:
        new ConvertersConfigurationInitializer(grailsApplication: new DefaultGrailsApplication()).initialize()

        then:
        new XML([title: 'Grails']).toString().endsWith('<map><entry key="title">Grails</entry></map>')

        and:
        String deep = null
        XML.use('deep') {
            deep = new XML([title: 'Grails']).toString()
        }
        deep.endsWith('<map><entry key="title">Grails</entry></map>')
    }

    void "initializing the converters by hand applies the application's XML settings"() {
        given:
        def application = new DefaultGrailsApplication()
        application.config.setAt('grails.converters.xml.pretty.print', true)

        when:
        new ConvertersConfigurationInitializer(grailsApplication: application).initialize()

        then:
        new XML([title: 'Grails']).toString().contains('\n')
    }

    void 'initializing the converters by hand registers the XML marshallers of the context'() {
        given:
        def context = new StaticApplicationContext()
        context.beanFactory.registerSingleton('proxyHandler', new DefaultProxyHandler())
        context.beanFactory.registerSingleton('titleMarshaller',
                new ObjectMarshallerRegisterer(converterClass: XML, marshaller: new TitleMarshaller()))
        context.refresh()
        this.context = context

        when:
        new ConvertersConfigurationInitializer(applicationContext: context).initialize()

        then:
        new XML(new Title(text: 'Grails')).toString().endsWith('<title>GRAILS</title>')
    }

    void 'initializing the converters again in an application configures the XML converter again'() {
        given:
        context = start(true)
        ConvertersConfigurationHolder.clear()

        when:
        context.getBean(ConvertersConfigurationInitializer).initialize()

        then:
        new XML([title: 'Grails']).toString().endsWith('<map><entry key="title">Grails</entry></map>')
    }

    void 'an application with the converters plugin and without the XML plugin has the XML converter configured'() {
        when:
        context = start(false)

        then:
        new XML([title: 'Grails']).toString().endsWith('<map><entry key="title">Grails</entry></map>')
    }

    void 'an application with both plugins configures the XML converter once'() {
        when:
        context = start(true)
        List<ObjectMarshaller<XML>> marshallers = ConvertersConfigurationHolder.getConverterConfiguration(XML).orderedObjectMarshallers

        then:
        marshallers.count { it instanceof MapMarshaller } == 1
        marshallers.count { it instanceof ValidationErrorsMarshaller } == 1
    }

    private static GenericApplicationContext start(boolean xmlPlugin) {
        def context = new GenericApplicationContext()
        context.beanFactory.registerSingleton('proxyHandler', new DefaultProxyHandler())
        context.beanFactory.registerSingleton('grailsApplication', new DefaultGrailsApplication())
        register(context, new ConvertersGrailsPlugin().beanRegistrar())
        if (xmlPlugin) {
            register(context, new XmlGrailsPlugin().beanRegistrar())
        }
        context.refresh()
        context
    }

    private static void register(GenericApplicationContext context, Object registrar) {
        new BeanRegistryAdapter(context, context.beanFactory, context.environment, registrar.getClass()).register(registrar)
    }
}

class Title {
    String text
}

class TitleMarshaller implements ObjectMarshaller<XML> {

    @Override
    boolean supports(Object object) {
        object instanceof Title
    }

    @Override
    void marshalObject(Object object, XML converter) {
        converter.chars(((Title) object).text.toUpperCase())
    }
}
