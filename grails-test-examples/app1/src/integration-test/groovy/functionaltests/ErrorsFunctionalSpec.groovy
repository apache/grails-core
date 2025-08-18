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

package functionaltests

import grails.gorm.transactions.Rollback
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import spock.lang.Issue
import spock.lang.PendingFeature

@Integration(applicationClass = Application)
@Rollback
class ErrorsFunctionalSpec extends ContainerGebSpec {

    void "Test exception handling methods for internal controller exceptions"() {
        when: "An action that throws a custom error that is handled by a 500 mapping in UrlMappings.groovy"
        go('/errors/throwCustomError')

        then: "The correct action is executed"
        pageSource.contains('Message = Something bad')
    }

    void "Test 500 mappings for custom exceptions"() {
        when: "An action that throws a custom error that is handled by a 500 mapping in UrlMappings.groovy"
        go('/demo/throwCustomError')

        then: "The correct action is executed"
        pageSource.contains('Message = Something bad')
    }

    void "Test default 500 mapping"() {
        when: "An excetion that throws a general error handled by the default 500 mapping in UrlMapings.groovy"
        go('/errors/throwGeneralError')

        println(pageSource)

        then: "The title is correct"
        $('ul', class: 'errors').text() == 'An error has occurred'
    }

    void "Test 404 mapping to controller"() {
        when: "When an action returns a 404"
        go('/errors/notFoundTest')
        then: "Make sure the global 404 handler is triggered"
        pageSource.contains 'Page Not Found'
    }

    @Issue("https://github.com/apache/grails-core/issues/9548")
    void "Test Interceptor.afterView() has access to the exception if thrown"() {
        expect:
        !System.properties[ErrorsControllerInterceptor.PROPERTY]

        when: "An action that throws an exception is requested"
        go('/errors/throwException')

        then:
        System.properties[ErrorsControllerInterceptor.PROPERTY] == 'Oops!'

        cleanup:
        System.clearProperty ErrorsControllerInterceptor.PROPERTY
    }
}
