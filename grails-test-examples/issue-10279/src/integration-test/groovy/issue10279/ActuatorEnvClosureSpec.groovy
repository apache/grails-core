/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package issue10279

import spock.lang.Specification
import spock.lang.Tag

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * Regression test for GitHub issue #10279.
 *
 * When application.groovy defines closure-valued properties (e.g. grails.gorm.default.mapping),
 * the Spring Boot Actuator /actuator/env endpoint must not throw a Jackson serialization error.
 *
 * In Grails 3.x this caused a JsonMappingException because the Closure held a delegate chain
 * that referenced GroovyClassLoader internals, which Jackson could not serialize.
 */
@Integration
@Tag('http-client')
class ActuatorEnvClosureSpec extends Specification implements HttpClientSupport {

    void 'actuator /env endpoint returns 200 when application.groovy contains closure-valued config'() {
        when: 'the actuator environment endpoint is accessed'
        def response = http('/actuator/env')

        then: 'it responds successfully without a Jackson serialization error (issue #10279)'
        response.assertStatus(200)
    }

    void 'actuator /env endpoint response contains normal string properties from application.groovy'() {
        when: 'the actuator environment endpoint is accessed'
        def response = http('/actuator/env')

        then: 'it responds successfully'
        response.assertStatus(200)

        and: 'the string property defined in application.groovy is present in the response'
        response.assertContains('hello-from-groovy-config')
    }
}
