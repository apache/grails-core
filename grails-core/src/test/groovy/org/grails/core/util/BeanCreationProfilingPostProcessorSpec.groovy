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
package org.grails.core.util

import org.springframework.context.event.ContextRefreshedEvent
import spock.lang.Specification

class BeanCreationProfilingPostProcessorSpec extends Specification {

    BeanCreationProfilingPostProcessor processor = new BeanCreationProfilingPostProcessor()

    void 'postProcessBeforeInstantiation returns null and does not throw'() {
        expect:
        processor.postProcessBeforeInstantiation(String, 'myBean') == null
    }

    void 'postProcessAfterInitialization returns the same bean unchanged'() {
        given:
        def bean = new Object()
        processor.postProcessBeforeInstantiation(String, 'myBean')

        expect:
        processor.postProcessAfterInitialization(bean, 'myBean').is(bean)
    }

    void 'onApplicationEvent completes the stopwatch without throwing'() {
        given:
        processor.postProcessBeforeInstantiation(String, 'myBean')
        processor.postProcessAfterInitialization(new Object(), 'myBean')
        def event = new ContextRefreshedEvent(Stub(org.springframework.context.ApplicationContext))

        when:
        processor.onApplicationEvent(event)

        then:
        noExceptionThrown()
    }
}
