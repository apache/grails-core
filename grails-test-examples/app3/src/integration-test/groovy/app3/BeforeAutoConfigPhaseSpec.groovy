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
 * End-to-end test of the before-auto-configuration phase through real plugin discovery and a real
 * GrailsApp boot. The {@code loadafter} plugin (an app3 dependency) registers
 * {@code beforeAutoConfigProbe} in {@code doWithSpringBeforeAutoConfiguration} (ahead of auto-config);
 * {@code Application} defines a {@code @ConditionalOnMissingBean(name='beforeAutoConfigProbe')}
 * default. The plugin's bean must win and the default must defer.
 */
@Integration
class BeforeAutoConfigPhaseSpec extends Specification {

    @Autowired
    ApplicationContext applicationContext

    void "a plugin's before-auto-config bean wins; the app @ConditionalOnMissingBean default defers"() {
        expect: 'the plugin registered the probe ahead of auto-config, so the conditional default deferred to it'
        applicationContext.getBean('beforeAutoConfigProbe') == 'from-plugin-before-autoconfig'
    }
}
