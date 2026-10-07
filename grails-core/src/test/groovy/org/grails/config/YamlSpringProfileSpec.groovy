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

import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import org.springframework.core.io.ByteArrayResource

import org.grails.config.yaml.YamlPropertySourceLoader

@RestoreSystemProperties
class YamlSpringProfileSpec extends Specification {

    void 'YAML document selection honors #selector with active profile #activeProfile'() {
        given:
        if (activeProfile == null) {
            System.clearProperty('spring.profiles.active')
        }
        else {
            System.setProperty('spring.profiles.active', activeProfile)
        }
        String yaml = """
sample.message: default
---
${selector}
sample.message: selected
"""

        when:
        def sources = new YamlPropertySourceLoader().load('application.yml', new ByteArrayResource(yaml.bytes))
        def config = new PropertySourcesConfig(sources.first())

        then:
        config.getProperty('sample.message') == expectedMessage

        where:
        selector                                                         | activeProfile | expectedMessage
        'spring.config.activate.on-profile: alpha'                        | null          | 'default'
        'spring.config.activate.on-profile: alpha'                        | ''            | 'default'
        'spring.config.activate.on-profile: alpha'                        | 'alpha'       | 'selected'
        'spring.config.activate.on-profile: alpha'                        | ' alpha '     | 'selected'
        'spring.config.activate.on-profile: alpha'                        | 'beta'        | 'default'
        'spring:\n  config:\n    activate:\n      on-profile: alpha'      | null          | 'default'
        'spring:\n  config:\n    activate:\n      on-profile: alpha'      | 'alpha'       | 'selected'
        'spring:\n  config:\n    activate:\n      on-profile: alpha'      | 'beta'        | 'default'
        'spring.profiles: alpha'                                          | null          | 'default'
        'spring.profiles: alpha'                                          | ''            | 'default'
        'spring.profiles: alpha'                                          | 'alpha'       | 'selected'
        'spring.profiles: alpha'                                          | 'beta'        | 'default'
        'spring:\n  profiles: alpha'                                      | null          | 'default'
        'spring:\n  profiles: alpha'                                      | 'alpha'       | 'selected'
        'spring:\n  profiles: alpha'                                      | 'beta'        | 'default'
        'spring.profiles.active: alpha'                                   | null          | 'selected'
        'spring.profiles.active: alpha'                                   | 'beta'        | 'selected'
        'spring:\n  profiles:\n    active: alpha'                         | 'beta'        | 'selected'
        'spring:\n  profiles:\n    include: [shared]'                     | 'beta'        | 'selected'
        'spring.config.activate.on-profile: ""'                           | null          | 'selected'
        ''                                                               | null          | 'selected'
        ''                                                               | 'beta'        | 'selected'
        'spring.profiles: alpha\nspring.config.activate.on-profile: alpha' | 'alpha'       | 'selected'
        'spring.profiles: beta\nspring.config.activate.on-profile: alpha'  | 'alpha'       | 'default'
        'spring.profiles: alpha\nspring.config.activate.on-profile: beta'  | 'alpha'       | 'default'
    }

    void 'a resource containing only an inactive profile document contributes no property source'() {
        given:
        System.clearProperty('spring.profiles.active')
        def resource = new ByteArrayResource('spring.config.activate.on-profile: alpha\nsample.message: selected'.bytes)

        expect:
        new YamlPropertySourceLoader().load('application.yml', resource).isEmpty()
    }

    void 'loaded YAML values survive a later change to the JVM profile'() {
        given:
        System.setProperty('spring.profiles.active', 'alpha')
        def resource = new ByteArrayResource('spring.config.activate.on-profile: alpha\nsample.message: selected'.bytes)
        def source = new YamlPropertySourceLoader().load('application.yml', resource).first()

        when:
        System.setProperty('spring.profiles.active', 'beta')
        def config = new PropertySourcesConfig(source)

        then:
        config.getProperty('sample.message') == 'selected'
    }

    void 'reusing the loader selects documents for each load without changing previously loaded values'() {
        given:
        def resource = new ByteArrayResource('''
spring.config.activate.on-profile: alpha
sample.message: first
---
spring.config.activate.on-profile: beta
sample.message: second
'''.bytes)
        def loader = new YamlPropertySourceLoader()
        System.setProperty('spring.profiles.active', 'alpha')
        def firstSource = loader.load('first.yml', resource).first()

        when:
        System.setProperty('spring.profiles.active', 'beta')
        def secondSource = loader.load('second.yml', resource).first()

        then:
        new PropertySourcesConfig(firstSource).getProperty('sample.message') == 'first'
        new PropertySourcesConfig(secondSource).getProperty('sample.message') == 'second'
    }
}
