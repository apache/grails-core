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
package org.grails.plugins.converters

import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.core.env.StandardEnvironment

import grails.converters.JSON
import grails.converters.XML
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.converters.configuration.ObjectMarshallerRegisterer
import org.grails.web.converters.marshaller.json.ValidationErrorsMarshaller as JsonErrorsMarshaller
import org.grails.web.converters.marshaller.xml.ValidationErrorsMarshaller as XmlErrorsMarshaller

import spock.lang.Specification

class ConvertersGrailsPluginSpec extends Specification {

    def beanFactory = new DefaultListableBeanFactory()

    void setup() {
        def registrar = new ConvertersGrailsPlugin().beanRegistrar()
        new BeanRegistryAdapter(beanFactory, new StandardEnvironment(), registrar.getClass()).register(registrar)
    }

    void "beanRegistrar registers the converters beans"() {
        expect:
        with(beanFactory) {
            getBeanDefinition('jsonErrorsMarshaller').beanClassName == JsonErrorsMarshaller.name
            getBeanDefinition('xmlErrorsMarshaller').beanClassName == XmlErrorsMarshaller.name
            getBeanDefinition('convertersConfigurationInitializer').beanClassName == ConvertersConfigurationInitializer.name
            containsBeanDefinition('errorsXmlMarshallerRegisterer')
            containsBeanDefinition('errorsJsonMarshallerRegisterer')
        }
    }

    void "the errors marshaller registerers use the named errors marshaller beans"() {
        when:
        def xmlRegisterer = beanFactory.getBean('errorsXmlMarshallerRegisterer', ObjectMarshallerRegisterer)
        def jsonRegisterer = beanFactory.getBean('errorsJsonMarshallerRegisterer', ObjectMarshallerRegisterer)

        then:
        xmlRegisterer.marshaller.is(beanFactory.getBean('xmlErrorsMarshaller', XmlErrorsMarshaller))
        xmlRegisterer.converterClass == XML
        jsonRegisterer.marshaller.is(beanFactory.getBean('jsonErrorsMarshaller', JsonErrorsMarshaller))
        jsonRegisterer.converterClass == JSON
    }
}
