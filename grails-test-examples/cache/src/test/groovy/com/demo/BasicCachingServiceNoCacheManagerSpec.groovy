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

package com.demo

import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class BasicCachingServiceNoCacheManagerSpec extends Specification implements ServiceUnitTest<BasicCachingService> {

    void 'test invoking cacheable method when no cache manager is present'() {
        when: 'a cached method is invoked the first time'
        def result = service.getData()

        then: 'the code in the method is executed'
        result == 'Hello World!'
        service.invocationCounter == 1

        when: 'a cached method is invoked the second time'
        result = service.getData()

        then: 'the code in the method is still executed because no cache manager is present'
        result == 'Hello World!'
        service.invocationCounter == 2
    }
}
