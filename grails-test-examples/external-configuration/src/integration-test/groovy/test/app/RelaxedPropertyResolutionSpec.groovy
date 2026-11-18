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
package test.app

import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.endpoint.Access
import org.springframework.boot.convert.ApplicationConversionService
import org.springframework.core.env.ConfigurableEnvironment
import spock.lang.Specification

/**
 * Regression tests for issue #15818. Actuator endpoint access is resolved directly through
 * {@code environment.getProperty(name, Access)} rather than relaxed configuration-property
 * binding, so the environment itself must be configured with the
 * {@link ApplicationConversionService} for lenient values such as {@code unrestricted} or
 * {@code read-only} to convert. Before the fix the application failed to start with the
 * {@code management.endpoint.*.access} values declared in {@code application.yml}.
 */
@Integration
class RelaxedPropertyResolutionSpec extends Specification {

    @Autowired
    ConfigurableEnvironment springEnvironment

    void 'the environment uses the ApplicationConversionService'() {
        expect: 'the conversion service installed by Spring Boot is present'
        springEnvironment.conversionService instanceof ApplicationConversionService
    }

    void 'a lowercase enum value resolves through environment.getProperty'() {
        expect: 'the lenient value from application.yml converts to the Access enum'
        springEnvironment.getProperty('management.endpoint.heapdump.access', Access) == Access.UNRESTRICTED
    }

    void 'a hyphenated enum value resolves through environment.getProperty'() {
        expect: 'the lenient value from application.yml converts to the Access enum'
        springEnvironment.getProperty('management.endpoint.threaddump.access', Access) == Access.READ_ONLY
    }
}
