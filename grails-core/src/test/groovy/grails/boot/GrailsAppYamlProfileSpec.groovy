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
package grails.boot

import spock.lang.Specification
import spock.lang.TempDir
import spock.util.environment.RestoreSystemProperties

import org.springframework.boot.WebApplicationType
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Configuration

import grails.util.Environment

/**
 * Verifies that profile-specific YAML documents selected by the Grails YAML loader survive Spring Boot's
 * config-data processing, where {@link GrailsApp} adds the Grails environment name as an active profile.
 */
@RestoreSystemProperties
class GrailsAppYamlProfileSpec extends Specification {

    @TempDir
    File configDirectory

    void cleanup() {
        Environment.reset()
    }

    void 'application.yml with #selector is retained when Boot activates #bootProfiles'() {
        given:
        System.setProperty(Environment.KEY, grailsEnvironment)
        if (systemProfiles == null) {
            System.clearProperty('spring.profiles.active')
        }
        else {
            System.setProperty('spring.profiles.active', systemProfiles)
        }
        File applicationFile = new File(configDirectory, 'application.yml')
        applicationFile.text = """
sample.base: base
sample.message: default
---
${selector}
sample.message: selected
"""
        GrailsApp app = new GrailsApp(YamlProfileTestConfiguration)
        app.webApplicationType = WebApplicationType.NONE
        List<String> args = ["--spring.config.location=file:${applicationFile.absolutePath}".toString()]
        if (argumentProfiles) {
            args << "--spring.profiles.active=${argumentProfiles}".toString()
        }

        when:
        ConfigurableApplicationContext context = app.run(args as String[])

        then:
        context.environment.activeProfiles.toList() == bootProfiles
        context.environment.getProperty('sample.base') == 'base'
        context.environment.getProperty('sample.message') == expectedMessage
        context.environment.getProperty('spring.config.activate.on-profile') == null
        context.environment.getProperty('spring.profiles') == null

        cleanup:
        context?.close()

        where:
        selector                                           | grailsEnvironment | systemProfiles | argumentProfiles || bootProfiles      | expectedMessage
        "spring.config.activate.on-profile: '!production'" | 'production'      | null           | null             || ['production']    | 'selected'
        "spring.config.activate.on-profile: '!prod'"       | 'test'            | null           | 'prod'           || ['prod', 'test']  | 'selected'
        "spring.config.activate.on-profile: '!test'"       | 'test'            | 'dev'          | null             || ['dev', 'test']   | 'selected'
        "spring.config.activate.on-profile: '!dev'"        | 'test'            | 'dev'          | null             || ['dev', 'test']   | 'default'
        'spring.config.activate.on-profile: alpha'         | 'test'            | 'alpha'        | null             || ['alpha', 'test'] | 'selected'
        'spring.config.activate.on-profile: alpha'         | 'test'            | 'beta'         | null             || ['beta', 'test']  | 'default'
        'spring.config.activate.on-profile: [alpha, beta]' | 'test'            | 'beta'         | null             || ['beta', 'test']  | 'selected'
        "spring.config.activate.on-profile: 'beta,alpha'"  | 'test'            | 'alpha'        | null             || ['alpha', 'test'] | 'selected'
        'spring.profiles: alpha'                           | 'test'            | 'alpha'        | null             || ['alpha', 'test'] | 'selected'
        'spring.profiles: [alpha, beta]'                   | 'test'            | 'beta'         | null             || ['beta', 'test']  | 'selected'
        'spring.profiles: alpha'                           | 'test'            | 'beta'         | null             || ['beta', 'test']  | 'default'
    }
}

@Configuration
class YamlProfileTestConfiguration {
}
