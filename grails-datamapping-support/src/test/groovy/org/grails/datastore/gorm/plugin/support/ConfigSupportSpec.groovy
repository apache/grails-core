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
package org.grails.datastore.gorm.plugin.support

import org.grails.config.PropertySourcesConfig
import org.springframework.context.support.StaticApplicationContext
import org.springframework.core.env.PropertyResolver
import spock.lang.Specification

class ConfigSupportSpec extends Specification {

    void "prepareConfig registers a String to Class converter so config values can be resolved as classes"() {
        given:
        def config = new PropertySourcesConfig(['some.class': String.name])
        def applicationContext = new StaticApplicationContext()
        applicationContext.refresh()

        expect: "the class value cannot be resolved before the config is prepared"
        config.getProperty('some.class', Class) == null

        when:
        ConfigSupport.prepareConfig(config, applicationContext)

        then:
        config.getProperty('some.class', Class) == String
    }

    void "prepareConfig does nothing when the config is not a PropertySourcesConfig"() {
        given:
        def config = Mock(PropertyResolver)
        def applicationContext = Mock(org.springframework.context.ConfigurableApplicationContext)

        when:
        ConfigSupport.prepareConfig(config, applicationContext)

        then:
        0 * applicationContext._
    }
}
