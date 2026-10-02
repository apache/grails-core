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

import org.springframework.context.support.StaticApplicationContext

import grails.converters.JSON
import grails.core.DefaultGrailsApplication
import grails.core.support.proxy.DefaultProxyHandler
import org.grails.web.converters.marshaller.ClosureObjectMarshaller
import org.grails.web.converters.marshaller.json.ValidationErrorsMarshaller

import spock.lang.Specification

class ConverterConfigurationCustomizationSpec extends Specification {

    void cleanup() {
        ConvertersConfigurationHolder.clear()
    }

    void 'the framework defaults leave the default JSON configuration uncustomized'() {
        when:
        initialize(context(new ValidationErrorsMarshaller()))

        then:
        !ConvertersConfigurationHolder.isDefaultConfigurationCustomized(JSON)
    }

    void 'an application ObjectMarshallerRegisterer bean customizes the default JSON configuration'() {
        when:
        initialize(context(new ClosureObjectMarshaller<JSON>(Locale, { Locale locale -> locale.toLanguageTag() })))

        then:
        ConvertersConfigurationHolder.isDefaultConfigurationCustomized(JSON)
    }

    void 'setting #setting to #value customizes the default JSON configuration: #customized'() {
        given:
        def grailsApplication = new DefaultGrailsApplication()
        grailsApplication.config.setAt(setting, value)

        when:
        new ConvertersConfigurationInitializer(grailsApplication: grailsApplication).initialize()

        then:
        ConvertersConfigurationHolder.isDefaultConfigurationCustomized(JSON) == customized

        where:
        setting                                | value        | customized
        'grails.converters.json.default.deep'  | true         | true
        'grails.converters.json.default.deep'  | false        | false
        'grails.converters.json.date'          | 'javascript' | true
        'grails.converters.json.date'          | 'default'    | false
    }

    void '#registration customizes the default JSON configuration'() {
        given:
        new ConvertersConfigurationInitializer().initialize()

        expect:
        !ConvertersConfigurationHolder.isDefaultConfigurationCustomized(JSON)

        when:
        register()

        then:
        ConvertersConfigurationHolder.isDefaultConfigurationCustomized(JSON)

        where:
        registration                         | register
        'registerObjectMarshaller'           | { -> JSON.registerObjectMarshaller(Locale) { Locale locale -> locale.toLanguageTag() } }
        'registerObjectMarshaller(priority)' | { -> JSON.registerObjectMarshaller(Locale, 10) { Locale locale -> locale.toLanguageTag() } }
        'withDefaultConfiguration'           | { -> JSON.withDefaultConfiguration { cfg -> cfg.prettyPrint = true } }
    }

    void 'clearing the holder forgets the customization'() {
        given:
        new ConvertersConfigurationInitializer().initialize()
        JSON.registerObjectMarshaller(Locale) { Locale locale -> locale.toLanguageTag() }

        when:
        ConvertersConfigurationHolder.clear()

        then:
        !ConvertersConfigurationHolder.isDefaultConfigurationCustomized(JSON)
    }

    private static StaticApplicationContext context(Object marshaller) {
        def context = new StaticApplicationContext()
        context.beanFactory.registerSingleton('proxyHandler', new DefaultProxyHandler())
        context.beanFactory.registerSingleton('jsonMarshallerRegisterer',
                new ObjectMarshallerRegisterer(marshaller: marshaller, converterClass: JSON))
        context.refresh()
        context
    }

    private static void initialize(StaticApplicationContext context) {
        new ConvertersConfigurationInitializer(applicationContext: context,
                grailsApplication: new DefaultGrailsApplication()).initialize()
    }
}
