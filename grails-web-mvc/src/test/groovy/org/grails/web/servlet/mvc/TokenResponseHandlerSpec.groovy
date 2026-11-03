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
package org.grails.web.servlet.mvc

import spock.lang.Specification

class TokenResponseHandlerSpec extends Specification {

    void 'the interface constants are the historical attribute and session keys'() {
        expect:
        TokenResponseHandler.INVALID_TOKEN_ATTRIBUTE == 'invalidToken'
        TokenResponseHandler.KEY == 'org.codehaus.groovy.grails.TOKEN_RESPONSE_HANDLER'
    }

    void 'invalidToken delegates to invalidTokenInternal and records that it was invoked'() {
        given:
        List invocations = []
        AbstractTokenResponseHandler handler = new AbstractTokenResponseHandler(false) {
            @Override
            protected Object invalidTokenInternal(Closure callable) {
                invocations << callable
                'handled'
            }
        }
        Closure callable = { 'ignored' }

        expect:
        !handler.wasInvoked()
        handler.wasInvalidToken()

        when:
        Object result = handler.invalidToken(callable)

        then:
        result == 'handled'
        handler.wasInvoked()
        invocations == [callable]
    }

    void 'wasInvalidToken reflects the constructor-supplied validity, not invocation'() {
        given:
        AbstractTokenResponseHandler valid = new AbstractTokenResponseHandler(true) {
            @Override
            protected Object invalidTokenInternal(Closure callable) { null }
        }

        expect:
        !valid.wasInvalidToken()
        !valid.wasInvoked()
    }

}
