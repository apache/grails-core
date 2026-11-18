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
package org.apache.grails.testing.latency

import spock.lang.Specification

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.web.servlet.FilterRegistrationBean

class LatencyAutoConfigurationSpec extends Specification {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LatencyAutoConfiguration))

    void 'registers the latency filter when enabled'() {
        expect:
        contextRunner
                .withPropertyValues(
                        'grails.testing.latency.enabled=true',
                        'grails.testing.latency.min-delay=50',
                        'grails.testing.latency.max-delay=250',
                        'grails.testing.latency.probability=0.75',
                        'grails.testing.latency.url-patterns=/report/*'
                )
                .run { context ->
                    assert context.getBean('latencyFilter', FilterRegistrationBean).filter instanceof LatencyFilter
                    def properties = context.getBean(LatencyProperties)
                    assert properties.minDelay.toMillis() == 50
                    assert properties.maxDelay.toMillis() == 250
                    assert properties.probability == 0.75d
                    assert properties.urlPatterns == ['/report/*']
                }
    }

    void 'does nothing when disabled or not configured'() {
        expect:
        contextRunner
                .withPropertyValues(properties as String[])
                .run { context ->
                    assert !context.containsBean('latencyFilter')
                }

        where:
        properties << [[], ['grails.testing.latency.enabled=false']]
    }
}
