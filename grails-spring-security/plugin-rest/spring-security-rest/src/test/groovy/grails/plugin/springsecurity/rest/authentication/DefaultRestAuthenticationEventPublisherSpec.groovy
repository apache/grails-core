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
package grails.plugin.springsecurity.rest.authentication

import grails.plugin.springsecurity.rest.RestTokenCreationEvent
import grails.plugin.springsecurity.rest.token.AccessToken
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.authentication.event.AuthenticationSuccessEvent
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

@Subject(DefaultRestAuthenticationEventPublisher)
class DefaultRestAuthenticationEventPublisherSpec extends Specification {

    @Shared
    ApplicationEventPublisher eventPublisher

    @Shared
    AccessToken accessToken = new AccessToken("test")

    void setup() {
        eventPublisher = Mock(ApplicationEventPublisher)
    }

    void "should execute publishEvent when publisher is set in constructor"() {
        when:
        new DefaultRestAuthenticationEventPublisher(eventPublisher).publishTokenCreation(accessToken)

        then:
        1 * eventPublisher.publishEvent(_ as RestTokenCreationEvent)
    }

    void "should execute publishEvent when publisher is set with setter"() {
        when:
        DefaultRestAuthenticationEventPublisher publisher = new DefaultRestAuthenticationEventPublisher()
        publisher.setApplicationEventPublisher(eventPublisher)
        publisher.publishTokenCreation(accessToken)

        then:
        1 * eventPublisher.publishEvent(_ as RestTokenCreationEvent)
    }

    void "should execute parent publishEvent when publisher is set in constructor"() {
        when:
        new DefaultRestAuthenticationEventPublisher(eventPublisher).publishAuthenticationSuccess(accessToken)

        then:
        1 * eventPublisher.publishEvent(_ as AuthenticationSuccessEvent)
    }

    void "should execute parent publishEvent when publisher is set with setter"() {
        when:
        DefaultRestAuthenticationEventPublisher publisher = new DefaultRestAuthenticationEventPublisher()
        publisher.setApplicationEventPublisher(eventPublisher)
        publisher.publishAuthenticationSuccess(accessToken)

        then:
        1 * eventPublisher.publishEvent(_ as AuthenticationSuccessEvent)
    }

    void "should not execute publishEvent when publisher is not set"() {
        when:
        new DefaultRestAuthenticationEventPublisher().publishTokenCreation(accessToken)

        then:
        0 * eventPublisher.publishEvent(_)
    }
}
