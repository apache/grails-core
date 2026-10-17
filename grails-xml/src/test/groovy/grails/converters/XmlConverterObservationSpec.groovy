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
package grails.converters

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry

import grails.core.support.proxy.DefaultProxyHandler
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.converters.configuration.XmlConvertersConfigurationInitializer
import org.springframework.context.support.StaticApplicationContext
import org.springframework.mock.web.MockHttpServletResponse

import spock.lang.Specification

class XmlConverterObservationSpec extends Specification {

    private final List<Observation.Context> recorded = []
    private StaticApplicationContext applicationContext

    void cleanup() {
        ConvertersConfigurationHolder.clear()
        recorded.clear()
        applicationContext?.close()
    }

    void "XML response rendering records a converter observation when a registry bean is available"() {
        given:
        initializeConverters(recordingRegistry())
        def response = new MockHttpServletResponse()

        when:
        new XML([title: 'Undertow']).render(response)

        then:
        response.contentAsString.contains('Undertow')
        recorded.size() == 1
        recorded[0].name == 'grails.convert'
        recorded[0].contextualName == 'grails.convert xml'
        recorded[0].lowCardinalityKeyValues.find { it.key == 'grails.convert.format' }?.value == 'xml'
        recorded[0].error == null
    }

    private ObservationRegistry recordingRegistry() {
        ObservationRegistry.create().tap {
            observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
                @Override
                boolean supportsContext(Observation.Context context) {
                    true
                }

                @Override
                void onStop(Observation.Context context) {
                    recorded << context
                }
            })
        }
    }

    private void initializeConverters(ObservationRegistry registry) {
        applicationContext = new StaticApplicationContext().tap {
            beanFactory.registerSingleton('proxyHandler', new DefaultProxyHandler())
            beanFactory.registerSingleton('observationRegistry', registry)
            refresh()
        }
        // the JSON converter's initializer sets the observation registry the XML converter observes with
        new ConvertersConfigurationInitializer(applicationContext: applicationContext).initialize()
        new XmlConvertersConfigurationInitializer(applicationContext: applicationContext).initialize()
    }
}
