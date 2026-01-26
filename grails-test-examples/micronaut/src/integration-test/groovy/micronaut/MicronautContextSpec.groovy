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

import grails.testing.mixin.integration.Integration
import io.micronaut.context.ApplicationContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContextAware
import spock.lang.Specification

/**
 * Integration tests for Micronaut context coexistence with Spring/Grails context.
 * 
 * Tests that:
 * 1. Micronaut ApplicationContext is available
 * 2. Spring ApplicationContext is available
 * 3. Both contexts can be used together
 * 4. Bean lookup works across contexts
 */
@Integration
class MicronautContextSpec extends Specification implements ApplicationContextAware {

    @Autowired
    io.micronaut.context.ApplicationContext micronautContext

    org.springframework.context.ApplicationContext springContext

    void setApplicationContext(org.springframework.context.ApplicationContext applicationContext) {
        this.springContext = applicationContext
    }

    void "micronaut application context is available"() {
        expect:
        micronautContext != null
        micronautContext.isRunning()
    }

    void "spring application context is available"() {
        expect:
        springContext != null
    }

    void "micronaut beans can be retrieved from micronaut context"() {
        when:
        def beans = micronautContext.getBeansOfType(bean.injection.NamedService)

        then:
        beans != null
        beans.size() == 4
    }

    void "grails services are accessible from spring context"() {
        when:
        def service = springContext.getBean(BeanInjectionService)

        then:
        service != null
        service instanceof BeanInjectionService
    }

    void "micronaut context has correct environment"() {
        expect:
        micronautContext.environment != null
    }

    void "both contexts share the same application lifecycle"() {
        expect:
        micronautContext.isRunning()
        springContext.isActive()
    }
}
