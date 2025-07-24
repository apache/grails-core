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
package grails.rest.render.xml

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

import jakarta.annotation.PostConstruct

import org.springframework.beans.factory.annotation.Autowired

import grails.converters.XML
import grails.core.GrailsApplication
import grails.core.support.proxy.DefaultProxyHandler
import grails.core.support.proxy.ProxyHandler
import grails.rest.render.RenderContext
import grails.web.mime.MimeType
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.plugins.web.rest.render.xml.DefaultXmlRenderer
import org.grails.web.converters.marshaller.ObjectMarshaller
import org.grails.web.converters.marshaller.xml.DeepDomainClassMarshaller
import org.grails.web.converters.marshaller.xml.GroovyBeanMarshaller

/**
 * An XML renderer that allows including / excluding properties
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class XmlRenderer<T> extends DefaultXmlRenderer<T> {

    @Autowired
    GrailsApplication grailsApplication

    @Autowired(required = false)
    ProxyHandler proxyHandler = new DefaultProxyHandler()

    /**
     * The properties to be included
     */
    List<String> includes

    /**
     * The properties to be excluded
     */
    List<String> excludes = []

    XmlRenderer(Class<T> targetType) {
        super(targetType)
    }

    XmlRenderer(Class<T> targetType, MimeType... mimeTypes) {
        super(targetType, mimeTypes)
    }

    @PostConstruct
    void registerCustomConverter() {
        def domain = grailsApplication != null ? grailsApplication.getArtefact(DomainClassArtefactHandler.TYPE, targetType.name) : null

        ObjectMarshaller<XML> marshaller = null

        if (domain) {
            marshaller = new DeepDomainClassMarshaller(false, proxyHandler, grailsApplication) {
                @Override
                protected boolean includesProperty(Object o, String property) {
                    return includes == null || includes.contains(property)
                }

                @Override
                protected boolean excludesProperty(Object o, String property) {
                    return excludes.contains(property)
                }
            }
        } else if(!Collection.isAssignableFrom(targetType) && !Map.isAssignableFrom(targetType)) {
            marshaller = new GroovyBeanMarshaller() {
                @Override
                protected boolean includesProperty(Object o, String property) {
                    return includes == null || includes.contains(property)
                }

                @Override
                protected boolean excludesProperty(Object o, String property) {
                    return excludes.contains(property)
                }
            }
        }
        if(marshaller) {
            registerCustomMarshaller(marshaller)
        }
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected void registerCustomMarshaller( ObjectMarshaller marshaller) {
        XML.registerObjectMarshaller(targetType, { object, XML xml ->
            marshaller.marshalObject(object, xml)
        })
    }

    @Override
    protected void renderXml(XML converter, RenderContext context) {
        converter.setExcludes(excludes ?: context.excludes)
        converter.setIncludes(includes != null ? includes : context.includes)
        converter.render(context.getWriter())
    }
}
