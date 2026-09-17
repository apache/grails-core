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
package org.grails.web.json

import spock.lang.Specification

class JSONExceptionSpec extends Specification {

    void 'constructing from a message keeps the message and has no cause'() {
        when:
        def ex = new JSONException('bad json')

        then:
        ex.message == 'bad json'
        ex.cause == null
    }

    void 'constructing from a Throwable carries it as the cause and uses its message'() {
        given:
        def original = new IllegalStateException('boom')

        when:
        def ex = new JSONException(original)

        then:
        ex.cause.is(original)
        ex.message == 'boom'
    }

}
