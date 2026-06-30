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

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

import grails.converters.JSON
import grails.converters.XML

import spock.lang.Specification

class ConvertersConfigurationHolderSpec extends Specification {

    void cleanup() {
        ConvertersConfigurationHolder.clear()
    }

    void "thread-local converter configuration is scoped to a virtual thread"() {
        given:
        def defaultConfiguration = new DefaultConverterConfiguration<JSON>()
        def scopedConfiguration = new DefaultConverterConfiguration<JSON>()
        ConvertersConfigurationHolder.setDefaultConfiguration(JSON, defaultConfiguration)

        when:
        def scopedConfigurations = Executors.newVirtualThreadPerTaskExecutor().withCloseable { executor ->
            executor.submit({
                ConvertersConfigurationHolder.setThreadLocalConverterConfiguration(JSON, scopedConfiguration)
                def threadLocalConfiguration = ConvertersConfigurationHolder.getConverterConfiguration(JSON)
                ConvertersConfigurationHolder.setThreadLocalConverterConfiguration(JSON, null)
                [threadLocalConfiguration, ConvertersConfigurationHolder.getConverterConfiguration(JSON)]
            } as Callable<List<ConverterConfiguration<JSON>>>).get()
        }

        then:
        scopedConfigurations[0].is(scopedConfiguration)
        scopedConfigurations[1].is(defaultConfiguration)
        ConvertersConfigurationHolder.getConverterConfiguration(JSON).is(defaultConfiguration)
    }

    void "default converter lookups do not allocate a thread-local configuration map"() {
        given:
        def defaultConfiguration = new DefaultConverterConfiguration<JSON>()
        ConvertersConfigurationHolder.setDefaultConfiguration(JSON, defaultConfiguration)

        expect:
        ConvertersConfigurationHolder.getConverterConfiguration(JSON).is(defaultConfiguration)
        ConvertersConfigurationHolder.getThreadLocalConverterConfiguration(JSON) == null
    }

    void "clearing the last thread-local converter configuration removes the map"() {
        given:
        def scopedConfiguration = new DefaultConverterConfiguration<JSON>()
        ConvertersConfigurationHolder.setThreadLocalConverterConfiguration(JSON, scopedConfiguration)

        expect:
        ConvertersConfigurationHolder.getThreadLocalConverterConfiguration(JSON).is(scopedConfiguration)

        when:
        ConvertersConfigurationHolder.setThreadLocalConverterConfiguration(JSON, null)

        then:
        ConvertersConfigurationHolder.getThreadLocalConverterConfiguration(JSON) == null
    }

    void "clearing one converter keeps other thread-local converter configurations"() {
        given:
        def jsonConfiguration = new DefaultConverterConfiguration<JSON>()
        def xmlConfiguration = new DefaultConverterConfiguration<XML>()
        ConvertersConfigurationHolder.setThreadLocalConverterConfiguration(JSON, jsonConfiguration)
        ConvertersConfigurationHolder.setThreadLocalConverterConfiguration(XML, xmlConfiguration)

        when:
        ConvertersConfigurationHolder.setThreadLocalConverterConfiguration(JSON, null)

        then:
        ConvertersConfigurationHolder.getThreadLocalConverterConfiguration(JSON) == null
        ConvertersConfigurationHolder.getThreadLocalConverterConfiguration(XML).is(xmlConfiguration)

        when:
        ConvertersConfigurationHolder.setThreadLocalConverterConfiguration(XML, null)

        then:
        ConvertersConfigurationHolder.getThreadLocalConverterConfiguration(XML) == null
    }

    void "converter use restores a missing thread-local configuration after a named scope"() {
        given:
        def defaultConfiguration = new DefaultConverterConfiguration<JSON>()
        def namedConfiguration = new DefaultConverterConfiguration<JSON>(defaultConfiguration)
        ConvertersConfigurationHolder.setDefaultConfiguration(JSON, defaultConfiguration)
        ConvertersConfigurationHolder.setNamedConverterConfiguration(JSON, 'named', namedConfiguration)
        def scopedConfiguration = new AtomicReference<ConverterConfiguration<JSON>>()
        def restoredConfiguration = new AtomicReference<ConverterConfiguration<JSON>>()

        when:
        JSON.use('named') {
            scopedConfiguration.set(ConvertersConfigurationHolder.getConverterConfiguration(JSON))
        }
        restoredConfiguration.set(ConvertersConfigurationHolder.getConverterConfiguration(JSON))

        then:
        scopedConfiguration.get().is(namedConfiguration)
        restoredConfiguration.get().is(defaultConfiguration)
        ConvertersConfigurationHolder.getThreadLocalConverterConfiguration(JSON) == null
    }

}
