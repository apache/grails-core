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

import grails.spring.BeanBuilder
import groovy.xml.XmlSlurper
import org.springframework.beans.factory.xml.DefaultNamespaceHandlerResolver
import org.springframework.beans.factory.xml.PluggableSchemaResolver
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader
import org.springframework.context.annotation.AnnotationConfigUtils
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.io.ByteArrayResource
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import spock.lang.Specification

class GrailsContextSchemaSpec extends Specification {

    static final String NAMESPACE = 'http://grails.org/schema/context'
    static final String SCHEMA_LOCATION = 'http://grails.org/schema/context/grails-context.xsd'

    PluggableSchemaResolver schemaResolver = new PluggableSchemaResolver(getClass().classLoader)

    void "the grails-context schema location resolves to a schema on the classpath"() {
        expect:
        schemaResolver.resolveEntity(null, SCHEMA_LOCATION) != null
    }

    void "the grails-context schema declares the namespace its namespace handler is registered for"() {
        given:
        InputSource schema = schemaResolver.resolveEntity(null, SCHEMA_LOCATION)

        expect:
        new DefaultNamespaceHandlerResolver(getClass().classLoader).resolve(NAMESPACE) instanceof GrailsContextNamespaceHandler
        new XmlSlurper().parse(schema.byteStream).@targetNamespace == NAMESPACE
    }

    void "a schema-validated XML bean definition can declare grails:component-scan"() {
        given:
        def context = new GenericApplicationContext()
        def reader = new XmlBeanDefinitionReader(context)
        reader.validationMode = XmlBeanDefinitionReader.VALIDATION_XSD
        reader.entityResolver = classpathOnlyEntityResolver()

        when:
        reader.loadBeanDefinitions(new ByteArrayResource("""<?xml version="1.0" encoding="UTF-8"?>
            <beans xmlns="http://www.springframework.org/schema/beans"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xmlns:grails="${NAMESPACE}"
                   xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd
                                       ${NAMESPACE} ${SCHEMA_LOCATION}">
                <grails:component-scan base-package="${getClass().package.name}"/>
            </beans>""".bytes))

        then: 'the element was parsed, which registers the annotation config processors'
        context.containsBeanDefinition(AnnotationConfigUtils.AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME)

        cleanup:
        context.close()
    }

    void "the bean builder DSL can declare grails:component-scan in the grails context namespace"() {
        given:
        def beanBuilder = new BeanBuilder()
        String basePackage = getClass().package.name

        when:
        beanBuilder.beans {
            xmlns(grailsContext: NAMESPACE)
            grailsContext.'component-scan'('base-package': basePackage)
        }
        def context = beanBuilder.createApplicationContext()

        then: 'the element was parsed, which registers the annotation config processors'
        context.containsBeanDefinition(AnnotationConfigUtils.AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME)

        cleanup:
        context?.close()
    }

    /**
     * Resolves schemas only from the classpath, so a schema that is not packaged fails the parse
     * instead of being fetched over the network.
     */
    private EntityResolver classpathOnlyEntityResolver() {
        { String publicId, String systemId ->
            InputSource source = schemaResolver.resolveEntity(publicId, systemId)
            if (source == null && systemId != null) {
                throw new IllegalStateException("${systemId} is not on the classpath")
            }
            source
        } as EntityResolver
    }
}
