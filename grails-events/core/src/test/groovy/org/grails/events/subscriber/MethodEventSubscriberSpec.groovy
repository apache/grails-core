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
package org.grails.events.subscriber

import grails.events.subscriber.MethodSubscriber
import spock.lang.Specification

/**
 * Created by graemerocher on 28/03/2017.
 */
class MethodEventSubscriberSpec extends Specification {

    void 'Test convert method argument'() {

        given: 'a class with a method foo(Integer)'
            def testService = new TestService()

        when: 'we create a method subscriber on that method'
            def subscriber = new MethodSubscriber(testService, TestService.getMethod('foo', Integer))

        then: 'the results of invoking the subscriber to be correct'
            subscriber.call(1) == 2
            subscriber.call('1') == 2
            subscriber.call('') == null
    }
}

class TestService {
    def foo(Integer num) {
       return num + 1
    }
}
