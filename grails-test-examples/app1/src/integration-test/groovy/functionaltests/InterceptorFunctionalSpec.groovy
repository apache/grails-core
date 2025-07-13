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

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import spock.lang.Issue
import spock.lang.PendingFeature

@Integration(applicationClass = Application)
class InterceptorFunctionalSpec extends ContainerGebSpec {

    @Issue('apache/grails-core#9434')
    void "Test that an interceptor exception is handled correctly"() {
        when:
        go('/errors/throwErrorInInterceptor')

        then:
        $().text() == 'Message = Interceptor threw error'
    }

    @Issue('apache/grails-core#9183')
    void "Test that an after interceptor can render text and return false to disable view rendering"() {
        when:
        go('/demo/show')

        then:
        $().text() == 'the after interceptor rendered this'
    }

    void 'Test that after interceptor can render a model and view'() {
        when:
        go('/demo/show?interceptorRendersView=true')

        then:
        $().text() == 'Name: JSB'
    }

    void 'Test that before interceptor can render a model and view'() {
        when:
        go('/demo/another')

        then:
        $().text() == 'Name: JSB'
    }    

    void 'Test that after interceptor can render text'() {
        when:
        go('/demo/show?interceptorRendersText=true')

        then:
        $().text() == 'text rendered by interceptor'
    }

    @Issue('apache/grails-core#9194')
    void 'Test that after interceptor can redirect'() {
        when:
        go('/demo/show?interceptorRedirects=true')

        then:
        $().text() == 'Hi There! Special Action: redirect'
    }


    @Issue('apache/grails-core#9194')
    void 'Test that after interceptor can forward'() {
        when:
        go('/demo/show?interceptorForwards=true')

        then:
        $().text() == 'Hi There! Special Action: forward'
    }

    @Issue('apache/grails-core#9194')
    void 'Test that after interceptor can chain'() {
        when:
        go('/demo/show?interceptorChains=true')

        then:
        $().text() == 'Hi There! Special Action: chain'
    }
}
