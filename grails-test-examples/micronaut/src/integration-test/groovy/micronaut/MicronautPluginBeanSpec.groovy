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
package micronaut

import com.example.grails.plugins.micronaut.PluginMessageProvider
import com.example.grails.plugins.micronaut.PluginSingletonService
import io.micronaut.context.ApplicationContext as MicronautApplicationContext
import spock.lang.Specification

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext as SpringApplicationContext

import grails.testing.mixin.integration.Integration

@Integration
class MicronautPluginBeanSpec extends Specification {

    @Autowired
    SpringApplicationContext springContext

    @Autowired
    MicronautApplicationContext micronautContext

    void "plugin-contributed @Singleton bean is available in Spring context"() {
        when: 'looking up the plugin bean from the Spring context'
        def service = springContext.getBean(PluginSingletonService)

        then: 'the bean is found and functional'
        service != null
        service.pluginMessage == 'from-plugin-singleton'
    }

    void "plugin-contributed @Singleton bean is available in Micronaut context"() {
        when: 'looking up the plugin bean from the Micronaut context'
        def service = micronautContext.getBean(PluginSingletonService)

        then: 'the bean is found and functional'
        service != null
        service.pluginMessage == 'from-plugin-singleton'
    }

    void "plugin-contributed @Singleton bean is a singleton instance"() {
        when: 'retrieving the bean twice'
        def first = springContext.getBean(PluginSingletonService)
        def second = springContext.getBean(PluginSingletonService)

        then: 'same instance is returned'
        first.is(second)
    }

    void "plugin-contributed @Singleton bean is resolvable by its interface"() {
        when: 'looking up the bean by its interface'
        def provider = springContext.getBean(PluginMessageProvider)

        then: 'the bean is found via the interface type'
        provider != null
        provider instanceof PluginSingletonService
        provider.pluginMessage == 'from-plugin-singleton'
    }

    void "plugin-contributed bean is same instance in Spring and Micronaut contexts"() {
        when: 'retrieving from both contexts'
        def fromSpring = springContext.getBean(PluginSingletonService)
        def fromMicronaut = micronautContext.getBean(PluginSingletonService)

        then: 'they are the same singleton instance'
        fromSpring.is(fromMicronaut)
    }
}
