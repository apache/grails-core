/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.grails.core.cfg

import grails.util.Environment
import org.grails.config.PropertySourcesConfig
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.env.PropertySources
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import spock.lang.Specification
import spock.lang.TempDir
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
@SuppressWarnings("GrMethodMayBeStatic")
class GroovyConfigPropertySourceLoaderSpec extends Specification implements EnvironmentAwareSpec {

    @TempDir
    File configDirectory

    void setup() {
        resetEnvironment()
    }

    void cleanup() {
        Environment.reset()
    }

    void '#fileName retains #profileKey as data and selects Grails environment blocks'() {
        given:
        environment = Environment.TEST
        Environment.reset()
        System.setProperty('spring.profiles.active', 'dev')
        File applicationFile = new File(configDirectory, 'application.groovy')
        applicationFile.text = "sample.message = 'application'"
        new File(configDirectory, fileName).text = """
${profileSetting}
sample.message = 'default'
sample.retained = 'loaded'
environments {
    test {
        sample.message = 'test'
    }
    production {
        sample.message = 'production'
        sample.productionOnly = 'production'
    }
}
"""

        when:
        def sources = new GroovyConfigPropertySourceLoader().load('application.groovy', new FileSystemResource(applicationFile))
        def config = new PropertySourcesConfig(sources.first())

        then:
        config.getProperty(profileKey) == 'prod'
        config.getProperty('sample.retained') == 'loaded'
        config.getProperty('sample.message') == 'test'
        config.getProperty('sample.productionOnly') == null

        where:
        fileName             | profileKey                          | profileSetting
        'application.groovy' | 'spring.profiles'                   | "spring.profiles = 'prod'"
        'application.groovy' | 'spring.config.activate.on-profile' | "spring.config.activate.'on-profile' = 'prod'"
        'runtime.groovy'     | 'spring.profiles'                   | "spring.profiles = 'prod'"
        'runtime.groovy'     | 'spring.config.activate.on-profile' | "spring.config.activate.'on-profile' = 'prod'"
    }

    void "test loading multiple configuration files"() {
        setup:
        Resource inputStreamWithDsl = new FileSystemResource(getClass().getClassLoader().getResource("test-application.groovy").getFile())
        Resource inputSteamBuiltInVars = new FileSystemResource(getClass().getClassLoader().getResource("builtin-config.groovy").getFile())

        GroovyConfigPropertySourceLoader groovyPropertySourceLoader = new GroovyConfigPropertySourceLoader()
        Map<String, Object> finalMap = [:]
        environment = Environment.TEST

        when:
        PropertySources propertySources = new MutablePropertySources()
        propertySources.addFirst(groovyPropertySourceLoader.load("test-application.groovy", inputStreamWithDsl).first())
        propertySources.addFirst(groovyPropertySourceLoader.load("builtin-config.groovy", inputSteamBuiltInVars).first())
        def config = new PropertySourcesConfig(propertySources)

        then:
        config.size() == 8
        config.getProperty("my.local.var", String.class) == "test"
        config.getProperty("foo.bar", String.class) == "test"
        config.getProperty("userHomeVar", String.class)

    }

}
