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
package app3

import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import spock.lang.Specification

/**
 * End-to-end test of the retimed plugin lifecycle through real plugin discovery and a real
 * application boot. The {@code loadafter} plugin (an app3 dependency) registers
 * {@code earlyPluginProbe} in plain {@code doWithSpring}, which now runs ahead of Spring Boot
 * auto-configuration; {@code Application} defines a
 * {@code @ConditionalOnMissingBean(name='earlyPluginProbe')} default. The plugin's bean must win.
 *
 * <p>The {@code loadafter} plugin also registers {@code registrarProbe} through the new
 * {@code beanRegistrar()} API (a closure coerced to a Spring {@code BeanRegistrar}), which the
 * matching {@code @ConditionalOnMissingBean} default must likewise defer to.
 *
 * <p>The strict ordering proof (the conditional default is never even created) is covered at unit
 * level by {@code EarlyPluginRegistrationOrderingSpec} in grails-core, including its control case.
 */
@Integration
class PluginBeansBeforeAutoConfigurationSpec extends Specification {

    @Autowired
    ApplicationContext applicationContext

    void "a plugin doWithSpring bean wins over the app's @ConditionalOnMissingBean default"() {
        expect: 'the plugin registered the probe ahead of auto-config, so the conditional default deferred to it'
        applicationContext.getBean('earlyPluginProbe') == 'from-plugin-doWithSpring'
    }

    void "a plugin beanRegistrar bean wins over the app's @ConditionalOnMissingBean default"() {
        expect: 'the plugin registrar registered the probe ahead of auto-config, so the conditional default deferred to it'
        applicationContext.getBean('registrarProbe') == 'from-plugin-beanRegistrar'
    }
}
