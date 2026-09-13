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
package grails.plugin.springsecurity

import spock.lang.Specification

class SecurityFilterPositionSpec extends Specification {

    void 'FIRST and LAST use their explicit extreme orders'() {
        expect:
        SecurityFilterPosition.FIRST.order == Integer.MIN_VALUE
        SecurityFilterPosition.LAST.order == Integer.MAX_VALUE
    }

    void 'ordinal based filters are spaced one hundred apart, starting after FIRST'() {
        expect:
        SecurityFilterPosition.DISABLE_ENCODE_URL_FILTER.order == SecurityFilterPosition.DISABLE_ENCODE_URL_FILTER.ordinal() * 100
        SecurityFilterPosition.SECURITY_CONTEXT_FILTER.order == SecurityFilterPosition.SECURITY_CONTEXT_FILTER.ordinal() * 100
        SecurityFilterPosition.FILTER_SECURITY_INTERCEPTOR.order > SecurityFilterPosition.SECURITY_CONTEXT_FILTER.order
        SecurityFilterPosition.values().findAll { it != SecurityFilterPosition.FIRST && it != SecurityFilterPosition.LAST }.every {
            it.order == it.ordinal() * 100
        }
    }

    void 'the declared order strictly increases from FIRST to LAST'() {
        expect:
        SecurityFilterPosition.values()*.order == SecurityFilterPosition.values()*.order.sort(false)
    }

}
