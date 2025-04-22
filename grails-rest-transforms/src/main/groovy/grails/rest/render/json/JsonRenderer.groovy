/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.rest.render.json

import grails.converters.JSON
import grails.rest.render.RenderContext
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.grails.core.artefact.DomainClassArtefactHandler
import grails.core.GrailsApplication
import grails.core.support.proxy.DefaultProxyHandler
import grails.core.support.proxy.ProxyHandler
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.web.converters.marshaller.ObjectMarshaller
import org.grails.web.converters.marshaller.json.DeepDomainClassMarshaller
import org.grails.web.converters.marshaller.json.GroovyBeanMarshaller
import grails.web.mime.MimeType
import org.grails.plugins.web.rest.render.json.DefaultJsonRenderer
import org.springframework.beans.factory.annotation.Autowired

import jakarta.annotation.PostConstruct

/**
 *
 * A JSON renderer that allows including / excluding properties
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class JsonRenderer <T> extends DefaultJsonRenderer<T> {

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

    JsonRenderer(Class<T> targetType) {
        super(targetType)
    }

    JsonRenderer(Class<T> targetType, MimeType... mimeTypes) {
        super(targetType, mimeTypes)
    }

    @PostConstruct
    void registerCustomConverter() {

        def domain = grailsApplication != null ? grailsApplication.getArtefact(DomainClassArtefactHandler.TYPE, targetType.name) : null

        ObjectMarshaller<JSON> marshaller  = null

        if (domain) {
            DeepDomainClassMarshaller domainClassMarshaller = new DeepDomainClassMarshaller(false, proxyHandler, grailsApplication) {
                @Override
                protected boolean includesProperty(Object o, String property) {
                    return includes == null || includes.contains(property)
                }

                @Override
                protected boolean excludesProperty(Object o, String property) {
                    return excludes.contains(property)
                }
            }
            if(includes?.contains(GormProperties.VERSION)) {
                domainClassMarshaller.includeVersion = true
            }
            if(includes?.contains('class')) {
                domainClassMarshaller.includeClass = true
            }

            marshaller = domainClassMarshaller
        } else if(!Collection.isAssignableFrom(targetType) && !Map.isAssignableFrom(targetType)) {
            marshaller = (ObjectMarshaller<JSON>)new GroovyBeanMarshaller() {
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
    protected void registerCustomMarshaller(ObjectMarshaller marshaller) {
        JSON.registerObjectMarshaller(targetType, { Object object, JSON json ->
            marshaller.marshalObject(object, json)
        })
    }

    @Override
    protected void renderJson(JSON converter, RenderContext context) {
        converter.setExcludes(excludes ?: context.excludes)
        converter.setIncludes(includes != null ? includes : context.includes)
        converter.render(context.getWriter())
    }
}
