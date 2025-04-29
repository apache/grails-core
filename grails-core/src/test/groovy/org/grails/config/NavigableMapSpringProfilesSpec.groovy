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

import org.grails.config.yaml.YamlPropertySourceLoader
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import spock.lang.Specification

class NavigableMapSpringProfilesSpec extends Specification {

    void 'test spring profiles configuration for default config'() {
        given:
        def propertySource = new YamlPropertySourceLoader()
        Resource resource = new FileSystemResource(getClass().getClassLoader().getResource('application.yml').getFile())
        def yamlPropertiesSource = propertySource.load('application.yml', resource, null)
        def config = new PropertySourcesConfig(yamlPropertiesSource.first())

        expect:
        config.getProperty('hello.message') == 'Default hello!'
    }

    void 'test spring profiles configuration for "sample" profile'() {
        given:
        System.setProperty('spring.profiles.active', 'sample')

        def propertySource = new YamlPropertySourceLoader()
        Resource resource = new FileSystemResource(getClass().getClassLoader().getResource('application.yml').getFile())
        def yamlPropertiesSource = propertySource.load('application.yml', resource)
        def config = new PropertySourcesConfig(yamlPropertiesSource.first())

        expect:
        config.getProperty('hello.message') == 'Hello from SAMPLE profile!'
    }

    void 'test spring profiles configuration for "demo" profile'() {
        given:
        System.setProperty('spring.profiles.active', 'demo')

        def propertySource = new YamlPropertySourceLoader()
        Resource resource = new FileSystemResource(getClass().getClassLoader().getResource('application.yml').getFile())
        def yamlPropertiesSource = propertySource.load('application.yml', resource)
        def config = new PropertySourcesConfig(yamlPropertiesSource.first())

        expect:
        config.getProperty('hello.message') == 'Hello from DEMO profile!'
    }

    void 'test spring profiles property resolution for default config'() {
        given:
        def propertySource = new YamlPropertySourceLoader()
        Resource resource = new FileSystemResource(getClass().getClassLoader().getResource('application.yml').getFile())
        def yamlPropertiesSource = propertySource.load('application.yml', resource, null)
        def config = new PropertySourcesConfig(yamlPropertiesSource.first())

        expect:
        config.getProperty('hello.evaluatedName') == 'Hello, my name is'
    }
}
