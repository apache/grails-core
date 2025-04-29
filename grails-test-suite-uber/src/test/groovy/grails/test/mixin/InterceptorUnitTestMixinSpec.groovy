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
package grails.test.mixin

import grails.artefact.Interceptor
import grails.testing.web.interceptor.InterceptorUnitTest
import spock.lang.Specification

/**
 * Created by graemerocher on 02/09/15.
 */
class InterceptorUnitTestMixinSpec extends Specification implements InterceptorUnitTest<TestInterceptor> {

    void "Test interceptor matching"() {
        when:"A request matches the interceptor"
        withRequest(controller:"foo", action:"bar")

        then:"The interceptor does match"
        interceptor.doesMatch()

        when:"A request matches the interceptor"
        withRequest(controller:"foo", action:"not")

        then:"The interceptor does match"
        !interceptor.doesMatch()

        when:"A request matches the interceptor"
        withRequest(controller:"foo")

        then:"The interceptor does match"
        !interceptor.doesMatch()

        when:"A request matches the interceptor"
        withRequest(controller:"bar", action:"not")

        then:"The interceptor does match"
        !interceptor.doesMatch()
    }
}

class TestInterceptor implements Interceptor {
    TestInterceptor() {
        match(controller:"foo", action:"bar")
    }
}
