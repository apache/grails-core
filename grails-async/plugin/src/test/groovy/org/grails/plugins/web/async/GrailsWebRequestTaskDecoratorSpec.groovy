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
package org.grails.plugins.web.async

import org.springframework.web.context.request.RequestContextHolder

import grails.util.GrailsWebMockUtil
import org.grails.web.servlet.mvc.GrailsWebRequest
import spock.lang.Specification

class GrailsWebRequestTaskDecoratorSpec extends Specification {

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void 'propagates and then clears the current Grails web request'() {
        given:
        GrailsWebRequest original = GrailsWebMockUtil.bindMockWebRequest()
        Runnable decorated = new GrailsWebRequestTaskDecorator().decorate {
            assert GrailsWebRequest.lookup().currentRequest.is(original.currentRequest)
            assert !GrailsWebRequest.lookup().is(original)
        }
        RequestContextHolder.resetRequestAttributes()

        when:
        decorated.run()

        then:
        GrailsWebRequest.lookup() == null
    }

    void 'checked failures retain their identity and restore the previous context'() {
        given:
        def previous = GrailsWebMockUtil.bindMockWebRequest()
        def failure = new IOException('io')
        Runnable work = () -> { throw failure }
        def decorated = new GrailsWebRequestTaskDecorator().decorate(work)

        when:
        decorated.run()

        then:
        def observed = thrown(IOException)
        observed.is(failure)
        GrailsWebRequest.lookup().is(previous)
    }
}
