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

import grails.plugin.cache.CustomCacheKeyGenerator
import grails.plugin.cache.GrailsConcurrentMapCacheManager
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class BasicCachingServiceSpec extends Specification implements ServiceUnitTest<BasicCachingService> {

    @Override
    Closure doWithSpring() {{ ->
        grailsCacheManager(GrailsConcurrentMapCacheManager)
        customCacheKeyGenerator(CustomCacheKeyGenerator)
    }}

    void 'test invoking cacheable method when cache manager is present'() {
        when: 'a cached method is invoked the first time'
        def result = service.getData()

        then: 'the code in the method is executed'
        result == 'Hello World!'
        service.invocationCounter == 1

        when: 'a cached method is invoked the second time'
        result = service.getData()

        then: 'the cached return value is returned and the code in the method is not executed'
        result == 'Hello World!'
        service.invocationCounter == 1
    }

    void 'test invoking a cacheable method that expresses a condition'() {
        when: 'multiply is called with x < 10'
        def result = service.multiply(4, 7)

        then: 'the method should have been invoked'
        result == 28
        service.conditionalInvocationCounter == 1

        when: 'the method is invoked with x > 10'
        result = service.multiply(40, 7)

        then: 'the method should have executed'
        result == 280
        service.conditionalInvocationCounter == 2

        when: 'multiply is called with x < 10 with a cached value'
        result = service.multiply(4, 7)

        then: 'the method should not have executed'
        result == 28
        service.conditionalInvocationCounter == 2

        when: 'the method is invoked with x > 10 again'
        result = service.multiply(40, 7)

        then: 'the condition should prevent caching'
        result == 280
        service.conditionalInvocationCounter == 3
    }
}
