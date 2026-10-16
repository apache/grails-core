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
package grails.core.support.proxy

import spock.lang.Specification

class DefaultProxyHandlerSpec extends Specification {

    ProxyHandler handler = new DefaultProxyHandler()

    void 'isInitialized is always true'() {
        expect:
        handler.isInitialized(new Object())
        handler.isInitialized(new Object(), 'someAssociation')
    }

    void 'unwrapIfProxy returns the same instance'() {
        given:
        def instance = new Object()

        expect:
        handler.unwrapIfProxy(instance).is(instance)
    }

    void 'isProxy is always false'() {
        expect:
        !handler.isProxy(new Object())
    }

    void 'initialize does nothing and does not throw'() {
        when:
        handler.initialize(new Object())

        then:
        noExceptionThrown()
    }
}
