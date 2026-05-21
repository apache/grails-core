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
package grails.events

import org.grails.events.EventIdUtils
import spock.lang.Specification
import spock.lang.Unroll

class EventIdUtilsSpec extends Specification {

    @Unroll
    def 'For Method Name ( #methodName ) event Id should be ( #expected )'(String methodName, String expected) {

        expect:
        EventIdUtils.eventIdForMethodName(methodName) == expected

        where:
        methodName   || expected
        'onSum'      || 'sum'
        'sum'        || 'sum'
        'onSaveUser' || 'saveUser'
        'saveUser'   || 'saveUser'
        'SaveUser'   || 'SaveUser'
        'onS'        || 's'
        'on'         || 'on'
        'save user'  || 'save user'
    }

}
