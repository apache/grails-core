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
package grails.validation

import spock.lang.Issue
import spock.lang.Specification

class ValidateableMockSpec extends Specification {

    @Issue('apache/grails-core#9761')
    void 'ensure command is mocked properly'(){
        given:
        SomeCommand command = GroovyMock()
        1 * command.validate() >> true
        1 * command.validate() >> false
        1 * command.validate() >> true
        1 * command.validate(_ as List) >> true
        1 * command.validate(_ as Map) >> false
        1 * command.validate(_ as List, _ as Map) >> true

        expect:
        command.validate()
        !command.validate()
        command.validate()
        command.validate([])
        !command.validate([:])
        command.validate([], [:])
    }
}

class SomeCommand implements Validateable {}
