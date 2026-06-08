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
package grails.plugin.springsecurity.rest

import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.rest.token.AccessToken
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationEvent
import org.springframework.context.support.GenericApplicationContext
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject


@Subject(RestSecurityEventListener)
class RestSecurityEventListenerSpec extends Specification {

    @Shared
    RestSecurityEventListener listener

    @Shared
    AccessToken accessToken = new AccessToken("test")

    void setConfig(String event, Closure closure) {
        ConfigObject co = new ConfigObject()
        co.put(event, closure)
        SpringSecurityUtils.securityConfig = co
    }

    void setup() {
        listener = new RestSecurityEventListener()
    }

    void "test closure gets executed for onRestTokenCreationEvent"() {
        setup:
        ApplicationEvent passedEvent
        ApplicationContext passedContext
        setConfig("onRestTokenCreationEvent") { ApplicationEvent e, ApplicationContext c ->
            passedEvent = e
            passedContext = c
        }

        when:
        listener.setApplicationContext(new GenericApplicationContext())
        listener.onApplicationEvent(new RestTokenCreationEvent(accessToken))

        then:
        passedEvent instanceof RestTokenCreationEvent
        passedContext instanceof GenericApplicationContext

    }

    void "test closure gets executed for onRestTokenCreationEvent with no context"() {
        setup:
        ApplicationEvent passedEvent
        ApplicationContext passedContext
        setConfig("onRestTokenCreationEvent") { ApplicationEvent e, ApplicationContext c ->
            passedEvent = e
            passedContext = c
        }

        when:
        listener.onApplicationEvent(new RestTokenCreationEvent(accessToken))

        then:
        passedEvent instanceof RestTokenCreationEvent
        passedContext == null
    }

    void "test closure gets executed for a standard spring security event"() {
        setup:
        ApplicationEvent passedEvent
        ApplicationContext passedContext
        setConfig("onInteractiveAuthenticationSuccessEvent") { ApplicationEvent e, ApplicationContext c ->
            passedEvent = e
            passedContext = c
        }

        when:
        listener.setApplicationContext(new GenericApplicationContext())
        listener.onApplicationEvent(new InteractiveAuthenticationSuccessEvent(accessToken, RestSecurityEventListenerSpec.class))

        then:
        passedEvent instanceof InteractiveAuthenticationSuccessEvent
        passedContext instanceof GenericApplicationContext
    }

    void "test closure gets executed for a standard spring security event with no context"() {
        setup:
        ApplicationEvent passedEvent
        ApplicationContext passedContext
        setConfig("onInteractiveAuthenticationSuccessEvent") { ApplicationEvent e, ApplicationContext c ->
            passedEvent = e
            passedContext = c
        }

        when:
        listener.onApplicationEvent(new InteractiveAuthenticationSuccessEvent(accessToken, RestSecurityEventListenerSpec.class))

        then:
        passedEvent instanceof InteractiveAuthenticationSuccessEvent
        passedContext == null
    }
}
