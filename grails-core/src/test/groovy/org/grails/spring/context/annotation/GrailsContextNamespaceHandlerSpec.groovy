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
package org.grails.spring.context.annotation

import java.nio.charset.StandardCharsets

import javax.xml.parsers.DocumentBuilderFactory

import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.xml.BeanDefinitionParserDelegate
import org.springframework.beans.factory.xml.DefaultNamespaceHandlerResolver
import org.springframework.beans.factory.xml.NamespaceHandler
import org.springframework.beans.factory.xml.ParserContext
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader
import org.springframework.beans.factory.xml.XmlReaderContext
import org.springframework.core.io.ByteArrayResource
import org.w3c.dom.Element
import spock.lang.Specification

class GrailsContextNamespaceHandlerSpec extends Specification {

    static final String GRAILS_CONTEXT_NAMESPACE = 'http://grails.org/schema/context'

    void 'META-INF/spring.handlers maps the Grails context namespace to this handler'() {
        when:
        NamespaceHandler handler = new DefaultNamespaceHandlerResolver(GrailsContextNamespaceHandler.classLoader)
                .resolve(GRAILS_CONTEXT_NAMESPACE)

        then:
        handler instanceof GrailsContextNamespaceHandler
    }

    void 'an initialised handler parses component-scan with the closure-ignoring parser'() {
        given:
        GrailsContextNamespaceHandler handler = new GrailsContextNamespaceHandler()
        handler.init()

        DefaultListableBeanFactory registry = new DefaultListableBeanFactory()
        ByteArrayResource resource = new ByteArrayResource(new byte[0])
        XmlReaderContext readerContext = new XmlBeanDefinitionReader(registry).createReaderContext(resource)
        ParserContext parserContext = new ParserContext(readerContext, new BeanDefinitionParserDelegate(readerContext))

        when:
        handler.parse(componentScanElement('org.grails.spring.context.annotation.fixture'), parserContext)

        then: 'the fixture component is registered'
        registry.containsBeanDefinition('scanFixtureComponent')

        and: 'the Grails-specific exclusion applies, so the parser was the closure-ignoring one, not Spring\'s'
        !registry.beanDefinitionNames.any { registry.getBeanDefinition(it).beanClassName?.contains('\$') }
    }

    void 'an element the handler does not know is rejected'() {
        given:
        GrailsContextNamespaceHandler handler = new GrailsContextNamespaceHandler()
        handler.init()

        DefaultListableBeanFactory registry = new DefaultListableBeanFactory()
        XmlReaderContext readerContext = new XmlBeanDefinitionReader(registry).createReaderContext(new ByteArrayResource(new byte[0]))
        ParserContext parserContext = new ParserContext(readerContext, new BeanDefinitionParserDelegate(readerContext))

        when:
        handler.parse(element('<grails:unknown xmlns:grails="' + GRAILS_CONTEXT_NAMESPACE + '"/>'), parserContext)

        then:
        thrown(Exception)
    }

    private static Element componentScanElement(String basePackage) {
        element('<grails:component-scan xmlns:grails="' + GRAILS_CONTEXT_NAMESPACE + '" base-package="' + basePackage + '"/>')
    }

    private static Element element(String xml) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance()
        factory.namespaceAware = true
        factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .documentElement
    }

}
