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
package grails.boot.config

import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.support.GenericApplicationContext
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import org.apache.grails.core.aot.GeneratedBeanDefinitions

/**
 * Covers the initializer that registers plugins early recording, on generated artifacts, what the generated
 * code contributed before any plugin declares a bean.
 */
@RestoreSystemProperties
class GrailsPluginLifecycleInitializerSpec extends Specification {

    GenericApplicationContext context = new GenericApplicationContext()

    void setup() {
        context.registerBeanDefinition('generated', new RootBeanDefinition(String))
    }

    void cleanup() {
        context.close()
    }

    void 'the generated definitions are recorded on generated artifacts'() {
        given:
            System.setProperty('spring.aot.enabled', 'true')

        when:
            new GrailsPluginLifecycleInitializer().initialize(context)

        then:
            GeneratedBeanDefinitions.isInjectedByGeneratedCode(context.beanFactory, 'generated', String)
    }

    void 'nothing is recorded otherwise'() {
        given:
            System.clearProperty('spring.aot.enabled')

        when:
            new GrailsPluginLifecycleInitializer().initialize(context)

        then:
            !context.beanFactory.containsSingleton(GeneratedBeanDefinitions.BEAN_NAME)
    }
}
