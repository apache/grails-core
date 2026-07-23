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
package org.apache.grails.configuration.metadata

import groovy.json.JsonSlurper
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Specification

import java.lang.reflect.Field
import java.lang.reflect.Modifier

class ConfigurationMetadataTransformationSpec extends Specification {

    def "global transform emits actual properties and nested groups with only constant defaults"() {
        given:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)

        when:
        Class<?> configuration = loader.parseClass('''
            package example

            import org.springframework.boot.context.properties.ConfigurationProperties

            @ConfigurationProperties('sample.service')
            class SampleConfiguration {
                String displayName = 'Grails'
                int port = 8080
                List<String> labels = ['one']
                final String immutableValue
                Nested nested = new Nested()
                String dynamicValue = UUID.randomUUID().toString()
                private String internalSecret = 'secret'

                SampleConfiguration(String immutableValue) {
                    this.immutableValue = immutableValue
                }
            }

            class Nested {
                boolean enabled = true
            }
        ''')
        Field payloadField = configuration.getDeclaredField(ConfigurationMetadataTransformation.PAYLOAD_FIELD)
        payloadField.accessible = true
        Map payload = new JsonSlurper().parseText(payloadField.get(null) as String) as Map

        then:
        Modifier.isPrivate(payloadField.modifiers)
        Modifier.isStatic(payloadField.modifiers)
        Modifier.isFinal(payloadField.modifiers)
        payloadField.synthetic
        payload.prefix == 'sample.service'
        payload.sourceType == 'example.SampleConfiguration'
        payload.get('groups') == [[
                name: 'sample.service.nested',
                sourceType: 'example.SampleConfiguration',
                type: 'example.Nested'
        ]]
        payload.get('properties')*.name == [
                'sample.service.displayName',
                'sample.service.dynamicValue',
                'sample.service.immutableValue',
                'sample.service.labels',
                'sample.service.nested.enabled',
                'sample.service.port'
        ]
        !payload.get('properties')*.name.contains('sample.service.internalSecret')
        payload.get('properties').find { it.name == 'sample.service.displayName' }.defaultValue == 'Grails'
        payload.get('properties').find { it.name == 'sample.service.port' }.defaultValue == 8080
        payload.get('properties').find { it.name == 'sample.service.nested.enabled' }.defaultValue
        payload.get('properties').find { it.name == 'sample.service.labels' }.type == 'java.util.List<java.lang.String>'
        !payload.get('properties').find { it.name == 'sample.service.dynamicValue' }.containsKey('defaultValue')
        !payload.get('properties').find { it.name == 'sample.service.immutableValue' }.containsKey('defaultValue')
        !payload.get('properties').find { it.name == 'sample.service.labels' }.containsKey('defaultValue')

        cleanup:
        loader.close()
    }

    def "global transform rejects a user property that collides with its metadata payload"() {
        given:
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)

        when:
        loader.parseClass('''
            import org.springframework.boot.context.properties.ConfigurationProperties

            @ConfigurationProperties('sample')
            class CollidingConfiguration {
                String __grailsConfigurationMetadata
            }
        ''')

        then:
        MultipleCompilationErrorsException error = thrown()
        error.message.contains("reserved field '__grailsConfigurationMetadata'")

        cleanup:
        loader.close()
    }
}
