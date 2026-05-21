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
package org.grails.config

import org.springframework.boot.env.RandomValuePropertySource
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import spock.lang.Issue
import spock.lang.Specification

class PropertyResolutionSpec extends Specification {

    @Issue('#10340')
    void 'test property evaluation'() {
        given:
        Resource resource1 = new ByteArrayResource('''
---
rabbitmq:
    something1: '${random.int}'
    something2: ${random.int}
    connections:
      - name: main
        host: ${random.int}
        host2: '${random.int}'
        username: guest
        password: guest
'''.bytes, 'test.yml')

        def propertySourceLoader = new YamlPropertySourceLoader()
        def yamlPropertiesSource1 = propertySourceLoader.load('test.yml', resource1)
        MutablePropertySources propertySources = new MutablePropertySources()
        propertySources.addFirst(yamlPropertiesSource1.first())
        propertySources.addFirst(new RandomValuePropertySource())

        def config = new PropertySourcesConfig(propertySources)

        expect:
        !config.getProperty('rabbitmq.something1').contains('random.int')
        config.getProperty('rabbitmq.something1').isNumber()
        !config.getProperty('rabbitmq.something2').contains('random.int')
        config.getProperty('rabbitmq.something2').isNumber()

        and:
        config.getProperty('rabbitmq.connections[0]', Map).get('name') == 'main'
        !config.getProperty('rabbitmq.connections[0]', Map).get('host').contains('random.int')
        config.getProperty('rabbitmq.connections[0]', Map).get('host').isNumber()
        !config.getProperty('rabbitmq.connections[0]', Map).get('host2').contains('random.int')
        config.getProperty('rabbitmq.connections[0]', Map).get('host2').isNumber()
    }
}
