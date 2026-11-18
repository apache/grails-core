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
package aiapp

import spock.lang.Specification

import grails.testing.ai.AiUnitTest
import grails.testing.services.ServiceUnitTest

class AnswerServiceSpec extends Specification implements ServiceUnitTest<AnswerService>, AiUnitTest {

    void 'answers with the scripted response and records the prompt'() {
        given:
        respondWith('Paris')

        expect:
        service.answer('Capital of France?') == 'Paris'
        prompts.last().userMessage.text == 'Capital of France?'
        prompts.last().systemMessage.text == 'You answer briefly.'
    }

    void 'streams the scripted response a word at a time'() {
        given:
        respondWith('Hello streaming world')

        expect:
        service.stream('hello').collectList().block() == ['Hello ', 'streaming ', 'world']
    }

}
