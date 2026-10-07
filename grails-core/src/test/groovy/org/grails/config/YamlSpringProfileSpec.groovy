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
        'spring.config.activate.on-profile: alpha'                        | 'ALPHA'       | 'default'
        'spring.config.activate.on-profile: alpha'                        | 'beta'        | 'default'
        'spring:\n  config:\n    activate:\n      on-profile: alpha'      | null          | 'default'
        'spring:\n  config:\n    activate:\n      on-profile: alpha'      | 'alpha'       | 'selected'
        'spring:\n  config:\n    activate:\n      on-profile: alpha'      | 'beta'        | 'default'
        'spring.profiles: alpha'                                          | null          | 'default'
        'spring.profiles: alpha'                                          | ''            | 'default'
        'spring.profiles: alpha'                                          | 'alpha'       | 'selected'
        'spring.profiles: alpha'                                          | 'ALPHA'       | 'selected'
        'spring.profiles: AlPhA'                                          | 'alpha'       | 'selected'
        'spring.profiles: alpha'                                          | 'dev, ALPHA ' | 'selected'
        'spring.profiles: alpha'                                          | 'beta'        | 'default'
        'spring:\n  profiles: alpha'                                      | null          | 'default'
        'spring:\n  profiles: alpha'                                      | 'alpha'       | 'selected'
        'spring:\n  profiles: AlPhA'                                      | 'dev,alpha'   | 'selected'
        'spring:\n  profiles: alpha'                                      | 'beta'        | 'default'
        'spring.profiles.active: alpha'                                   | null          | 'selected'
        'spring.profiles.active: alpha'                                   | 'beta'        | 'selected'
        'spring:\n  profiles:\n    active: alpha'                         | 'beta'        | 'selected'
        'spring:\n  profiles:\n    include: [shared]'                     | 'beta'        | 'selected'
        'spring.config.activate.on-profile: ""'                           | null          | 'selected'
        'spring.profiles: ""'                                             | null          | 'selected'
        ''                                                               | null          | 'selected'
        ''                                                               | 'beta'        | 'selected'
        'spring.profiles: alpha\nspring.config.activate.on-profile: alpha' | 'alpha'       | 'selected'
        'spring.profiles: ALPHA\nspring.config.activate.on-profile: alpha' | 'dev,alpha'   | 'selected'
        'spring.profiles: beta\nspring.config.activate.on-profile: alpha'  | 'alpha'       | 'default'
        'spring.profiles: alpha\nspring.config.activate.on-profile: beta'  | 'alpha'       | 'default'
        'spring.profiles: " "\nspring.config.activate.on-profile: alpha'   | 'alpha'       | 'selected'
        'spring.profiles: " "\nspring.config.activate.on-profile: beta'    | 'alpha'       | 'default'
        'spring.profiles: alpha\nspring.config.activate.on-profile: " "'   | 'alpha'       | 'selected'
        'spring.profiles: beta\nspring.config.activate.on-profile: " "'    | 'alpha'       | 'default'
    }

    void 'YAML profile expression #expression selects documents with active profiles #activeProfiles'() {
        given:
        if (activeProfiles == null) {
            System.clearProperty('spring.profiles.active')
        }
        else {
            System.setProperty('spring.profiles.active', activeProfiles)
        }

        expect:
        for (String selector : [
                'spring.config.activate.on-profile:',
                'spring:\n  config:\n    activate:\n      on-profile:',
                'spring.profiles:',
                'spring:\n  profiles:'
        ]) {
            String yaml = """
sample.message: default
---
${selector} '${expression}'
sample.message: selected
"""
            def sources = new YamlPropertySourceLoader().load('application.yml', new ByteArrayResource(yaml.bytes))
            def config = new PropertySourcesConfig(sources.first())
            assert config.getProperty('sample.message') == expectedMessage : selector
        }

        where:
        expression                | activeProfiles        | expectedMessage
        ''                        | null                  | 'selected'
        ' '                       | null                  | 'selected'
        ' '                       | 'alpha'               | 'selected'
        ' \t '                    | null                  | 'selected'
        ' \t '                    | 'alpha'               | 'selected'
        ' alpha '                 | 'alpha'               | 'selected'
        ' alpha '                 | 'beta'                | 'default'
        'alpha'                   | 'dev,alpha'           | 'selected'
        'alpha'                   | ' alpha , dev '       | 'selected'
        'alpha'                   | 'dev,,alpha,alpha,'    | 'selected'
        'alpha'                   | 'dev,beta'            | 'default'
        'dev,alpha'               | 'dev'                 | 'default'
        'dev,alpha'               | 'alpha'               | 'default'
        'dev,alpha'               | 'dev,alpha'           | 'default'
        'alpha | beta'            | 'dev,alpha'           | 'selected'
        'alpha | beta'            | 'dev,beta'            | 'selected'
        'alpha | beta'            | 'dev,gamma'           | 'default'
        'alpha | beta'            | null                  | 'default'
        '!prod'                   | 'dev,alpha'           | 'selected'
        '!prod'                   | 'dev,prod'            | 'default'
        '!prod'                   | null                  | 'selected'
        '!prod'                   | ''                    | 'selected'
        'dev & alpha'             | 'dev,alpha'           | 'selected'
        'dev & alpha'             | 'alpha'               | 'default'
        'dev & alpha'             | 'dev'                 | 'default'
        'dev & alpha'             | null                  | 'default'
        '(dev | test) & !prod'     | 'test,alpha'          | 'selected'
        '(dev | test) & !prod'     | 'dev,prod'            | 'default'
        '(dev | test) & !prod'     | 'alpha'               | 'default'
        '!(dev | prod)'           | 'test,alpha'          | 'selected'
        '!(dev | prod)'           | 'dev,alpha'           | 'default'
    }

    void 'YAML sequence selector #entries selects documents with active profiles #activeProfiles'() {
        given:
        if (activeProfiles == null) {
            System.clearProperty('spring.profiles.active')
        }
        else {
            System.setProperty('spring.profiles.active', activeProfiles)
        }
        String quotedEntries = entries.collect { "'${it}'" }.join(', ')

        expect:
        for (Map.Entry<String, String> selector : [
                'spring.config.activate.on-profile:': '',
                'spring:\n  config:\n    activate:\n      on-profile:': '      ',
                'spring.profiles:': '',
                'spring:\n  profiles:': '  '
        ]) {
            List<String> sequences = ["${selector.key} [${quotedEntries}]".toString()]
            if (entries) {
                sequences << selector.key + entries.collect { "\n${selector.value}  - '${it}'" }.join('')
            }
            for (String sequence : sequences) {
                String yaml = """
sample.message: default
---
${sequence}
sample.message: selected
"""
                def sources = new YamlPropertySourceLoader().load('application.yml', new ByteArrayResource(yaml.bytes))
                def config = new PropertySourcesConfig(sources.first())
                assert config.getProperty('sample.message') == expectedMessage : sequence
            }
        }

        where:
        entries                    | activeProfiles | expectedMessage
        ['alpha', 'beta']          | 'gamma'        | 'default'
        ['alpha', 'beta']          | null           | 'default'
        ['alpha', 'beta']          | ''             | 'default'
        ['alpha', 'beta']          | 'alpha'        | 'selected'
        ['alpha', 'beta']          | 'beta'         | 'selected'
        ['alpha', 'beta']          | 'dev,beta'     | 'selected'
        ['alpha']                  | 'alpha'        | 'selected'
        ['alpha']                  | 'beta'         | 'default'
        [' alpha ', 'beta']        | 'alpha'        | 'selected'
        ['dev & alpha', 'beta']    | 'alpha'        | 'default'
        ['dev & alpha', 'beta']    | 'dev,alpha'    | 'selected'
        ['dev & alpha', 'beta']    | 'beta'         | 'selected'
        ['!prod', 'alpha']         | 'prod'         | 'default'
        ['!prod', 'alpha']         | 'prod,alpha'   | 'selected'
        ['!prod', 'alpha']         | null           | 'selected'
        [' ', 'alpha']             | 'beta'         | 'default'
        [' ', 'alpha']             | 'alpha'        | 'selected'
        ['', ' ']                  | 'alpha'        | 'selected'
        []                         | null           | 'selected'
        []                         | 'alpha'        | 'selected'
    }

    void 'YAML sequence selectors preserve case sensitivity for #selector'() {
        given:
        System.setProperty('spring.profiles.active', 'dev,ALPHA')
        def resource = new ByteArrayResource("""
sample.message: default
---
${selector}: [alpha, beta]
sample.message: selected
""".bytes)

        when:
        def sources = new YamlPropertySourceLoader().load('application.yml', resource)
        def config = new PropertySourcesConfig(sources.first())

        then:
        config.getProperty('sample.message') == expectedMessage

        where:
        selector                            | expectedMessage
        'spring.config.activate.on-profile' | 'default'
        'spring.profiles'                   | 'selected'
    }

    void 'YAML sequence and scalar selectors must both match active profiles #activeProfiles'() {
        given:
        System.setProperty('spring.profiles.active', activeProfiles)
        def resource = new ByteArrayResource('''
sample.message: default
---
spring.config.activate.on-profile: [alpha, beta]
spring.profiles: dev
sample.message: selected
'''.bytes)

        when:
        def sources = new YamlPropertySourceLoader().load('application.yml', resource)
        def config = new PropertySourcesConfig(sources.first())

        then:
        config.getProperty('sample.message') == expectedMessage

        where:
        activeProfiles | expectedMessage
        'dev,beta'     | 'selected'
        'dev,gamma'    | 'default'
        'beta'         | 'default'
    }

    void 'YAML sequence entries do not select documents through unrelated indexed keys'() {
        given:
        System.setProperty('spring.profiles.active', 'beta')
        def resource = new ByteArrayResource('''
sample.message: default
---
spring.profiles.include: [alpha]
spring.config.activate.on-profile-group: [alpha]
sample.message: selected
'''.bytes)

        when:
        def sources = new YamlPropertySourceLoader().load('application.yml', resource)
        def config = new PropertySourcesConfig(sources.first())

        then:
        config.getProperty('sample.message') == 'selected'
    }

    void 'YAML profile expressions preserve case sensitivity for #selector'() {
        given:
        System.setProperty('spring.profiles.active', 'dev,ALPHA')
        String yaml = """
sample.message: default
---
${selector}: '${expression}'
sample.message: selected
"""

        when:
        def sources = new YamlPropertySourceLoader().load('application.yml', new ByteArrayResource(yaml.bytes))
        def config = new PropertySourcesConfig(sources.first())

        then:
        config.getProperty('sample.message') == expectedMessage

        where:
        selector                            | expression     | expectedMessage
        'spring.config.activate.on-profile' | 'alpha | beta' | 'default'
        'spring.profiles'                   | 'alpha | beta' | 'selected'
        'spring.config.activate.on-profile' | '!alpha'       | 'selected'
        'spring.profiles'                   | '!alpha'       | 'default'
    }

    void 'invalid YAML profile expression #value is rejected for #selector'() {
        given:
        System.setProperty('spring.profiles.active', 'alpha')
        def resource = new ByteArrayResource("${selector}: ${value}\nsample.message: selected".bytes)

        when:
        new YamlPropertySourceLoader().load('application.yml', resource)

        then:
        IllegalArgumentException exception = thrown()
        exception.message.contains('Malformed profile expression')

        where:
        selector                            | value
        'spring.config.activate.on-profile' | "'alpha & beta | gamma'"
        'spring.profiles'                   | "'alpha & beta | gamma'"
        'spring.config.activate.on-profile' | "[beta, 'alpha & beta | gamma']"
        'spring.profiles'                   | "[beta, 'alpha & beta | gamma']"
    }

    void 'both modern and legacy YAML profile conditions must match active profiles #activeProfiles'() {
        given:
        System.setProperty('spring.profiles.active', activeProfiles)
        def resource = new ByteArrayResource('''
sample.message: default
---
spring.config.activate.on-profile: 'dev & !prod'
spring.profiles: 'ALPHA | BETA'
sample.message: selected
'''.bytes)

        when:
        def sources = new YamlPropertySourceLoader().load('application.yml', resource)
        def config = new PropertySourcesConfig(sources.first())

        then:
        config.getProperty('sample.message') == expectedMessage

        where:
        activeProfiles   | expectedMessage
        'dev,alpha'      | 'selected'
        'dev,beta'       | 'selected'
        'dev,gamma'      | 'default'
        'alpha'          | 'default'
        'dev,prod,alpha' | 'default'
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

    void 'reusing the loader selects #selector expressions for each load without changing previously loaded values'() {
        given:
        def resource = new ByteArrayResource("""
${selector}: 'alpha & !prod'
sample.message: first
---
${selector}: 'beta | gamma'
sample.message: second
""".bytes)
        def loader = new YamlPropertySourceLoader()
        System.setProperty('spring.profiles.active', 'dev,alpha')
        def firstSource = loader.load('first.yml', resource).first()

        when:
        System.setProperty('spring.profiles.active', 'prod,beta')
        def secondSource = loader.load('second.yml', resource).first()

        then:
        new PropertySourcesConfig(firstSource).getProperty('sample.message') == 'first'
        new PropertySourcesConfig(secondSource).getProperty('sample.message') == 'second'

        where:
        selector << ['spring.config.activate.on-profile', 'spring.profiles']
    }
}
