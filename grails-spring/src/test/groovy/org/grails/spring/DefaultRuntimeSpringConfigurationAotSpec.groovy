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
package org.grails.spring

import org.springframework.aot.AotDetector
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.core.SpringProperties
import spock.lang.Specification

/**
 * Covers plugin bean definitions being registered without replacing ones generated ahead of time.
 *
 * <p>Running on generated artifacts, the plugins that produced these definitions already ran: they
 * ran while the artifacts were being generated, and what they registered was written out as code and
 * registered again ahead of this phase. Registering over that discards the instance supplier the
 * generator wrote and replaces it with a definition that finds everything by reflection.</p>
 */
class DefaultRuntimeSpringConfigurationAotSpec extends Specification {

    DefaultListableBeanFactory registry = new DefaultListableBeanFactory()

    DefaultRuntimeSpringConfiguration springConfig = new DefaultRuntimeSpringConfiguration()

    void cleanup() {
        SpringProperties.setProperty(AotDetector.AOT_ENABLED, null)
    }

    private void runningOnGeneratedArtifacts(boolean enabled) {
        SpringProperties.setProperty(AotDetector.AOT_ENABLED, String.valueOf(enabled))
    }

    /** A definition contributed the way a plugin's {@code doWithSpring()} contributes one. */
    private void contribute(String beanName, Class<?> beanClass) {
        springConfig.addSingletonBean(beanName, beanClass)
    }

    void 'a generated definition is kept'() {
        given:
            runningOnGeneratedArtifacts(true)
            RootBeanDefinition generated = new RootBeanDefinition(Collaborator)
            registry.registerBeanDefinition('subject', generated)
            contribute('subject', Collaborator)

        when:
            springConfig.registerBeansWithRegistry(registry)

        then:
            registry.getBeanDefinition('subject').is(generated)
    }

    void 'a definition the generator did not produce is still registered'() {
        given:
            runningOnGeneratedArtifacts(true)
            contribute('conditional', Collaborator)

        when:
            springConfig.registerBeansWithRegistry(registry)

        then: 'a bean a plugin contributes conditionally has nothing generated for it'
            registry.containsBeanDefinition('conditional')
    }

    void 'a plugin overrides what came before it on a normal start'() {
        given:
            runningOnGeneratedArtifacts(false)
            RootBeanDefinition earlier = new RootBeanDefinition(Collaborator)
            registry.registerBeanDefinition('subject', earlier)
            contribute('subject', Collaborator)

        when:
            springConfig.registerBeansWithRegistry(registry)

        then: 'the ordering plugins rely on to replace one another is unchanged'
            !registry.getBeanDefinition('subject').is(earlier)
    }

    static class Collaborator {
    }
}
