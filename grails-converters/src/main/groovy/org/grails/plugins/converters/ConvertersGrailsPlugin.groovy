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

import groovy.transform.CompileStatic

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.core.env.Environment

import grails.converters.JSON
import grails.converters.json.NamedJsonConfigurationRegistry
import grails.core.GrailsApplication
import grails.core.support.proxy.ProxyHandler
import grails.plugins.Plugin
import grails.util.GrailsUtil
import org.grails.plugins.codecs.JSONCodec
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.converters.configuration.ObjectMarshallerRegisterer
import org.grails.web.converters.jackson.GrailsJsonMapperCustomizer
import org.grails.web.converters.jackson.JacksonNamedJsonRenderer
import tools.jackson.databind.json.JsonMapper
import org.grails.web.converters.marshaller.json.ValidationErrorsMarshaller as JsonErrorsMarshaller

/**
 * Allows the "obj as XML" and "obj as JSON" syntax.
 *
 * @author Siegfried Puchbauer
 * @author Graeme Rocher
 *
 * @since 0.6
 */
@CompileStatic
class ConvertersGrailsPlugin extends Plugin {

    def version = GrailsUtil.getGrailsVersion()
    def observe = ['controllers']
    def dependsOn = [controllers: version, domainClass: version]
    def providedArtefacts = [
        JSONCodec
    ]

    @Override
    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            registry.registerBean('jsonErrorsMarshaller', JsonErrorsMarshaller)

            registry.registerBean('convertersConfigurationInitializer', ConvertersConfigurationInitializer)
            registry.registerBean('grailsJsonMapperCustomizer', GrailsJsonMapperCustomizer) {
                it.supplier {
                    new GrailsJsonMapperCustomizer(
                            it.bean('grailsApplication', GrailsApplication),
                            it.bean('proxyHandler', ProxyHandler)
                    )
                }
            }
            registry.registerBean('namedJsonConfigurationRegistry', NamedJsonConfigurationRegistry) {
                it.supplier { context ->
                    // Resolved when a configuration is first written, not here: the registry can be
                    // created before Jackson auto-configuration has produced Boot's mapper, and
                    // substituting a separately built one would silently drop spring.jackson.*,
                    // the application's builder customizers and the Grails serializers.
                    new NamedJsonConfigurationRegistry(context.beanProvider(JsonMapper)::getIfAvailable)
                }
            }
            registry.registerBean('namedJsonRenderer', JacksonNamedJsonRenderer) {
                it.supplier {
                    new JacksonNamedJsonRenderer(it.bean('namedJsonConfigurationRegistry', NamedJsonConfigurationRegistry))
                }
            }

            registry.registerBean('errorsJsonMarshallerRegisterer', ObjectMarshallerRegisterer) {
                it.supplier {
                    new ObjectMarshallerRegisterer(
                            marshaller: it.bean('jsonErrorsMarshaller', JsonErrorsMarshaller),
                            converterClass: JSON
                    )
                }
            }
        }
    }
}
