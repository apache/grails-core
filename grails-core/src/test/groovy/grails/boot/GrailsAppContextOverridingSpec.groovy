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

import grails.util.Environment
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.boot.WebApplicationType
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Configuration
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

/**
 * Verifies that bean definition overriding and circular references default to {@code true} (the
 * historical Grails behavior) but can be turned off via the standard {@code spring.main.*}
 * configuration properties.
 */
@RestoreSystemProperties
class GrailsAppContextOverridingSpec extends Specification {

    ConfigurableApplicationContext context

    void setup() {
        System.setProperty(Environment.KEY, Environment.TEST.getName())
    }

    void cleanup() {
        context?.close()
    }

    void "bean definition overriding and circular references default to true"() {
        when:
        context = runApplication([:])

        then:
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.beanFactory
        beanFactory.isAllowBeanDefinitionOverriding()
        beanFactory.isAllowCircularReferences()
    }

    void "bean definition overriding can be disabled via spring.main.allow-bean-definition-overriding"() {
        when:
        context = runApplication(['spring.main.allow-bean-definition-overriding': 'false'])

        then:
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.beanFactory
        !beanFactory.isAllowBeanDefinitionOverriding()
        beanFactory.isAllowCircularReferences()
    }

    void "circular references can be disabled via spring.main.allow-circular-references"() {
        when:
        context = runApplication(['spring.main.allow-circular-references': 'false'])

        then:
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.beanFactory
        beanFactory.isAllowBeanDefinitionOverriding()
        !beanFactory.isAllowCircularReferences()
    }

    void "both settings can be disabled together"() {
        when:
        context = runApplication([
                'spring.main.allow-bean-definition-overriding': 'false',
                'spring.main.allow-circular-references'       : 'false'
        ])

        then:
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.beanFactory
        !beanFactory.isAllowBeanDefinitionOverriding()
        !beanFactory.isAllowCircularReferences()
    }

    private static ConfigurableApplicationContext runApplication(Map<String, Object> properties) {
        GrailsApp app = new GrailsApp(GrailsAppOverridingTestConfig)
        app.webApplicationType = WebApplicationType.NONE
        if (properties) {
            app.setDefaultProperties(properties)
        }
        return app.run()
    }
}

@Configuration
class GrailsAppOverridingTestConfig {
}
